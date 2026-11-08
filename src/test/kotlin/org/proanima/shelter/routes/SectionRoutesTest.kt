package org.proanima.shelter.routes

import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.proanima.shelter.configureRoutes
import org.proanima.shelter.model.ChoiceOption
import org.proanima.shelter.model.FieldType
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.model.Section
import org.proanima.shelter.model.SectionField
import org.proanima.shelter.model.SectionItem
import org.proanima.shelter.model.SectionLayout
import org.proanima.shelter.repository.InMemorySectionRepository
import org.proanima.shelter.repository.MarkdownGuideRepository
import org.proanima.shelter.repository.JsonCatRepository
import org.proanima.shelter.repository.SectionRepository
import org.proanima.shelter.service.CatService
import org.proanima.shelter.service.GuideService
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The date format of the language puts a narrow no-break space before "г."; the tests compare plain spaces.
private const val NARROW_NBSP = ' '

// The pages of the sections made in the admin panel, as a visitor sees them.
class SectionRoutesTest {

    private fun text(ru: String, en: String? = null, sr: String? = null) = LocalizedText(ru, en, sr)

    private val articles = Section(
        id = 1, slug = "articles", title = text("Полезные статьи", "Useful articles", "Korisni članci"),
        menuTitle = text("Статьи", "Articles", "Članci"), intro = text("Про уход.", "About care."),
        layout = SectionLayout.CARDS, position = 1, useDate = true, usePhotos = true,
        tags = listOf(ChoiceOption("health", text("Здоровье", "Health")), ChoiceOption("cats", text("Кошки", "Cats")), ChoiceOption("unused", text("Не нужна", "Unused"))),
        fields = listOf(
            SectionField("f1", FieldType.LONGTEXT, text("Кратко", "Summary"), inList = true, showLabel = false),
            SectionField("f2", FieldType.MARKDOWN, text("Текст", "Text"), showLabel = false),
            SectionField("f3", FieldType.TEXT, text("Автор", "Author")),
            SectionField("f4", FieldType.LONGTEXT, text("Итог", "Result"), showLabel = true)
        )
    )

    private val archive = Section(
        id = 2, slug = "archive", title = text("Архив визитов", "Visit archive"), layout = SectionLayout.LIST, position = 2,
        useDate = true, usePhotos = false,
        fields = listOf(
            SectionField(
                "f1", FieldType.CHOICE, text("Статус", "Status"), inList = true,
                options = listOf(ChoiceOption("held", text("Состоялся", "Took place")), ChoiceOption("cancelled", text("Отменён", "Cancelled")))
            ),
            SectionField("f2", FieldType.LONGTEXT, text("Итог", "Result"), inList = true)
        )
    )

    private fun article(
        id: Int, title: LocalizedText, date: String, published: Boolean = true, tags: List<String> = emptyList(), photos: List<String> = emptyList(),
        vararg texts: Pair<String, LocalizedText>
    ) = SectionItem(
        id = id, sectionId = 1, title = title, date = date, photoUrls = photos, tags = tags,
        texts = mapOf("f1" to text("Кратко $id", "Short $id")) + texts, published = published
    )

    private val cats = article(10, text("Здоровая кошка", "Healthy cat", "Zdrava mačka"), "2026-10-01", tags = listOf("health", "cats"), photos = listOf("/images/item-10-aaaa1111.jpg"))
    private val winter = article(11, text("Зимний корм"), "2026-11-03", tags = listOf("health"))
    private val draft = article(12, text("Черновик", "Draft"), "2026-11-05", published = false)

    private fun ApplicationTestBuilder.start(sections: SectionRepository) {
        application {
            configureRoutes(CatService(JsonCatRepository()), GuideService(MarkdownGuideRepository()), sections = sections)
        }
    }

    private fun repository(vararg items: SectionItem, sections: List<Section> = listOf(articles, archive)) =
        InMemorySectionRepository(sections, items.toList())

    private fun main(body: String) = body.substringAfter("<main").substringBefore("</main>")

