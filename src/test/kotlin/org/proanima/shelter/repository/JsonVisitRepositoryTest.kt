package org.proanima.shelter.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JsonVisitRepositoryTest {

    private val repository = JsonVisitRepository()

    @Test
    fun `findAll returns visits from json file`() {
        val visits = repository.findAll()

        assertTrue(visits.isNotEmpty())
    }

    @Test
    fun `findById returns visit when visit exists`() {
        val visits = repository.findAll()
        val firstVisit = visits.first()

        val result = repository.findById(firstVisit.id)

        assertNotNull(result)
        assertEquals(firstVisit.id, result.id)
    }

    @Test
    fun `findById returns null when visit does not exist`() {
        val result = repository.findById(999999)

        assertEquals(null, result)
    }
}