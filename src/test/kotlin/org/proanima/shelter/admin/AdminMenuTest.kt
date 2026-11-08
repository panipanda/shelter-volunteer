package org.proanima.shelter.admin

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.model.MenuTree
import org.proanima.shelter.model.SectionItem
import org.proanima.shelter.routes.plainText
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The page "Меню сайта" and the choice of a place in the form of a section.
class AdminMenuTest : AdminTestBase() {

    private val visitsPreset = SectionPresets.visits
    private val articlesPreset = SectionPresets.articles

    private fun HttpResponse.location() = headers[HttpHeaders.Location]

    private fun groupForm(vararg changes: Pair<String, String>): Map<String, String> =
        MenuGroupForm.valuesOf(null) + mapOf("title_ru" to "Животные приюта", "title_en" to "Shelter animals") + changes

    private fun newSectionForm(preset: SectionPreset, vararg changes: Pair<String, String>): Map<String, String> =
        SectionForm.newValues(preset.section) + ("parent" to "") + ("preset" to preset.id) + changes

    // The ids of the saved tree, in the order they are drawn in, a child right after its parent with a dash.
    private fun outline(): List<String> {
        val tree = checkNotNull(menu.load()) { "no tree was saved" }
        return tree.nodes.filter { it.parent == null }.sortedBy { it.position }.flatMap { top ->
            listOf(top.id) + tree.nodes.filter { it.parent == top.id }.sortedBy { it.position }.map { "-${it.id}" }
        }
    }

    private fun publishedEntry(sectionId: Int, title: LocalizedText = LocalizedText("Выезд", "Trip", "Izlazak")) =
        sections.saveItem(SectionItem(id = 0, sectionId = sectionId, title = title, published = true))

    private fun navOf(body: String) = Regex("""<nav class="main-nav".*?</nav>""", RegexOption.DOT_MATCHES_ALL).find(body)!!.value

    // ---- access

    @Test
    fun `nobody who is not logged in sees or changes the menu`() = testApplication {
        start()
        val browser = browser()

        listOf("/admin/menu", "/admin/menu/groups/new", "/admin/menu/groups/g1", "/admin/menu/groups/g1/delete").forEach { path ->
            val response = browser.get(path)
            assertEquals(HttpStatusCode.SeeOther, response.status, path)
            assertEquals("/admin/login", response.location(), path)
        }
        listOf("/admin/menu/move", "/admin/menu/parent", "/admin/menu/visibility", "/admin/menu/groups", "/admin/menu/groups/g1", "/admin/menu/groups/g1/delete").forEach { path ->
            val response = browser.post(path, mapOf("node" to "page:cats", "direction" to "down"))
            assertEquals(HttpStatusCode.SeeOther, response.status, path)
            assertEquals("/admin/login", response.location(), path)
        }
        assertNull(menu.load())
    }

    @Test
    fun `a change without the form's token is refused`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()

