package org.proanima.shelter.admin

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.proanima.shelter.configureRoutes
import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.Cat
import org.proanima.shelter.model.CatLocation
import org.proanima.shelter.model.Dog
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.repository.GuideContent
import org.proanima.shelter.repository.GuideRepository
import org.proanima.shelter.repository.PgCatRepository
import org.proanima.shelter.repository.PgDogRepository
import org.proanima.shelter.repository.PgPhotoStore
import org.proanima.shelter.repository.TestClock
import org.proanima.shelter.repository.newTestDataSource
import org.proanima.shelter.routes.plainText
import org.proanima.shelter.service.CatService
import org.proanima.shelter.service.DogService
import org.proanima.shelter.service.GuideService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminRoutesTest : AdminTestBase() {

    private fun catForm(vararg changes: Pair<String, String>): Map<String, String> = mapOf(
        "name_ru" to "Барсик", "name_en" to "Barsik", "name_sr" to "Barsik",
        "description_ru" to "Добрый кот", "description_en" to "A kind cat", "description_sr" to "Dobar mačak",
        "location" to "IN_SHELTER", "adoptionStage" to "FOR_ADOPTION", "published" to "false"
    ) + changes

    private fun dogForm(vararg changes: Pair<String, String>): Map<String, String> = mapOf(
        "name_ru" to "Рекс", "name_en" to "Rex", "name_sr" to "Reks",
        "description_ru" to "Весёлый пёс", "description_en" to "A cheerful dog", "description_sr" to "Veseo pas",
        "location" to "IN_SHELTER", "published" to "false"
    ) + changes

    private fun storedCat(name: String = "Tom", photoUrls: List<String> = emptyList(), published: Boolean = true) = cats.save(
        Cat(
            id = 0, name = LocalizedText(ru = name, en = name), description = LocalizedText(ru = "Описание", en = "Description"),
            photoUrls = photoUrls, location = CatLocation.IN_SHELTER, adoptionStage = AdoptionStage.FOR_ADOPTION,
            createdAt = "2026-01-01T00:00:00+01:00", updatedAt = "2026-01-01T00:00:00+01:00", published = published
        )
    )

    // ---- the panel exists only when it is configured

    @Test
    fun `without the admin setup there is no admin address at all`() = testApplication {
        start(admin = null)

        listOf("/admin", "/admin/login", "/admin/cats/new").forEach { path ->
            assertEquals(HttpStatusCode.NotFound, client.get(path).status, path)
        }
        assertEquals(HttpStatusCode.NotFound, client.post("/admin/login", mapOf("password" to password)).status)
    }

    // ---- who may come in

    @Test
    fun `an anonymous visitor is sent to the login page and can change nothing`() = testApplication {
        start()
        val anonymous = browser()
        val cat = storedCat()

        listOf("/admin", "/admin/cats/new", "/admin/cats/${cat.id}", "/admin/cats/${cat.id}/delete", "/admin/cats/${cat.id}/preview", "/admin/dogs/new").forEach { path ->
            val response = anonymous.get(path)
            assertEquals(HttpStatusCode.SeeOther, response.status, path)
            assertEquals("/admin/login", response.headers[HttpHeaders.Location], path)
        }
        listOf("/admin/cats", "/admin/cats/${cat.id}", "/admin/cats/${cat.id}/delete", "/admin/dogs").forEach { path ->
            assertEquals("/admin/login", anonymous.post(path, catForm()).headers[HttpHeaders.Location], path)
        }
        assertEquals(listOf(cat), cats.findAll())
        assertTrue(dogs.findAll().isEmpty())
    }

    @Test
    fun `a wrong password is refused and the right one logs in`() = testApplication {
        start()
        val browser = browser()

        val wrong = browser.post("/admin/login", mapOf("password" to "not the password"))
        assertEquals(HttpStatusCode.Forbidden, wrong.status)
        assertContains(wrong.bodyAsText(), "Неверный пароль")
        assertFalse(wrong.headers.contains(HttpHeaders.SetCookie))
        assertEquals("/admin/login", browser.get("/admin").headers[HttpHeaders.Location])

        val right = browser.post("/admin/login", mapOf("password" to password))
        assertEquals(HttpStatusCode.SeeOther, right.status)
        assertEquals("/admin", right.headers[HttpHeaders.Location])
        assertEquals(HttpStatusCode.OK, browser.get("/admin").status)
    }

    @Test
    fun `the session cookie is for the admin address only, and hidden from scripts and other sites`() = testApplication {
        start(adminSetup(secureCookie = true))

        val cookie = client.post("/admin/login", mapOf("password" to password)).headers[HttpHeaders.SetCookie]

        assertNotNull(cookie)
        listOf("Path=/admin", "HttpOnly", "Secure", "SameSite=Lax").forEach { assertContains(cookie, it) }
    }

    @Test
    fun `a forged or altered cookie is not a session`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()
        val genuine = browser.post("/admin/login", mapOf("password" to password)).headers[HttpHeaders.SetCookie]!!
        val value = genuine.substringAfter("shelter_admin=").substringBefore(';')

        // A client that does not follow the redirect, so the answer itself can be read.
        val stranger = createClient { followRedirects = false }
        val forged = stranger.get("/admin") { headers.append(HttpHeaders.Cookie, "shelter_admin=" + value.dropLast(3) + "abc") }
        val invented = stranger.get("/admin") { headers.append(HttpHeaders.Cookie, "shelter_admin=abc.123/deadbeef") }
        val genuineCookie = stranger.get("/admin") { headers.append(HttpHeaders.Cookie, "shelter_admin=$value") }

        assertEquals(HttpStatusCode.OK, genuineCookie.status)

        assertEquals("/admin/login", forged.headers[HttpHeaders.Location])
        assertEquals("/admin/login", invented.headers[HttpHeaders.Location])
    }

    @Test
    fun `a session ends after twelve hours`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()

        clock.now = clock.now.plus(Duration.ofHours(11))
        assertEquals(HttpStatusCode.OK, browser.get("/admin").status)

        clock.now = clock.now.plus(Duration.ofHours(2))
        assertEquals("/admin/login", browser.get("/admin").headers[HttpHeaders.Location])
    }

    @Test
    fun `logging out ends the session`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        val response = browser.post("/admin/logout", emptyMap(), csrf)

        assertEquals("/admin/login", response.headers[HttpHeaders.Location])
        assertEquals("/admin/login", browser.get("/admin").headers[HttpHeaders.Location])
    }

    @Test
    fun `too many wrong passwords at once are turned away, and the right password still works`() = testApplication {
        val slow = AdminSetup(AdminConfig(password, "s".repeat(40), false), cats, dogs, photos, clock, FailedLoginThrottle(penaltyMillis = 300, maxWaiting = 2))
        start(slow)
        val statuses = coroutineScope {
            (1..5).map { attempt -> async { client.post("/admin/login", mapOf("password" to "wrong $attempt")).status } }
                .awaitAll()
        }

        assertEquals(2, statuses.count { it == HttpStatusCode.Forbidden }, statuses.toString())
        assertEquals(3, statuses.count { it == HttpStatusCode.TooManyRequests }, statuses.toString())
        assertEquals(HttpStatusCode.SeeOther, client.post("/admin/login", mapOf("password" to password)).status)
    }

    @Test
    fun `a login body that is too big or has no length is refused`() = testApplication {
        start()

        val response = client.post("/admin/login", mapOf("password" to "x".repeat(5000)))

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
    }

    // ---- what the panel sends

    @Test
    fun `admin pages are for no search engine, cache or frame`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()

        listOf("/admin", "/admin/login", "/admin/cats/new").forEach { path ->
            val response = browser.get(path)
            assertEquals("noindex, nofollow", response.headers["X-Robots-Tag"], path)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl], path)
            assertEquals("DENY", response.headers["X-Frame-Options"], path)
        }
        assertContains(browser.get("/admin").bodyAsText(), """<meta name="robots" content="noindex, nofollow">""")
    }

    @Test
    fun `no public page links to the admin`() = testApplication {
        start()

        listOf("/ru", "/en", "/sr", "/ru/cats", "/ru/dogs", "/ru/help").forEach { path ->
            assertFalse("/admin" in client.get(path).plainText(), path)
        }
    }

    // ---- forms are protected against forged posts

    @Test
    fun `a post without the form token, or with a wrong one, changes nothing`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()
        val cat = storedCat()

        listOf(null, "wrong-token").forEach { token ->
            val responses = listOf(
                browser.post("/admin/cats", catForm(), token),
                browser.post("/admin/cats/${cat.id}", catForm("name_ru" to "Взлом"), token),
                browser.post("/admin/cats/${cat.id}/delete", emptyMap(), token),
                browser.post("/admin/logout", emptyMap(), token)
            )
            responses.forEach { assertEquals(HttpStatusCode.Forbidden, it.status, "token=$token") }
        }
        assertEquals(listOf(cat), cats.findAll())
        assertEquals(HttpStatusCode.OK, browser.get("/admin").status)
    }

    @Test
    fun `a form body that is too big is refused before it is read`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        val response = browser.post("/admin/cats", catForm("description_ru" to "я".repeat(120_000)), csrf)

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertTrue(cats.findAll().isEmpty())
    }

    // ---- cats

    @Test
    fun `a new cat starts as a draft, and only the draft button-state decides whether visitors see it`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        assertContains(browser.get("/admin/cats/new").bodyAsText(), """<option value="false" selected="selected">""")

        val created = browser.post("/admin/cats", catForm(), csrf)

        val cat = cats.findAll().single()
        assertEquals(HttpStatusCode.SeeOther, created.status)
        assertEquals("/admin/cats/${cat.id}?msg=created", created.headers[HttpHeaders.Location])
        assertFalse(cat.published)
        // A draft is in the admin list and nowhere on the site.
        assertContains(browser.get("/admin").bodyAsText(), "Барсик")
        assertContains(browser.get("/admin").bodyAsText(), "Черновик")
        listOf("/ru", "/en/cats", "/ru/cats", "/sr/cats").forEach { path ->
            assertFalse("Barsik" in client.get(path).plainText() || "Барсик" in client.get(path).plainText(), path)
        }
        assertEquals(HttpStatusCode.NotFound, client.get("/en/cats/barsik").status)

        // Published: visitors see it.
        browser.post("/admin/cats/${cat.id}", catForm("published" to "true"), csrf)
        assertContains(client.get("/en/cats").plainText(), "Barsik")
        assertEquals(HttpStatusCode.OK, client.get("/en/cats/barsik").status)
        assertContains(client.get("/en").plainText(), "Barsik")

        // And back to a draft: gone again.
        browser.post("/admin/cats/${cat.id}", catForm("published" to "false"), csrf)
        assertFalse("Barsik" in client.get("/en/cats").plainText())
        assertEquals(HttpStatusCode.NotFound, client.get("/en/cats/barsik").status)
    }

    @Test
    fun `the saved cat has what was typed, in all three languages`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        browser.post(
            "/admin/cats",
            catForm("birthYear" to "2025", "birthMonth" to "3", "medicalStatus_ru" to "Здорова", "adoptionInstruction_en" to "Write to us", "published" to "true"),
            csrf
        )

        val cat = cats.findAll().single()
        assertEquals(LocalizedText(ru = "Барсик", en = "Barsik", sr = "Barsik"), cat.name)
        assertEquals(LocalizedText(ru = "Добрый кот", en = "A kind cat", sr = "Dobar mačak"), cat.description)
        assertEquals(LocalizedText(ru = "Здорова"), cat.medicalStatus)
        assertEquals(LocalizedText(en = "Write to us"), cat.adoptionInstruction)
        assertEquals(2025, cat.birthYear)
        assertEquals(3, cat.birthMonth)
        assertEquals(AdoptionStage.FOR_ADOPTION, cat.adoptionStage)
        assertTrue(cat.published)
        assertEquals("2026-11-08T11:00:00+01:00", cat.createdAt)
        assertEquals(cat.createdAt, cat.updatedAt)
    }

    @Test
    fun `a form with mistakes comes back with everything typed and nothing is saved`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        val response = browser.post(
            "/admin/cats",
            catForm("description_ru" to "", "name_ru" to "Не потеряй меня", "birthYear" to "1850", "birthMonth" to "13"),
            csrf
        )

        val html = response.bodyAsText()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertContains(html, "Заполните русский текст.")
        assertContains(html, "Укажите число от 2000 до 2026.")
        assertContains(html, "Выберите месяц из списка.")
        assertContains(html, "Не потеряй меня")
        assertContains(html, "A kind cat")
        assertContains(html, """aria-invalid="true"""")
        // The summary at the top names each problem and links to its field.
        assertContains(html, "<a href=\"#f-description_ru\">Описание (Русский): Заполните русский текст.</a>")
        assertContains(html, "<a href=\"#f-birthYear\">Год рождения: Укажите число от 2000 до 2026.</a>")
        assertTrue(cats.findAll().isEmpty())
    }

    @Test
    fun `two cats cannot share an English name, because they would share the page address`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        browser.post("/admin/cats", catForm(), csrf)

        val second = browser.post("/admin/cats", catForm("name_ru" to "Другой Барсик", "name_en" to "BARSIK!"), csrf)

        assertEquals(HttpStatusCode.UnprocessableEntity, second.status)
        assertContains(second.bodyAsText(), "Страница с таким адресом уже есть")
        assertEquals(1, cats.findAll().size)

        // Saving the first cat again under its own name is not a clash with itself.
        val first = cats.findAll().single()
        assertEquals(HttpStatusCode.SeeOther, browser.post("/admin/cats/${first.id}", catForm("description_ru" to "Новое"), csrf).status)
    }

    @Test
    fun `editing keeps the photos and the creation time, and moves the update time`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val cat = storedCat(photoUrls = listOf("/images/tom.jpg", "/images/tom-2.jpg"))
        clock.now = clock.now.plus(Duration.ofHours(6))

        val response = browser.post("/admin/cats/${cat.id}", catForm("name_en" to "Tommy", "published" to "true"), csrf)

        val edited = cats.findById(cat.id)!!
        assertEquals("/admin/cats/${cat.id}?msg=saved", response.headers[HttpHeaders.Location])
        assertEquals(listOf("/images/tom.jpg", "/images/tom-2.jpg"), edited.photoUrls)
        assertEquals("2026-01-01T00:00:00+01:00", edited.createdAt)
        assertEquals("2026-11-08T17:00:00+01:00", edited.updatedAt)
        assertEquals("Tommy", edited.name?.en)
        assertContains(browser.get("/admin/cats/${cat.id}?msg=saved").bodyAsText(), "Изменения сохранены.")
    }

    @Test
    fun `line breaks in a text become one space, and a cat may have no name at all`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        browser.post(
            "/admin/cats",
            catForm("description_ru" to "Первая строка.\r\n\r\nВторая   строка.\n  Третья.", "name_ru" to "", "name_en" to "", "name_sr" to ""),
            csrf
        )

        val cat = cats.findAll().single()
        assertEquals("Первая строка. Вторая   строка. Третья.", cat.description.ru)
        assertNull(cat.name)
    }

    @Test
    fun `text typed into a form is shown as text and never run`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val hostile = "<script>alert(1)</script>\"><img src=x onerror=alert(2)>"

        browser.post("/admin/cats", catForm("name_ru" to hostile, "description_ru" to hostile), csrf)
        val cat = cats.findAll().single()

        listOf("/admin", "/admin/cats/${cat.id}", "/admin/cats/${cat.id}/delete").forEach { path ->
            val html = browser.get(path).bodyAsText()
            assertFalse("<script>alert" in html, path)
            assertFalse("<img src=x" in html, path)
        }
        assertContains(browser.get("/admin").bodyAsText(), "&lt;script&gt;alert(1)&lt;/script&gt;")
        // And when the form comes back with a mistake:
        val again = browser.post("/admin/cats", catForm("name_ru" to hostile, "description_ru" to "").plus("name_en" to "Other"), csrf).bodyAsText()
        assertFalse("<script>alert" in again)
    }

    @Test
    fun `the list shows which cats lack a translation`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        browser.post("/admin/cats", mapOf("name_ru" to "Муся", "description_ru" to "Ласковая", "location" to "IN_SHELTER", "adoptionStage" to "NONE", "published" to "true"), csrf)
        browser.post("/admin/cats", catForm("published" to "true"), csrf)

        val html = browser.get("/admin").bodyAsText()
        val rows = html.split("<li class=\"admin-row\">").drop(1)

        val musya = rows.single { "Муся" in it }
        assertContains(musya, "нет en")
        assertContains(musya, "нет sr")
        assertContains(musya, "Опубликовано")
        val barsik = rows.single { "Барсик" in it }
        assertFalse("нет en" in barsik || "нет sr" in barsik)
    }

    @Test
    fun `a draft can be looked at in every language before it is published`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        browser.post("/admin/cats", catForm(), csrf)
        val cat = cats.findAll().single()

        mapOf("ru" to "Барсик", "en" to "Barsik", "sr" to "Barsik", "xx" to "Барсик").forEach { (lang, name) ->
            val response = browser.get("/admin/cats/${cat.id}/preview?lang=$lang")
            assertEquals(HttpStatusCode.OK, response.status, lang)
            assertContains(response.plainText(), name)
            assertEquals("noindex, nofollow", response.headers["X-Robots-Tag"])
        }
        assertEquals(HttpStatusCode.NotFound, client.get("/en/cats/barsik").status)
    }

    @Test
    fun `a preview of a draft says so, keeps its language links inside the preview and stays out of search`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        browser.post("/admin/cats", catForm(), csrf)
        val cat = cats.findAll().single()

        val html = browser.get("/admin/cats/${cat.id}/preview?lang=en").plainText()

        assertContains(html, "Предпросмотр.")
        assertContains(html, "Это черновик: посетители сайта этой страницы не видят.")
        assertContains(html, """href="/admin/cats/${cat.id}"""")
        assertContains(html, "← Вернуться к редактированию")
        // The other languages stay in the preview: the public address of a draft is a 404.
        assertContains(html, """href="/admin/cats/${cat.id}/preview?lang=ru"""")
        assertContains(html, """href="/admin/cats/${cat.id}/preview?lang=sr"""")
        assertFalse("""href="/ru/cats/""" in html || """href="/sr/cats/""" in html)
        assertContains(html, """content="noindex"""")
        assertFalse("""rel="canonical"""" in html || """rel="alternate"""" in html)
        assertContains(html, "/styles/admin.css")
        // The page itself is the public page of the cat.
        assertContains(html, "Barsik")
        assertContains(html, "A kind cat")
    }

    @Test
    fun `a preview of a published record says it is how visitors see it`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        browser.post("/admin/dogs", dogForm("published" to "true"), csrf)
        val dog = dogs.findAll().single()

        val html = browser.get("/admin/dogs/${dog.id}/preview?lang=sr").plainText()

        assertContains(html, "Предпросмотр.")
        assertContains(html, "Так страницу видят посетители сайта.")
        assertFalse("Это черновик" in html)
        assertContains(html, """href="/admin/dogs/${dog.id}/preview?lang=en"""")
        assertContains(html, """href="/admin/dogs/${dog.id}"""")
    }

    @Test
    fun `the public pages have no preview banner and keep their own language links`() = testApplication {
        start()
        storedCat("Tom")

        val html = client.get("/en/cats/tom").plainText()

        assertFalse("Предпросмотр" in html || "/styles/admin.css" in html)
        assertContains(html, """href="/ru/cats/tom"""")
        assertContains(html, """href="/sr/cats/tom"""")
        assertContains(html, """rel="canonical"""")
    }

    @Test
    fun `an address with an unknown or odd id is a 404`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        listOf("/admin/cats/999", "/admin/cats/abc", "/admin/cats/999/delete", "/admin/cats/999/preview", "/admin/dogs/999").forEach { path ->
            assertEquals(HttpStatusCode.NotFound, browser.get(path).status, path)
        }
        assertEquals(HttpStatusCode.NotFound, browser.post("/admin/cats/999", catForm(), csrf).status)
        assertEquals(HttpStatusCode.NotFound, browser.post("/admin/cats/999/delete", emptyMap(), csrf).status)
    }

    @Test
    fun `deleting asks first, then removes the cat and only the photos no one else uses`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        listOf("tom.jpg", "tom-card.jpg", "shared.jpg", "shared-card.jpg", "home-hero.jpg").forEach { photos.put(it, "image/jpeg", byteArrayOf(1)) }
        val tom = storedCat("Tom", listOf("/images/tom.jpg", "/images/shared.jpg"))
        dogs.save(Dog(id = 0, name = LocalizedText(ru = "Рекс", en = "Rex"), description = LocalizedText(ru = "Пёс"), photoUrls = listOf("/images/shared.jpg")))

        val ask = browser.get("/admin/cats/${tom.id}/delete")
        assertContains(ask.bodyAsText(), "Удалить кошку «Tom»?")
        assertEquals(listOf(tom), cats.findAll())

        val done = browser.post("/admin/cats/${tom.id}/delete", emptyMap(), csrf)

        assertEquals("/admin?msg=deleted", done.headers[HttpHeaders.Location])
        assertTrue(cats.findAll().isEmpty())
        assertEquals(setOf("shared.jpg", "shared-card.jpg", "home-hero.jpg"), photos.names())
        assertContains(browser.get("/admin?msg=deleted").bodyAsText(), "Запись удалена.")
    }

    // ---- dogs

    @Test
    fun `a dog goes through the same steps as a cat`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        val created = browser.post("/admin/dogs", dogForm("age_ru" to "Около 3 лет"), csrf)
        val dog = dogs.findAll().single()
        assertEquals("/admin/dogs/${dog.id}?msg=created", created.headers[HttpHeaders.Location])
        assertFalse(dog.published)
        assertEquals(LocalizedText(ru = "Около 3 лет"), dog.age)
        assertFalse("Rex" in client.get("/en/dogs").plainText())
        assertEquals(HttpStatusCode.NotFound, client.get("/en/dogs/rex").status)

        browser.post("/admin/dogs/${dog.id}", dogForm("published" to "true"), csrf)
        assertContains(client.get("/en/dogs").plainText(), "Rex")
        assertEquals(HttpStatusCode.OK, client.get("/en/dogs/rex").status)
        assertEquals(HttpStatusCode.OK, browser.get("/admin/dogs/${dog.id}/preview?lang=sr").status)

        assertEquals(HttpStatusCode.UnprocessableEntity, browser.post("/admin/dogs/${dog.id}", dogForm("name_ru" to ""), csrf).status)
        assertEquals("Рекс", dogs.findAll().single().name.ru)

        assertContains(browser.get("/admin/dogs/${dog.id}/delete").bodyAsText(), "Удалить собаку «Рекс»?")
        browser.post("/admin/dogs/${dog.id}/delete", emptyMap(), csrf)
        assertTrue(dogs.findAll().isEmpty())
    }
}
