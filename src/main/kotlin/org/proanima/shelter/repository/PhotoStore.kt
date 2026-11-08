package org.proanima.shelter.repository

import java.security.MessageDigest
import java.sql.Connection
import java.time.Clock
import java.time.OffsetDateTime
import javax.sql.DataSource

class StoredPhoto(val name: String, val contentType: String, val bytes: ByteArray, val sha256: String)

// Photos live in the database because the hosting plan wipes the disk on every deploy. Names are the part of
// the public URL after /images/, for example "felix.jpg" for /images/felix.jpg.
interface PhotoStore {
    fun get(name: String): StoredPhoto?

    // The content hash is the ETag: asking for it does not load the picture itself.
    fun sha256(name: String): String?

    fun names(): Set<String>

    fun put(name: String, contentType: String, bytes: ByteArray)

    fun delete(name: String): Boolean
}

// Pictures that belong to the site's pages (the home page photos) and the placeholders for pets without a
// photo. They are part of the code: they are committed with the site and refreshed in the database on every
// start, while the photos of the cats and dogs are owned by the admin panel after the first seeding.
fun isSiteAsset(name: String): Boolean = name.startsWith("home-") || name.startsWith("default-")

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

class PgPhotoStore(
    private val dataSource: DataSource,
    private val clock: Clock = Clock.systemUTC()
) : PhotoStore {

    override fun get(name: String): StoredPhoto? = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT content_type, data, sha256 FROM photos WHERE name = ?").use { statement ->
            statement.setString(1, name)
            statement.executeQuery().use { rows ->
                if (rows.next()) StoredPhoto(name, rows.getString(1), rows.getBytes(2), rows.getString(3)) else null
            }
        }
    }

    override fun sha256(name: String): String? = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT sha256 FROM photos WHERE name = ?").use { statement ->
            statement.setString(1, name)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }
    }

    override fun names(): Set<String> = dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT name FROM photos").use { rows ->
                buildSet { while (rows.next()) add(rows.getString(1)) }
            }
        }
    }

    override fun put(name: String, contentType: String, bytes: ByteArray) {
        val now = OffsetDateTime.now(clock)
        dataSource.inTransaction { connection -> upsertPhoto(connection, name, contentType, bytes, now) }
    }

    override fun delete(name: String): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("DELETE FROM photos WHERE name = ?").use { statement ->
            statement.setString(1, name)
            statement.executeUpdate() > 0
        }
    }
}

// Replaces a photo of the same name. Update first and insert only if nothing was there: both steps are
// plain SQL, unlike "insert or update", which is spelled differently in PostgreSQL and H2.
internal fun upsertPhoto(connection: Connection, name: String, contentType: String, bytes: ByteArray, now: OffsetDateTime) {
    val hash = sha256Hex(bytes)
    val updated = connection.prepareStatement(
        "UPDATE photos SET content_type = ?, data = ?, sha256 = ?, created_at = ? WHERE name = ?"
    ).use { statement ->
        statement.setString(1, contentType)
        statement.setBytes(2, bytes)
        statement.setString(3, hash)
        statement.setObject(4, now)
        statement.setString(5, name)
        statement.executeUpdate()
    }
    if (updated == 0) {
        connection.prepareStatement(
            "INSERT INTO photos (name, content_type, data, sha256, created_at) VALUES (?, ?, ?, ?, ?)"
        ).use { statement ->
            statement.setString(1, name)
            statement.setString(2, contentType)
            statement.setBytes(3, bytes)
            statement.setString(4, hash)
            statement.setObject(5, now)
            statement.executeUpdate()
        }
    }
}
