package org.proanima.shelter.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JsonCatRepositoryTest {

    private val repository = JsonCatRepository()

    @Test
    fun `findAll returns cats from json file`() {
        val cats = repository.findAll()

        assertTrue(cats.isNotEmpty())
    }

    @Test
    fun `findById returns cat when cat exists`() {
        val cats = repository.findAll()
        val firstCat = cats.first()

        val result = repository.findById(firstCat.id)

        assertNotNull(result)
        assertEquals(firstCat.id, result.id)
    }

    @Test
    fun `findById returns null when cat does not exist`() {
        val result = repository.findById(999999)

        assertEquals(null, result)
    }
}