package org.proanima.shelter.routes

import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.proanima.shelter.configureRoutes
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.Cat
import org.proanima.shelter.repository.CatRepository
import org.proanima.shelter.repository.GuideContent
import org.proanima.shelter.repository.GuideRepository
import org.proanima.shelter.service.CatService
import org.proanima.shelter.service.GuideService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Link previews, uptime monitors and some crawlers ask with HEAD ("the headers, please, not the page"). Without
// help from the framework only files served from a folder answered it; a page gave 405.
class HeadRequestsTest {

    private val guideRepository = object : GuideRepository {
        override fun findGuideMarkdown(locale: AppLocale): GuideContent = GuideContent("# Guide", locale.code)
    }

    private val noCats = object : CatRepository {
        override fun findAll(): List<Cat> = emptyList()
        override fun findById(id: Int): Cat? = null
    }

    @Test
    fun `a HEAD request to a page gets the headers of the page and no body`() = testApplication {
        application { configureRoutes(CatService(noCats), GuideService(guideRepository)) }

        listOf("/ru", "/en/cats", "/ru/dogs", "/ru/help", "/health").forEach { path ->
            val head = client.head(path)

            assertEquals(HttpStatusCode.OK, head.status, path)
            assertEquals(client.get(path).headers[HttpHeaders.ContentType], head.headers[HttpHeaders.ContentType], path)
            assertEquals("", head.bodyAsText(), path)
        }
    }

    @Test
    fun `a HEAD request to a missing page is a 404 as before`() = testApplication {
        application { configureRoutes(CatService(noCats), GuideService(guideRepository)) }

        assertEquals(HttpStatusCode.NotFound, client.head("/ru/cats/nobody").status)
        assertEquals(HttpStatusCode.NotFound, client.head("/nope").status)
    }

    @Test
    fun `a HEAD request to a picture from the folder tells its type and size`() = testApplication {
        application { configureRoutes(CatService(noCats), GuideService(guideRepository)) }

        val head = client.head("/images/felix.jpg")

        assertEquals(HttpStatusCode.OK, head.status)
        assertEquals("image/jpeg", head.headers[HttpHeaders.ContentType])
        assertTrue((head.headers[HttpHeaders.ContentLength]?.toLong() ?: 0) > 1000)
        assertNotNull(head.headers[HttpHeaders.CacheControl])
    }
}
