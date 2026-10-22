package org.proanima.shelter.routes

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.proanima.shelter.configureRoutes
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.Cat
import org.proanima.shelter.module
import org.proanima.shelter.repository.CatRepository
import org.proanima.shelter.repository.GuideContent
import org.proanima.shelter.repository.GuideRepository
import org.proanima.shelter.service.CatService
import org.proanima.shelter.service.GuideService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ErrorPagesTest {

    @Test
    fun `unknown cat returns a localized HTML 404`() = testApplication {
        application { module() }

        val ru = client.get("/ru/cats/nobody")
        assertEquals(HttpStatusCode.NotFound, ru.status)
        assertTrue(ru.headers[HttpHeaders.ContentType]!!.startsWith("text/html"))
        assertTrue(ru.bodyAsText().contains("Страница не найдена"))

        assertTrue(client.get("/en/cats/nobody").bodyAsText().contains("Page not found"))
        assertTrue(client.get("/sr/cats/nobody").bodyAsText().contains("Stranica nije pronađena"))
    }

    @Test
    fun `unknown dog returns a localized HTML 404`() = testApplication {
        application { module() }

        val response = client.get("/en/dogs/nobody")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.bodyAsText().contains("Page not found"))
    }

    @Test
    fun `unknown URL without a locale prefix returns the 404 page in the default language`() = testApplication {
        application { module() }

        val response = client.get("/no/such/page")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.bodyAsText().contains("Страница не найдена"))
    }

    @Test
    fun `trailing slash does not change the page`() = testApplication {
        application { module() }

        assertEquals(HttpStatusCode.OK, client.get("/ru/cats/").status)
        assertEquals(HttpStatusCode.OK, client.get("/en/dogs/").status)
    }

    @Test
    fun `unexpected failure returns a localized HTML 500`() = testApplication {
        val failingCats = object : CatRepository {
            override fun findAll(): List<Cat> = error("data file is broken")
            override fun findById(id: Int): Cat? = null
        }
        val guide = object : GuideRepository {
            override fun findGuideMarkdown(locale: AppLocale) = GuideContent("# Guide", locale.code)
        }
        application {
            configureRoutes(catService = CatService(failingCats), guideService = GuideService(guide))
        }

        val response = client.get("/en/cats")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Something went wrong"))
        assertTrue(!body.contains("data file is broken"), "the error details must not leak to visitors")
    }

    @Test
    fun `static assets get a cache lifetime`() = testApplication {
        application { module() }

        assertEquals("max-age=3600", client.get("/styles/main.css").headers[HttpHeaders.CacheControl])
        assertEquals("max-age=86400", client.get("/images/default-cat.jpg").headers[HttpHeaders.CacheControl])
    }
}
