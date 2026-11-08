package org.proanima.shelter.routes

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.proanima.shelter.configureRoutes
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.model.MenuNode
import org.proanima.shelter.model.MenuTree
import org.proanima.shelter.model.Section
import org.proanima.shelter.model.SectionItem
import org.proanima.shelter.repository.InMemoryMenuRepository
import org.proanima.shelter.repository.InMemorySectionRepository
import org.proanima.shelter.repository.JsonCatRepository
import org.proanima.shelter.repository.MarkdownGuideRepository
import org.proanima.shelter.repository.MenuRepository
import org.proanima.shelter.service.CatService
import org.proanima.shelter.service.GuideService
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The menu as a visitor's browser gets it: the tree drawn as links, groups and drop-downs.
class MenuRoutesTest {

    private fun text(ru: String, en: String? = null, sr: String? = null) = LocalizedText(ru, en, sr)

    private val archive = Section(id = 1, slug = "archive", title = text("Архив визитов", "Visit archive", "Arhiva poseta"))
    private val trip = SectionItem(id = 10, sectionId = 1, title = text("Выезд", "Trip", "Izlazak"), published = true)

    // The five pages, Cats and Dogs in a group, the archive under Visits: five entries at the top level.
    private val target = MenuTree(
        listOf(
            MenuNode("group:g1", position = 10, title = text("Животные", "Animals", "Životinje")),
            MenuNode("page:cats", parent = "group:g1", position = 10),
            MenuNode("page:dogs", parent = "group:g1", position = 20),
            MenuNode("page:guide", position = 20),
            MenuNode("page:visits", position = 30),
            MenuNode("section:1", parent = "page:visits", position = 10),
            MenuNode("page:help", position = 40)
        ),
        nextGroupNumber = 2
    )

    private fun ApplicationTestBuilder.start(tree: MenuTree?, sections: List<Section> = listOf(archive), items: List<SectionItem> = listOf(trip)) {
        start(InMemoryMenuRepository(tree), sections, items)
    }

    private fun ApplicationTestBuilder.start(menu: MenuRepository, sections: List<Section> = listOf(archive), items: List<SectionItem> = listOf(trip)) {
        application {
            configureRoutes(
                CatService(JsonCatRepository()), GuideService(MarkdownGuideRepository()),
                sections = InMemorySectionRepository(sections, items), menu = menu
            )
        }
    }

    private fun nav(body: String) = Regex("""<nav class="main-nav".*?</nav>""", RegexOption.DOT_MATCHES_ALL).find(body)!!.value

