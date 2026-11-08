package org.proanima.shelter.repository

import org.proanima.shelter.service.ContentKey
import org.proanima.shelter.service.SiteContent
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("org.proanima.shelter.repository.SiteContent")

// Where the edited texts of the pages come from. Without a database nothing can be edited, so the pages have
// the texts that come with the code.
interface SiteContentRepository {
    fun load(): SiteContent
}

interface MutableSiteContentRepository : SiteContentRepository {
    // A key with a text is stored (replacing the old text), a key with null goes back to the text that comes with
    // the code. All of it happens together or not at all.
    fun update(changes: Map<ContentKey, String?>)
}

object NoSiteContent : SiteContentRepository {
    override fun load(): SiteContent = SiteContent.EMPTY
}

class InMemorySiteContentRepository(initial: Map<ContentKey, String> = emptyMap()) : MutableSiteContentRepository {

    @Volatile
    private var content = SiteContent(initial)

    override fun load(): SiteContent = content

    @Synchronized
    override fun update(changes: Map<ContentKey, String?>) {
        val entries = content.entries.toMutableMap()
        changes.forEach { (key, text) -> if (text == null) entries.remove(key) else entries[key] = text }
        content = SiteContent(entries)
    }
}

private val DEFAULT_CACHE_TTL: Duration = Duration.ofSeconds(30)
private val RETRY_PAUSE: Duration = Duration.ofSeconds(10)

// The same arrangement as the cats and dogs: read whole, kept in memory for a short time (every page asks for it
// and a free Neon database may be asleep), dropped at every write. Unlike a cat list, the page texts are
// decoration over a site that works without them: if the database cannot be reached and there is no older copy,
// the pages show the texts that come with the code instead of an error.
class PgSiteContentRepository(
    private val dataSource: DataSource,
    private val clock: Clock = Clock.systemUTC(),
    private val cacheTtl: Duration = DEFAULT_CACHE_TTL
) : MutableSiteContentRepository {

    private class Snapshot(val loadedAt: Instant, val content: SiteContent)

    @Volatile
    private var snapshot: Snapshot? = null

    // Counts writes. A read that started before a write must not put its (already stale) result in the cache.
    @Volatile
    private var generation = 0L

    // After a failed read the database is left alone for a few seconds: asking again at once would make every
    // page wait for the connection to time out.
    @Volatile
    private var retryAfter: Instant? = null

    override fun load(): SiteContent {
        val cached = snapshot
        val now = clock.instant()
        if (cached != null && Duration.between(cached.loadedAt, now) < cacheTtl) {
            return cached.content
        }
        if (retryAfter?.isAfter(now) == true) {
            return cached?.content ?: SiteContent.EMPTY
        }
        val startedAt = generation
        return try {
            read().also { content ->
                if (generation == startedAt) {
                    snapshot = Snapshot(now, content)
                }
            }
        } catch (e: SQLException) {
            retryAfter = now.plus(RETRY_PAUSE)
            if (cached == null) {
                log.warn("Could not read the page texts ({}); the pages use the texts that come with the code", e.message)
                SiteContent.EMPTY
            } else {
                log.warn("Could not read the page texts ({}); serving the copy from {}", e.message, cached.loadedAt)
                cached.content
            }
        }
    }

    override fun update(changes: Map<ContentKey, String?>) {
        if (changes.isEmpty()) {
            return
        }
        val now = OffsetDateTime.now(clock)
        dataSource.inTransaction { connection ->
            changes.forEach { (key, text) ->
                if (text == null) {
                    connection.prepareStatement("DELETE FROM site_content WHERE content_key = ? AND lang = ?").use { statement ->
                        statement.setString(1, key.key)
                        statement.setString(2, key.language)
                        statement.executeUpdate()
                    }
                } else {
                    upsert(connection, key, text, now)
                }
            }
        }
        generation++
        snapshot = null
        retryAfter = null
    }

    // Update first and insert only if nothing was there: plain SQL that PostgreSQL and H2 both accept.
    private fun upsert(connection: java.sql.Connection, key: ContentKey, text: String, now: OffsetDateTime) {
        val updated = connection.prepareStatement(
            "UPDATE site_content SET body = ?, updated_at = ? WHERE content_key = ? AND lang = ?"
        ).use { statement ->
            statement.setString(1, text)
            statement.setObject(2, now)
            statement.setString(3, key.key)
            statement.setString(4, key.language)
            statement.executeUpdate()
        }
        if (updated == 0) {
            connection.prepareStatement(
                "INSERT INTO site_content (content_key, lang, body, updated_at) VALUES (?, ?, ?, ?)"
            ).use { statement ->
                statement.setString(1, key.key)
                statement.setString(2, key.language)
                statement.setString(3, text)
                statement.setObject(4, now)
                statement.executeUpdate()
            }
        }
    }

    private fun read(): SiteContent = dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT content_key, lang, body FROM site_content").use { rows ->
                val entries = buildMap {
                    while (rows.next()) {
                        put(ContentKey(rows.getString(1), rows.getString(2)), rows.getString(3))
                    }
                }
                SiteContent(entries)
            }
        }
    }
}
