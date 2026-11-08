package org.proanima.shelter.admin

import org.proanima.shelter.model.ChoiceOption
import org.proanima.shelter.model.FieldType
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.model.Section
import org.proanima.shelter.model.SectionField
import org.proanima.shelter.model.SectionItem
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SectionFormsTest {

    private val today = LocalDate.of(2026, 11, 8)
    private val now = "2026-11-08T12:00:00+01:00"

    private fun text(ru: String, en: String? = null, sr: String? = null) = LocalizedText(ru, en, sr)

    private val articles = SectionPresets.articles.section.copy(id = 7)

    // What the form of a new section sends with the settings of the articles preset.
    private fun sectionValues(vararg changes: Pair<String, String>): Map<String, String> =
        SectionForm.valuesOf(SectionPresets.articles.section) + changes

    // ---- the settings of a section

    @Test
    fun `a new section is the preset with its fields and tags, after the sections that are there`() {
        val other = articles.copy(id = 1, slug = "other", position = 4)

        val values = SectionForm.newValues(SectionPresets.visits.section)
        val result = SectionForm.validate(values, null, SectionPresets.visits.section, listOf(other))

        assertTrue(result.ok, result.errors.toString())
        val section = result.value!!
        assertEquals(5, section.position)
        assertFalse("position" in values, "where a section stands is the menu's business, not a setting of the section")
        assertEquals("archive", section.slug)
        assertEquals(SectionPresets.visits.section.fields, section.fields)
        assertEquals(SectionPresets.visits.section.nextKeyNumber, section.nextKeyNumber)
        assertEquals(0, section.id)
    }

    @Test
    fun `an address left empty is made from the English title`() {
        val result = SectionForm.validate(sectionValues("slug" to "", "title_en" to "Stories & News!"), null, articles, emptyList())

        assertEquals("stories-news", result.value!!.slug)
    }

    @Test
    fun `an address that is empty and cannot be made is refused`() {
        val result = SectionForm.validate(sectionValues("slug" to "", "title_en" to ""), null, articles, emptyList())

        assertFalse(result.ok)
        assertContains(result.errors.getValue("slug"), "Укажите адрес")
    }

    @Test
    fun `addresses of the site's own pages, bad addresses and taken addresses are refused`() {
        listOf("cats", "dogs", "guide", "visits", "help").forEach { slug ->
            assertContains(
                SectionForm.validate(sectionValues("slug" to slug), null, articles, emptyList()).errors.getValue("slug"),
                "уже занят", message = slug
            )
        }
        listOf("Статьи", "two words", "-edge", "a--b", "a/b", "a".repeat(41)).forEach { slug ->
            assertContains(
                SectionForm.validate(sectionValues("slug" to slug), null, articles, emptyList()).errors.getValue("slug"),
                "латинские", message = slug
            )
        }
        val taken = articles.copy(id = 1, slug = "news")
        assertContains(SectionForm.validate(sectionValues("slug" to "news"), null, articles, listOf(taken)).errors.getValue("slug"), "уже есть")
        // The section's own address is not "taken" when it is saved again.
        assertTrue(SectionForm.validate(sectionValues("slug" to "news"), taken, taken, emptyList()).ok)
    }

    @Test
    fun `the Russian title is required and the numbers and choices are checked`() {
        val result = SectionForm.validate(
            sectionValues("title_ru" to "", "layout" to "GRID", "useDate" to "maybe"), null, articles, emptyList()
        )

        assertEquals(setOf("title_ru", "layout", "useDate"), result.errors.keys)
    }

    @Test
    fun `saving the settings of a section keeps its fields, tags and key counter`() {
        val result = SectionForm.validate(sectionValues("title_ru" to "Новое название"), articles, articles, emptyList())

        val section = result.value!!
        assertEquals(7, section.id)
        assertEquals("Новое название", section.title.ru)
        assertEquals(articles.fields, section.fields)
        assertEquals(articles.tags, section.tags)
        assertEquals(articles.nextKeyNumber, section.nextKeyNumber)
        assertEquals(articles.position, section.position, "saving the settings does not move the section")
    }

    @Test
    fun `there is a limit to the number of sections`() {
        val others = (1..MAX_SECTIONS).map { articles.copy(id = it, slug = "s$it") }

        assertTrue(SectionForm.validate(sectionValues("slug" to "one-more"), null, articles, others).errors.containsKey("title_ru"))
        assertTrue(SectionForm.validate(sectionValues("slug" to "s1"), others.first(), others.first(), others.drop(1)).ok)
    }

    // ---- tags and choices

    private fun tagValues(vararg rows: List<String>, keys: List<String> = emptyList()): Map<String, String> = buildMap {
        rows.forEachIndexed { index, (ru, en, sr) ->
            put("opt_${index + 1}_key", keys.getOrElse(index) { "" })
            put("opt_${index + 1}_ru", ru)
            put("opt_${index + 1}_en", en)
            put("opt_${index + 1}_sr", sr)
        }
    }

    @Test
    fun `new tags get keys from their English names, and the counter makes one where there is no English name`() {
        val section = articles.copy(tags = emptyList(), nextKeyNumber = 5)

        val result = TagsForm.validate(tagValues(listOf("Здоровье", "Health", ""), listOf("Уход", "", ""), listOf("", "", "")), section)

        val changed = result.value!!
        assertEquals(listOf("health", "o5"), changed.tags.map { it.key })
        assertEquals(6, changed.nextKeyNumber)
        assertEquals("Здоровье", changed.tags.first().name.ru)
    }

    @Test
    fun `a tag keeps its key when it is renamed, and a cleared row removes the tag`() {
        val section = articles.copy(tags = listOf(ChoiceOption("cats", text("Кошки", "Cats")), ChoiceOption("dogs", text("Собаки", "Dogs"))))

        val result = TagsForm.validate(
            tagValues(listOf("Коты", "Cats and kittens", ""), listOf("", "", ""), keys = listOf("cats", "dogs")), section
        )

        assertEquals(listOf("cats"), result.value!!.tags.map { it.key })
        assertEquals("Коты", result.value!!.tags.single().name.ru)
    }

    @Test
    fun `a key that the list never had is not trusted`() {
        val section = articles.copy(tags = listOf(ChoiceOption("cats", text("Кошки"))), nextKeyNumber = 9)

        val result = TagsForm.validate(
            tagValues(listOf("Кошки", "", ""), listOf("Хитрость", "", ""), keys = listOf("cats", "../../etc")), section
        )

        assertEquals(listOf("cats", "o9"), result.value!!.tags.map { it.key })
    }

    @Test
    fun `a tag with a name only in another language is refused`() {
        val result = TagsForm.validate(tagValues(listOf("", "Health", "")), articles.copy(tags = emptyList()))

        assertEquals(setOf("opt_1_ru"), result.errors.keys)
    }

    @Test
    fun `an English name that is already a key gets a counter key instead of a clash`() {
        val section = articles.copy(tags = listOf(ChoiceOption("health", text("Здоровье", "Health"))), nextKeyNumber = 3)

        val result = TagsForm.validate(tagValues(listOf("Здоровье", "Health", ""), listOf("Здоровье 2", "Health", ""), keys = listOf("health", "")), section)

        assertEquals(listOf("health", "o3"), result.value!!.tags.map { it.key })
    }

    // ---- the fields of a section

    private fun fieldValues(vararg changes: Pair<String, String>): Map<String, String> =
        FieldForm.newValues() + mapOf("label_ru" to "Место", "label_en" to "Place") + changes

    @Test
    fun `a new field gets the next key and is added at the end`() {
        val result = FieldForm.validate(fieldValues("type" to "TEXT", "required" to "true"), articles, null)

        val section = result.value!!
        val added = section.fields.last()
        assertEquals("f3", added.key)
        assertEquals(FieldType.TEXT, added.type)
        assertTrue(added.required)
        assertFalse(added.inList)
        assertEquals(4, section.nextKeyNumber)
        assertEquals(articles.fields, section.fields.dropLast(1))
    }

    @Test
    fun `the type of a field that exists cannot be changed`() {
        val field = articles.fields.first()

        val result = FieldForm.validate(fieldValues("type" to "DATE", "label_ru" to "Коротко"), articles, field)

        val saved = result.value!!.fields.first()
        assertEquals(field.key, saved.key)
        assertEquals(field.type, saved.type)
        assertEquals("Коротко", saved.label.ru)
        assertEquals(articles.nextKeyNumber, result.value!!.nextKeyNumber)
    }

    @Test
    fun `a long text with formatting cannot be put in the list`() {
        val result = FieldForm.validate(fieldValues("type" to "MARKDOWN", "inList" to "true"), articles, null)

        assertEquals(setOf("inList"), result.errors.keys)
    }

    @Test
    fun `a choice field needs two options, and keeps the key of an option that stays`() {
        val none = FieldForm.validate(fieldValues("type" to "CHOICE") + tagValues(listOf("Один", "", "")), articles, null)
        assertEquals(setOf("opt_1_ru"), none.errors.keys)

        val made = FieldForm.validate(fieldValues("type" to "CHOICE") + tagValues(listOf("Лёгкий", "Easy", ""), listOf("Сложный", "Hard", "")), articles, null)
        val field = made.value!!.fields.last()
        assertEquals(listOf("easy", "hard"), field.options.map { it.key })

        val renamed = FieldForm.validate(
            fieldValues() + tagValues(listOf("Простой", "Easy", ""), listOf("Сложный", "Hard", ""), keys = listOf("easy", "hard")),
            made.value!!, field
        )
        assertEquals(listOf("easy", "hard"), renamed.value!!.fields.last().options.map { it.key })
        assertEquals("Простой", renamed.value!!.fields.last().options.first().name.ru)
    }

    @Test
    fun `options typed for a field that is not a choice are dropped`() {
        val result = FieldForm.validate(fieldValues("type" to "TEXT") + tagValues(listOf("Лишнее", "", "")), articles, null)

        assertEquals(emptyList(), result.value!!.fields.last().options)
    }

    @Test
    fun `a field needs a Russian name and there is a limit to the number of fields`() {
        assertEquals(setOf("label_ru"), FieldForm.validate(fieldValues("label_ru" to ""), articles, null).errors.keys)

        val full = articles.copy(fields = (1..MAX_FIELDS).map { SectionField("f$it", FieldType.TEXT, text("Поле $it")) })
        assertEquals(setOf("label_ru"), FieldForm.validate(fieldValues(), full, null).errors.keys)
    }

    // ---- an entry

    private val visits = SectionPresets.visits.section.copy(id = 3)

    private fun itemValues(section: Section, vararg changes: Pair<String, String>): Map<String, String> =
        ItemForm.newValues(section, today) + mapOf("title_ru" to "Заголовок", "published" to "true") + changes

    private fun articleValues(vararg changes: Pair<String, String>) = itemValues(
        articles, "f_f1_ru" to "Кратко", "f_f2_ru" to "## Раздел\n\nТекст", *changes
    )

    @Test
    fun `an entry is read from the form and goes back into it unchanged`() {
        val values = articleValues(
            "title_en" to "A title", "f_f1_en" to "Short", "f_f2_en" to "- one\n- two", "tag_health" to "true", "tag_cats" to "true", "date" to "2026-10-30"
        )
        val result = ItemForm.validate(values, articles, null, emptyList(), now)

        val item = result.value!!
        assertEquals(listOf("cats", "health"), item.tags)
        assertEquals("2026-10-30", item.date)
        assertEquals("- one\n- two", item.texts.getValue("f2").en)
        assertEquals(7, item.sectionId)
        assertEquals(now, item.createdAt)
        assertTrue(item.published)

        val again = ItemForm.validate(ItemForm.valuesOf(articles, item), articles, item, emptyList(), now).value!!
        assertEquals(item, again)
    }

    @Test
    fun `the Russian title, the required fields and a date are required`() {
        val result = ItemForm.validate(itemValues(articles, "title_ru" to "", "date" to ""), articles, null, emptyList(), now)

        assertEquals(setOf("title_ru", "date", "f_f1_ru", "f_f2_ru"), result.errors.keys)
    }

    @Test
    fun `a date must be a whole date in a sane year`() {
        listOf("2026-13-01", "2026-02-30", "8 ноября", "1999-01-01", "2101-01-01").forEach { date ->
            assertEquals(setOf("date"), ItemForm.validate(articleValues("date" to date), articles, null, emptyList(), now).errors.keys, date)
        }
    }

    @Test
    fun `a section without dates ignores the date`() {
        val noDates = articles.copy(useDate = false)

        val item = ItemForm.validate(articleValues("date" to "garbage"), noDates, null, emptyList(), now).value!!

        assertNull(item.date)
    }

    @Test
    fun `a choice must be one of the options`() {
        val bad = ItemForm.validate(itemValues(visits, "f_f1" to "exploded", "f_f2_ru" to "Итог"), visits, null, emptyList(), now)
        assertEquals(setOf("f_f1"), bad.errors.keys)
        val missing = ItemForm.validate(itemValues(visits, "f_f1" to "", "f_f2_ru" to "Итог"), visits, null, emptyList(), now)
        assertEquals(setOf("f_f1"), missing.errors.keys)

        val ok = ItemForm.validate(itemValues(visits, "f_f1" to "cancelled", "f_f2_ru" to "Итог"), visits, null, emptyList(), now)
        assertEquals("cancelled", ok.value!!.plain["f1"])
    }

    @Test
    fun `a tag that the section does not have is ignored`() {
        val item = ItemForm.validate(articleValues("tag_nonsense" to "true", "tag_dogs" to "true"), articles, null, emptyList(), now).value!!

        assertEquals(listOf("dogs"), item.tags)
    }

    @Test
    fun `a short text of a few sentences keeps its paragraphs and the line breaks of markdown stay`() {
        val values = itemValues(visits, "f_f1" to "held", "f_f2_ru" to "Первый.\r\n\r\n\r\n\r\nВторой.  \r\nтот же абзац", "f_f3_ru" to "- а\n- б\n")
        val item = ItemForm.validate(values, visits, null, emptyList(), now).value!!

        assertEquals("Первый.\n\nВторой.\nтот же абзац", item.texts.getValue("f2").ru)
        assertEquals("- а\n- б", item.texts.getValue("f3").ru)
    }

    @Test
    fun `a title is one line`() {
        val item = ItemForm.validate(articleValues("title_ru" to "Два\nряда"), articles, null, emptyList(), now).value!!

        assertEquals("Два ряда", item.title.ru)
    }

    @Test
    fun `a link that is not a web address never gets into a text`() {
        val result = ItemForm.validate(articleValues("f_f2_en" to "[click](javascript:alert(1))"), articles, null, emptyList(), now)

        assertEquals(setOf("f_f2_en"), result.errors.keys)
        assertContains(result.errors.getValue("f_f2_en"), "https://")
        assertTrue(ItemForm.validate(articleValues("f_f2_en" to "[ok](https://example.com) and [here](/ru/help)"), articles, null, emptyList(), now).ok)
    }

    @Test
    fun `two entries of a section cannot share the address that the English title makes`() {
        val first = ItemForm.validate(articleValues("title_en" to "Winter care"), articles, null, emptyList(), now).value!!.copy(id = 1)

        val clash = ItemForm.validate(articleValues("title_en" to "winter  CARE!"), articles, null, listOf(first), now)
        assertEquals(setOf("title_en"), clash.errors.keys)

        // The entry itself is not "another entry" when it is saved again.
        assertTrue(ItemForm.validate(articleValues("title_en" to "Winter care"), articles, first, emptyList(), now).ok)
    }

    @Test
    fun `the photos and the creation time stay as they were`() {
        val item = SectionItem(
            id = 4, sectionId = 7, title = text("Старое"), photoUrls = listOf("/images/item-4-aaaa1111.jpg"),
            createdAt = "2026-01-01T00:00:00+01:00", published = false
        )

        val saved = ItemForm.validate(articleValues(), articles, item, emptyList(), now).value!!

        assertEquals(item.photoUrls, saved.photoUrls)
        assertEquals("2026-01-01T00:00:00+01:00", saved.createdAt)
        assertEquals(now, saved.updatedAt)
        assertNotNull(saved.texts["f1"])
    }

    @Test
    fun `the value of a field that was deleted is left out when the entry is saved`() {
        val old = SectionItem(id = 4, sectionId = 7, title = text("Старое"), texts = mapOf("f9" to text("Забытое"), "f1" to text("Кратко")))

        val saved = ItemForm.validate(articleValues(), articles, old, emptyList(), now).value!!

        assertEquals(setOf("f1", "f2"), saved.texts.keys)
    }
}
