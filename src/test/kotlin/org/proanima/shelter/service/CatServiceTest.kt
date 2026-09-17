package org.proanima.shelter.service

import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.Cat
import org.proanima.shelter.model.CatLocation
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.repository.CatRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CatServiceTest {

    private val cats = listOf(
        Cat(
            id = 1,
            name = "Zoka",
            birthYear = 2023,
            description = LocalizedText(en = "Calm and friendly cat."),
            photoUrls = listOf("/images/cats/zoka.jpg"),
            location = CatLocation.IN_SHELTER,
            adoptionStage = AdoptionStage.FOR_ADOPTION,
            createdAt = "2026-05-20T20:30:00+02:00",
            updatedAt = "2026-05-20T20:30:00+02:00"
        ),
        Cat(
            id = 2,
            name = "Marta",
            birthYear = null,
            description = LocalizedText(en = "Shy but sweet cat."),
            location = CatLocation.IN_SHELTER,
            adoptionStage = AdoptionStage.NONE,
            createdAt = "2026-05-20T20:30:00+02:00",
            updatedAt = "2026-05-20T20:30:00+02:00"
        )
    )

    private val repository = object : CatRepository {
        override fun findAll(): List<Cat> {
            return cats
        }

        override fun findById(id: Int): Cat? {
            return cats.firstOrNull { it.id == id }
        }
    }

    private val service = CatService(repository)

    @Test
    fun `getAllCats returns all cats`() {
        val result = service.getAllCats()

        assertEquals(2, result.size)
    }

    @Test
    fun `getAvailableCats returns only available cats`() {
        val result = service.getAvailableCats()

        assertEquals(1, result.size)
        assertEquals("Zoka", result.first().name)
    }

    @Test
    fun `getCatById returns cat when cat exists`() {
        val result = service.getCatById(1)

        assertEquals("Zoka", result?.name)
    }

    @Test
    fun `getCatById returns null when cat does not exist`() {
        val result = service.getCatById(999)

        assertNull(result)
    }

    @Test
    fun `getCatBySlug returns cat matching the name-based slug`() {
        val result = service.getCatBySlug("zoka")

        assertEquals(1, result?.id)
    }

    @Test
    fun `getCatBySlug returns null when no cat matches`() {
        val result = service.getCatBySlug("no-such-cat")

        assertNull(result)
    }
}