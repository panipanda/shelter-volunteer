package org.proanima.shelter.admin

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.routes.hasTag
import org.proanima.shelter.routes.plainText
import org.proanima.shelter.service.ContentKey
import org.proanima.shelter.views.PUBLIC_BASE_URL
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AdminPagesTest : AdminTestBase() {

    private val defaults = ContentDefaults(guideRepository)
    private val ruMessages = messagesFor(AppLocale.RU)
    private val enMessages = messagesFor(AppLocale.EN)

    // What the browser sends when the person changes only the given fields: every field of the page as the
    // form shows it now.
    private fun formWith(pageId: String, vararg changes: Pair<String, String>): Map<String, String> =
        PageForm.valuesOf(ContentCatalog.find(pageId)!!, siteContent.load(), defaults) + changes.toMap()

    private suspend fun HttpClient.save(pageId: String, csrf: String?, vararg changes: Pair<String, String>): HttpResponse =
        post("/admin/pages/$pageId", formWith(pageId, *changes), csrf)

    private val headline = ContentKey("home.hero.headline", "ru")

    // ---- who may

    @Test
    fun `an anonymous visitor is sent to the login page and cannot save`() = testApplication {
        start()
        val anonymous = browser()

        listOf("/admin/pages", "/admin/pages/home", "/admin/pages/home/photo").forEach { path ->
            val response = anonymous.get(path)
            assertEquals(HttpStatusCode.SeeOther, response.status, path)
            assertEquals("/admin/login", response.headers[HttpHeaders.Location], path)
        }
        val posted = anonymous.save("home", null, "home.hero.headline_ru" to "Взлом")
        assertEquals("/admin/login", posted.headers[HttpHeaders.Location])
        assertEquals(emptyMap(), siteContent.load().entries)
    }

    @Test
    fun `a form without the token, or with a wrong one, saves nothing`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()

        listOf(null, "wrong-token").forEach { token ->
            val response = browser.save("home", token, "home.hero.headline_ru" to "Чужой заголовок")
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
        assertEquals(emptyMap(), siteContent.load().entries)
    }

    @Test
    fun `an unknown page is not found`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()

        assertEquals(HttpStatusCode.NotFound, browser.get("/admin/pages/nope").status)
        assertEquals(HttpStatusCode.NotFound, browser.get("/admin/pages/nope/photo").status)
    }

    // ---- the list and the form

    @Test
    fun `the list shows the four pages and how many texts were changed`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        val before = browser.get("/admin/pages").bodyAsText()
        listOf("Главная", "Визиты", "Гайд волонтёра", "Как помочь").forEach { assertContains(before, it) }
        assertFalse(before.contains("изменено:"))

        browser.save("home", csrf, "home.hero.headline_ru" to "Новый заголовок")

        assertContains(browser.get("/admin/pages").bodyAsText(), "изменено: 1")
    }

    @Test
    fun `the menu has both parts of the panel and marks the current one`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()

        val body = browser.get("/admin/pages").bodyAsText()
        assertContains(body, """href="/admin"""")
        assertTrue(Regex("""<a href="/admin/pages"[^>]*aria-current="page"[^>]*>""").containsMatchIn(body) ||
            Regex("""<a [^>]*aria-current="page"[^>]*href="/admin/pages"""").containsMatchIn(body))
    }

    @Test
    fun `the form of a page shows the texts the site has now, in all three languages`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()

        val body = browser.get("/admin/pages/home").bodyAsText()

        listOf("ru", "en", "sr").forEach { code ->
            val shipped = messagesFor(AppLocale.entries.first { it.code == code }).t("home.hero.headline")
            assertTrue(hasTag(body, "input", """name="home.hero.headline_$code"""", """value="$shipped""""), code)
        }
    }

    @Test
    fun `a changed text is marked, with the shipped one shown and the reset box offered`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        browser.save("home", csrf, "home.hero.headline_ru" to "Новый заголовок")

        val body = browser.get("/admin/pages/home").bodyAsText()

        assertContains(body, """name="reset:home.hero.headline_ru"""")
        assertContains(body, "Исходный текст: «${ruMessages.t("home.hero.headline")}»")
        // The texts nobody changed have neither.
        assertFalse(body.contains("""name="reset:home.hero.headline_en""""))
    }

    // ---- saving

    @Test
    fun `saving one text changes that text in that language only`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        val response = browser.save("home", csrf, "home.hero.headline_ru" to "Новый заголовок")

        assertEquals(HttpStatusCode.SeeOther, response.status)
        assertEquals("/admin/pages/home?msg=saved", response.headers[HttpHeaders.Location])
        assertEquals(mapOf(headline to "Новый заголовок"), siteContent.load().entries)
        assertContains(client.get("/ru").plainText(), "Новый заголовок")
        assertContains(client.get("/en").plainText(), enMessages.t("home.hero.headline"))
        assertFalse(client.get("/en").plainText().contains("Новый заголовок"))
    }

    @Test
    fun `saving the form without changes stores nothing`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        ContentCatalog.pages.forEach { page ->
            assertEquals(HttpStatusCode.SeeOther, browser.save(page.id, csrf).status, page.id)
        }

        assertEquals(emptyMap(), siteContent.load().entries)
    }

    @Test
    fun `typing the shipped text back, or emptying the field, brings the shipped text back`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        browser.save("home", csrf, "home.hero.headline_ru" to "Новый", "home.hero.lead_en" to "A new lead")
        assertEquals(2, siteContent.load().entries.size)

        browser.save("home", csrf, "home.hero.headline_ru" to ruMessages.t("home.hero.headline"), "home.hero.lead_en" to "")

        assertEquals(emptyMap(), siteContent.load().entries)
        assertContains(client.get("/ru").plainText(), ruMessages.t("home.hero.headline"))
    }

    @Test
    fun `the reset box brings the shipped text back even if the field still holds the edit`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        browser.save("home", csrf, "home.hero.headline_ru" to "Новый")

        browser.save("home", csrf, "reset:home.hero.headline_ru" to "on")

        assertEquals(emptyMap(), siteContent.load().entries)
    }

    @Test
    fun `line breaks in a text become spaces, as the site prints one paragraph`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        browser.save("home", csrf, "home.hero.lead_ru" to "Первая строка\r\n  вторая строка  \n")

        assertEquals("Первая строка вторая строка", siteContent.load().text("home.hero.lead", "ru"))
    }

    @Test
    fun `HTML typed into a text is shown as text`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        browser.save("home", csrf, "home.hero.headline_ru" to "<script>alert(1)</script>")

        val body = client.get("/ru").bodyAsText()
        assertFalse(body.contains("<script>alert(1)"))
        assertContains(body, "&lt;script&gt;alert(1)&lt;/script&gt;")
    }

    // ---- what is refused

    @Test
    fun `a text that is too long is refused, nothing is saved and the form comes back as typed`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val tooLong = "я".repeat(121)

        val response = browser.save("home", csrf, "home.hero.headline_ru" to tooLong, "home.hero.lead_en" to "Fine lead")

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Слишком длинный текст: не больше 120 знаков.")
        assertContains(body, tooLong)
        assertContains(body, "Fine lead")
        // One mistake keeps the whole save back, the correct fields too.
        assertEquals(emptyMap(), siteContent.load().entries)
    }

    @Test
    fun `the time and the guide link must stay in the sentences that need them`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        val noTime = browser.save("visits", csrf, "home.nextVisit.departure_ru" to "Выезд в девять")
        val twoTimes = browser.save("visits", csrf, "home.nextVisit.departure_ru" to "Выезд в {0}, потом в {0}")
        val otherBraces = browser.save("visits", csrf, "home.nextVisit.departure_ru" to "Выезд в {0} {x}")
        val noLink = browser.save("visits", csrf, "home.join.step1_ru" to "Прочитайте гайд.")

        listOf(noTime, twoTimes, otherBraces, noLink).forEach { assertEquals(HttpStatusCode.UnprocessableEntity, it.status) }
        assertContains(noTime.bodyAsText(), "должно остаться {0}")
        assertContains(noLink.bodyAsText(), "должно остаться {link}")
        assertEquals(emptyMap(), siteContent.load().entries)
    }

    @Test
    fun `a sentence with the time keeps its apostrophe and gets the time`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        assertEquals(HttpStatusCode.SeeOther, browser.save("visits", csrf, "home.nextVisit.departure_en" to "We're leaving at {0}.").status)

        val body = client.get("/en/visits").plainText()
        assertTrue(Regex("""We(&#39;|')re leaving at \d{1,2}:\d{2}\.""").containsMatchIn(body), body)
    }

    @Test
    fun `a Telegram name is cleaned up, checked and used in the link`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        browser.save("visits", csrf, "contact.nata.telegram" to "@New_Nata")
        browser.save("visits", csrf, "contact.alina.telegram" to "https://t.me/Alina_Two")

        assertEquals("New_Nata", siteContent.load().plain("contact.nata.telegram"))
        assertEquals("Alina_Two", siteContent.load().plain("contact.alina.telegram"))
        val home = client.get("/ru").bodyAsText()
        assertTrue(hasTag(home, "a", """href="https://t.me/New_Nata""""))
        assertTrue(hasTag(home, "a", """href="https://t.me/Alina_Two""""))
        assertFalse(home.contains("t.me/Nata_Rogava"))

        val bad = browser.save("visits", csrf, "contact.dasha.telegram" to "no way!")
        assertEquals(HttpStatusCode.UnprocessableEntity, bad.status)
        assertContains(bad.bodyAsText(), "Имя в Telegram")
    }

    @Test
    fun `a name that is the shipped one stores nothing`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        browser.save("visits", csrf, "contact.nata.telegram" to "@Nata_Rogava")

        assertEquals(emptyMap(), siteContent.load().entries)
    }

    @Test
    fun `the donation address must be a full https address`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        listOf("javascript:alert(1)", "http://example.org/donate", "example.org", "https://exa mple.org", "https://").forEach { bad ->
            val response = browser.save("help", csrf, "help.donate.url" to bad)
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status, bad)
        }
        assertEquals(emptyMap(), siteContent.load().entries)

        browser.save("help", csrf, "help.donate.url" to "https://example.org/donate")

        assertTrue(hasTag(client.get("/ru/help").bodyAsText(), "a", """href="https://example.org/donate"""", "button"))
    }

    // ---- lists and the guide

    @Test
    fun `a list takes one item per line and the page shows exactly those`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        browser.save("help", csrf, "help.needs.items_ru" to "корм\nлежанки\n\n  миски  ")

        val list = client.get("/ru/help").plainText().substringAfter("""class="help-list"""").substringBefore("</ul>")
        assertEquals(listOf("корм", "лежанки", "миски"), Regex("<li>(.*?)</li>").findAll(list).map { it.groupValues[1] }.toList())
        // The other languages keep their lists.
        val english = client.get("/en/help").plainText().substringAfter("""class="help-list"""").substringBefore("</ul>")
        assertEquals(5, Regex("<li>").findAll(english).count())
    }

    @Test
    fun `a list that is too long, or has a too long item, is refused`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        val tooMany = browser.save("help", csrf, "help.needs.items_ru" to (1..13).joinToString("\n") { "пункт $it" })
        val tooLong = browser.save("help", csrf, "help.needs.items_ru" to "я".repeat(301))

        assertEquals(HttpStatusCode.UnprocessableEntity, tooMany.status)
        assertContains(tooMany.bodyAsText(), "не больше 12")
        assertEquals(HttpStatusCode.UnprocessableEntity, tooLong.status)
        assertEquals(emptyMap(), siteContent.load().entries)
    }

    @Test
    fun `the guide is edited per language and the others keep theirs`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val text = "# Свой гайд\n\n## Раздел {#razdel}\n\nТекст с [ссылкой](https://example.org).\n\n- первый\n- второй"

        val response = browser.save("guide", csrf, "guide.markdown_ru" to text)

        assertEquals(HttpStatusCode.SeeOther, response.status)
        val ru = client.get("/ru/guide").plainText()
        assertContains(ru, "Свой гайд")
        assertContains(ru, """id="razdel"""")
        assertContains(ru, """<a href="https://example.org">ссылкой</a>""")
        assertContains(client.get("/en/guide").plainText(), "<h1>Guide</h1>")
        assertEquals(setOf(ContentKey("guide.markdown", "ru")), siteContent.load().entries.keys)
    }

    @Test
    fun `the guide form shows the shipped guide until it is edited`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()

        val body = browser.get("/admin/pages/guide").bodyAsText()

        assertTrue(Regex("""<textarea[^>]*name="guide.markdown_ru"[^>]*>\s*# Guide""").containsMatchIn(body), body)
    }

    @Test
    fun `the guide needs a title, and its links must be safe`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        val noTitle = browser.save("guide", csrf, "guide.markdown_ru" to "## Только раздел\n\nТекст")
        val badLink = browser.save("guide", csrf, "guide.markdown_ru" to "# Гайд\n\n[нажми](javascript:alert(1))")
        val goodLinks = browser.save(
            "guide", csrf,
            "guide.markdown_ru" to "# Гайд\n\n[a](https://example.org) [b](/ru/help) [c](#razdel) [d](mailto:a@example.org)"
        )

        assertEquals(HttpStatusCode.UnprocessableEntity, noTitle.status)
        assertContains(noTitle.bodyAsText(), "# ")
        assertEquals(HttpStatusCode.UnprocessableEntity, badLink.status)
        assertContains(badLink.bodyAsText(), "не подходит")
        assertEquals(HttpStatusCode.SeeOther, goodLinks.status)
    }

    @Test
    fun `a very long guide in three languages still fits in one form`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val long = "# Гайд\n\n" + "Строка гайда на русском языке.\n\n".repeat(700)
        assertTrue(long.length < 30_000)

        val response = browser.save(
            "guide", csrf,
            "guide.markdown_ru" to long, "guide.markdown_en" to long.replace("Строка", "Line"), "guide.markdown_sr" to long
        )

        assertEquals(HttpStatusCode.SeeOther, response.status)
        assertEquals(3, siteContent.load().entries.size)
    }

    // ---- stored content that is not what the form would allow must not break a page

    @Test
    fun `a page still renders when the stored text lacks a placeholder or a list is empty`() = testApplication {
        start()
        siteContent.update(
            mapOf(
                ContentKey("home.join.step1", "ru") to "Прочитайте гайд.",
                ContentKey("home.nextVisit.departure", "ru") to "Выезд в 9 утра",
                ContentKey("help.needs.items", "ru") to "\n \n"
            )
        )

        assertEquals(HttpStatusCode.OK, client.get("/ru").status)
        assertEquals(HttpStatusCode.OK, client.get("/ru/visits").status)
        val home = client.get("/ru").plainText()
        assertContains(home, "Прочитайте гайд.")
        assertContains(home, ruMessages.t("home.join.step1.link"))
        assertContains(client.get("/ru/help").plainText(), ruMessages.t("help.item.food"))
    }

    @Test
    fun `a link with an unsafe address in stored guide text is shown as plain text`() = testApplication {
        start()
        siteContent.update(mapOf(ContentKey("guide.markdown", "ru") to "# Гайд\n\n[нажми](javascript:alert(1)) и [читай](https://example.org)"))

        val body = client.get("/ru/guide").plainText()

        assertFalse(body.contains("javascript:"))
        assertContains(body, "нажми")
        assertContains(body, """<a href="https://example.org">читай</a>""")
    }

    // ---- photos

    private suspend fun HttpClient.uploadPagePhoto(
        pageId: String,
        csrf: String?,
        bytes: ByteArray,
        tokenFirst: Boolean = true
    ): HttpResponse = submitFormWithBinaryData("/admin/pages/$pageId/photo", formData {
        if (csrf != null && tokenFirst) append("_csrf", csrf)
        append("photo", bytes, Headers.build {
            append(HttpHeaders.ContentType, "image/jpeg")
            append(HttpHeaders.ContentDisposition, "filename=\"hero.jpg\"")
        })
        if (csrf != null && !tokenFirst) append("_csrf", csrf)
    })

    private val picture = jpegBytes(1200, 800)

    @Test
    fun `a new home photo is shown on the page and in the link previews, and the old one goes away`() = testApplication {
        start()
        photos.put("home-hero.jpg", "image/jpeg", byteArrayOf(1, 2, 3))
        val browser = browser()
        val csrf = browser.logIn()

        val first = browser.uploadPagePhoto("home", csrf, picture)

        assertEquals(HttpStatusCode.SeeOther, first.status)
        assertEquals("/admin/pages/home/photo?msg=uploaded", first.headers[HttpHeaders.Location])
        val firstUrl = siteContent.load().plain("image.home.hero")!!
        assertTrue(Regex("/images/page-home-hero-[0-9a-f]{8}\\.jpg").matches(firstUrl), firstUrl)
        val served = client.get(firstUrl)
        assertEquals(HttpStatusCode.OK, served.status)
        assertEquals(ContentType.Image.JPEG, served.contentType()?.withoutParameters())

        val home = client.get("/ru").bodyAsText()
        assertTrue(hasTag(home, "img", """src="$firstUrl"""", "hero-photo"))
        assertTrue(hasTag(home, "meta", """property="og:image"""", """content="$PUBLIC_BASE_URL$firstUrl""""))
        // A page without a photo of its own shares the home photo in a link preview: the new one.
        assertTrue(hasTag(client.get("/ru/cats").bodyAsText(), "meta", """property="og:image"""", """content="$PUBLIC_BASE_URL$firstUrl""""))

        browser.uploadPagePhoto("home", csrf, jpegBytes(1000, 700))
        val secondUrl = siteContent.load().plain("image.home.hero")!!
        assertNotEquals(firstUrl, secondUrl)
        assertEquals(setOf("home-hero.jpg", secondUrl.removePrefix("/images/")), photos.names())
        assertEquals(HttpStatusCode.NotFound, client.get(firstUrl).status)
    }

    @Test
    fun `the guide photo is changed the same way and is its own setting`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        browser.uploadPagePhoto("guide", csrf, picture)

        val url = siteContent.load().plain("image.guide.photo")!!
        assertTrue(url.startsWith("/images/page-guide-"), url)
        assertEquals(null, siteContent.load().plain("image.home.hero"))
        assertTrue(hasTag(client.get("/ru/guide").bodyAsText(), "img", """src="$url"""", "guide-photo"))
    }

    @Test
    fun `bringing the original photo back deletes the uploaded one and leaves the committed file`() = testApplication {
        start()
        photos.put("home-hero.jpg", "image/jpeg", byteArrayOf(1, 2, 3))
        val browser = browser()
        val csrf = browser.logIn()
        browser.uploadPagePhoto("home", csrf, picture)

        val response = browser.post("/admin/pages/home/photo/reset", emptyMap(), csrf)

        assertEquals("/admin/pages/home/photo?msg=reset", response.headers[HttpHeaders.Location])
        assertEquals(null, siteContent.load().plain("image.home.hero"))
        assertEquals(setOf("home-hero.jpg"), photos.names())
        assertTrue(hasTag(client.get("/ru").bodyAsText(), "img", """src="/images/home-hero.jpg"""", "hero-photo"))
    }

    @Test
    fun `resetting a photo that was never replaced changes nothing`() = testApplication {
        start()
        photos.put("home-hero.jpg", "image/jpeg", byteArrayOf(1, 2, 3))
        val browser = browser()
        val csrf = browser.logIn()

        browser.post("/admin/pages/home/photo/reset", emptyMap(), csrf)

        assertEquals(setOf("home-hero.jpg"), photos.names())
    }

    @Test
    fun `a photo upload needs the token first, a real picture and a page that has a photo`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        assertEquals(HttpStatusCode.Forbidden, browser.uploadPagePhoto("home", null, picture).status)
        assertEquals(HttpStatusCode.Forbidden, browser.uploadPagePhoto("home", "wrong", picture).status)
        assertEquals(HttpStatusCode.Forbidden, browser.uploadPagePhoto("home", csrf, picture, tokenFirst = false).status)
        val notAPicture = browser.uploadPagePhoto("home", csrf, "это не картинка".toByteArray())
        assertEquals(HttpStatusCode.UnprocessableEntity, notAPicture.status)
        assertContains(notAPicture.bodyAsText(), "Принимаются только JPEG и PNG")
        assertEquals(HttpStatusCode.NotFound, browser.uploadPagePhoto("visits", csrf, picture).status)
        assertEquals(HttpStatusCode.NotFound, browser.get("/admin/pages/visits/photo").status)

        assertEquals(emptySet(), photos.names())
        assertEquals(emptyMap(), siteContent.load().entries)
    }

    @Test
    fun `the photo page shows the current photo and offers the way back only for an uploaded one`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        val before = browser.get("/admin/pages/home/photo").bodyAsText()
        assertContains(before, "/images/home-hero.jpg")
        assertFalse(before.contains("Вернуть исходное фото"))

        browser.uploadPagePhoto("home", csrf, picture)

        val after = browser.get("/admin/pages/home/photo").bodyAsText()
        assertContains(after, "Вернуть исходное фото")
        assertContains(browser.get("/admin/pages/home").bodyAsText(), "своё фото")
    }
}
