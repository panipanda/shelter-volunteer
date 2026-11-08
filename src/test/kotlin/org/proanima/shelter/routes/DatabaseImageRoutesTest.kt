package org.proanima.shelter.routes

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.call.body
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.proanima.shelter.configureRoutes
import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.Cat
import org.proanima.shelter.model.CatLocation
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.repository.GuideContent
import org.proanima.shelter.repository.GuideRepository
import org.proanima.shelter.repository.PgCatRepository
import org.proanima.shelter.repository.PgDogRepository
import org.proanima.shelter.repository.PgPhotoStore
import org.proanima.shelter.repository.newTestDataSource
import org.proanima.shelter.repository.sha256Hex
import org.proanima.shelter.service.CatService
import org.proanima.shelter.service.DogService
import org.proanima.shelter.service.GuideService
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseImageRoutesTest {

    private val dataSource = newTestDataSource()
    private val store = PgPhotoStore(dataSource)
    private val bytes = ByteArray(2000) { (it % 200).toByte() }
    private val etag = "\"${sha256Hex(bytes)}\""

    private val guideRepository = object : GuideRepository {
        override fun findGuideMarkdown(locale: AppLocale): GuideContent = GuideContent("# Guide", locale.code)
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.start() {
        store.put("felix.jpg", "image/jpeg", bytes)
        application {
            configureRoutes(
                CatService(PgCatRepository(dataSource)),
                GuideService(guideRepository),
                dogService = DogService(PgDogRepository(dataSource)),
                photoStore = store
            )
        }
    }

    @Test
    fun `a photo is served from the database with its type, ETag and a day of caching`() = testApplication {
        start()

        val response = client.get("/images/felix.jpg")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("image/jpeg", response.headers[HttpHeaders.ContentType])
        assertEquals(etag, response.headers[HttpHeaders.ETag])
        assertEquals(listOf("max-age=86400"), response.headers.getAll(HttpHeaders.CacheControl))
        assertContentEquals(bytes, response.body<ByteArray>())
    }

    @Test
    fun `a browser that already has the photo gets 304 with no body`() = testApplication {
        start()

        listOf(etag, "W/$etag", "\"other\", $etag", "*").forEach { sent ->
            val response = client.get("/images/felix.jpg") { header(HttpHeaders.IfNoneMatch, sent) }

            assertEquals(HttpStatusCode.NotModified, response.status, sent)
            assertEquals(etag, response.headers[HttpHeaders.ETag], sent)
            assertEquals(listOf("max-age=86400"), response.headers.getAll(HttpHeaders.CacheControl), sent)
            assertEquals(0, response.body<ByteArray>().size, sent)
        }
    }

    @Test
    fun `a replaced photo has a new ETag and an old one gets the new picture`() = testApplication {
        start()
        val newBytes = byteArrayOf(7, 7, 7)
        store.put("felix.jpg", "image/png", newBytes)

        val response = client.get("/images/felix.jpg") { header(HttpHeaders.IfNoneMatch, etag) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("image/png", response.headers[HttpHeaders.ContentType])
        assertContentEquals(newBytes, response.body<ByteArray>())
    }

    @Test
    fun `an unknown photo is the usual 404 page`() = testApplication {
        start()

        val response = client.get("/images/nope.jpg")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertFalse(response.headers.contains(HttpHeaders.ETag))
    }

    @Test
    fun `a cat saved in the database has a page, and its photo urls lead to photos in the database`() = testApplication {
        start()
        PgCatRepository(dataSource).save(
            Cat(
                id = 0,
                name = LocalizedText(en = "Felix"),
                description = LocalizedText(en = "Affectionate"),
                photoUrls = listOf("/images/felix.jpg"),
                location = CatLocation.IN_SHELTER,
                adoptionStage = AdoptionStage.FOR_ADOPTION,
                createdAt = "2026-01-01T00:00:00+01:00",
                updatedAt = "2026-01-01T00:00:00+01:00"
            )
        )

        val page = client.get("/en/cats/felix")

        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue("""src="/images/felix.jpg"""" in page.plainText())
    }
}
