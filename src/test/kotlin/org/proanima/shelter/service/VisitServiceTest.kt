package org.proanima.shelter.service

import org.proanima.shelter.model.VisitStatus
import org.proanima.shelter.model.VolunteerDirection
import org.proanima.shelter.model.VolunteerVisit
import org.proanima.shelter.repository.VisitRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VisitServiceTest {

    private val visits = listOf(
        VolunteerVisit(
            id = 1,
            direction = VolunteerDirection.CATS,
            date = "2026-06-12",
            time = "11:00",
            timezone = "Europe/Belgrade",
            title = "Cat shelter visit",
            capacity = 5,
            freePlaces = 2,
            status = VisitStatus.OPEN,
            signupInstruction = "Signup happens in the cat volunteer chat.",
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
            title = "Completed cat shelter visit",
            capacity = 5,
            freePlaces = 0,
            status = VisitStatus.COMPLETED,
            signupInstruction = null,
            publicSummary = "Visit completed.",
            lastUpdatedAt = "2026-05-10T18:00:00+02:00",
            createdAt = "2026-05-01T12:00:00+02:00",
            updatedAt = "2026-05-10T18:00:00+02:00"
        ),
        VolunteerVisit(
            id = 3,
            direction = VolunteerDirection.DOGS,
            date = "2026-06-14",
            time = "12:00",
            timezone = "Europe/Belgrade",
            title = "Dog shelter visit",
            capacity = 4,
            freePlaces = 1,
            status = VisitStatus.OPEN,
            signupInstruction = "Signup happens in the dog volunteer chat.",
            publicSummary = null,
            lastUpdatedAt = "2026-05-20T20:30:00+02:00",
            createdAt = "2026-05-20T20:30:00+02:00",
            updatedAt = "2026-05-20T20:30:00+02:00"
        )
    )

    private val repository = object : VisitRepository {
        override fun findAll(): List<VolunteerVisit> {
            return visits
        }

        override fun findById(id: Int): VolunteerVisit? {
            return visits.firstOrNull { it.id == id }
        }
    }

    private val service = VisitService(repository)

    @Test
    fun `getAllVisits returns all visits`() {
        val result = service.getAllVisits()

        assertEquals(3, result.size)
    }

    @Test
    fun `getCatVisits returns only cat visits`() {
        val result = service.getCatVisits()

        assertEquals(2, result.size)
        assertEquals(VolunteerDirection.CATS, result.first().direction)
    }

    @Test
    fun `getArchivedVisits returns completed visits`() {
        val result = service.getArchivedVisits()

        assertEquals(1, result.size)
        assertEquals(VisitStatus.COMPLETED, result.first().status)
    }

    @Test
    fun `getUpcomingVisits returns visits that are not completed`() {
        val result = service.getUpcomingVisits()

        assertEquals(2, result.size)
        assertEquals(false, result.any { it.status == VisitStatus.COMPLETED })
    }

    @Test
    fun `getVisitById returns visit when visit exists`() {
        val result = service.getVisitById(1)

        assertEquals("Cat shelter visit", result?.title)
    }

    @Test
    fun `getVisitById returns null when visit does not exist`() {
        val result = service.getVisitById(999)

        assertNull(result)
    }
}