package org.proanima.shelter.repository

import org.proanima.shelter.service.NextVisit
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonVisitOverridesRepositoryTest {

    private fun fileWith(content: String): String =
        Files.createTempFile("visit-overrides", ".json").toFile().apply {
            writeText(content)
            deleteOnExit()
        }.path

    @Test
    fun `a missing file means the regular schedule`() {
        val overrides = JsonVisitOverridesRepository(File("no-such-dir/visit-overrides.json").path).load()

        assertTrue(overrides.cancelled.isEmpty())
        assertTrue(overrides.extra.isEmpty())
    }

    @Test
    fun `cancelled and extra trips are read from the file`() {
        val path = fileWith(
            """{"cancelled": ["2026-10-24"], "extra": [{"date": "2026-10-25", "departure": "11:00"}]}"""
        )

        val overrides = JsonVisitOverridesRepository(path).load()

        assertEquals(setOf(LocalDate.of(2026, 10, 24)), overrides.cancelled)
        assertEquals(listOf(NextVisit(LocalDate.of(2026, 10, 25), LocalTime.of(11, 0))), overrides.extra)
    }

    @Test
    fun `an empty object is valid`() {
        assertTrue(JsonVisitOverridesRepository(fileWith("{}")).load().cancelled.isEmpty())
    }

    @Test
    fun `a malformed date fails loudly instead of being ignored`() {
        val path = fileWith("""{"cancelled": ["24.10.2026"]}""")

        assertFailsWith<Exception> { JsonVisitOverridesRepository(path).load() }
    }
}
