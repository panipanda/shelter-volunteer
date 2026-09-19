package org.proanima.shelter.routes

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
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
import kotlin.test.assertTrue

class VisitRoutesTest {

    private val catRepository = object : CatRepository {
        override fun findAll(): List<Cat> = emptyList()
        override fun findById(id: Int): Cat? = null
    }

    private val guideRepository = object : GuideRepository {
        override fun findGuideMarkdown(locale: AppLocale): GuideContent =
            GuideContent("# Guide\n\nStub guide content for tests.", locale.code)
    }

    @Test
    fun `GET visits returns a stub page pointing to the chat`() = testApplication {
        application {
            configureRoutes(CatService(catRepository), GuideService(guideRepository))
        }

        val response = client.get("/ru/visits")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("Визиты волонтёров"))
        assertTrue(body.contains("Список ближайших визитов скоро появится здесь"))
    }

    @Test
    fun `GET visits archive returns a stub page`() = testApplication {
        application {
            configureRoutes(CatService(catRepository), GuideService(guideRepository))
        }

        val response = client.get("/ru/visits/archive")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("Архив визитов"))
        assertTrue(body.contains("Архив прошедших визитов скоро появится здесь"))
    }

    @Test
    fun `visit stubs are served in every locale`() = testApplication {
        application {
            configureRoutes(CatService(catRepository), GuideService(guideRepository))
        }

        listOf("ru", "en", "sr").forEach { code ->
            listOf("/$code/visits", "/$code/visits/archive").forEach { path ->
                assertEquals(HttpStatusCode.OK, client.get(path).status, path)
            }
        }
    }
}
