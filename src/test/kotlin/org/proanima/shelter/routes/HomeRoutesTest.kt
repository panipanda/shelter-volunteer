package org.proanima.shelter.routes

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.proanima.shelter.configureRoutes
import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.Cat
import org.proanima.shelter.model.CatLocation
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.repository.CatRepository
import org.proanima.shelter.repository.GuideContent
import org.proanima.shelter.repository.GuideRepository
import org.proanima.shelter.service.CatService
import org.proanima.shelter.service.GuideService
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeRoutesTest {

    private fun cat(id: Int, name: String, stage: AdoptionStage) = Cat(
        id = id,
        name = LocalizedText(en = name),
        birthYear = 2025,
        birthMonth = 3,
        description = LocalizedText(en = "Friendly cat"),
        medicalStatus = null,
        photoUrls = emptyList(),
        location = CatLocation.IN_SHELTER,
        adoptionStage = stage,
        adoptionInstruction = null,
        createdAt = "2026-01-01T00:00:00+01:00",
        updatedAt = "2026-01-01T00:00:00+01:00"
    )

    // Часы стоят на UTC, чтобы тест не зависел от часового пояса машины; 2026-09-16 — среда.
    private fun clockAt(dateTime: String): Clock =
        Clock.fixed(Instant.parse("${dateTime}Z"), ZoneId.of("UTC"))

    private fun catRepository(cats: List<Cat>) = object : CatRepository {
        override fun findAll(): List<Cat> = cats
        override fun findById(id: Int): Cat? = cats.firstOrNull { it.id == id }
    }

    private val guideRepository = object : GuideRepository {
        override fun findGuideMarkdown(locale: AppLocale): GuideContent =
            GuideContent("# Guide\n\nStub guide content for tests.", locale.code)
    }

    @Test
    fun `GET home shows about, volunteer info and contacts in every locale`() = testApplication {
        application {
            configureRoutes(
                catService = CatService(catRepository(emptyList())),
                guideService = GuideService(guideRepository)
            )
        }

        listOf("ru", "en", "sr").forEach { code ->
            val response = client.get("/$code")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status, code)
            assertTrue(body.contains("https://www.instagram.com/proanima_beograd"), code)
            assertTrue(body.contains("https://t.me/Nata_Rogava"), code)
            assertTrue(body.contains("https://t.me/Trikcsy"), code)
            assertTrue(body.contains("Mirica Toma") || body.contains("Мирица Тома"), code)
            assertTrue(body.contains("id=\"join\""), code)
        }
    }

    @Test
    fun `every page has a logo linking to its locale home and serves the icons`() = testApplication {
        application {
            configureRoutes(
                catService = CatService(catRepository(emptyList())),
                guideService = GuideService(guideRepository)
            )
        }

        listOf("ru", "en", "sr").forEach { code ->
            listOf("/$code/cats", "/$code/guide", "/$code/visits").forEach { path ->
                val body = client.get(path).bodyAsText()
                assertTrue(body.contains("<a href=\"/$code\" class=\"logo\">"), path)
                assertTrue(body.contains("/icons/favicon-32.png"), path)
            }
        }
        listOf("logo.png", "favicon-32.png", "favicon-192.png", "apple-touch-icon.png").forEach { icon ->
            assertEquals(HttpStatusCode.OK, client.get("/icons/$icon").status, icon)
        }
    }

    private fun homeAt(dateTime: String, path: String = "/ru") = run {
        var body = ""
        testApplication {
            application {
                configureRoutes(
                    catService = CatService(catRepository(emptyList())),
                    guideService = GuideService(guideRepository),
                    clock = clockAt(dateTime)
                )
            }
            body = client.get(path).bodyAsText()
        }
        body
    }

    @Test
    fun `GET home shows Wednesday when it is the nearest visit`() {
        // Понедельник 14 сентября 2026: ближайшая среда — 16-е, выезд в 9:00.
        val body = homeAt("2026-09-14T10:00:00")

        assertTrue(body.contains("<strong>16</strong>"))
        assertTrue(body.contains("Среда"))
        assertTrue(body.contains("Выезд из Врачара (Белград) в 9:00."))
    }

    @Test
    fun `GET home shows Saturday when it is the nearest visit`() {
        // Четверг 17 сентября 2026: после среды ближайшая — суббота 19-го, выезд в 10:00.
        val body = homeAt("2026-09-17T12:00:00")

        assertTrue(body.contains("<strong>19</strong>"))
        assertTrue(body.contains("Суббота"))
        assertTrue(body.contains("Выезд из Врачара (Белград) в 10:00."))
    }

    @Test
    fun `GET home switches to the next visit once today's departure has passed`() {
        assertTrue(homeAt("2026-09-16T08:59:00").contains("<strong>16</strong>"))
        assertTrue(homeAt("2026-09-16T09:01:00").contains("<strong>19</strong>"))
        assertTrue(homeAt("2026-09-19T10:01:00").contains("<strong>23</strong>"))
    }

    @Test
    fun `GET home mentions the chat and coordinators but no free places`() {
        val body = homeAt("2026-09-14T10:00:00")

        assertTrue(body.contains("Запись и свободные места — в чате котоволонтёров. Если есть вопросы, пишите координаторам."))
        assertFalse(body.contains("Свободных мест:"))
    }

    @Test
    fun `GET home shows at most three cats that are waiting for adoption`() = testApplication {
        val cats = listOf(
            cat(1, "Alfa", AdoptionStage.FOR_ADOPTION),
            cat(2, "Beta", AdoptionStage.ADOPTED),
            cat(3, "Gamma", AdoptionStage.FOR_ADOPTION),
            cat(4, "Delta", AdoptionStage.FOR_ADOPTION),
            cat(5, "Epsilon", AdoptionStage.FOR_ADOPTION)
        )
        application {
            configureRoutes(
                catService = CatService(catRepository(cats)),
                guideService = GuideService(guideRepository)
            )
        }

        val body = client.get("/en").bodyAsText()

        assertTrue(body.contains("Alfa"))
        assertTrue(body.contains("Gamma"))
        assertTrue(body.contains("Delta"))
        assertFalse(body.contains("Beta"))
        assertFalse(body.contains("Epsilon"))
    }
}