        val response = browser.post("/admin/menu/move", mapOf("node" to "page:cats", "direction" to "down"), csrf = "wrong")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertNull(menu.load())
    }

    // ---- the page

    @Test
    fun `the page shows the default menu and what visitors get in each language, and opening it saves nothing`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()

        val body = browser.get("/admin/menu").bodyAsText()

        assertContains(body, "Меню сайта")
        assertContains(body, """href="/admin/menu"""")
        assertContains(body, "RU: ")
        assertContains(body, "Кошки · Собаки · Гайд · Визиты · Как помочь")
        assertContains(body, "в одной строке с названием")
        listOf("Кошки", "Собаки", "Гайд", "Визиты", "Как помочь").forEach { assertContains(body, """<span class="admin-row-title">$it</span>""") }
        assertEquals("noindex, nofollow", browser.get("/admin/menu").headers["X-Robots-Tag"])
        assertNull(menu.load(), "looking at the menu does not write it down")
    }

    @Test
    fun `an entry that cannot go anywhere has no move form, and the first entry has no up button`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()

        val body = browser.get("/admin/menu").bodyAsText()

        val cats = body.substringAfter("""id="n-page-cats"""").substringBefore("</li>")
        assertFalse("Выше: Кошки" in cats)
        assertContains(cats, "Ниже: Кошки")
        val help = body.substringAfter("""id="n-page-help"""").substringBefore("</li>")
        assertFalse("Ниже: Как помочь" in help)
        assertContains(help, "Выше: Как помочь")
    }

    // ---- order

    @Test
    fun `an entry moves one step down, the menu on the site follows, and the tree is saved`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        val response = browser.post("/admin/menu/move", mapOf("node" to "page:cats", "direction" to "down"), csrf)

        assertEquals(HttpStatusCode.SeeOther, response.status)
        assertEquals("/admin/menu?msg=moved#n-page-cats", response.location())
        assertEquals(listOf("page:dogs", "page:cats", "page:guide", "page:visits", "page:help"), outline())
        assertContains(browser.get("/admin/menu?msg=moved").bodyAsText(), "Порядок сохранён.")
        assertEquals(listOf("/ru/dogs", "/ru/cats"), Regex("""href="(/ru/(?:dogs|cats))"""").findAll(navOf(browser.get("/ru").plainText())).map { it.groupValues[1] }.toList())
    }

    @Test
    fun `moving past the end changes nothing, and an unknown entry is refused`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        browser.post("/admin/menu/move", mapOf("node" to "page:cats", "direction" to "up"), csrf)
        assertNull(menu.load(), "nothing moved, so nothing was written")

        val refused = browser.post("/admin/menu/move", mapOf("node" to "page:nope", "direction" to "up"), csrf)
        assertEquals(HttpStatusCode.UnprocessableEntity, refused.status)
        assertNull(menu.load())
    }

    // ---- groups

    @Test
    fun `a group is made, filled and shown as a drop-down on the site`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        assertContains(browser.get("/admin/menu/groups/new").bodyAsText(), "Новая группа")
        val created = browser.post("/admin/menu/groups", groupForm("menuTitle_ru" to "Звери"), csrf)
        assertEquals(HttpStatusCode.SeeOther, created.status)
        assertEquals("/admin/menu?msg=group-created#n-group-g1", created.location())

        assertEquals(HttpStatusCode.SeeOther, browser.post("/admin/menu/parent", mapOf("node" to "page:cats", "parent" to "group:g1"), csrf).status)
        assertEquals(HttpStatusCode.SeeOther, browser.post("/admin/menu/parent", mapOf("node" to "page:dogs", "parent" to "group:g1"), csrf).status)

        assertEquals(listOf("page:guide", "page:visits", "page:help", "group:g1", "-page:cats", "-page:dogs"), outline())
        val nav = navOf(browser.get("/ru").plainText())
        assertContains(nav, "Звери")
        assertContains(nav, """<div class="nav-sub" id="nav-sub-group-g1"><a href="/ru/cats">Кошки</a><a href="/ru/dogs">Собаки</a></div>""")
        val page = browser.get("/admin/menu").bodyAsText()
        assertContains(page, "Звери ▸ ")
        assertContains(page, "Кошки, Собаки")
    }

    @Test
    fun `a group needs a Russian name and the mistakes come back as typed`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        val response = browser.post("/admin/menu/groups", groupForm("title_ru" to "", "title_en" to "Keep me", "menuTitle_ru" to "x".repeat(40)), csrf)

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Заполните русский текст.")
        assertContains(body, "Слишком длинный текст")
        assertContains(body, "Keep me")
        assertNull(menu.load())
    }

    @Test
    fun `a group is renamed`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        browser.post("/admin/menu/groups", groupForm(), csrf)

        assertContains(browser.get("/admin/menu/groups/g1").bodyAsText(), "Животные приюта")
        val response = browser.post("/admin/menu/groups/g1", groupForm("title_ru" to "Питомцы"), csrf)

        assertEquals("/admin/menu?msg=group-saved#n-group-g1", response.location())
        assertEquals("Питомцы", menu.load()!!.nodes.first { it.id == "group:g1" }.title!!.ru)
        assertEquals(HttpStatusCode.NotFound, browser.get("/admin/menu/groups/g99").status)
        assertEquals(HttpStatusCode.NotFound, browser.get("/admin/menu/groups/cats").status)
    }

    @Test
    fun `taking a group away asks first, and what was in it stays in the menu`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        browser.post("/admin/menu/groups", groupForm(), csrf)
        browser.post("/admin/menu/parent", mapOf("node" to "page:cats", "parent" to "group:g1"), csrf)

        val ask = browser.get("/admin/menu/groups/g1/delete").bodyAsText()
        assertContains(ask, "Удалить группу «Животные приюта»?")
        assertContains(ask, "«Кошки»")
        assertTrue(menu.load()!!.nodes.any { it.id == "group:g1" }, "asking does not delete")

        val done = browser.post("/admin/menu/groups/g1/delete", emptyMap(), csrf)

        assertEquals("/admin/menu?msg=group-deleted", done.location())
        assertEquals(listOf("page:dogs", "page:guide", "page:visits", "page:help", "page:cats"), outline())
    }

    // ---- moving under another entry

    @Test
    fun `a page goes under another one and back to the top level`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val section = sections.saveSection(visitsPreset.section)
        publishedEntry(section.id)

        val moved = browser.post("/admin/menu/parent", mapOf("node" to "section:${section.id}", "parent" to "page:visits"), csrf)

        assertEquals("/admin/menu?msg=placed#n-section-${section.id}", moved.location())
        assertEquals(listOf("page:cats", "page:dogs", "page:guide", "page:visits", "-section:${section.id}", "page:help"), outline())
        assertContains(navOf(browser.get("/ru").plainText()), """<div class="nav-sub" id="nav-sub-page-visits"><a href="/ru/archive">Архив</a></div>""")

        browser.post("/admin/menu/parent", mapOf("node" to "section:${section.id}", "parent" to ""), csrf)
        assertEquals(listOf("page:cats", "page:dogs", "page:guide", "page:visits", "page:help", "section:${section.id}"), outline())
    }

    @Test
    fun `a forged or out of date move is refused and changes nothing`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        browser.post("/admin/menu/parent", mapOf("node" to "page:cats", "parent" to "page:dogs"), csrf)
        val before = menu.load()

        listOf(
            mapOf("node" to "page:dogs", "parent" to "page:dogs"),
            mapOf("node" to "page:dogs", "parent" to "page:cats"),
            mapOf("node" to "page:guide", "parent" to "page:cats"),
            mapOf("node" to "page:guide", "parent" to "page:nowhere"),
            mapOf("node" to "section:77", "parent" to "page:dogs")
        ).forEach { fields ->
            val response = browser.post("/admin/menu/parent", fields, csrf)
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status, fields.toString())
            assertContains(response.bodyAsText(), "Так сделать нельзя")
        }
        assertEquals(before, menu.load())
    }

    // ---- hiding

    @Test
    fun `a hidden page leaves the menu but not the site, and shows up again`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        val hidden = browser.post("/admin/menu/visibility", mapOf("node" to "page:guide", "hidden" to "true"), csrf)

        assertEquals("/admin/menu?msg=hidden#n-page-guide", hidden.location())
        assertFalse("/ru/guide" in navOf(browser.get("/ru").plainText()))
        assertEquals(HttpStatusCode.OK, browser.get("/ru/guide").status)
        val page = browser.get("/admin/menu?msg=hidden").bodyAsText()
        assertContains(page, "скрыт из меню")
        assertContains(page, "Показать в меню")
        assertContains(page, "по-прежнему открывается по адресу")

        browser.post("/admin/menu/visibility", mapOf("node" to "page:guide", "hidden" to "false"), csrf)
        assertContains(navOf(browser.get("/ru").plainText()), "/ru/guide")
    }

    @Test
    fun `hiding something with entries in it says that they go with it`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val section = sections.saveSection(visitsPreset.section)
        publishedEntry(section.id)
        browser.post("/admin/menu/parent", mapOf("node" to "section:${section.id}", "parent" to "page:visits"), csrf)

        assertContains(browser.get("/admin/menu").bodyAsText(), "Внутри пунктов: 1. Если скрыть этот пункт, они уйдут из меню вместе с ним.")

        browser.post("/admin/menu/visibility", mapOf("node" to "page:visits", "hidden" to "true"), csrf)

        val page = browser.get("/admin/menu").bodyAsText()
        assertContains(page, "скрыт вместе с «Визиты»")
        assertFalse("archive" in navOf(browser.get("/ru").plainText()))
        assertEquals(HttpStatusCode.OK, browser.get("/ru/archive").status)
    }

    @Test
    fun `a section that visitors cannot see is marked, with the reason`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()
        val section = sections.saveSection(visitsPreset.section)
        publishedEntry(section.id, LocalizedText("Только по-русски"))

        val page = browser.get("/admin/menu").bodyAsText()

        assertContains(page, "нет в меню на языках: EN, SR")
        assertEquals(1, Regex("нет в меню на языках").findAll(page).count())
        sections.saveItem(sections.findItems().single().copy(published = false))
        assertContains(browser.get("/admin/menu").bodyAsText(), "посетители не видят: нет опубликованных записей")
    }

    // ---- the form of a section

    @Test
    fun `the form of a new section offers the places, and the archive starts under Visits`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()

        val archive = browser.get("/admin/sections/new?preset=visits").bodyAsText()
        val articles = browser.get("/admin/sections/new?preset=articles").bodyAsText()

        assertContains(archive, "Где в меню")
        assertContains(archive, """<option value="page:visits" selected="selected">Внутри «Визиты»</option>""")
        assertContains(archive, """<option value="">Верхний уровень меню</option>""")
        assertContains(archive, """<option value="page:cats">Внутри «Кошки»</option>""")
        assertContains(articles, """<option value="" selected="selected">Верхний уровень меню</option>""")
        assertFalse("Место в меню" in archive)
    }

    @Test
    fun `a section made under a page stands there, and one made on the top level leaves no tree behind`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        browser.post("/admin/sections", newSectionForm(articlesPreset), csrf)
        assertNull(menu.load(), "the default menu is still the default")
        assertEquals(1, sections.findSections().size)

        val created = browser.post("/admin/sections", newSectionForm(visitsPreset, "parent" to "page:visits"), csrf)

        assertEquals(HttpStatusCode.SeeOther, created.status)
        val archive = sections.findSections().first { it.slug == "archive" }
        assertEquals("page:visits", menu.load()!!.nodes.first { it.id == "section:${archive.id}" }.parent)
        assertContains(browser.get("/admin/sections").bodyAsText(), "место в меню: внутри «Визиты»")
        assertContains(browser.get("/admin/sections").bodyAsText(), "место в меню: верхний уровень")
    }

    @Test
    fun `the place of a section is changed in its settings`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        browser.post("/admin/sections", newSectionForm(visitsPreset, "parent" to "page:visits"), csrf)
        val archive = sections.findSections().single()

        val settings = browser.get("/admin/sections/${archive.id}/settings").bodyAsText()
        assertContains(settings, """<option value="page:visits" selected="selected">Внутри «Визиты»</option>""")
        // A section is not offered itself as a place.
        assertFalse("Внутри «Архив визитов»" in settings)

        val form = SectionForm.valuesOf(archive) + ("parent" to "page:help")
        val saved = browser.post("/admin/sections/${archive.id}/settings", form, csrf)

        assertEquals("/admin/sections/${archive.id}/settings?msg=settings-saved", saved.location())
        assertEquals("page:help", menu.load()!!.nodes.first { it.id == "section:${archive.id}" }.parent)

        browser.post("/admin/sections/${archive.id}/settings", form + ("parent" to ""), csrf)
        assertNull(menu.load()!!.nodes.first { it.id == "section:${archive.id}" }.parent)
    }

    @Test
    fun `a place that was not offered comes back as a mistake and nothing is saved`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        val created = browser.post("/admin/sections", newSectionForm(articlesPreset, "parent" to "page:nowhere"), csrf)

        assertEquals(HttpStatusCode.UnprocessableEntity, created.status)
        assertContains(created.bodyAsText(), "Выберите место из списка.")
        assertContains(created.bodyAsText(), "Где в меню")
        assertTrue(sections.findSections().isEmpty())

        browser.post("/admin/sections", newSectionForm(articlesPreset), csrf)
        val section = sections.findSections().single()
        val settings = browser.post("/admin/sections/${section.id}/settings", SectionForm.valuesOf(section) + ("parent" to "section:${section.id}"), csrf)
        assertEquals(HttpStatusCode.UnprocessableEntity, settings.status)
        assertNull(menu.load())
    }

    @Test
    fun `a section is not offered a place on the second level`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        browser.post("/admin/sections", newSectionForm(visitsPreset, "parent" to "page:visits"), csrf)

        val form = browser.get("/admin/sections/new?preset=articles").bodyAsText()

        assertFalse("Внутри «Архив визитов»" in form, "the archive is on the second level already")
    }

    @Test
    fun `deleting a section takes it out of the tree and lifts what stood under it`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        browser.post("/admin/sections", newSectionForm(articlesPreset), csrf)
        browser.post("/admin/sections", newSectionForm(visitsPreset, "parent" to "section:${sections.findSections().single().id}"), csrf)
        val (articles, archive) = sections.findSections()
        assertEquals("section:${articles.id}", menu.load()!!.nodes.first { it.id == "section:${archive.id}" }.parent)

        val deleted = browser.post("/admin/sections/${articles.id}/delete", emptyMap(), csrf)

        assertEquals("/admin/sections?msg=section-deleted", deleted.location())
        val tree = menu.load()!!
        assertNull(tree.nodes.firstOrNull { it.id == "section:${articles.id}" })
        assertNull(tree.nodes.first { it.id == "section:${archive.id}" }.parent)
    }

    // ---- the backup

    @Test
    fun `the backup carries the menu, an empty one when it was never saved`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        suspend fun menuFile(): String {
            val response = browser.post("/admin/export", emptyMap(), csrf)
            val found = mutableMapOf<String, String>()
            ZipInputStream(response.body<ByteArray>().inputStream()).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry -> found[entry.name] = ByteArrayOutputStream().also { zip.copyTo(it) }.toByteArray().decodeToString() }
            }
            assertContains(found.getValue("README.txt"), "menu.json")
            return found.getValue("menu.json")
        }

        assertEquals(MenuTree(), kotlinx.serialization.json.Json.decodeFromString(MenuTree.serializer(), menuFile()))

        browser.post("/admin/menu/groups", groupForm(), csrf)
        assertContains(menuFile(), "group:g1")
    }
}
