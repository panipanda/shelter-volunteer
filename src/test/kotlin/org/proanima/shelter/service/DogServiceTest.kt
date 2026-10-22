package org.proanima.shelter.service

import org.proanima.shelter.model.Dog
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.repository.DogRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DogServiceTest {

    private fun dog(id: Int, name: String) =
        Dog(id = id, name = LocalizedText(en = name), description = LocalizedText(en = "d"))

    private val service = DogService(object : DogRepository {
        override fun findAll() = listOf(dog(1, "Sava"), dog(2, "Mila Mae"))
    })

    @Test
    fun `getAllDogs returns every dog`() {
        assertEquals(listOf(1, 2), service.getAllDogs().map { it.id })
    }

    @Test
    fun `getDogBySlug finds a dog by its name slug`() {
        assertEquals(2, service.getDogBySlug("mila-mae")?.id)
    }

    @Test
    fun `getDogBySlug returns null for an unknown slug`() {
        assertNull(service.getDogBySlug("nobody"))
    }
}
