package org.proanima.shelter.repository

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.proanima.shelter.model.Cat
import org.proanima.shelter.model.Dog
import org.proanima.shelter.model.MenuTree
import org.proanima.shelter.model.Section
import org.proanima.shelter.model.SectionItem
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("org.proanima.shelter.repository.Pg")

// Defaults are written out too, so that changing a default in the code later does not silently change
// the pets that were saved before.
internal val documentJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val DEFAULT_CACHE_TTL: Duration = Duration.ofSeconds(30)

// One table of JSON documents: a row is a cat or a dog as the existing serializable model, so a new field
// needs no migration. The list is cached in memory for a short time because every page asks for it and the
// database may be asleep (a free Neon database takes a moment to wake up); saving or deleting drops the cache.
// If the database cannot be reached and there is an older copy, the older copy is served instead of an error.
internal class PgDocuments<T>(
    private val dataSource: DataSource,
    private val table: String,
    private val serializer: KSerializer<T>,
    private val idOf: (T) -> Int,
    private val withId: (T, Int) -> T,
    private val clock: Clock,
    private val cacheTtl: Duration
) {
    private class Snapshot<T>(val loadedAt: Instant, val items: List<T>)

    @Volatile
    private var snapshot: Snapshot<T>? = null

    // Counts writes. A read that started before a write must not put its (already stale) result in the cache.
    @Volatile
    private var generation = 0L

    fun findAll(): List<T> {
        val cached = snapshot
        val now = clock.instant()
        if (cached != null && Duration.between(cached.loadedAt, now) < cacheTtl) {
            return cached.items
        }
        val startedAt = generation
        return try {
            load().also { items ->
                if (generation == startedAt) {
                    snapshot = Snapshot(now, items)
                }
            }
        } catch (e: SQLException) {
            if (cached == null) throw e
            log.warn("Could not read {} ({}); serving the copy from {}", table, e.message, cached.loadedAt)
            cached.items
        }
    }

    fun findById(id: Int): T? = findAll().firstOrNull { idOf(it) == id }

    // A new item (id 0) is inserted and gets the id the database chose; an existing one is replaced.
    fun save(item: T): T {
        val now = OffsetDateTime.now(clock)
        val saved = dataSource.inTransaction { connection ->
            val id = idOf(item)
            if (id == 0) {
                val newId = connection.prepareStatement(
                    "INSERT INTO $table (data, updated_at) VALUES (?, ?)",
                    arrayOf("id")
                ).use { statement ->
                    statement.setString(1, encode(item))
                    statement.setObject(2, now)
                    statement.executeUpdate()
                    statement.generatedKeys.use { keys ->
                        check(keys.next()) { "The database did not return the id of the new row in $table" }
                        keys.getInt(1)
                    }
                }
                // The document carries its own id, so write it again now that the id is known.
                val withRealId = withId(item, newId)
                connection.prepareStatement("UPDATE $table SET data = ? WHERE id = ?").use { statement ->
                    statement.setString(1, encode(withRealId))
                    statement.setInt(2, newId)
                    statement.executeUpdate()
                }
                withRealId
            } else {
                val updated = connection.prepareStatement("UPDATE $table SET data = ?, updated_at = ? WHERE id = ?").use { statement ->
                    statement.setString(1, encode(item))
                    statement.setObject(2, now)
                    statement.setInt(3, id)
                    statement.executeUpdate()
                }
                if (updated == 0) {
                    throw NoSuchElementException("There is no row $id in $table to update")
                }
                item
            }
        }
        invalidate()
        return saved
    }

    fun delete(id: Int): Boolean {
        val deleted = dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM $table WHERE id = ?").use { statement ->
                statement.setInt(1, id)
                statement.executeUpdate() > 0
            }
        }
        invalidate()
        return deleted
    }

    private fun invalidate() {
        generation++
        snapshot = null
    }

    private fun encode(item: T): String = documentJson.encodeToString(serializer, item)

    private fun load(): List<T> = dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT id, data FROM $table ORDER BY id").use { rows ->
                buildList {
                    while (rows.next()) {
                        // The id column is the truth; the id inside the document is only a copy of it.
                        add(withId(documentJson.decodeFromString(serializer, rows.getString(2)), rows.getInt(1)))
                    }
                }
            }
        }
    }
}

class PgCatRepository(
    dataSource: DataSource,
    clock: Clock = Clock.systemUTC(),
    cacheTtl: Duration = DEFAULT_CACHE_TTL
) : MutableCatRepository {

    private val documents = PgDocuments(dataSource, "cats", Cat.serializer(), Cat::id, { cat, id -> cat.copy(id = id) }, clock, cacheTtl)

    override fun findAll(): List<Cat> = documents.findAll()

    override fun findById(id: Int): Cat? = documents.findById(id)

    override fun save(cat: Cat): Cat = documents.save(cat)

    override fun delete(id: Int): Boolean = documents.delete(id)
}

