package org.proanima.shelter.service

import org.proanima.shelter.model.Cat
import org.proanima.shelter.repository.CatRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CatServiceTest {

    private val cats = listOf(
        Cat(
            id = 1,
            name = "Zoka",
            age = 3,
            description = "Calm and friendly cat.",
            photoUrl = "/images/cats/zoka.jpg",
            isAvailable = true,
            createdAt = "2026-05-20T20:30:00+02:00",
            updatedAt = "2026-05-20T20:30:00+02:00"
        ),
        Cat(
            id = 2,
            name = "Marta",
            age = null,
            description = "Shy but sweet cat.",
            photoUrl = null,
            isAvailable = false,
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
}