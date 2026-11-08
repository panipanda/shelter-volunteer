package org.proanima.shelter.repository

import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.Cat
import org.proanima.shelter.model.CatLocation
import org.proanima.shelter.model.Dog
import org.proanima.shelter.model.LocalizedText
import java.sql.Connection
import java.sql.SQLException
import java.time.Duration
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PgRepositoriesTest {

    private val dataSource = newTestDataSource()
    private val clock = TestClock()
    private val cats = PgCatRepository(dataSource, clock, Duration.ofSeconds(30))
    private val dogs = PgDogRepository(dataSource, clock, Duration.ofSeconds(30))

    private fun cat(id: Int = 0, name: String = "Felix") = Cat(
        id = id,
        name = LocalizedText(ru = "Феликс", en = name),
        birthYear = 2024,
        description = LocalizedText(ru = "Ласковый", en = "Affectionate"),
        photoUrls = listOf("/images/felix.jpg"),
        location = CatLocation.IN_SHELTER,
        adoptionStage = AdoptionStage.FOR_ADOPTION,
        createdAt = "2026-01-01T00:00:00+01:00",
        updatedAt = "2026-01-01T00:00:00+01:00"
    )

    private fun dog(id: Int = 0, name: String = "Sava") =
        Dog(id = id, name = LocalizedText(en = name), description = LocalizedText(en = "Friendly"))

    @Test
    fun `a new cat gets an id and the document carries it`() {
        val saved = cats.save(cat())

        assertNotEquals(0, saved.id)
        assertEquals(saved, cats.findById(saved.id))
        assertEquals(listOf(saved), cats.findAll())
    }

    @Test
    fun `every field of a cat survives the round trip, Cyrillic included`() {
        val original = cat().copy(
            medicalStatus = LocalizedText(ru = "Здоров", sr = "Zdrav"),
            adoptionInstruction = LocalizedText(en = "Write to us"),
            location = CatLocation.IN_FOSTERHOME,
            adoptionStage = AdoptionStage.RESERVED
        )

        val saved = cats.save(original)
        clock.now = clock.now.plusSeconds(60)

        assertEquals(original.copy(id = saved.id), cats.findById(saved.id))
    }

    @Test
    fun `ids are handed out one after another`() {
        val first = cats.save(cat(name = "A"))
        val second = cats.save(cat(name = "B"))

        assertEquals(listOf(first.id, second.id), cats.findAll().map { it.id })
        assertTrue(second.id > first.id)
    }

    @Test
    fun `saving a cat with an id replaces it`() {
        val saved = cats.save(cat())

        cats.save(saved.copy(name = LocalizedText(en = "Renamed")))

        assertEquals("Renamed", cats.findById(saved.id)?.name?.en)
        assertEquals(1, cats.findAll().size)
    }

    @Test
    fun `saving a cat that is no longer there is an error and does not bring it back`() {
        val saved = cats.save(cat())
        cats.delete(saved.id)

        assertFailsWith<NoSuchElementException> { cats.save(saved) }
        assertEquals(emptyList(), cats.findAll())
    }

    @Test
    fun `delete says whether anything was removed`() {
        val saved = cats.save(cat())

        assertTrue(cats.delete(saved.id))
        assertFalse(cats.delete(saved.id))
        assertNull(cats.findById(saved.id))
    }

    @Test
    fun `dogs work the same way`() {
        val saved = dogs.save(dog())

        assertNotEquals(0, saved.id)
        assertEquals(listOf(saved), dogs.findAll())
        assertTrue(dogs.delete(saved.id))
        assertEquals(emptyList(), dogs.findAll())
    }

    @Test
    fun `the list is cached for the cache lifetime and read again after it`() {
        val counting = CountingDataSource(dataSource)
        val repository = PgCatRepository(counting, clock, Duration.ofSeconds(30))
        repository.save(cat())
        counting.connections = 0

        repository.findAll()
        repository.findAll()
        clock.now = clock.now.plusSeconds(29)
        repository.findAll()
        assertEquals(1, counting.connections)

        clock.now = clock.now.plusSeconds(2)
        repository.findAll()
        assertEquals(2, counting.connections)
    }

    @Test
    fun `saving and deleting drop the cache at once`() {
        val first = cats.save(cat(name = "A"))
        assertEquals(1, cats.findAll().size)

        val second = cats.save(cat(name = "B"))
        assertEquals(2, cats.findAll().size)

        cats.delete(first.id)
        assertEquals(listOf(second.id), cats.findAll().map { it.id })
    }

    @Test
    fun `when the database is down an older copy is served, and with no copy the error shows`() {
        val flaky = FlakyDataSource(dataSource)
        val repository = PgCatRepository(flaky, clock, Duration.ofSeconds(30))
        val saved = repository.save(cat())

        flaky.down = true
        assertFailsWith<SQLException> { repository.findAll() }

        flaky.down = false
        assertEquals(listOf(saved), repository.findAll())

        flaky.down = true
        clock.now = clock.now.plusSeconds(120)
        assertEquals(listOf(saved), repository.findAll())
    }

    @Test
    fun `a failed save leaves nothing behind`() {
        val flaky = FlakyDataSource(dataSource)
        val repository = PgCatRepository(flaky, clock, Duration.ofSeconds(30))

        flaky.failAfterStatements = 1
        assertFailsWith<SQLException> { repository.save(cat()) }
        flaky.failAfterStatements = null

        assertEquals(emptyList(), repository.findAll())
    }
}

private class CountingDataSource(private val delegate: DataSource) : DataSource by delegate {
    var connections = 0

    override fun getConnection(): Connection {
        connections++
        return delegate.connection
    }
}

// Fails like a database that is unreachable (down) or that dies in the middle of a transaction (failAfterStatements).
private class FlakyDataSource(private val delegate: DataSource) : DataSource by delegate {
    var down = false
    var failAfterStatements: Int? = null

    override fun getConnection(): Connection {
        if (down) throw SQLException("connection refused")
        val connection = delegate.connection
        val limit = failAfterStatements ?: return connection
        var prepared = 0
        return java.lang.reflect.Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java)
        ) { _, method, args ->
            if (method.name == "prepareStatement" && ++prepared > limit) throw SQLException("connection lost")
            try {
                method.invoke(connection, *(args ?: emptyArray()))
            } catch (e: java.lang.reflect.InvocationTargetException) {
                throw e.targetException
            }
        } as Connection
    }
}
