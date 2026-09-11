package org.proanima.shelter.routes

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.proanima.shelter.configureRoutes
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.model.VisitStatus
import org.proanima.shelter.model.VolunteerDirection
import org.proanima.shelter.model.VolunteerVisit
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.repository.CatRepository
import org.proanima.shelter.repository.GuideContent
import org.proanima.shelter.repository.GuideRepository
import org.proanima.shelter.repository.VisitRepository
import org.proanima.shelter.service.CatService
import org.proanima.shelter.service.GuideService
import org.proanima.shelter.service.VisitService
import org.proanima.shelter.model.Cat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisitRoutesTest {

    private val visits = listOf(
        VolunteerVisit(
            id = 1,
            direction = VolunteerDirection.CATS,
            date = "2026-06-12",
            time = "11:00",
            timezone = "Europe/Belgrade",
            title = LocalizedText(en = "Cat shelter visit"),
            capacity = 5,
            freePlaces = 2,
            status = VisitStatus.OPEN,
            signupInstruction = LocalizedText(en = "Signup happens in the cat volunteer chat."),
            publicSummary = null,
            lastUpdatedAt = "2026-05-20T20:30:00+02:00",
            createdAt = "2026-05-20T20:30:00+02:00",
            updatedAt = "2026-05-20T20:30:00+02:00"
        ),
        VolunteerVisit(
            id = 2,
            direction = VolunteerDirection.CATS,
            date = "2026-05-10",
            time = "11:00",
            timezone = "Europe/Belgrade",
            title = LocalizedText(en = "Completed cat shelter visit"),
            capacity = 5,
            freePlaces = 0,
            status = VisitStatus.COMPLETED,
            signupInstruction = null,
            publicSummary = LocalizedText(
                en = "Visit completed. Volunteers helped with cleaning, feeding and cat socialization."
            ),
            lastUpdatedAt = "2026-05-10T18:00:00+02:00",
            createdAt = "2026-05-01T12:00:00+02:00",
            updatedAt = "2026-05-10T18:00:00+02:00"
        )
    )

    private val visitRepository = object : VisitRepository {
        override fun findAll(): List<VolunteerVisit> {
            return visits
        }

        override fun findById(id: Int): VolunteerVisit? {
            return visits.firstOrNull { it.id == id }
        }
    }

    private val catRepository = object : CatRepository {
        override fun findAll(): List<Cat> {
            return emptyList()
        }

        override fun findById(id: Int): Cat? {
            return null
        }
    }

    private val guideRepository = object : GuideRepository {
        override fun findGuideMarkdown(locale: AppLocale): GuideContent {
            return GuideContent("# Guide\n\nStub guide content for tests.", locale.code)
        }
    }

    @Test
    fun `GET visits returns upcoming visits page`() = testApplication {
        application {
            configureRoutes(
                catService = CatService(catRepository),
                visitService = VisitService(visitRepository),
                guideService = GuideService(guideRepository)
            )
        }

        val response = client.get("/ru/visits")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("Визиты волонтёров"))
        assertTrue(body.contains("Cat shelter visit"))
        assertTrue(body.contains("2026-06-12"))
        assertTrue(body.contains("Свободных мест: 2"))
        assertFalse(body.contains("Visit completed. Volunteers helped with cleaning, feeding and cat socialization."))
    }

    @Test
    fun `GET visits archive returns completed visits page`() = testApplication {
        application {
            configureRoutes(
                catService = CatService(catRepository),
                visitService = VisitService(visitRepository),
                guideService = GuideService(guideRepository)
            )
        }

        val response = client.get("/ru/visits/archive")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("Архив визитов"))
        assertTrue(body.contains("Completed cat shelter visit"))
        assertTrue(body.contains("2026-05-10"))
        assertTrue(body.contains("Visit completed. Volunteers helped with cleaning, feeding and cat socialization."))
        assertFalse(body.contains("Свободных мест: 2"))
    }
}