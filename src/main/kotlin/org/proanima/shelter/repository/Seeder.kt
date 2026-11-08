package org.proanima.shelter.repository

import kotlinx.serialization.KSerializer
import org.proanima.shelter.model.Cat
import org.proanima.shelter.model.Dog
import org.proanima.shelter.service.ContentKey
import java.io.File
import java.sql.Connection
import java.time.Clock
import java.time.OffsetDateTime
import javax.sql.DataSource

private const val SEEDED_KEY = "seeded_at"

class SeedPhoto(val name: String, val contentType: String, val bytes: ByteArray)

// What the first start puts into an empty database: the cats, the dogs and their photos, as they are in the
// repository's data folder, and the edited texts of the pages if a backup brought a file of them.
class SeedData(
    val cats: List<Cat>,
    val dogs: List<Dog>,
    val photos: List<SeedPhoto>,
    val siteContent: Map<ContentKey, String> = emptyMap()
)

fun contentTypeFor(fileName: String): String? = when (fileName.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "svg" -> "image/svg+xml"
    else -> null
}

fun loadSeedData(catsFile: File, dogsFile: File, imagesDir: File, siteContentFile: File? = null): SeedData = SeedData(
    cats = JsonCatRepository(catsFile.path).findAll(),
    dogs = JsonDogRepository(dogsFile.path).findAll(),
    photos = imageFiles(imagesDir).filterNot { isSiteAsset(it.name) }.map(::toSeedPhoto),
    siteContent = siteContentFile?.let(::readSiteContentFile).orEmpty()
)

private fun imageFiles(imagesDir: File): List<File> =
    imagesDir.listFiles { file -> file.isFile && contentTypeFor(file.name) != null }.orEmpty().sortedBy { it.name }

private fun toSeedPhoto(file: File) = SeedPhoto(file.name, requireNotNull(contentTypeFor(file.name)), file.readBytes())

// Fills an empty database once. The mark is written in the same transaction as the content, so a start that
// fails halfway leaves nothing behind and the next start tries again. It is a mark and not "the tables are
// empty" on purpose: if the shelter removes its last dog, a restart must not bring the sample dogs back.
// Returns true if it seeded.
fun seedIfNeeded(dataSource: DataSource, clock: Clock, load: () -> SeedData): Boolean {
    if (readMeta(dataSource, SEEDED_KEY) != null) {
        return false
    }
    val data = load()
    val now = OffsetDateTime.now(clock)
    dataSource.inTransaction { connection ->
        data.photos.forEach { upsertPhoto(connection, it.name, it.contentType, it.bytes, now) }
        insertDocuments(connection, "cats", Cat.serializer(), data.cats, Cat::id, now)
        insertDocuments(connection, "dogs", Dog.serializer(), data.dogs, Dog::id, now)
        insertSiteContent(connection, data.siteContent, now)
        connection.prepareStatement("INSERT INTO app_meta (meta_key, meta_value) VALUES (?, ?)").use { statement ->
            statement.setString(1, SEEDED_KEY)
            statement.setString(2, now.toString())
            statement.executeUpdate()
        }
    }
    return true
}

private fun insertSiteContent(connection: Connection, content: Map<ContentKey, String>, now: OffsetDateTime) {
    if (content.isEmpty()) {
        return
    }
    connection.prepareStatement("INSERT INTO site_content (content_key, lang, body, updated_at) VALUES (?, ?, ?, ?)").use { statement ->
        content.forEach { (key, text) ->
            statement.setString(1, key.key)
            statement.setString(2, key.language)
            statement.setString(3, text)
            statement.setObject(4, now)
            statement.executeUpdate()
        }
    }
}

// The ids come from the files, so the database must carry on counting after the largest of them.
private fun <T> insertDocuments(
    connection: Connection,
    table: String,
    serializer: KSerializer<T>,
    items: List<T>,
    idOf: (T) -> Int,
    now: OffsetDateTime
) {
    if (items.isEmpty()) {
        return
    }
    connection.prepareStatement("INSERT INTO $table (id, data, updated_at) VALUES (?, ?, ?)").use { statement ->
        items.forEach { item ->
            statement.setInt(1, idOf(item))
            statement.setString(2, documentJson.encodeToString(serializer, item))
            statement.setObject(3, now)
            statement.executeUpdate()
        }
    }
    val next = items.maxOf(idOf) + 1
    connection.createStatement().use { it.execute("ALTER TABLE $table ALTER COLUMN id RESTART WITH $next") }
}

private fun readMeta(dataSource: DataSource, key: String): String? = dataSource.connection.use { connection ->
    connection.prepareStatement("SELECT meta_value FROM app_meta WHERE meta_key = ?").use { statement ->
        statement.setString(1, key)
        statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
    }
}

// The page photos and placeholders are part of the code, so the committed files win over the database on every
// start: a new hero photo reaches the site with the deploy, without anyone touching the database.
// Returns the names that were added or replaced.
fun syncSiteAssets(store: PhotoStore, imagesDir: File): List<String> =
    imageFiles(imagesDir).filter { isSiteAsset(it.name) }.mapNotNull { file ->
        val photo = toSeedPhoto(file)
        if (store.sha256(photo.name) == sha256Hex(photo.bytes)) {
            null
        } else {
            store.put(photo.name, photo.contentType, photo.bytes)
            photo.name
        }
    }
