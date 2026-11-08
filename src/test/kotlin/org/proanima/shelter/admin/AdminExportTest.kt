package org.proanima.shelter.admin

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.server.testing.testApplication
import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.Cat
import org.proanima.shelter.model.CatLocation
import org.proanima.shelter.model.Dog
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.repository.loadSeedData
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminExportTest : AdminTestBase() {

    private fun newCat(name: String, photoUrls: List<String> = emptyList(), published: Boolean = true) = cats.save(
        Cat(
            id = 0, name = LocalizedText(ru = name, en = name), description = LocalizedText(ru = "Описание «$name»", en = "About $name"),
            photoUrls = photoUrls, location = CatLocation.IN_SHELTER, adoptionStage = AdoptionStage.FOR_ADOPTION,
            createdAt = "2026-01-01T00:00:00+01:00", updatedAt = "2026-01-01T00:00:00+01:00", published = published
        )
    )

    private fun newDog(name: String, photoUrls: List<String> = emptyList()) = dogs.save(
        Dog(id = 0, name = LocalizedText(ru = name, en = name), description = LocalizedText(ru = "Пёс"), photoUrls = photoUrls)
    )

    // An uploaded photo as the admin stores it: the picture and its card copy.
    private fun storedPhoto(base: String, width: Int = 400): String {
        photos.put("$base.jpg", "image/jpeg", jpegBytes(width, 500))
        photos.put("$base-card.jpg", "image/jpeg", jpegBytes(width / 2, 250))
        return "/images/$base.jpg"
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes())
            }
        }
    }

    private suspend fun HttpResponse.entries(): Map<String, ByteArray> = unzip(body<ByteArray>())

    // ---- who may

    @Test
    fun `an anonymous visitor gets no backup`() = testApplication {
        start()
        newCat("Том", listOf(storedPhoto("tom")))

        val response = browser().post("/admin/export", emptyMap())

        assertEquals("/admin/login", response.headers[HttpHeaders.Location])
        assertEquals(HttpStatusCode.SeeOther, response.status)
    }

    @Test
    fun `a request without the form token, or with a wrong one, gets no backup`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()

        assertEquals(HttpStatusCode.Forbidden, browser.post("/admin/export", emptyMap()).status)
        assertEquals(HttpStatusCode.Forbidden, browser.post("/admin/export", emptyMap(), "wrong").status)
    }

    @Test
    fun `a link to the backup address is not enough, it takes a form post`() = testApplication {
        start()
        val browser = browser()
        browser.logIn()

        assertFalse(browser.get("/admin/export").status.isSuccess())
    }

    // ---- what is in it

    @Test
    fun `the backup is a zip with the texts, the photos and a readme, named by the date`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        newCat("Том", listOf(storedPhoto("cat-1-aaaa1111")))
        newDog("Рекс")

        val response = browser.post("/admin/export", emptyMap(), csrf)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Zip, response.headers[HttpHeaders.ContentType]?.let(ContentType::parse)?.withoutParameters())
        assertContains(response.headers[HttpHeaders.ContentDisposition].orEmpty(), "attachment")
        assertContains(response.headers[HttpHeaders.ContentDisposition].orEmpty(), "proanima-backup-2026-11-08.zip")
        assertEquals("noindex, nofollow", response.headers["X-Robots-Tag"])
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])

        val files = response.entries()
        assertEquals(
            setOf("cats.json", "dogs.json", "README.txt", "images/cat-1-aaaa1111.jpg", "images/cat-1-aaaa1111-card.jpg"),
            files.keys
        )
        assertContentEquals(photos.get("cat-1-aaaa1111.jpg")!!.bytes, files.getValue("images/cat-1-aaaa1111.jpg"))
        val readme = files.getValue("README.txt").decodeToString()
        assertContains(readme, "2026-11-08")
        assertContains(readme, "Кошек: 1. Собак: 1. Файлов в images/: 2.")
        assertFalse("ВНИМАНИЕ" in readme)
    }

    @Test
    fun `the pictures of the site's own pages are not in the backup, the pet photos are, byte for byte`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        photos.put("home-hero.jpg", "image/jpeg", byteArrayOf(1, 2, 3))
        photos.put("default-cat.svg", "image/svg+xml", byteArrayOf(4, 5))
        val url = storedPhoto("felix")
        newCat("Феликс", listOf(url))

        val files = browser.post("/admin/export", emptyMap(), csrf).entries()

        assertEquals(setOf("images/felix.jpg", "images/felix-card.jpg"), files.keys.filter { it.startsWith("images/") }.toSet())
        assertContentEquals(photos.get("felix.jpg")!!.bytes, files.getValue("images/felix.jpg"))
        assertContentEquals(photos.get("felix-card.jpg")!!.bytes, files.getValue("images/felix-card.jpg"))
    }

    @Test
    fun `drafts are in the backup too, marked as drafts`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        newCat("Опубликованная")
        newCat("Черновик", published = false)

        val json = browser.post("/admin/export", emptyMap(), csrf).entries().getValue("cats.json").decodeToString()

        assertContains(json, "Опубликованная")
        assertContains(json, "Черновик")
        assertContains(json, "\"published\": false")
    }

    @Test
    fun `an unpacked backup is what the site starts from, the same cats, dogs and photos`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val first = storedPhoto("cat-1-aaaa1111")
        val second = storedPhoto("cat-1-bbbb2222", width = 600)
        newCat("Том", listOf(first, second))
        newCat("Скрытая", published = false)
        newDog("Рекс", listOf(storedPhoto("dog-1-cccc3333")))
        newDog("Без фото")

        val files = browser.post("/admin/export", emptyMap(), csrf).entries()
        val root = Files.createTempDirectory("backup-test")
        try {
            val imagesDir = root.resolve("images").also { it.createDirectories() }
            files.forEach { (name, bytes) ->
                if (name.startsWith("images/")) imagesDir.resolve(name.removePrefix("images/")).writeBytes(bytes)
                else root.resolve(name).writeBytes(bytes)
            }

            val seed = loadSeedData(root.resolve("cats.json").toFile(), root.resolve("dogs.json").toFile(), imagesDir.toFile())

            assertEquals(cats.findAll(), seed.cats)
            assertEquals(dogs.findAll(), seed.dogs)
            assertEquals(photos.names(), seed.photos.map { it.name }.toSet())
            seed.photos.forEach { assertContentEquals(photos.get(it.name)!!.bytes, it.bytes, it.name) }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `an empty site still gives a valid backup`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        val files = browser.post("/admin/export", emptyMap(), csrf).entries()

        assertEquals(setOf("cats.json", "dogs.json", "README.txt"), files.keys)
        assertEquals("[]", files.getValue("cats.json").decodeToString().trim())
        assertContains(files.getValue("README.txt").decodeToString(), "Кошек: 0. Собак: 0. Файлов в images/: 0.")
    }

    @Test
    fun `a photo that a record points at but the store lacks is named in the readme, and the backup is still made`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        photos.put("here.jpg", "image/jpeg", jpegBytes(400, 500))
        newCat("Том", listOf("/images/here.jpg", "/images/gone.jpg"))

        val response = browser.post("/admin/export", emptyMap(), csrf)

        assertEquals(HttpStatusCode.OK, response.status)
        val files = response.entries()
        assertTrue("images/here.jpg" in files)
        val readme = files.getValue("README.txt").decodeToString()
        assertContains(readme, "ВНИМАНИЕ")
        assertContains(readme, "gone.jpg")
        assertContains(readme, "gone-card.jpg")
        // here.jpg has no card copy in this store, which is a gap too.
        assertContains(readme, "here-card.jpg")
        assertFalse("  here.jpg" in readme)
    }

    @Test
    fun `many photos make a valid archive`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()
        val urls = (1..12).map { storedPhoto("cat-1-%08x".format(it)) }
        newCat("Том", urls)

        val files = browser.post("/admin/export", emptyMap(), csrf).entries()

        assertEquals(24, files.keys.count { it.startsWith("images/") })
        assertContains(files.getValue("README.txt").decodeToString(), "Файлов в images/: 24.")
    }

    // ---- one at a time, and the button

    @Test
    fun `while one backup is being made another is turned away, and it can be made again after`() = testApplication {
        val admin = adminSetup()
        start(admin)
        val browser = browser()
        val csrf = browser.logIn()

        admin.exportRunning.set(true)
        val turnedAway = browser.post("/admin/export", emptyMap(), csrf)
        assertEquals(HttpStatusCode.TooManyRequests, turnedAway.status)
        assertContains(turnedAway.bodyAsText(), "Копия уже собирается")
        // Turning someone away must not release the other backup's place.
        assertTrue(admin.exportRunning.get())

        admin.exportRunning.set(false)
        assertEquals(HttpStatusCode.OK, browser.post("/admin/export", emptyMap(), csrf).status)
        assertFalse(admin.exportRunning.get())
        assertEquals(HttpStatusCode.OK, browser.post("/admin/export", emptyMap(), csrf).status)
    }

    @Test
    fun `the list page has the backup button, which posts to the export address with the token`() = testApplication {
        start()
        val browser = browser()
        val csrf = browser.logIn()

        val html = browser.get("/admin").bodyAsText()

        assertContains(html, "Резервная копия")
        assertContains(html, """action="/admin/export"""")
        assertContains(html, "Скачать копию (zip)")
        val form = html.substringAfter("""action="/admin/export"""").substringBefore("</form>")
        assertContains(form, """name="_csrf" value="$csrf"""")
    }
}
