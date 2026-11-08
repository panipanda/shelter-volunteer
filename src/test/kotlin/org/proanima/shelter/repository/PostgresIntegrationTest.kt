package org.proanima.shelter.repository

import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.proanima.shelter.model.FieldType
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.model.Section
import org.proanima.shelter.model.SectionField
import org.proanima.shelter.model.SectionItem
import org.proanima.shelter.service.ContentKey
import org.proanima.shelter.service.validatePets
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The other database tests run on H2, which speaks the PostgreSQL dialect but is not PostgreSQL. This one runs
// the same code on the real thing, and only when TEST_DATABASE_URL is set (a postgresql:// string, for example
// from a throwaway Neon branch). It works in a schema of its own that it drops at the end, so it never touches
// the tables of the site even if the string points at the real database.
class PostgresIntegrationTest {

    @Test
    fun `the repositories, photos and seeding work on a real PostgreSQL`() {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL is not set")
        checkOnPostgres(parseDatabaseUrl(url!!))
    }
}

// Shared with the one-off run against a local PostgreSQL: takes a connection config, does everything inside
// a new schema and removes the schema afterwards.
fun checkOnPostgres(config: DatabaseConfig) {
    val schema = "shelter_test_" + UUID.randomUUID().toString().replace("-", "").take(12)
    val separator = if ('?' in config.jdbcUrl) "&" else "?"
    val admin = createDataSource(config)
    try {
        admin.connection.use { it.createStatement().use { statement -> statement.execute("CREATE SCHEMA $schema") } }
        val dataSource = createDataSource(DatabaseConfig("${config.jdbcUrl}${separator}currentSchema=$schema", config.username, config.password))
        try {
            runChecks(dataSource)
        } finally {
            dataSource.close()
        }
    } finally {
        admin.connection.use { it.createStatement().use { statement -> statement.execute("DROP SCHEMA IF EXISTS $schema CASCADE") } }
        admin.close()
    }
}

private fun runChecks(dataSource: HikariDataSource) {
    val clock = TestClock()
    createSchema(dataSource)
    createSchema(dataSource) // a second start must not fail on the tables that already exist

    val cats = PgCatRepository(dataSource, clock)
    val dogs = PgDogRepository(dataSource, clock)
    val photos = PgPhotoStore(dataSource, clock)
    fun seed() = loadSeedData(File("data/cats.json"), File("data/dogs.json"), File("data/images"))

    // First start: the real content goes in, and what comes out is what went in (Cyrillic and Serbian included).
    assertTrue(seedIfNeeded(dataSource, clock, ::seed))
    assertFalse(seedIfNeeded(dataSource, clock, ::seed))
    assertEquals(seed().cats.sortedBy { it.id }, cats.findAll())
    assertEquals(seed().dogs.sortedBy { it.id }, dogs.findAll())
    val names = photos.names()
    assertEquals(emptyList(), validatePets(cats.findAll(), dogs.findAll(), "the database") { it in names })

    // The first big photo: stored whole, with the hash of its bytes.
    val original = File("data/images/felix.jpg").readBytes()
    assertContentEquals(original, photos.get("felix.jpg")!!.bytes)
    assertEquals(sha256Hex(original), photos.sha256("felix.jpg"))

    // New pets carry on counting after the ids that came from the files.
    val newCat = cats.save(cats.findAll().first().copy(id = 0))
    assertEquals(seed().cats.maxOf { it.id } + 1, newCat.id)
    assertEquals(newCat, cats.findById(newCat.id))
    val newDog = dogs.save(dogs.findAll().first().copy(id = 0))
    assertEquals(seed().dogs.maxOf { it.id } + 1, newDog.id)

    // Edit, replace a photo, delete.
    cats.save(newCat.copy(name = newCat.name?.copy(ru = "Новое имя")))
    assertEquals("Новое имя", cats.findById(newCat.id)?.name?.ru)
    photos.put("felix.jpg", "image/png", byteArrayOf(1, 2, 3))
    assertEquals("image/png", photos.get("felix.jpg")?.contentType)
    assertTrue(cats.delete(newCat.id))
    assertNull(cats.findById(newCat.id))
    assertTrue(photos.delete("felix.jpg"))

    // The texts of the pages: written, replaced, read back (Cyrillic, a long guide, a value for every language),
    // removed, and put in by the seeder from a backup.
    val content = PgSiteContentRepository(dataSource, clock)
    val headline = ContentKey("home.hero.headline", "ru")
    val guide = "# Гайд\n\n" + "Строка гайда.\n".repeat(5_000)
    content.update(mapOf(headline to "Первый", ContentKey("guide.markdown", "ru") to guide, ContentKey("contact.nata.telegram") to "New_Nata"))
    content.update(mapOf(headline to "Второй"))
    assertEquals("Второй", content.load().text("home.hero.headline", "ru"))
    assertEquals(guide, content.load().text("guide.markdown", "ru"))
    assertEquals("New_Nata", content.load().plain("contact.nata.telegram"))
    content.update(mapOf(headline to null, ContentKey("guide.markdown", "ru") to null))
    assertEquals(mapOf(ContentKey("contact.nata.telegram") to "New_Nata"), content.load().entries)

    checkSections(dataSource, clock)
}

// The sections made in the admin panel: two tables of documents, read back as written (Cyrillic and Serbian
// letters, a long text), the entries going with their section, and a backup's numbers carried on from.
private fun checkSections(dataSource: HikariDataSource, clock: TestClock) {
    val sections = PgSectionRepository(dataSource, clock)
    val section = sections.saveSection(
        Section(
            id = 0, slug = "articles", title = LocalizedText("Статьи", "Articles", "Članci"), position = 1, useDate = true,
            fields = listOf(SectionField("f1", FieldType.MARKDOWN, LocalizedText("Текст")))
        )
    )
    val long = "## Заголовок\n\n" + "Строка с čćžšđ.\n".repeat(2_000)
    val item = sections.saveItem(
        SectionItem(id = 0, sectionId = section.id, title = LocalizedText("Запись"), texts = mapOf("f1" to LocalizedText(long)), published = true)
    )
    assertEquals(listOf(section), sections.findSections())
    assertEquals(long, sections.findItems().single().texts.getValue("f1").ru)
    assertEquals(item, sections.findItems().single())
    assertTrue(sections.deleteSection(section.id))
    assertEquals(emptyList(), sections.findItems())
    assertEquals(emptyList(), sections.findSections())
}