    private fun navLinks(body: String): List<String> =
        Regex("""<nav class="main-nav".*?</nav>""", RegexOption.DOT_MATCHES_ALL).find(body)!!.value
            .let { nav -> Regex("""href="([^"]+)"""").findAll(nav).map { it.groupValues[1] }.toList() }

    // ---- the menu

    @Test
    fun `a section is in the menu of every page once it has something published, after the fixed pages`() = testApplication {
        start(repository(cats))

        listOf("/ru", "/ru/cats", "/ru/guide", "/ru/articles", "/ru/nothing-here").forEach { path ->
            val body = client.get(path).plainText()
            assertEquals(
                listOf("/ru/cats", "/ru/dogs", "/ru/guide", "/ru/visits", "/ru/help", "/ru/articles"), navLinks(body), path
            )
            assertTrue(hasTag(body, "header", """class="site-header nav-two-rows""""), path)
        }
    }

    @Test
    fun `the menu uses the short name, and a section with only drafts is not in it`() = testApplication {
        start(repository(cats, draft.copy(id = 13, sectionId = 2)))

        val body = client.get("/ru").plainText()
        val nav = body.substringAfter("""<nav class="main-nav"""").substringBefore("</nav>")

        assertContains(nav, ">Статьи</a>")
        assertFalse(nav.contains("Архив"))
        assertEquals(listOf("/ru/cats", "/ru/dogs", "/ru/guide", "/ru/visits", "/ru/help", "/ru/articles"), navLinks(body))
    }

    @Test
    fun `without a section the header and the menu are what they were`() = testApplication {
        start(repository(draft))

        val body = client.get("/ru").plainText()

        assertTrue(hasTag(body, "header", """class="site-header""""))
        assertFalse(body.contains("nav-two-rows"))
        assertEquals(5, navLinks(body).size)
    }

    @Test
    fun `the menu entries follow the position and the language`() = testApplication {
        val moved = articles.copy(position = 9)
        start(repository(cats, SectionItem(id = 20, sectionId = 2, title = text("Выезд", "Trip"), date = "2026-11-01", published = true), sections = listOf(moved, archive)))

        assertEquals("/ru/archive", navLinks(client.get("/ru").plainText())[5])
        val en = client.get("/en").plainText().substringAfter("""<nav class="main-nav"""").substringBefore("</nav>")
        assertContains(en, ">Visit archive</a>")
        assertContains(en, ">Articles</a>")
    }

    @Test
    fun `a menu that cannot be read is a menu without the sections, not a broken page`() = testApplication {
        start(object : SectionRepository {
            override fun findSections(): List<Section> = error("the database is down")

            override fun findItems(): List<SectionItem> = error("the database is down")
        })

        assertEquals(HttpStatusCode.OK, client.get("/ru/cats").status)
        assertEquals(5, navLinks(client.get("/ru").plainText()).size)
    }

    // ---- the list of a section

    @Test
    fun `the list shows the published entries newest first, as cards`() = testApplication {
        start(repository(cats, winter, draft))

        val response = client.get("/ru/articles")
        val body = response.plainText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf("Зимний корм", "Здоровая кошка"), Regex("""<span class="cat-card-name">([^<]*)</span>""").findAll(body).map { it.groupValues[1] }.toList())
        assertFalse(body.contains("Черновик"))
        assertContains(body, """<h1>Полезные статьи</h1>""")
        assertContains(body, "Про уход.")
        assertContains(body, "Кратко 11")
        assertContains(body, "3 ноября 2026")
        assertContains(body, """href="/ru/articles/healthy-cat"""")
        assertTrue(hasTag(body, "img", """src="/images/item-10-aaaa1111-card.jpg""""))
        assertEquals("Полезные статьи · Pro Anima", Regex("<title>\\s*(.*?)\\s*</title>").find(body)!!.groupValues[1])
    }

    @Test
    fun `a section without dates lists the entries from the newest made, and a list section is rows`() = testApplication {
        val plain = articles.copy(useDate = false)
        start(repository(cats, winter, sections = listOf(plain, archive)))

        val body = client.get("/ru/articles").plainText()
        assertEquals(listOf("Зимний корм", "Здоровая кошка"), Regex("""<span class="cat-card-name">([^<]*)</span>""").findAll(body).map { it.groupValues[1] }.toList())
        assertFalse(body.contains("ноября 2026"))
    }

    @Test
    fun `an archive of trips is rows with the date, the status and the result`() = testApplication {
        val trip = SectionItem(
            id = 30, sectionId = 2, title = text("Выезд в среду", "Wednesday"), date = "2026-11-05", published = true,
            plain = mapOf("f1" to "cancelled"), texts = mapOf("f2" to text("Не поехали: шторм. " + "и ".repeat(200)))
        )
        val earlier = trip.copy(id = 31, title = text("Выезд в субботу"), date = "2026-10-25", plain = mapOf("f1" to "held"), texts = mapOf("f2" to text("Съездили.")))
        start(repository(earlier, trip))

        val body = main(client.get("/ru/archive").plainText()).replace(NARROW_NBSP, ' ')

        assertTrue(hasTag(body, "ul", """class="entry-list""""))
        assertEquals(listOf("Выезд в среду", "Выезд в субботу"), Regex("""<a href="[^"]+" class="entry-title">([^<]*)</a>""").findAll(body).map { it.groupValues[1] }.toList())
        assertContains(body, "5 ноября 2026 г. · Отменён")
        assertContains(body, "25 октября 2026 г. · Состоялся")
        assertContains(body, "Не поехали: шторм.")
        // A long result is cut in the list; the page of the entry has all of it.
        assertTrue("…" in body, "the long text is cut")
    }

    @Test
    fun `tags filter the list, and only tags that an entry has are offered`() = testApplication {
        start(repository(cats, winter))

        val all = main(client.get("/ru/articles").plainText())
        assertEquals(listOf("Все", "Здоровье", "Кошки"), Regex("""<a href="[^"]+" class="tag"[^>]*>([^<]*)</a>""").findAll(all).map { it.groupValues[1] }.toList())
        assertFalse(all.contains("Не нужна"))

        val filtered = main(client.get("/ru/articles?tag=cats").plainText())
        assertEquals(listOf("Здоровая кошка"), Regex("""<span class="cat-card-name">([^<]*)</span>""").findAll(filtered).map { it.groupValues[1] }.toList())
        assertTrue(hasTag(filtered, "a", """aria-current="true"""", "/ru/articles?tag=cats"))

        // A tag that does not exist, or that no entry has, filters nothing.
        listOf("nonsense", "unused", "").forEach { tag ->
            val unfiltered = main(client.get("/ru/articles?tag=$tag").plainText())
            assertEquals(2, Regex("""cat-card-name""").findAll(unfiltered).count(), tag)
        }
    }

    @Test
    fun `a section whose tags are not used offers no filter`() = testApplication {
        start(repository(winter.copy(tags = emptyList())))

        assertFalse(client.get("/ru/articles").plainText().contains("tag-filter"))
    }

    // ---- languages

    @Test
    fun `an entry is shown in a language only when its title is written in it`() = testApplication {
        start(repository(cats, winter))

        val en = client.get("/en/articles").plainText()
        assertEquals(listOf("Healthy cat"), Regex("""<span class="cat-card-name">([^<]*)</span>""").findAll(en).map { it.groupValues[1] }.toList())
        assertContains(en, "<h1>Useful articles</h1>")
        assertEquals(HttpStatusCode.OK, client.get("/en/articles/healthy-cat").status)
        // The entry with no English title has the number for an address, and no page in English.
        assertEquals(HttpStatusCode.NotFound, client.get("/en/articles/11").status)
        assertEquals(HttpStatusCode.OK, client.get("/ru/articles/11").status)
    }

    @Test
    fun `a section with nothing in a language is not in that language's menu and has no page there`() = testApplication {
        start(repository(winter))

        assertEquals(HttpStatusCode.OK, client.get("/ru/articles").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/en/articles").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/sr/articles").status)
        assertEquals(5, navLinks(client.get("/en").plainText()).size)
        assertEquals(6, navLinks(client.get("/ru").plainText()).size)
        // The header is as high in every language: the rule counts the languages together, not the page's own.
        listOf("/ru", "/en", "/sr").forEach { assertTrue(hasTag(client.get(it).plainText(), "header", """class="site-header nav-two-rows""""), it) }
    }

    @Test
    fun `the language links of an entry in two languages lead home for the third, and the alternates list the two`() = testApplication {
        start(repository(cats.copy(title = text("Здоровая кошка", "Healthy cat"))))

        val body = client.get("/ru/articles/healthy-cat").plainText()

        assertTrue(hasTag(body, "a", """href="/en/articles/healthy-cat"""", """hreflang="en""""))
        assertTrue(hasTag(body, "a", """href="/sr"""", """hreflang="sr""""))
        val alternates = Regex("""<link href="([^"]+)" rel="alternate" hreflang="([^"]+)"""").findAll(body).map { it.groupValues[2] }.toList()
        assertEquals(listOf("ru", "en", "x-default"), alternates)
    }

    // ---- the page of an entry

    @Test
    fun `an entry page has the title, date, tags, photos and the fields in order`() = testApplication {
        val full = cats.copy(
            texts = cats.texts + mapOf(
                "f2" to text("## Что смотреть\n\n- Шерсть\n- Аппетит **как обычно**\n\n[врач](https://example.com)", "## Look at"),
                "f3" to text("Мария", "Maria"),
                "f4" to text("Всё хорошо.\n\nВторой абзац.", "Fine.")
            )
        )
        start(repository(full))

        val response = client.get("/ru/articles/healthy-cat")
        val body = response.plainText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(hasTag(body, "main", """class="page cat-page""""))
        assertContains(body, """<p class="back"><a href="/ru/articles">← Полезные статьи</a></p>""")
        assertContains(body, "<h1>Здоровая кошка</h1>")
        assertContains(body, "1 октября 2026")
        assertTrue(hasTag(body, "a", """href="/ru/articles?tag=health"""", """class="tag""""))
        assertTrue(hasTag(body, "a", """href="/ru/articles?tag=cats"""", """class="tag""""))
        assertContains(body, """class="cat-gallery"""")
        assertContains(body, "<h2>Что смотреть</h2>")
        assertContains(body, "<li>Аппетит <strong>как обычно</strong></li>")
        assertContains(body, """<a href="https://example.com">врач</a>""")
        // A short value has its label beside it; a long text under its label only if the field says so.
        assertTrue(Regex("""<dt>Автор</dt>\s*<dd>Мария</dd>""").containsMatchIn(body))
        assertContains(body, "<h2>Итог</h2>")
        assertFalse(body.contains("<h2>Текст</h2>"), "the text field says not to show its label")
        assertFalse(body.contains("<h2>Кратко</h2>"))
        assertContains(body, "<p>Второй абзац.</p>")
        assertTrue(body.indexOf("Кратко 10") < body.indexOf("Что смотреть"), "the fields keep the section's order")
        assertTrue(hasTag(body, "meta", """property="og:image"""", "item-10-aaaa1111.jpg"))
    }

    @Test
    fun `an entry without photos is the text alone`() = testApplication {
        start(repository(winter))

        val noPhoto = client.get("/ru/articles/11").plainText()

        assertTrue(hasTag(noPhoto, "main", """class="page""""))
        assertFalse(noPhoto.contains("cat-gallery"))
        assertContains(noPhoto, """class="entry-body"""")
    }

    @Test
    fun `a section without photos ignores the photos an entry still carries`() = testApplication {
        val none = Section(id = 5, slug = "words", title = text("Слова"), usePhotos = false)
        start(InMemorySectionRepository(listOf(none), listOf(SectionItem(id = 50, sectionId = 5, title = text("Слово"), photoUrls = listOf("/images/x.jpg"), published = true))))

        val body = client.get("/ru/words/50").plainText()

        assertEquals(HttpStatusCode.OK, client.get("/ru/words/50").status)
        assertFalse(body.contains("cat-gallery"))
        assertFalse(body.contains("/images/x"))
    }

    @Test
    fun `a link that is not a web address is shown as text, never as a link`() = testApplication {
        val evil = winter.copy(texts = winter.texts + ("f2" to text("[click me](javascript:alert(1)) and [fine](https://example.com) and <b>x</b>")))
        start(repository(evil))

        val body = client.get("/ru/articles/11").plainText()

        assertFalse(body.contains("javascript:"))
        assertContains(body, "click me")
        assertContains(body, """<a href="https://example.com">fine</a>""")
        assertContains(body, "&lt;b&gt;x&lt;/b&gt;")
    }

    @Test
    fun `a draft entry has no page, and a section that has only drafts has no page`() = testApplication {
        start(repository(cats, draft, draft.copy(id = 13, sectionId = 2, title = text("Черновик выезда"))))

        assertEquals(HttpStatusCode.NotFound, client.get("/ru/articles/draft").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/ru/articles/12").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/ru/archive").status)
        assertEquals(HttpStatusCode.OK, client.get("/ru/articles").status)
    }

    @Test
    fun `addresses that are no section's are the usual 404, and an entry is only under its own section`() = testApplication {
        start(repository(cats, SectionItem(id = 40, sectionId = 2, title = text("Выезд", "Trip"), date = "2026-11-01", published = true)))

        assertEquals(HttpStatusCode.NotFound, client.get("/ru/unknown").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/ru/articles/unknown").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/ru/articles/trip").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/ru/archive/healthy-cat").status)
        assertEquals(HttpStatusCode.OK, client.get("/ru/archive/trip").status)
        val missing = client.get("/ru/unknown").plainText()
        assertContains(missing, "<title>")
        assertEquals(7, navLinks(missing).size, "the 404 page has the same menu, with both sections")
    }

    @Test
    fun `the fixed pages win over a section that has taken their address`() = testApplication {
        val hijack = articles.copy(id = 3, slug = "cats")
        start(InMemorySectionRepository(listOf(hijack), listOf(cats.copy(sectionId = 3))))

        val body = client.get("/ru/cats").plainText()

        assertEquals(HttpStatusCode.OK, client.get("/ru/cats").status)
        assertFalse(body.contains("Здоровая кошка"))
    }

    @Test
    fun `a HEAD request is answered like a GET without the body`() = testApplication {
        start(repository(cats))

        val response: HttpResponse = client.head("/ru/articles/healthy-cat")

        assertEquals(HttpStatusCode.OK, response.status)
    }
}
