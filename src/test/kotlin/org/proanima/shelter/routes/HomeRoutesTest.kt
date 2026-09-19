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
import org.proanima.shelter.model.VisitStatus
import org.proanima.shelter.model.VolunteerDirection
import org.proanima.shelter.model.VolunteerVisit
import org.proanima.shelter.repository.CatRepository
import org.proanima.shelter.repository.GuideContent
import org.proanima.shelter.repository.GuideRepository
import org.proanima.shelter.repository.VisitRepository
import org.proanima.shelter.service.CatService
import org.proanima.shelter.service.GuideService
import org.proanima.shelter.service.VisitService
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

    private fun visit(id: Int, date: String, status: VisitStatus, title: String) = VolunteerVisit(
        id = id,
        direction = VolunteerDirection.CATS,
        date = date,
        time = "11:00",
        timezone = "Europe/Belgrade",
        title = LocalizedText(en = title),
        capacity = 5,
        freePlaces = 2,
        status = status,
        signupInstruction = null,
        publicSummary = null,
        lastUpdatedAt = "2026-01-01T00:00:00+01:00",
        createdAt = "2026-01-01T00:00:00+01:00",
        updatedAt = "2026-01-01T00:00:00+01:00"
    )

    private fun catRepository(cats: List<Cat>) = object : CatRepository {
        override fun findAll(): List<Cat> = cats
        override fun findById(id: Int): Cat? = cats.firstOrNull { it.id == id }
    }

    private fun visitRepository(visits: List<VolunteerVisit>) = object : VisitRepository {
        override fun findAll(): List<VolunteerVisit> = visits
        override fun findById(id: Int): VolunteerVisit? = visits.firstOrNull { it.id == id }
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
                visitService = VisitService(visitRepository(emptyList())),
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
    fun `GET home shows the nearest open cat visit and skips completed and cancelled ones`() = testApplication {
        val visits = listOf(
            visit(1, "2026-05-10", VisitStatus.COMPLETED, "Completed visit"),
            visit(2, "2026-06-01", VisitStatus.CANCELLED, "Cancelled visit"),
            visit(3, "2026-07-20", VisitStatus.OPEN, "Later visit"),
            visit(4, "2026-07-15", VisitStatus.OPEN, "Nearest visit")
        )
        application {
            configureRoutes(
                catService = CatService(catRepository(emptyList())),
                visitService = VisitService(visitRepository(visits)),
                guideService = GuideService(guideRepository)
            )
        }

        val body = client.get("/en").bodyAsText()

        assertTrue(body.contains("Nearest visit"))
        assertFalse(body.contains("Later visit"))
        assertFalse(body.contains("Completed visit"))
        assertFalse(body.contains("Cancelled visit"))
    }

    @Test
    fun `GET home falls back to the chat hint when there are no visits`() = testApplication {
        application {
            configureRoutes(
                catService = CatService(catRepository(emptyList())),
                visitService = VisitService(visitRepository(emptyList())),
                guideService = GuideService(guideRepository)
            )
        }

        val body = client.get("/ru").bodyAsText()

        assertTrue(body.contains("Актуальное расписание визитов смотрите в чате котоволонтёров."))
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
                visitService = VisitService(visitRepository(emptyList())),
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
