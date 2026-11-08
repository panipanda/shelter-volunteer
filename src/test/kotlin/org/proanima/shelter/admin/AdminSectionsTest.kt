package org.proanima.shelter.admin

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.call.body
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.proanima.shelter.model.ChoiceOption
import org.proanima.shelter.model.FieldType
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.model.Section
import org.proanima.shelter.model.SectionItem
import org.proanima.shelter.repository.readSectionItemsFile
import org.proanima.shelter.routes.plainText
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminSectionsTest : AdminTestBase() {

    private val articlesPreset = SectionPresets.articles
    private val visitsPreset = SectionPresets.visits

    // What the form of a new section sends for a preset.
    private fun newSectionForm(preset: SectionPreset, vararg changes: Pair<String, String>): Map<String, String> =
        SectionForm.newValues(preset.section) + ("parent" to "") + ("preset" to preset.id) + changes

    private fun storedSection(preset: SectionPreset = articlesPreset, slug: String = preset.section.slug): Section =
        sections.saveSection(preset.section.copy(slug = slug))

    private fun item(section: Section, title: String = "Заглавие", en: String? = "A title", published: Boolean = true, photoUrls: List<String> = emptyList()) =
        sections.saveItem(
            SectionItem(
                id = 0, sectionId = section.id, title = LocalizedText(title, en), date = "2026-11-01", photoUrls = photoUrls,
                texts = mapOf("f1" to LocalizedText("Кратко", "Short"), "f2" to LocalizedText("## Текст\n\nАбзац", "## Text")),
                published = published, createdAt = "2026-11-01T10:00:00+01:00", updatedAt = "2026-11-01T10:00:00+01:00"
            )
        )

    private fun itemForm(section: Section, vararg changes: Pair<String, String>): Map<String, String> =
        ItemForm.newValues(section, LocalDate.of(2026, 11, 8)) + mapOf(
            "title_ru" to "Статья", "title_en" to "An article", "f_f1_ru" to "Кратко", "f_f1_en" to "Short",
            "f_f2_ru" to "## Раздел\n\nТекст", "f_f2_en" to "## Part", "published" to "true"
        ) + changes

    private fun storedPhoto(base: String): String {
        photos.put("$base.jpg", "image/jpeg", jpegBytes(400, 500))
        photos.put("$base-card.jpg", "image/jpeg", jpegBytes(400, 500))
        return "/images/$base.jpg"
    }

    private suspend fun HttpClient.upload(path: String, csrf: String, vararg files: Pair<String, ByteArray>): HttpResponse =
        submitFormWithBinaryData(path, formData {
            append("_csrf", csrf)
            files.forEach { (name, bytes) ->
                append("photos", bytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"$name\"")
                })
            }
        })

    // ---- who may

    @Test
    fun `an anonymous visitor is sent to the login page and can change nothing`() = testApplication {
        start()
        val anonymous = browser()
        val section = storedSection()
        val entry = item(section)
        val base = "/admin/sections/${section.id}"

        listOf(
            "/admin/sections", "/admin/sections/new", "/admin/sections/new?preset=articles", base, "$base/settings", "$base/tags", "$base/fields/new",
            "$base/fields/f1", "$base/fields/f1/delete", "$base/delete", "$base/items/new", "$base/items/${entry.id}", "$base/items/${entry.id}/delete",
            "$base/items/${entry.id}/preview", "$base/items/${entry.id}/photos"
        ).forEach { path ->
            val response = anonymous.get(path)
            assertEquals(HttpStatusCode.SeeOther, response.status, path)
            assertEquals("/admin/login", response.headers[HttpHeaders.Location], path)
        }
        listOf(
            "/admin/sections", "$base/settings", "$base/tags", "$base/fields", "$base/fields/f1", "$base/fields/f1/move", "$base/fields/f1/delete",
            "$base/delete", "$base/items", "$base/items/${entry.id}", "$base/items/${entry.id}/delete"
        ).forEach { path ->
            assertEquals("/admin/login", anonymous.post(path, newSectionForm(articlesPreset)).headers[HttpHeaders.Location], path)
        }
        assertEquals(listOf(section), sections.findSections())
        assertEquals(listOf(entry), sections.findItems())
    }

    // ---- the list of sections and the new section

    @Test
    fun `the list of sections says what the panel is for, and offers the presets`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()

        val empty = browser.get("/admin/sections").bodyAsText()
        assertContains(empty, "Пока ни одного раздела")
        assertContains(empty, "Создать раздел")

        val presets = browser.get("/admin/sections/new").bodyAsText()
        listOf("Полезные статьи", "Архив визитов", "Пустой раздел").forEach { assertContains(presets, it) }
        assertContains(presets, "/admin/sections/new?preset=visits")
    }

    @Test
    fun `a section is made from a preset, with its fields and tags`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        val form = browser.get("/admin/sections/new?preset=articles").bodyAsText()
        assertContains(form, "Полезные статьи")
        assertContains(form, """name="preset"""")

        val response = browser.post("/admin/sections", newSectionForm(articlesPreset), csrf)

        assertEquals(HttpStatusCode.SeeOther, response.status)
        val section = sections.findSections().single()
        assertEquals("/admin/sections/${section.id}?msg=section-created", response.headers[HttpHeaders.Location])
        assertEquals("articles", section.slug)
        assertEquals(articlesPreset.section.fields, section.fields)
        assertEquals(articlesPreset.section.tags, section.tags)
        assertContains(browser.get("/admin/sections/${section.id}?msg=section-created").bodyAsText(), "Раздел создан")
        assertContains(browser.get("/admin/sections").bodyAsText(), "пока не видно")
    }

    @Test
    fun `an unknown preset gives an empty section with no fields`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        browser.post("/admin/sections", mapOf("title_ru" to "Новости", "slug" to "news", "layout" to "LIST", "useDate" to "true", "usePhotos" to "false", "position" to "1", "preset" to "nonsense"), csrf)

        val section = sections.findSections().single()
        assertEquals(emptyList(), section.fields)
        assertEquals(emptyList(), section.tags)
        assertEquals("news", section.slug)
    }

    @Test
    fun `a section with a mistake comes back as typed and is not saved`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        val response = browser.post("/admin/sections", newSectionForm(articlesPreset, "slug" to "cats", "title_ru" to "Мои «статьи»"), csrf)

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.bodyAsText()
        assertContains(body, "уже занят страницей сайта")
        assertContains(body, "Мои «статьи»")
        assertContains(body, """name="preset"""")
        assertTrue(sections.findSections().isEmpty())
    }

    @Test
    fun `a section is found by its number and an unknown number is a 404`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        listOf("/admin/sections/99", "/admin/sections/99/settings", "/admin/sections/99/tags", "/admin/sections/99/fields/new", "/admin/sections/99/items/new", "/admin/sections/abc", "/admin/sections/99/delete")
            .forEach { assertEquals(HttpStatusCode.NotFound, browser.get(it).status, it) }
        assertEquals(HttpStatusCode.NotFound, browser.post("/admin/sections/99/settings", newSectionForm(articlesPreset), csrf).status)
        assertEquals(HttpStatusCode.NotFound, browser.post("/admin/sections/99/items", itemForm(articlesPreset.section), csrf).status)
    }

    // ---- the settings

    @Test
    fun `the settings are saved, and the fields and tags are kept`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val section = storedSection()

        val response = browser.post("/admin/sections/${section.id}/settings", SectionForm.valuesOf(section) + ("title_ru" to "Статьи про уход"), csrf)

        assertEquals("/admin/sections/${section.id}/settings?msg=settings-saved", response.headers[HttpHeaders.Location])
        val saved = sections.findSections().single()
        assertEquals("Статьи про уход", saved.title.ru)
        assertEquals(section.fields, saved.fields)
        assertEquals(section.tags, saved.tags)
        assertContains(browser.get("/admin/sections/${section.id}/settings?msg=settings-saved").bodyAsText(), "Настройки сохранены")
    }

    @Test
    fun `two sections cannot share an address`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        storedSection(articlesPreset)
        val other = storedSection(visitsPreset)

        val response = browser.post("/admin/sections/${other.id}/settings", SectionForm.valuesOf(other) + ("slug" to "articles"), csrf)

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertContains(response.bodyAsText(), "Раздел с таким адресом уже есть")
        assertEquals("archive", sections.findSections().first { it.id == other.id }.slug)
    }

    // ---- the fields

    @Test
    fun `a field is added, changed, moved and deleted`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val section = storedSection()
        val base = "/admin/sections/${section.id}"

        // added
        val added = browser.post("$base/fields", FieldForm.newValues() + mapOf("label_ru" to "Автор", "type" to "TEXT", "required" to "true"), csrf)
        assertEquals("$base/settings?msg=field-added", added.headers[HttpHeaders.Location])
        val withField = sections.findSections().single()
        assertEquals(listOf("f1", "f2", "f3"), withField.fields.map { it.key })
        assertEquals(FieldType.TEXT, withField.fields.last().type)
        assertContains(browser.get("$base/settings").bodyAsText(), "Автор")

        // changed: the label changes, the type does not
        val field = withField.fields.last()
        browser.post("$base/fields/f3", FieldForm.valuesOf(field) + mapOf("label_ru" to "Кто написал", "type" to "DATE", "required" to "false"), csrf)
        val changed = sections.findSections().single().fields.last()
        assertEquals("Кто написал", changed.label.ru)
        assertEquals(FieldType.TEXT, changed.type)
        assertFalse(changed.required)
        assertContains(browser.get("$base/fields/f3").bodyAsText(), "Кто написал")

        // moved
        browser.post("$base/fields/f3/move", mapOf("direction" to "up"), csrf)
        assertEquals(listOf("f1", "f3", "f2"), sections.findSections().single().fields.map { it.key })
        browser.post("$base/fields/f3/move", mapOf("direction" to "down"), csrf)
        browser.post("$base/fields/f3/move", mapOf("direction" to "down"), csrf)
        assertEquals(listOf("f1", "f2", "f3"), sections.findSections().single().fields.map { it.key })
        browser.post("$base/fields/f1/move", mapOf("direction" to "up"), csrf)
        assertEquals(listOf("f1", "f2", "f3"), sections.findSections().single().fields.map { it.key })

        // deleted, after asking
        assertContains(browser.get("$base/fields/f3/delete").bodyAsText(), "Удалить поле «Кто написал»?")
        assertEquals(3, sections.findSections().single().fields.size)
        browser.post("$base/fields/f3/delete", emptyMap(), csrf)
        assertEquals(listOf("f1", "f2"), sections.findSections().single().fields.map { it.key })
        // The key is not used again.
        browser.post("$base/fields", FieldForm.newValues() + mapOf("label_ru" to "Снова", "type" to "TEXT"), csrf)
        assertEquals("f4", sections.findSections().single().fields.last().key)
    }

    @Test
    fun `a field with a mistake comes back, and an unknown field is a 404`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val section = storedSection()
        val base = "/admin/sections/${section.id}"

        val response = browser.post("$base/fields", FieldForm.newValues() + mapOf("label_ru" to "", "type" to "CHOICE"), csrf)

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertContains(response.bodyAsText(), "Заполните русский текст")
        assertEquals(articlesPreset.section.fields, sections.findSections().single().fields)
        assertEquals(HttpStatusCode.NotFound, browser.get("$base/fields/f99").status)
        assertEquals(HttpStatusCode.NotFound, browser.post("$base/fields/f99/delete", emptyMap(), csrf).status)
    }

    @Test
    fun `a choice field is made with its options and then used in an entry`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val section = storedSection(visitsPreset)

        val entry = browser.post(
            "/admin/sections/${section.id}/items",
            itemForm(section, "f_f1" to "cancelled", "f_f2_ru" to "Не поехали", "f_f1_ru" to ""), csrf
        )

        assertEquals(HttpStatusCode.SeeOther, entry.status)
        assertEquals("cancelled", sections.findItems().single().plain["f1"])
        assertContains(browser.get("/admin/sections/${section.id}/items/${sections.findItems().single().id}").bodyAsText(), """value="cancelled" selected""")
    }

    // ---- the tags

    @Test
    fun `tags are changed, and a removed tag comes off the entries that had it`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val section = storedSection()
        val entry = sections.saveItem(item(section).copy(tags = listOf("cats", "health")))
        val base = "/admin/sections/${section.id}"
        val form = TagsForm.valuesOf(section).toMutableMap()
        // The row of "dogs" (the second) is cleared, a new tag is typed in a spare row.
        form["opt_2_ru"] = ""; form["opt_2_en"] = ""; form["opt_2_sr"] = ""
        form["opt_5_ru"] = "Питание"; form["opt_5_en"] = "Food"

        val response = browser.post("$base/tags", form, csrf)

        assertEquals("$base/settings?msg=tags-saved", response.headers[HttpHeaders.Location])
        assertEquals(listOf("cats", "health", "care", "food"), sections.findSections().single().tags.map { it.key })
        assertEquals(listOf("cats", "health"), sections.findItems().single { it.id == entry.id }.tags)

        val removeCats = TagsForm.valuesOf(sections.findSections().single()).toMutableMap()
        removeCats["opt_1_ru"] = ""; removeCats["opt_1_en"] = ""; removeCats["opt_1_sr"] = ""
        browser.post("$base/tags", removeCats, csrf)
        assertEquals(listOf("health"), sections.findItems().single { it.id == entry.id }.tags)
        assertContains(browser.get("$base/tags").bodyAsText(), "Питание")
    }

    // ---- the entries

    @Test
    fun `an entry is a draft first, and shows on the site and in the menu once published`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val section = storedSection()
        val base = "/admin/sections/${section.id}"

        val created = browser.post("$base/items", itemForm(section, "published" to "false", "tag_health" to "true"), csrf)

        val entry = sections.findItems().single()
        assertEquals("$base/items/${entry.id}?msg=created", created.headers[HttpHeaders.Location])
        assertFalse(entry.published)
        assertEquals(listOf("health"), entry.tags)
        assertEquals(HttpStatusCode.NotFound, client.get("/ru/articles").status)
        assertFalse(client.get("/ru").plainText().contains("/ru/articles"))
        val list = browser.get(base).bodyAsText()
        assertContains(list, "Статья")
        assertContains(list, "Черновик")

        browser.post("$base/items/${entry.id}", ItemForm.valuesOf(section, entry) + ("published" to "true"), csrf)

        assertTrue(sections.findItems().single().published)
        assertEquals(HttpStatusCode.OK, client.get("/ru/articles").status)
        assertContains(client.get("/ru").plainText(), """href="/ru/articles"""")
        assertContains(client.get("/ru/articles/an-article").plainText(), "<h1>Статья</h1>")
        assertContains(browser.get("/admin/sections").bodyAsText(), "в меню сайта")
    }

    @Test
    fun `an entry with a mistake comes back as typed and is not saved`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val section = storedSection()

        val response = browser.post(
            "/admin/sections/${section.id}/items",
            itemForm(section, "title_ru" to "", "date" to "завтра", "f_f1_ru" to "Набрано, но не сохранено", "f_f2_en" to "[x](javascript:alert(1))"), csrf
        )

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Набрано, но не сохранено")
        assertContains(body, "Заполните русский текст")
        assertContains(body, "Укажите дату целиком")
        assertContains(body, "Ссылка «javascript:")
        assertContains(body, "Название (Русский)")
        assertTrue(sections.findItems().isEmpty())
    }

    @Test
    fun `the list of entries shows the status, the languages that are missing and the date`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()
        val section = storedSection()
        item(section, title = "Только по-русски", en = null)
        val complete = item(section, title = "Полностью", en = "Complete")
        sections.saveItem(
            complete.copy(
                title = LocalizedText("Полностью", "Complete", "Kompletno"),
                texts = mapOf("f1" to LocalizedText("К", "S", "K"), "f2" to LocalizedText("Т", "T", "T"))
            )
        )

        val body = browser.get("/admin/sections/${section.id}").bodyAsText()

        assertContains(body, "Только по-русски")
        assertContains(body, "нет en")
        assertContains(body, "нет sr")
        assertContains(body, "2026-11-01")
        assertContains(body, "Нет названия на этом языке")
    }

    @Test
    fun `an entry is only reachable under its own section`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val articles = storedSection(articlesPreset)
        val visits = storedSection(visitsPreset)
        val entry = item(articles)

        listOf("", "/delete", "/preview", "/photos").forEach { suffix ->
            assertEquals(HttpStatusCode.NotFound, browser.get("/admin/sections/${visits.id}/items/${entry.id}$suffix").status, suffix)
        }
        assertEquals(HttpStatusCode.NotFound, browser.post("/admin/sections/${visits.id}/items/${entry.id}", itemForm(articles), csrf).status)
        assertEquals(HttpStatusCode.NotFound, browser.post("/admin/sections/${visits.id}/items/${entry.id}/delete", emptyMap(), csrf).status)
        assertEquals(listOf(entry), sections.findItems())
    }

    @Test
    fun `the preview shows a draft as visitors will see it, in the chosen language`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()
        val section = storedSection()
        val draft = item(section, published = false)
        val base = "/admin/sections/${section.id}/items/${draft.id}"

        val ru = browser.get("$base/preview").bodyAsText()
        assertContains(ru, "Это черновик")
        assertContains(ru, "<h1>Заглавие</h1>")
        assertContains(ru, "preview-banner")
        assertContains(browser.get("$base/preview?lang=en").bodyAsText(), "<h1>A title</h1>")
        // The language links stay inside the preview: a draft has no public address.
        assertContains(ru, "$base/preview?lang=en")
        assertEquals(HttpStatusCode.NotFound, client.get("/ru/articles/a-title").status)
    }

    @Test
    fun `the form of a new entry has a field for each field of the section, in order`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()
        val section = storedSection(visitsPreset)

        val body = browser.get("/admin/sections/${section.id}/items/new").bodyAsText()

        listOf("f-title_ru", "f-date", "f-f_f1", "f-f_f2_ru", "f-f_f3_sr").forEach { assertContains(body, """id="$it"""") }
        assertTrue(body.indexOf("f-f_f1") < body.indexOf("f-f_f2_ru") && body.indexOf("f-f_f2_ru") < body.indexOf("f-f_f3_ru"))
        assertContains(body, "Фотографии можно добавить после первого сохранения")
        assertFalse(body.contains("tag_"), "a section with no tags has no tag boxes")
    }

    // ---- photos

    @Test
    fun `photos are added to an entry under names of their own, and appear on its page`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val section = storedSection()
        val entry = item(section)

        val response = browser.upload("/admin/sections/${section.id}/items/${entry.id}/photos", csrf, "a.jpg" to jpegBytes(800, 600))

        assertEquals(HttpStatusCode.SeeOther, response.status)
        val url = sections.findItems().single().photoUrls.single()
        assertTrue(Regex("/images/item-${entry.id}-[0-9a-f]{8}\\.jpg").matches(url), url)
        assertNotNull(photos.get(url.removePrefix("/images/")))
        assertNotNull(photos.get(url.removePrefix("/images/").replace(".jpg", "-card.jpg")))
        assertContains(client.get("/ru/articles/a-title").plainText(), url)
        assertContains(browser.get("/admin/sections/${section.id}/items/${entry.id}/photos").bodyAsText(), "Фотографии")
    }

    @Test
    fun `deleting an entry removes its photos unless another entry still uses them`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val section = storedSection()
        val own = storedPhoto("item-1-aaaa1111")
        val shared = storedPhoto("item-2-bbbb2222")
        val doomed = item(section, photoUrls = listOf(own, shared))
        item(section, title = "Другая", en = "Other", photoUrls = listOf(shared))

        assertContains(browser.get("/admin/sections/${section.id}/items/${doomed.id}/delete").bodyAsText(), "Удалить запись «Заглавие»?")
        val response = browser.post("/admin/sections/${section.id}/items/${doomed.id}/delete", emptyMap(), csrf)

        assertEquals("/admin/sections/${section.id}?msg=deleted", response.headers[HttpHeaders.Location])
        assertNull(photos.get("item-1-aaaa1111.jpg"))
        assertNull(photos.get("item-1-aaaa1111-card.jpg"))
        assertNotNull(photos.get("item-2-bbbb2222.jpg"))
        assertEquals(1, sections.findItems().size)
        assertContains(browser.get("/admin/sections/${section.id}?msg=deleted").bodyAsText(), "Запись удалена")
    }

    @Test
    fun `deleting a section removes its entries and their photos, after asking`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val section = storedSection()
        val other = storedSection(visitsPreset)
        val photo = storedPhoto("item-1-aaaa1111")
        item(section, photoUrls = listOf(photo))
        item(section, title = "Вторая", en = "Second")
        val kept = item(other, title = "Чужая", en = "Alien")

        val ask = browser.get("/admin/sections/${section.id}/delete").bodyAsText()
        assertContains(ask, "Удалить раздел «Полезные статьи»?")
        assertContains(ask, "(2)")
        assertEquals(3, sections.findItems().size)

        val response = browser.post("/admin/sections/${section.id}/delete", emptyMap(), csrf)

        assertEquals("/admin/sections?msg=section-deleted", response.headers[HttpHeaders.Location])
        assertEquals(listOf(other.id), sections.findSections().map { it.id })
        assertEquals(listOf(kept.id), sections.findItems().map { it.id })
        assertNull(photos.get("item-1-aaaa1111.jpg"))
        assertEquals(HttpStatusCode.NotFound, client.get("/ru/articles").status)
        assertContains(browser.get("/admin/sections?msg=section-deleted").bodyAsText(), "Раздел удалён")
    }

    // ---- sizes

    @Test
    fun `a long text in three languages is accepted, an absurd form is refused`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val section = storedSection()
        val long = "Очень длинный абзац. ".repeat(900)
        val values = itemForm(section, "f_f2_ru" to long, "f_f2_en" to long, "f_f2_sr" to long)

        val accepted = browser.post("/admin/sections/${section.id}/items", values, csrf)
        assertEquals(HttpStatusCode.SeeOther, accepted.status)

        val refused = browser.post("/admin/sections/${section.id}/items", itemForm(section, "f_f2_ru" to "я".repeat(160_000), "f_f2_en" to "я".repeat(160_000)), csrf)
        assertEquals(HttpStatusCode.PayloadTooLarge, refused.status)
        assertEquals(1, sections.findItems().size)
        // A cat form is still limited to the small size.
        assertEquals(HttpStatusCode.PayloadTooLarge, browser.post("/admin/cats", mapOf("name_ru" to "я".repeat(120_000)), csrf).status)
    }

    // ---- the backup

    @Test
    fun `the backup has the sections, their entries and the entries' photos`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val section = storedSection()
        val photo = storedPhoto("item-1-aaaa1111")
        val entry = item(section, photoUrls = listOf(photo))

        val response = browser.post("/admin/export", emptyMap(), csrf)

        assertEquals(HttpStatusCode.OK, response.status)
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(response.body<ByteArray>().inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entryInZip -> files[entryInZip.name] = ByteArrayOutputStream().also { zip.copyTo(it) }.toByteArray() }
        }
        assertTrue(files.keys.containsAll(listOf("sections.json", "section-items.json", "images/item-1-aaaa1111.jpg", "images/item-1-aaaa1111-card.jpg")), files.keys.toString())
        assertContains(files.getValue("sections.json").decodeToString(), "\"slug\": \"articles\"")
        assertContains(files.getValue("section-items.json").decodeToString(), "\"title\"")
        assertContains(files.getValue("README.txt").decodeToString(), "Разделов: 1, записей в них: 1.")
        val file = kotlin.io.path.createTempFile("section-items", ".json").toFile().also { it.writeBytes(files.getValue("section-items.json")); it.deleteOnExit() }
        assertEquals(listOf(entry), readSectionItemsFile(file))
    }


    // ---- the option rows survive a round trip through the form

    @Test
    fun `the tag rows of the form come back with the keys they were made with`() {
        val rows = OptionRows.valuesOf(listOf(ChoiceOption("cats", LocalizedText("Кошки", "Cats", "Mačke"))))

        assertEquals("cats", rows["opt_1_key"])
        assertEquals("Mačke", rows["opt_1_sr"])
        assertEquals(listOf(1, 2, 3, 4), OptionRows.rowNumbers(rows))
        assertEquals("", rows["opt_2_key"])
    }
}
