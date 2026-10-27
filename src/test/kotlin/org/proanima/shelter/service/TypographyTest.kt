package org.proanima.shelter.service

import org.proanima.shelter.model.AppLocale
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypographyTest {

    // The no-break space is built from its code, so it cannot be mistaken for a plain space in this file.
    private val nbsp = Char(0xA0)

    @Test
    fun `russian one-letter and short prepositions stay with the next word`() {
        assertEquals(
            "в${nbsp}приюте и${nbsp}в${nbsp}лесу, на${nbsp}улице",
            typographicText("в приюте и в лесу, на улице", AppLocale.RU)
        )
    }

    @Test
    fun `a capital letter and a time after the preposition are handled`() {
        assertEquals("В${nbsp}приюте в${nbsp}9:00", typographicText("В приюте в 9:00", AppLocale.RU))
    }

    @Test
    fun `short words inside longer words are left alone`() {
        assertEquals("налить воду, вопрос", typographicText("налить воду, вопрос", AppLocale.RU))
    }

    @Test
    fun `serbian glues one-letter words only`() {
        assertEquals("u${nbsp}azilu i${nbsp}na putu", typographicText("u azilu i na putu", AppLocale.SR))
    }

    @Test
    fun `the dash does not start a line in any language`() {
        assertEquals("кошки${nbsp}— это", typographicText("кошки — это", AppLocale.RU))
        assertEquals("cats${nbsp}— dogs", typographicText("cats — dogs", AppLocale.EN))
    }

    @Test
    fun `english text is not touched except for the dash`() {
        assertEquals("a cat in the shelter", typographicText("a cat in the shelter", AppLocale.EN))
    }

    @Test
    fun `a second pass changes nothing`() {
        val once = typographicText("Мы в приюте — и на улице", AppLocale.RU)

        assertEquals(once, typographicText(once, AppLocale.RU))
    }

    @Test
    fun `cardPhotoUrl points at the card copy and leaves svg placeholders alone`() {
        assertEquals("/images/felix-card.jpg", cardPhotoUrl("/images/felix.jpg"))
        assertEquals("/images/felix-2-card.jpg", cardPhotoUrl("/images/felix-2.jpg"))
        assertEquals("/images/shot-card.jpg", cardPhotoUrl("/images/shot.png"))
        assertEquals("/images/default-cat.svg", cardPhotoUrl("/images/default-cat.svg"))
    }

    @Test
    fun `guide files use typographic quotes only`() {
        val files = File("content").listFiles { file -> file.name.endsWith(".md") }.orEmpty()
        assertTrue(files.isNotEmpty(), "no guide files found, the working directory is wrong")

        files.forEach { file ->
            val lines = file.readLines().withIndex().filter { (_, line) -> '"' in line }.map { it.index + 1 }
            assertTrue(lines.isEmpty(), "${file.name}: straight quote on line(s) $lines")
        }
    }
}
