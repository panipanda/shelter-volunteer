package org.proanima.shelter.admin

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.testing.testApplication
import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.Cat
import org.proanima.shelter.model.CatLocation
import org.proanima.shelter.model.Dog
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.routes.plainText
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AdminPhotosTest : AdminTestBase() {

    private fun newCat(photoUrls: List<String> = emptyList()) = cats.save(
        Cat(
            id = 0, name = LocalizedText(ru = "Том", en = "Tom"), description = LocalizedText(ru = "Кот"),
            photoUrls = photoUrls, location = CatLocation.IN_SHELTER, adoptionStage = AdoptionStage.FOR_ADOPTION,
            createdAt = "2026-01-01T00:00:00+01:00", updatedAt = "2026-01-01T00:00:00+01:00", published = true
        )
    )

    private fun newDog() = dogs.save(Dog(id = 0, name = LocalizedText(ru = "Рекс", en = "Rex"), description = LocalizedText(ru = "Пёс")))

    // A photo that is already stored (an original and its card copy), as if it had been uploaded earlier.
    private fun storedPhoto(base: String): String {
        photos.put("$base.jpg", "image/jpeg", jpegBytes(400, 500))
        photos.put("$base-card.jpg", "image/jpeg", jpegBytes(400, 500))
        return "/images/$base.jpg"
    }

    private suspend fun HttpClient.upload(
        path: String,
        csrf: String?,
        vararg files: Pair<String, ByteArray>,
        tokenFirst: Boolean = true
    ): HttpResponse = submitFormWithBinaryData(path, formData {
        if (csrf != null && tokenFirst) append("_csrf", csrf)
        files.forEach { (name, bytes) ->
            append("photos", bytes, Headers.build {
                append(HttpHeaders.ContentType, "image/jpeg")
                append(HttpHeaders.ContentDisposition, "filename=\"$name\"")
            })
        }
        if (csrf != null && !tokenFirst) append("_csrf", csrf)
    })

    private val small = jpegBytes(600, 800)

    // ---- who may

    @Test
    fun `an anonymous visitor cannot see the photos page or upload`() = testApplication {
        start()
        val cat = newCat()
        val anonymous = browser()

        assertEquals("/admin/login", anonymous.get("/admin/cats/${cat.id}/photos").headers[HttpHeaders.Location])
        val response = anonymous.upload("/admin/cats/${cat.id}/photos", null, "a.jpg" to small)
        assertEquals("/admin/login", response.headers[HttpHeaders.Location])
        assertTrue(photos.names().isEmpty())
        assertEquals(emptyList(), cats.findById(cat.id)!!.photoUrls)
    }

    @Test
    fun `an upload without the form token, or with the token after the files, stores nothing`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val cat = newCat()

        listOf(
            browser.upload("/admin/cats/${cat.id}/photos", null, "a.jpg" to small),
            browser.upload("/admin/cats/${cat.id}/photos", "wrong-token", "a.jpg" to small),
            browser.upload("/admin/cats/${cat.id}/photos", csrf, "a.jpg" to small, tokenFirst = false)
        ).forEach { assertEquals(HttpStatusCode.Forbidden, it.status) }
        assertTrue(photos.names().isEmpty())
        assertEquals(emptyList(), cats.findById(cat.id)!!.photoUrls)
    }

    @Test
    fun `an unknown cat or dog has no photos page`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        assertEquals(HttpStatusCode.NotFound, browser.get("/admin/cats/999/photos").status)
        assertEquals(HttpStatusCode.NotFound, browser.get("/admin/dogs/999/photos").status)
        assertEquals(HttpStatusCode.NotFound, browser.upload("/admin/cats/999/photos", csrf, "a.jpg" to small).status)
    }

    // ---- uploading

    @Test
    fun `an uploaded photo is stored in two sizes under a new name and added to the cat`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val cat = newCat()
        clock.now = clock.now.plus(Duration.ofHours(1))

        val response = browser.upload("/admin/cats/${cat.id}/photos", csrf, "IMG_2041.JPG" to jpegBytes(3000, 2000))

        assertEquals(HttpStatusCode.SeeOther, response.status)
        assertEquals("/admin/cats/${cat.id}/photos?msg=uploaded", response.headers[HttpHeaders.Location])
        val url = cats.findById(cat.id)!!.photoUrls.single()
        assertTrue(Regex("/images/cat-${cat.id}-[0-9a-f]{8}\\.jpg").matches(url), url)
        assertFalse("IMG_2041" in url)
        val name = url.removePrefix("/images/")
        assertEquals(setOf(name, name.removeSuffix(".jpg") + "-card.jpg"), photos.names())
        assertEquals(1600, decode(photos.get(name)!!.bytes).width)
        assertEquals(550, decode(photos.get(name.removeSuffix(".jpg") + "-card.jpg")!!.bytes).height)
        assertEquals("image/jpeg", photos.get(name)!!.contentType)
        assertEquals("2026-11-08T12:00:00+01:00", cats.findById(cat.id)!!.updatedAt)
        // And the site serves it.
        assertEquals(HttpStatusCode.OK, client.get(url).status)
        assertContains(browser.get("/admin/cats/${cat.id}/photos?msg=uploaded").bodyAsText(), "Фото добавлены.")
    }

    @Test
    fun `several files at once keep their order and each gets its own name`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val cat = newCat()

        browser.upload("/admin/cats/${cat.id}/photos", csrf, "1.jpg" to jpegBytes(900, 600), "2.jpg" to jpegBytes(600, 900), "3.jpg" to pngBytes(700, 700))

        val urls = cats.findById(cat.id)!!.photoUrls
        assertEquals(3, urls.size)
        assertEquals(3, urls.toSet().size)
        assertEquals(900, decode(photos.get(urls[0].removePrefix("/images/"))!!.bytes).width)
        assertEquals(600, decode(photos.get(urls[1].removePrefix("/images/"))!!.bytes).width)
        assertEquals(700, decode(photos.get(urls[2].removePrefix("/images/"))!!.bytes).width)
        assertEquals(6, photos.names().size)
    }

    @Test
    fun `photos are added after the ones the cat already has`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val first = storedPhoto("old-one")
        val cat = newCat(listOf(first))

        browser.upload("/admin/cats/${cat.id}/photos", csrf, "new.jpg" to small)

        val urls = cats.findById(cat.id)!!.photoUrls
        assertEquals(2, urls.size)
        assertEquals(first, urls.first())
    }

    @Test
    fun `a file that is not a picture is reported by name while the good ones are kept`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val cat = newCat()

        val response = browser.upload(
            "/admin/cats/${cat.id}/photos", csrf,
            "good.jpg" to small, "<script>x.txt" to "hello".toByteArray(), "iphone.heic" to ByteArray(30) + "ftypheic".toByteArray() + ByteArray(50)
        )

        val html = response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(html, "Не всё загрузилось")
        assertContains(html, "&lt;script&gt;x.txt: ")
        assertContains(html, "iphone.heic: ")
        assertContains(html, "Добавлено фото: 1.")
        assertFalse("<script>x.txt" in html)
        assertEquals(1, cats.findById(cat.id)!!.photoUrls.size)
    }

    @Test
    fun `when nothing is accepted nothing changes and the answer says so`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val cat = newCat()

        val bad = browser.upload("/admin/cats/${cat.id}/photos", csrf, "a.txt" to "hello".toByteArray())
        val none = browser.upload("/admin/cats/${cat.id}/photos", csrf)

        assertEquals(HttpStatusCode.UnprocessableEntity, bad.status)
        assertEquals(HttpStatusCode.UnprocessableEntity, none.status)
        assertContains(none.bodyAsText(), "Выберите хотя бы один файл.")
        assertTrue(photos.names().isEmpty())
        assertEquals(emptyList(), cats.findById(cat.id)!!.photoUrls)
    }

    @Test
    fun `a file over 8 MB is refused and the rest still goes through`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val cat = newCat()
        val huge = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(PhotoLimits.MAX_FILE_BYTES)

        val response = browser.upload("/admin/cats/${cat.id}/photos", csrf, "huge.jpg" to huge, "ok.jpg" to small)

        assertContains(response.bodyAsText(), "huge.jpg: Файл больше 8 МБ.")
        assertEquals(1, cats.findById(cat.id)!!.photoUrls.size)
    }

    @Test
    fun `a request far over the limit is refused before it is read`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val cat = newCat()

        val response = browser.upload("/admin/cats/${cat.id}/photos", csrf, "a.jpg" to ByteArray(PhotoLimits.MAX_UPLOAD_REQUEST_BYTES.toInt() + 1024))

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertTrue(photos.names().isEmpty())
    }

    @Test
    fun `no more than five files at a time and twelve photos for one cat`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val cat = newCat()
        val files = (1..7).map { "f$it.jpg" to jpegBytes(400, 400) }.toTypedArray()

        val response = browser.upload("/admin/cats/${cat.id}/photos", csrf, *files)

        assertContains(response.bodyAsText(), "f6.jpg: за один раз можно загрузить не больше 5 файлов.")
        assertContains(response.bodyAsText(), "f7.jpg: за один раз можно загрузить не больше 5 файлов.")
        assertEquals(5, cats.findById(cat.id)!!.photoUrls.size)

        browser.upload("/admin/cats/${cat.id}/photos", csrf, *files.copyOf(5).requireNoNulls())
        val tenOrSo = cats.findById(cat.id)!!.photoUrls.size
        assertEquals(10, tenOrSo)

        val last = browser.upload("/admin/cats/${cat.id}/photos", csrf, "a.jpg" to jpegBytes(400, 400), "b.jpg" to jpegBytes(400, 400), "c.jpg" to jpegBytes(400, 400))
        assertContains(last.bodyAsText(), "c.jpg: у одной записи не больше 12 фото.")
        assertEquals(12, cats.findById(cat.id)!!.photoUrls.size)
    }

    @Test
    fun `a dog gets photos the same way`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val dog = newDog()

        val response = browser.upload("/admin/dogs/${dog.id}/photos", csrf, "rex.jpg" to small)

        assertEquals("/admin/dogs/${dog.id}/photos?msg=uploaded", response.headers[HttpHeaders.Location])
        val url = dogs.findAll().single().photoUrls.single()
        assertTrue(Regex("/images/dog-${dog.id}-[0-9a-f]{8}\\.jpg").matches(url), url)
    }

    // ---- order, cover, removal

    private suspend fun HttpClient.change(cat: Cat, csrf: String?, photo: String, action: String): HttpResponse =
        post("/admin/cats/${cat.id}/photos/update", mapOf("photo" to photo, "action" to action), csrf)

    @Test
    fun `a photo moves earlier or later, becomes the cover, and the ends stay put`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val (a, b, c) = listOf(storedPhoto("a"), storedPhoto("b"), storedPhoto("c"))
        val cat = newCat(listOf(a, b, c))
        fun order() = cats.findById(cat.id)!!.photoUrls

        val moved = browser.change(cat, csrf, a, "right")
        assertEquals("/admin/cats/${cat.id}/photos?msg=moved", moved.headers[HttpHeaders.Location])
        assertEquals(listOf(b, a, c), order())

        browser.change(cat, csrf, b, "left")
        assertEquals(listOf(b, a, c), order())
        browser.change(cat, csrf, c, "right")
        assertEquals(listOf(b, a, c), order())

        browser.change(cat, csrf, c, "left")
        assertEquals(listOf(b, c, a), order())
        browser.change(cat, csrf, a, "cover")
        assertEquals(listOf(a, b, c), order())
        browser.change(cat, csrf, a, "cover")
        assertEquals(listOf(a, b, c), order())
    }

    @Test
    fun `the first photo is the cover on the site, and changing it changes the card`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val (a, b) = listOf(storedPhoto("first"), storedPhoto("second"))
        val cat = newCat(listOf(a, b))

        assertContains(client.get("/en/cats").plainText(), """src="/images/first-card.jpg"""")
        assertFalse("""src="/images/second-card.jpg"""" in client.get("/en/cats").plainText())

        browser.change(cat, csrf, b, "cover")

        assertContains(client.get("/en/cats").plainText(), """src="/images/second-card.jpg"""")
        assertContains(client.get("/en/cats/tom").plainText(), """src="/images/second.jpg"""")
    }

    @Test
    fun `deleting a photo removes it from the cat and both copies from the store`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val (a, b) = listOf(storedPhoto("a"), storedPhoto("b"))
        val cat = newCat(listOf(a, b))
        clock.now = clock.now.plus(Duration.ofHours(2))

        val response = browser.change(cat, csrf, a, "delete")

        assertEquals("/admin/cats/${cat.id}/photos?msg=removed", response.headers[HttpHeaders.Location])
        assertEquals(listOf(b), cats.findById(cat.id)!!.photoUrls)
        assertEquals(setOf("b.jpg", "b-card.jpg"), photos.names())
        assertEquals("2026-11-08T13:00:00+01:00", cats.findById(cat.id)!!.updatedAt)
        assertEquals(HttpStatusCode.NotFound, client.get("/images/a.jpg").status)
        assertContains(browser.get("/admin/cats/${cat.id}/photos?msg=removed").bodyAsText(), "Фото удалено.")
    }

    @Test
    fun `a photo that another cat or dog still uses stays in the store`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val shared = storedPhoto("shared")
        val cat = newCat(listOf(shared))
        dogs.save(Dog(id = 0, name = LocalizedText(ru = "Рекс"), description = LocalizedText(ru = "Пёс"), photoUrls = listOf(shared)))

        browser.change(cat, csrf, shared, "delete")

        assertEquals(emptyList(), cats.findById(cat.id)!!.photoUrls)
        assertEquals(setOf("shared.jpg", "shared-card.jpg"), photos.names())
    }

    @Test
    fun `deleting the last photo brings the placeholder back`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val only = storedPhoto("only")
        val cat = newCat(listOf(only))

        browser.change(cat, csrf, only, "delete")

        assertContains(client.get("/en/cats").plainText(), """src="/images/default-cat.svg"""")
    }

    @Test
    fun `a stale or forged change touches nothing`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val a = storedPhoto("a")
        val cat = newCat(listOf(a))

        assertEquals(HttpStatusCode.Forbidden, browser.change(cat, null, a, "delete").status)
        assertEquals(HttpStatusCode.Forbidden, browser.change(cat, "wrong", a, "delete").status)
        assertEquals(HttpStatusCode.NotFound, browser.change(cat, csrf, "/images/not-his.jpg", "delete").status)
        assertEquals(HttpStatusCode.BadRequest, browser.change(cat, csrf, a, "explode").status)
        assertEquals(listOf(a), cats.findById(cat.id)!!.photoUrls)
        assertEquals(setOf("a.jpg", "a-card.jpg"), photos.names())
        // A page that was open before the photo was deleted cannot delete it twice.
        browser.change(cat, csrf, a, "delete")
        assertEquals(HttpStatusCode.NotFound, browser.change(cat, csrf, a, "delete").status)
    }

    // ---- the pages

    @Test
    fun `the photos page shows the cover, the right buttons for each place, and the upload form`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()
        val cat = newCat(listOf(storedPhoto("a"), storedPhoto("b"), storedPhoto("c")))

        val html = browser.get("/admin/cats/${cat.id}/photos").bodyAsText()

        assertEquals(1, Regex("""badge-published">Обложка</span>""").findAll(html).count())
        assertContains(html, """enctype="multipart/form-data"""")
        assertContains(html, """accept="image/jpeg,image/png"""")
        assertContains(html, "multiple")
        // The token field comes before the file field: the server reads it first.
        assertTrue(html.indexOf("name=\"_csrf\"") < html.indexOf("name=\"photos\""))
        val tiles = html.split("<li class=\"photo-tile\">").drop(1)
        assertEquals(3, tiles.size)
        assertFalse("← Раньше" in tiles[0] || "Сделать обложкой" in tiles[0])
        assertTrue("Позже →" in tiles[0] && "Удалить" in tiles[0])
        assertTrue("← Раньше" in tiles[1] && "Позже →" in tiles[1] && "Сделать обложкой" in tiles[1])
        assertFalse("Позже →" in tiles[2])
    }

    // ---- deleting a photo asks first

    @Test
    fun `the delete link of a photo leads to a question, not to the deletion`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()
        val cat = newCat(listOf(storedPhoto("a"), storedPhoto("b")))

        val html = browser.get("/admin/cats/${cat.id}/photos").bodyAsText()

        val tiles = html.split("<li class=\"photo-tile\">").drop(1)
        assertContains(tiles[1], """href="/admin/cats/${cat.id}/photos/delete?photo=%2Fimages%2Fb.jpg"""")
        // No button of the tile deletes anything by itself.
        assertFalse(Regex("""<button[^>]*value="delete"""").containsMatchIn(html))
    }

    @Test
    fun `the question shows the photo and deletes nothing until it is confirmed`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val (a, b) = listOf(storedPhoto("a"), storedPhoto("b"))
        val cat = newCat(listOf(a, b))

        val ask = browser.get("/admin/cats/${cat.id}/photos/delete?photo=${"/images/b.jpg".encodeURLParameter()}")

        assertEquals(HttpStatusCode.OK, ask.status)
        val html = ask.bodyAsText()
        assertContains(html, "Удалить это фото?")
        assertContains(html, """src="/images/b-card.jpg"""")
        assertContains(html, "Нет, вернуться")
        assertContains(html, """href="/admin/cats/${cat.id}/photos"""")
        // An ordinary photo: no warning about the cover or the placeholder.
        assertFalse("обложка" in html.lowercase() || "заглушка" in html)
        assertEquals(listOf(a, b), cats.findById(cat.id)!!.photoUrls)
        assertEquals(setOf("a.jpg", "a-card.jpg", "b.jpg", "b-card.jpg"), photos.names())

        // "Yes" is the same request the old button sent, with this page's token.
        assertEquals(csrf, csrfOf(html))
        assertContains(html, """action="/admin/cats/${cat.id}/photos/update"""")
        assertContains(html, """name="photo" value="/images/b.jpg"""")
        assertContains(html, """name="action" value="delete"""")
        val done = browser.change(cat, csrf, b, "delete")
        assertEquals("/admin/cats/${cat.id}/photos?msg=removed", done.headers[HttpHeaders.Location])
        assertEquals(listOf(a), cats.findById(cat.id)!!.photoUrls)
    }

    @Test
    fun `the question warns when the photo is the cover or the only one`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()
        val cat = newCat(listOf(storedPhoto("a"), storedPhoto("b")))
        val lone = newCat(listOf(storedPhoto("c")))

        val cover = browser.get("/admin/cats/${cat.id}/photos/delete?photo=%2Fimages%2Fa.jpg").bodyAsText()
        assertContains(cover, "Это обложка: обложкой станет следующее фото.")
        assertFalse("заглушка" in cover)

        val only = browser.get("/admin/cats/${lone.id}/photos/delete?photo=%2Fimages%2Fc.jpg").bodyAsText()
        assertContains(only, "Это единственное фото: на сайте вместо него будет заглушка.")
        assertFalse("обложкой станет" in only)
    }

    @Test
    fun `the question is only for the person logged in and only about a photo of that record`() = testApplication {
        start()
        val cat = newCat(listOf(storedPhoto("a")))
        val other = newCat(listOf(storedPhoto("z")))
        val anonymous = browser()

        assertEquals(
            "/admin/login",
            anonymous.get("/admin/cats/${cat.id}/photos/delete?photo=%2Fimages%2Fa.jpg").headers[HttpHeaders.Location]
        )

        val browser = browser()
        browser.logIn()
        listOf(
            "/admin/cats/${cat.id}/photos/delete",
            "/admin/cats/${cat.id}/photos/delete?photo=",
            "/admin/cats/${cat.id}/photos/delete?photo=%2Fimages%2Fnot-his.jpg",
            // A photo of another record is not this record's photo.
            "/admin/cats/${cat.id}/photos/delete?photo=%2Fimages%2Fz.jpg",
            "/admin/cats/999/photos/delete?photo=%2Fimages%2Fa.jpg",
            "/admin/dogs/${cat.id}/photos/delete?photo=%2Fimages%2Fa.jpg"
        ).forEach { path ->
            assertEquals(HttpStatusCode.NotFound, browser.get(path).status, path)
        }
        assertEquals(listOf("/images/z.jpg"), cats.findById(other.id)!!.photoUrls)
    }

    @Test
    fun `the edit form links to the photos page, and a new record says to save first`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()
        val cat = newCat(listOf(storedPhoto("a")))

        assertContains(browser.get("/admin/cats/${cat.id}").bodyAsText(), """href="/admin/cats/${cat.id}/photos"""")
        assertContains(browser.get("/admin/cats/${cat.id}").bodyAsText(), "Фотографии: 1, изменить")
        val fresh = browser.get("/admin/cats/new").bodyAsText()
        assertContains(fresh, "Фотографии можно добавить после первого сохранения.")
        assertFalse("/photos" in fresh)
    }

    @Test
    fun `the list shows how many photos each one has and how much room they take`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()
        newCat(listOf(storedPhoto("a"), storedPhoto("b")))
        newCat()

        val html = browser.get("/admin").bodyAsText()

        assertContains(html, "фото: 2")
        assertContains(html, "без фото")
        assertContains(html, "Фотографии занимают ")
        assertContains(html, "МБ из 1024 МБ")
    }

    @Test
    fun `deleting a cat deletes its uploaded photos too`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val cat = newCat()
        browser.upload("/admin/cats/${cat.id}/photos", csrf, "a.jpg" to small, "b.jpg" to small)
        assertEquals(4, photos.names().size)

        browser.post("/admin/cats/${cat.id}/delete", emptyMap(), csrf)

        assertTrue(photos.names().isEmpty())
    }

    @Test
    fun `editing the text of a cat keeps the photos that were uploaded after the form was opened`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val cat = newCat()
        // The form is open (and would carry the photos as they were), then a photo is added, then the form is saved.
        assertEquals(HttpStatusCode.OK, browser.get("/admin/cats/${cat.id}").status)
        browser.upload("/admin/cats/${cat.id}/photos", csrf, "a.jpg" to small)

        browser.post(
            "/admin/cats/${cat.id}",
            mapOf("name_ru" to "Том", "name_en" to "Tom", "description_ru" to "Новое описание", "location" to "IN_SHELTER", "adoptionStage" to "FOR_ADOPTION", "published" to "true"),
            csrf
        )

        val saved = cats.findById(cat.id)!!
        assertEquals("Новое описание", saved.description.ru)
        assertEquals(1, saved.photoUrls.size)
        assertNotEquals(emptyList(), saved.photoUrls)
    }
}
