package org.proanima.shelter.repository

import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.model.MenuNode
import org.proanima.shelter.model.MenuTree
import java.io.File
import java.sql.Connection
import java.sql.SQLException
import java.time.Duration
import javax.sql.DataSource
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The two stores of the menu tree (the database one the site runs on, the in-memory one that stands in for it when the
// site runs on files) behave the same, so one set of checks runs on both.
class MenuRepositoryTest {

    private val dataSource = newTestDataSource()
    private val clock = TestClock()

    private val tree = MenuTree(
        listOf(
            MenuNode("group:g1", position = 10, title = LocalizedText("Животные", "Animals", "Životinje"), menuTitle = LocalizedText("Звери")),
            MenuNode("page:cats", parent = "group:g1", position = 10),
            MenuNode("page:guide", position = 20, hidden = true)
        ),
        nextGroupNumber = 2
    )

    private fun stores(): List<Pair<String, MutableMenuRepository>> = listOf(
        "database" to PgMenuRepository(dataSource, clock, Duration.ofSeconds(30)),
        "memory" to InMemoryMenuRepository()
    )

    @Test
    fun `nothing is saved at first, and what is read is what was saved`() {
        stores().forEach { (name, store) ->
            assertNull(store.load(), name)

            store.save(tree)

            assertEquals(tree, store.load(), name)
        }
    }

    @Test
    fun `saving again replaces the tree, in one row`() {
        stores().forEach { (name, store) ->
            store.save(tree)
            store.save(tree.copy(nodes = tree.nodes.drop(1), nextGroupNumber = 3))

            assertEquals(2, store.load()!!.nodes.size, name)
            assertEquals(3, store.load()!!.nextGroupNumber, name)
        }
        val rows = dataSource.connection.use { c -> c.createStatement().use { s -> s.executeQuery("SELECT COUNT(*) FROM site_menu").use { it.next(); it.getInt(1) } } }
        assertEquals(1, rows)
    }

    @Test
    fun `the letters of all three languages survive the database`() {
        val store = PgMenuRepository(dataSource, clock)

        store.save(tree)

        assertEquals(LocalizedText("Животные", "Animals", "Životinje"), store.load()!!.node("group:g1")!!.title)
    }

    private fun MenuTree.node(id: String) = nodes.first { it.id == id }

    @Test
    fun `the tree is cached for the cache lifetime and read again after it, and a save drops the cache at once`() {
        val counting = MenuCountingSource(dataSource)
        val store = PgMenuRepository(counting, clock, Duration.ofSeconds(30))
        store.save(tree)
        counting.connections = 0

        store.load()
        store.load()
        clock.now = clock.now.plusSeconds(29)
        store.load()
        assertEquals(1, counting.connections)

        clock.now = clock.now.plusSeconds(2)
        store.load()
        assertEquals(2, counting.connections)

        store.save(tree.copy(nextGroupNumber = 9))
        assertEquals(9, store.load()!!.nextGroupNumber)
    }

    @Test
    fun `a tree that was never saved is cached too`() {
        val counting = MenuCountingSource(dataSource)
        val store = PgMenuRepository(counting, clock, Duration.ofSeconds(30))

        assertNull(store.load())
        assertNull(store.load())

        assertEquals(1, counting.connections)
    }

    @Test
    fun `when the database is down an older copy is served, and with no copy the error shows`() {
        val flaky = MenuFlakySource(dataSource)
        val store = PgMenuRepository(flaky, clock, Duration.ofSeconds(30))
        store.save(tree)

        flaky.down = true
        assertFailsWith<SQLException> { store.load() }

        flaky.down = false
        assertEquals(tree, store.load())

        flaky.down = true
        clock.now = clock.now.plusSeconds(120)
        assertEquals(tree, store.load())
    }

    @Test
    fun `a document with fields from a newer version, or without fields added later, is still read`() {
        dataSource.connection.use { c ->
            c.prepareStatement("INSERT INTO site_menu (id, data, updated_at) VALUES (1, ?, CURRENT_TIMESTAMP)").use {
                it.setString(1, """{"nodes":[{"id":"page:cats","somethingNew":1}]}""")
                it.executeUpdate()
            }
        }

        assertEquals(MenuTree(listOf(MenuNode("page:cats"))), PgMenuRepository(dataSource, clock).load())
    }

    // ---- the file

    @Test
    fun `the menu file is optional, round-trips, and a broken one is an error, not a silent gap`() {
        val dir = createTempDirectory("menu").toFile().also { it.deleteOnExit() }
        val file = File(dir, MENU_FILE_NAME)

        assertNull(readMenuFile(file))

        file.writeText(menuToJson(tree))
        assertEquals(tree, readMenuFile(file))

        file.writeText("{ not json")
        assertFailsWith<Exception> { readMenuFile(file) }
    }

    // ---- the first start

    @Test
    fun `the menu of a backup is put in the database with the rest, and nothing is put when the backup has none`() {
        val seeded = newTestDataSource()
        val withMenu = SeedData(emptyList(), emptyList(), emptyList(), menu = tree)

        assertTrue(seedIfNeeded(seeded, clock) { withMenu })

        assertEquals(tree, PgMenuRepository(seeded, clock).load())

        val bare = newTestDataSource()
        seedIfNeeded(bare, clock) { SeedData(emptyList(), emptyList(), emptyList()) }
        assertNull(PgMenuRepository(bare, clock).load())
    }

    @Test
    fun `a seeding that fails leaves no menu behind`() {
        val seeded = newTestDataSource()
        val real = loadSeedData(File("data/cats.json"), File("data/dogs.json"), File("data/images"))
        // Two cats with one id: the insert fails after the menu was already written in the same transaction.
        val broken = SeedData(real.cats + real.cats.first(), real.dogs, real.photos, menu = tree)

        assertFailsWith<Exception> { seedIfNeeded(seeded, clock) { broken } }

        assertNull(PgMenuRepository(seeded, clock).load())
    }
}

private class MenuCountingSource(private val delegate: DataSource) : DataSource by delegate {
    var connections = 0

    override fun getConnection(): Connection {
        connections++
        return delegate.connection
    }
}

private class MenuFlakySource(private val delegate: DataSource) : DataSource by delegate {
    var down = false

    override fun getConnection(): Connection {
        if (down) throw SQLException("connection refused")
        return delegate.connection
    }
}
