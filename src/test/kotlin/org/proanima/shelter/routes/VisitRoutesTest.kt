package org.proanima.shelter.routes

import io.ktor.client.request.get
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
import org.proanima.shelter.service.VisitOverrides
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private fun visitsAt(dateTime: String, overrides: VisitOverrides = VisitOverrides()): String {
        var body = ""
        testApplication {
            application {
                configureRoutes(
                    CatService(catRepository),
                    GuideService(guideRepository),
                    clock = Clock.fixed(Instant.parse("${dateTime}Z"), ZoneId.of("UTC")),
                    visitOverrides = overrides
                )
            }
            body = client.get("/ru/visits").plainText()
        }
        return body
    }

    @Test
    fun `GET visits shows the next visit and how to sign up`() {
        // Monday, September 14, 2026: the next trip is Wednesday the 16th at 9:00.
        val body = visitsAt("2026-09-14T10:00:00")

        assertTrue(body.contains("Визиты волонтёров"))
        assertTrue(body.contains("<strong>16</strong>"))
        assertTrue(body.contains("Выезд из Врачара (Белград) в 9:00."))
        assertTrue(body.contains("""id="join""""))
        assertFalse(body.contains("скоро появится"))
    }

    @Test
    fun `GET visits skips a cancelled trip`() {
        val body = visitsAt("2026-09-14T10:00:00", VisitOverrides(cancelled = setOf(LocalDate.of(2026, 9, 16))))

        assertTrue(body.contains("<strong>19</strong>"))
        assertFalse(body.contains("<strong>16</strong>"))
    }

    @Test
    fun `GET visits archive returns a stub page`() = testApplication {
        application {
            configureRoutes(CatService(catRepository), GuideService(guideRepository))
        }

        val response = client.get("/ru/visits/archive")
        val body = response.plainText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("Архив визитов"))
        assertTrue(body.contains("Архив прошедших визитов скоро появится здесь"))
        assertTrue(body.contains("""href="/ru/visits""""))
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
