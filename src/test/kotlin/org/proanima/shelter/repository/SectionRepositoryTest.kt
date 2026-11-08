package org.proanima.shelter.repository

import org.proanima.shelter.model.ChoiceOption
import org.proanima.shelter.model.FieldType
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.model.Section
import org.proanima.shelter.model.SectionField
import org.proanima.shelter.model.SectionItem
import org.proanima.shelter.model.SectionLayout
import java.io.File
import java.time.Duration
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The two stores of the sections (the database one the site runs on, the in-memory one that stands in for it when
// the site runs on files) behave the same, so one set of checks runs on both.
class SectionRepositoryTest {

    private val dataSource = newTestDataSource()
    private val clock = TestClock()

    private fun text(ru: String, en: String? = null) = LocalizedText(ru, en)

    private val section = Section(
        id = 0, slug = "articles", title = text("Статьи", "Articles"), position = 2, useDate = true,
        tags = listOf(ChoiceOption("health", text("Здоровье", "Health"))),
        fields = listOf(
            SectionField("f1", FieldType.MARKDOWN, text("Текст"), required = true, showLabel = false),
            SectionField("f2", FieldType.CHOICE, text("Статус"), options = listOf(ChoiceOption("a", text("А")), ChoiceOption("b", text("Б"))))
        ),
        nextKeyNumber = 3
    )

    private fun entry(sectionId: Int, title: String) = SectionItem(
        id = 0, sectionId = sectionId, title = text(title, "T"), date = "2026-11-01", tags = listOf("health"),
        texts = mapOf("f1" to text("## Заголовок\n\nТекст с «кавычками» и Srpski: čćžšđ", "Text")), plain = mapOf("f2" to "a"),
        photoUrls = listOf("/images/item-1-aaaa1111.jpg"), published = true, createdAt = "2026-11-01T10:00:00+01:00"
    )

    private fun stores(): List<Pair<String, MutableSectionRepository>> = listOf(
        "database" to PgSectionRepository(dataSource, clock, Duration.ofSeconds(30)),
        "memory" to InMemorySectionRepository()
    )

    @Test
    fun `a new section and a new entry get their ids, and what is read is what was saved`() {
        stores().forEach { (name, store) ->
            val saved = store.saveSection(section)
            val item = store.saveItem(entry(saved.id, "Первая"))

            assertTrue(saved.id > 0, name)
            assertEquals(section.copy(id = saved.id), saved, name)
            assertEquals(listOf(saved), store.findSections(), name)
            assertEquals(listOf(item), store.findItems(), name)
            assertEquals(saved.id, item.sectionId, name)
            assertEquals("Текст с «кавычками» и Srpski: čćžšđ", item.texts.getValue("f1").ru?.substringAfter("\n\n"), name)
        }
    }

    @Test
    fun `saving again replaces, and saving something that does not exist fails`() {
        stores().forEach { (name, store) ->
            val saved = store.saveSection(section)
            store.saveSection(saved.copy(title = text("Новое")))
            val item = store.saveItem(entry(saved.id, "Первая"))
            store.saveItem(item.copy(published = false))

            assertEquals("Новое", store.findSections().single().title.ru, name)
            assertFalse(store.findItems().single().published, name)
            assertFailsWith<NoSuchElementException>(name) { store.saveSection(saved.copy(id = 999)) }
            assertFailsWith<NoSuchElementException>(name) { store.saveItem(item.copy(id = 999)) }
        }
    }

    @Test
    fun `sections come from the smallest position up, then in the order they were made`() {
        stores().forEach { (name, store) ->
            val third = store.saveSection(section.copy(slug = "c", position = 5))
            val first = store.saveSection(section.copy(slug = "a", position = 1))
            val second = store.saveSection(section.copy(slug = "b", position = 1))

            assertEquals(listOf(first.id, second.id, third.id), store.findSections().map { it.id }, name)
        }
    }

    @Test
    fun `deleting an entry removes it, and deleting a section takes its entries with it and no others`() {
        stores().forEach { (name, store) ->
            val a = store.saveSection(section.copy(slug = "a"))
            val b = store.saveSection(section.copy(slug = "b"))
            val one = store.saveItem(entry(a.id, "Один"))
            store.saveItem(entry(a.id, "Два"))
            val three = store.saveItem(entry(b.id, "Три"))

            assertTrue(store.deleteItem(one.id), name)
            assertFalse(store.deleteItem(one.id), name)
            assertEquals(2, store.findItems().size, name)

            assertTrue(store.deleteSection(a.id), name)
            assertFalse(store.deleteSection(a.id), name)
            assertEquals(listOf(b.id), store.findSections().map { it.id }, name)
            assertEquals(listOf(three.id), store.findItems().map { it.id }, name)
        }
    }

    @Test
    fun `a database read after a write sees the write at once, not the cached copy`() {
        val store = PgSectionRepository(dataSource, clock, Duration.ofHours(1))
        assertTrue(store.findSections().isEmpty())

        val saved = store.saveSection(section)

        assertEquals(listOf(saved), store.findSections())
        store.deleteSection(saved.id)
        assertTrue(store.findSections().isEmpty())
    }

    @Test
    fun `an entry saved before a field existed reads as before`() {
        // A document written by an older version has none of the newer keys.
        dataSource.connection.use { connection ->
            connection.createStatement().use {
                it.execute("INSERT INTO sections (data, updated_at) VALUES ('{\"id\":1,\"slug\":\"old\",\"title\":{\"ru\":\"Старый\"}}', CURRENT_TIMESTAMP)")
                it.execute("INSERT INTO section_items (data, updated_at) VALUES ('{\"id\":1,\"sectionId\":1,\"title\":{\"ru\":\"Запись\"}}', CURRENT_TIMESTAMP)")
            }
        }
        val store = PgSectionRepository(dataSource, clock)

        val old = store.findSections().single()
        val item = store.findItems().single()

        assertEquals(SectionLayout.CARDS, old.layout)
        assertTrue(old.usePhotos)
        assertEquals(emptyList(), old.fields)
        assertFalse(item.published)
        assertNull(item.date)
        assertEquals(emptyList(), item.photoUrls)
    }

    @Test
    fun `no sections at all is an empty site`() {
        assertEquals(emptyList(), NoSections.findSections())
        assertEquals(emptyList(), NoSections.findItems())
    }

    // ---- files, for the backup and the first start

    @Test
    fun `sections and entries go to files and come back unchanged`() {
        val store = InMemorySectionRepository()
        val saved = store.saveSection(section)
        store.saveItem(entry(saved.id, "Первая"))
        val dir = createTempDirectory("sections").toFile()
        File(dir, SECTIONS_FILE_NAME).writeText(sectionsToJson(store.findSections()))
        File(dir, SECTION_ITEMS_FILE_NAME).writeText(sectionItemsToJson(store.findItems()))

        assertEquals(store.findSections(), readSectionsFile(File(dir, SECTIONS_FILE_NAME)))
        assertEquals(store.findItems(), readSectionItemsFile(File(dir, SECTION_ITEMS_FILE_NAME)))
        // A site that never had sections has no files, and that is not an error.
        assertEquals(emptyList(), readSectionsFile(File(dir, "missing.json")))
        assertEquals(emptyList(), readSectionItemsFile(File(dir, "missing.json")))
        // A broken file stops the start instead of leaving the sections quietly out.
        File(dir, "broken.json").writeText("[{")
        assertFailsWith<Exception> { readSectionsFile(File(dir, "broken.json")) }
    }

    @Test
    fun `the first start puts the sections of a backup in, with their numbers, and carries on counting after them`() {
        val dir = createTempDirectory("backup").toFile()
        File(dir, SECTIONS_FILE_NAME).writeText(sectionsToJson(listOf(section.copy(id = 7))))
        File(dir, SECTION_ITEMS_FILE_NAME).writeText(sectionItemsToJson(listOf(entry(7, "Из копии").copy(id = 41))))
        val empty = File(dir, "empty.json").also { it.writeText("[]") }

        assertTrue(seedIfNeeded(dataSource, clock) {
            loadSeedData(empty, empty, File(dir, "images"), null, File(dir, SECTIONS_FILE_NAME), File(dir, SECTION_ITEMS_FILE_NAME))
        })

        val store = PgSectionRepository(dataSource, clock)
        assertEquals(listOf(7), store.findSections().map { it.id })
        assertEquals(listOf(41), store.findItems().map { it.id })
        assertEquals(8, store.saveSection(section.copy(slug = "next")).id)
        assertEquals(42, store.saveItem(entry(7, "Следующая")).id)
    }
}