    private fun hrefs(markup: String) = Regex("""href="([^"]+)"""").findAll(markup).map { it.groupValues[1] }.toList()

    // The tag of the link to this address, the first one in the markup given.
    private fun linkTo(markup: String, href: String) = Regex("""<a [^>]*href="$href"[^>]*>""").find(markup)!!.value

    // ---- a site that never touched its menu

    @Test
    fun `without a tree and without sections the header is the one the site always had`() = testApplication {
        start(null, sections = emptyList(), items = emptyList())

        listOf("/ru", "/en/cats", "/sr/guide", "/ru/nothing-here").forEach { path ->
            val body = client.get(path).plainText()
            val menu = nav(body)
            assertTrue(hasTag(body, "header", """class="site-header""""), path)
            assertFalse("nav-group" in body || "nav-js" in body || "nav-menu.js" in body || "nav-two-rows" in body, path)
            assertEquals(5, hrefs(menu).size, path)
            assertFalse("<button" in menu, path)
        }
        val ru = nav(client.get("/ru/guide").plainText())
        assertContains(ru, """<a href="/ru/cats">Кошки</a>""")
        assertContains(ru, """<a href="/ru/guide" aria-current="page">Гайд</a>""")
    }

    // ---- the tree drawn

    @Test
    fun `a group is a button with a drop-down, and a page with entries below it is a link with a button beside it`() = testApplication {
        start(target)

        val menu = nav(client.get("/ru").plainText())

        assertEquals(
            listOf("/ru/cats", "/ru/dogs", "/ru/guide", "/ru/visits", "/ru/archive", "/ru/help"), hrefs(menu)
        )
        // The group has no page of its own: its label is a button that names the drop-down it opens.
        assertContains(menu, """class="nav-toggle nav-label"""")
        assertContains(menu, """aria-controls="nav-sub-group-g1"""")
        assertContains(menu, """<div class="nav-sub" id="nav-sub-group-g1"><a href="/ru/cats">Кошки</a><a href="/ru/dogs">Собаки</a></div>""")
        assertContains(menu, "Животные")
        // The page keeps its own link; the button beside it is hidden until the script has run.
        assertContains(menu, """<a href="/ru/visits">Визиты</a>""")
        val toggle = Regex("""<button[^>]*nav-toggle-only[^>]*>""").find(menu)!!.value
        assertContains(toggle, """hidden="hidden"""")
        assertContains(toggle, """aria-expanded="false"""")
        assertContains(toggle, """aria-controls="nav-sub-page-visits"""")
        assertContains(toggle, """aria-label="Подразделы: Визиты"""")
        assertContains(menu, """<div class="nav-sub" id="nav-sub-page-visits"><a href="/ru/archive">Архив визитов</a></div>""")
    }

    @Test
    fun `every language draws the tree in its own words`() = testApplication {
        start(target)

        val en = nav(client.get("/en").plainText())
        val sr = nav(client.get("/sr").plainText())

        assertContains(en, "Animals")
        assertContains(en, """aria-label="Subsections: Visits"""")
        assertContains(en, """<a href="/en/archive">Visit archive</a>""")
        assertContains(sr, "Životinje")
        assertContains(sr, """aria-label="Podstranice: Posete"""")
        assertContains(sr, """<a href="/sr/archive">Arhiva poseta</a>""")
    }

    @Test
    fun `a page with a drop-down brings the scripts that work it, a page without one does not`() = testApplication {
        start(target)
        val with = client.get("/ru").plainText()
        assertContains(with, """document.documentElement.classList.add("nav-js")""")
        assertContains(with, """<script src="/scripts/nav-menu.js" defer="defer">""")
        assertEquals(HttpStatusCode.OK, client.get("/scripts/nav-menu.js").status)
    }

    @Test
    fun `five entries at the top level are one row`() = testApplication {
        start(target)

        listOf("/ru", "/en", "/sr").forEach { assertTrue(hasTag(client.get(it).plainText(), "header", """class="site-header""""), it) }
    }

    @Test
    fun `six entries at the top level are two rows in every language`() = testApplication {
        start(
            MenuTree(
                listOf(
                    MenuNode("page:cats", position = 10), MenuNode("page:dogs", position = 20), MenuNode("page:guide", position = 30),
                    MenuNode("page:visits", position = 40), MenuNode("page:help", position = 50), MenuNode("section:1", position = 60)
                )
            )
        )

        listOf("/ru", "/en", "/sr").forEach { assertTrue(hasTag(client.get(it).plainText(), "header", """class="site-header nav-two-rows""""), it) }
    }

    // ---- where the visitor is

    @Test
    fun `the page the visitor is on is marked, and a parent of it is only underlined`() = testApplication {
        start(target)

        val onChild = nav(client.get("/ru/archive").plainText())
        assertContains(linkTo(onChild, "/ru/archive"), """aria-current="page"""")
        assertContains(linkTo(onChild, "/ru/visits"), """class="nav-branch"""")
        assertFalse("aria-current" in linkTo(onChild, "/ru/visits"))

        val onEntry = nav(client.get("/ru/archive/trip").plainText())
        assertContains(linkTo(onEntry, "/ru/archive"), """aria-current="page"""")
        assertContains(linkTo(onEntry, "/ru/visits"), """class="nav-branch"""")

        val onParent = nav(client.get("/ru/visits").plainText())
        assertContains(linkTo(onParent, "/ru/visits"), """aria-current="page"""")
        assertFalse("nav-branch" in onParent.substringBefore("nav-sub"))
    }

    @Test
    fun `the button of a group is underlined when the visitor is on a page inside it`() = testApplication {
        start(target)

        val inside = nav(client.get("/ru/dogs").plainText())
        val outside = nav(client.get("/ru/guide").plainText())

        assertContains(inside, """class="nav-toggle nav-label nav-branch"""")
        assertContains(linkTo(inside, "/ru/dogs"), """aria-current="page"""")
        assertContains(outside, """class="nav-toggle nav-label"""")
    }

    // ---- hiding

    @Test
    fun `a hidden page is out of the menu but still opens, and what is in it goes too`() = testApplication {
        val hidden = target.copy(nodes = target.nodes.map { if (it.id == "page:visits" || it.id == "page:guide") it.copy(hidden = true) else it })
        start(hidden)

        val menu = nav(client.get("/ru").plainText())

        assertEquals(listOf("/ru/cats", "/ru/dogs", "/ru/help"), hrefs(menu))
        assertEquals(HttpStatusCode.OK, client.get("/ru/guide").status)
        assertEquals(HttpStatusCode.OK, client.get("/ru/visits").status)
        assertEquals(HttpStatusCode.OK, client.get("/ru/archive").status)
        // The links of the other pages to a hidden page still work: the home page and the footer carry them.
        assertContains(client.get("/ru").plainText().substringAfter("</header>"), """href="/ru/guide"""")
    }

    // ---- faults

    @Test
    fun `a tree that cannot be read is the default menu, not a broken page`() = testApplication {
        start(object : MenuRepository {
            override fun load(): MenuTree = error("the database is down")
        })

        val response = client.get("/ru/cats")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf("/ru/cats", "/ru/dogs", "/ru/guide", "/ru/visits", "/ru/help", "/ru/archive"), hrefs(nav(response.plainText())))
    }

    @Test
    fun `the page for an unknown address has the same menu`() = testApplication {
        start(target)

        val missing = client.get("/ru/nothing-here")

        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertContains(nav(missing.plainText()), "nav-sub-group-g1")
    }
}