class PgDogRepository(
    dataSource: DataSource,
    clock: Clock = Clock.systemUTC(),
    cacheTtl: Duration = DEFAULT_CACHE_TTL
) : MutableDogRepository {

    private val documents = PgDocuments(dataSource, "dogs", Dog.serializer(), Dog::id, { dog, id -> dog.copy(id = id) }, clock, cacheTtl)

    override fun findAll(): List<Dog> = documents.findAll()

    override fun save(dog: Dog): Dog = documents.save(dog)

    override fun delete(id: Int): Boolean = documents.delete(id)
}

// Two tables of documents, like the cats and the dogs: the sections, and the entries of all of them. An entry holds
// the id of its section.
class PgSectionRepository(
    private val dataSource: DataSource,
    clock: Clock = Clock.systemUTC(),
    cacheTtl: Duration = DEFAULT_CACHE_TTL
) : MutableSectionRepository {

    private val sections = PgDocuments(dataSource, "sections", Section.serializer(), Section::id, { section, id -> section.copy(id = id) }, clock, cacheTtl)
    private val items = PgDocuments(dataSource, "section_items", SectionItem.serializer(), SectionItem::id, { item, id -> item.copy(id = id) }, clock, cacheTtl)

    override fun findSections(): List<Section> = sections.findAll().sortedWith(sectionOrder)

    override fun findItems(): List<SectionItem> = items.findAll()

    override fun saveSection(section: Section): Section = sections.save(section)

    // The entries go first: a section that is gone must not leave entries that no page can reach.
    override fun deleteSection(id: Int): Boolean {
        items.findAll().filter { it.sectionId == id }.forEach { items.delete(it.id) }
        return sections.delete(id)
    }

    override fun saveItem(item: SectionItem): SectionItem = items.save(item)

    override fun deleteItem(id: Int): Boolean = items.delete(id)
}

// The menu tree: one document in one row. It is cached for a short time like the other documents, because every page
// asks for it, and an older copy is served when the database cannot be reached.
class PgMenuRepository(
    private val dataSource: DataSource,
    private val clock: Clock = Clock.systemUTC(),
    private val cacheTtl: Duration = DEFAULT_CACHE_TTL
) : MutableMenuRepository {

    // The tree is null while nothing was ever saved; that is an answer worth caching too.
    private class Snapshot(val loadedAt: Instant, val tree: MenuTree?)

    @Volatile
    private var snapshot: Snapshot? = null

    // Counts writes. A read that started before a write must not put its (already stale) result in the cache.
    @Volatile
    private var generation = 0L

    override fun load(): MenuTree? {
        val cached = snapshot
        val now = clock.instant()
        if (cached != null && Duration.between(cached.loadedAt, now) < cacheTtl) {
            return cached.tree
        }
        val startedAt = generation
        return try {
            read().also { tree ->
                if (generation == startedAt) {
                    snapshot = Snapshot(now, tree)
                }
            }
        } catch (e: SQLException) {
            if (cached == null) throw e
            log.warn("Could not read {} ({}); serving the copy from {}", MENU_TABLE, e.message, cached.loadedAt)
            cached.tree
        }
    }

    override fun save(tree: MenuTree) {
        val now = OffsetDateTime.now(clock)
        val data = documentJson.encodeToString(MenuTree.serializer(), tree)
        dataSource.inTransaction { connection -> upsertMenu(connection, data, now) }
        generation++
        snapshot = null
    }

    private fun read(): MenuTree? = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT data FROM $MENU_TABLE WHERE id = ?").use { statement ->
            statement.setInt(1, MENU_ROW_ID)
            statement.executeQuery().use { rows ->
                if (rows.next()) documentJson.decodeFromString(MenuTree.serializer(), rows.getString(1)) else null
            }
        }
    }
}

private const val MENU_TABLE = "site_menu"
private const val MENU_ROW_ID = 1

// Update first and insert when there was no row, as the photos are saved.
internal fun upsertMenu(connection: java.sql.Connection, data: String, now: OffsetDateTime) {
    val updated = connection.prepareStatement("UPDATE $MENU_TABLE SET data = ?, updated_at = ? WHERE id = ?").use { statement ->
        statement.setString(1, data)
        statement.setObject(2, now)
        statement.setInt(3, MENU_ROW_ID)
        statement.executeUpdate()
    }
    if (updated == 0) {
        connection.prepareStatement("INSERT INTO $MENU_TABLE (id, data, updated_at) VALUES (?, ?, ?)").use { statement ->
            statement.setInt(1, MENU_ROW_ID)
            statement.setString(2, data)
            statement.setObject(3, now)
            statement.executeUpdate()
        }
    }
}
