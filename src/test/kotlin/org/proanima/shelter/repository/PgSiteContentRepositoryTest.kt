package org.proanima.shelter.repository

import org.proanima.shelter.service.ContentKey
import java.sql.Connection
import java.sql.SQLException
import java.time.Duration
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class PgSiteContentRepositoryTest {

    private val dataSource = newTestDataSource()
    private val clock = TestClock()
    private val repository = PgSiteContentRepository(dataSource, clock, Duration.ofSeconds(30))

    private val headline = ContentKey("home.hero.headline", "ru")

    @Test
    fun `an empty database has no edited texts`() {
        assertEquals(emptyMap(), repository.load().entries)
    }

    @Test
    fun `a text is stored with its language, Cyrillic included, and a setting without one`() {
        repository.update(
            mapOf(
                headline to "Волонтёры — это мы",
                ContentKey("home.hero.headline", "sr") to "Dobrovoljci",
                ContentKey("contact.nata.telegram") to "Someone"
            )
        )

        val content = repository.load()
        assertEquals("Волонтёры — это мы", content.text("home.hero.headline", "ru"))
        assertEquals("Dobrovoljci", content.text("home.hero.headline", "sr"))
        assertNull(content.text("home.hero.headline", "en"))
        assertEquals("Someone", content.plain("contact.nata.telegram"))
    }

    @Test
    fun `saving a key again replaces its text, and null brings back the shipped one`() {
        repository.update(mapOf(headline to "Первый"))
        repository.update(mapOf(headline to "Второй"))
        assertEquals(mapOf(headline to "Второй"), repository.load().entries)

        repository.update(mapOf(headline to null))
        assertEquals(emptyMap(), repository.load().entries)
    }

    @Test
    fun `removing a text that was never stored is not an error`() {
        repository.update(mapOf(headline to null))

        assertEquals(emptyMap(), repository.load().entries)
    }

    @Test
    fun `a long text such as a whole guide fits`() {
        val guide = "# Гайд\n\n" + "Строка гайда.\n".repeat(5_000)
        repository.update(mapOf(ContentKey("guide.markdown", "ru") to guide))

        assertEquals(guide, repository.load().text("guide.markdown", "ru"))
    }

    @Test
    fun `the list is kept for a while and a save drops it at once`() {
        val counting = CountingConnections(dataSource)
        val counted = PgSiteContentRepository(counting, clock, Duration.ofSeconds(30))

        val first = counted.load()
        assertSame(first, counted.load())
        assertEquals(1, counting.opened)

        clock.now = clock.now.plusSeconds(31)
        counted.load()
        assertEquals(2, counting.opened)

        counted.update(mapOf(headline to "Новый"))
        assertEquals("Новый", counted.load().text("home.hero.headline", "ru"))
    }

    @Test
    fun `a failed save changes nothing`() {
        repository.update(mapOf(headline to "Старый"))
        val flaky = FailingStatements(dataSource)
        val broken = PgSiteContentRepository(flaky, clock, Duration.ofSeconds(30))

        flaky.failAfter = 1
        runCatching {
            broken.update(mapOf(headline to "Новый", ContentKey("home.hero.lead", "ru") to "Ещё"))
        }
        flaky.failAfter = null

        assertEquals(mapOf(headline to "Старый"), PgSiteContentRepository(dataSource, clock).load().entries)
    }

    @Test
    fun `with the database down the older copy is served, and with no copy the shipped texts`() {
        val flaky = DownSwitch(dataSource)
        val flakyRepository = PgSiteContentRepository(flaky, clock, Duration.ofSeconds(30))

        // No copy yet: the pages still work, with the texts that come with the code.
        flaky.down = true
        assertEquals(emptyMap(), flakyRepository.load().entries)

        flaky.down = false
        flakyRepository.update(mapOf(headline to "Текст"))
        assertEquals("Текст", flakyRepository.load().text("home.hero.headline", "ru"))

        flaky.down = true
        clock.now = clock.now.plusSeconds(120)
        assertEquals("Текст", flakyRepository.load().text("home.hero.headline", "ru"))
    }

    @Test
    fun `after a failed read the database is left alone for a few seconds`() {
        val down = DownSwitch(dataSource)
        val counting = CountingConnections(down)
        val flaky = PgSiteContentRepository(counting, clock, Duration.ofSeconds(30))

        // Five page views, one attempt: the other four are answered with the shipped texts without asking.
        down.down = true
        repeat(5) { assertEquals(emptyMap(), flaky.load().entries) }
        assertEquals(1, counting.opened)

        clock.now = clock.now.plusSeconds(11)
        down.down = false
        assertEquals(emptyMap(), flaky.load().entries)
        assertEquals(2, counting.opened)

        // A write makes the pause end at once, and the copy that was read after it is what an outage serves later.
        flaky.update(mapOf(headline to "Текст"))
        assertEquals("Текст", flaky.load().text("home.hero.headline", "ru"))
        down.down = true
        clock.now = clock.now.plusSeconds(60)
        assertEquals("Текст", flaky.load().text("home.hero.headline", "ru"))
    }

    @Test
    fun `the in-memory repository behaves the same way`() {
        val memory = InMemorySiteContentRepository()
        memory.update(mapOf(headline to "Текст", ContentKey("home.hero.lead", "ru") to "Лид"))
        memory.update(mapOf(headline to null))

        assertEquals(mapOf(ContentKey("home.hero.lead", "ru") to "Лид"), memory.load().entries)
    }
}

private class CountingConnections(private val delegate: DataSource) : DataSource by delegate {
    var opened = 0

    override fun getConnection(): Connection {
        opened++
        return delegate.connection
    }
}

private class DownSwitch(private val delegate: DataSource) : DataSource by delegate {
    var down = false

    override fun getConnection(): Connection {
        if (down) throw SQLException("connection refused")
        return delegate.connection
    }
}

// Dies in the middle of a transaction: the first statement goes through, the next ones fail.
private class FailingStatements(private val delegate: DataSource) : DataSource by delegate {
    var failAfter: Int? = null

    override fun getConnection(): Connection {
        val connection = delegate.connection
        val limit = failAfter ?: return connection
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
