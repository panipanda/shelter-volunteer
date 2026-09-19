package org.proanima.shelter.service

import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.LocalizedText
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayHelpersTest {

    private val today = LocalDate.of(2026, 9, 17)

    @Test
    fun `displayCatAge returns unknown when birth year is missing`() {
        assertEquals("Age unknown", displayCatAge(null, null, AppLocale.EN, today))
    }

    @Test
    fun `displayCatAge with only year known counts full years from today`() {
        assertEquals("2 years old", displayCatAge(2024, null, AppLocale.EN, today))
    }

    @Test
    fun `displayCatAge with only year known handles exactly one year`() {
        assertEquals("1 year old", displayCatAge(2025, null, AppLocale.EN, today))
    }

    @Test
    fun `displayCatAge with year and month counts months before the first year`() {
        assertEquals("8 months old", displayCatAge(2026, 1, AppLocale.EN, today))
    }

    @Test
    fun `displayCatAge with year and month handles exactly one month`() {
        assertEquals("1 month old", displayCatAge(2026, 8, AppLocale.EN, today))
    }

    @Test
    fun `displayCatAge with year and month switches to years after the first year`() {
        assertEquals("2 years old", displayCatAge(2024, 3, AppLocale.EN, today))
    }

    @Test
    fun `displayCatAge in Russian uses the correct plural form for years`() {
        assertEquals("1 год", displayCatAge(2025, null, AppLocale.RU, today))
        assertEquals("2 года", displayCatAge(2024, null, AppLocale.RU, today))
        assertEquals("5 лет", displayCatAge(2021, null, AppLocale.RU, today))
        assertEquals("14 лет", displayCatAge(2012, null, AppLocale.RU, today))
        assertEquals("21 год", displayCatAge(2005, null, AppLocale.RU, today))
        assertEquals("22 года", displayCatAge(2004, null, AppLocale.RU, today))
    }

    @Test
    fun `displayCatAge in Russian uses the correct plural form for months`() {
        assertEquals("1 месяц", displayCatAge(2026, 8, AppLocale.RU, today))
        assertEquals("2 месяца", displayCatAge(2026, 7, AppLocale.RU, today))
        assertEquals("8 месяцев", displayCatAge(2026, 1, AppLocale.RU, today))
    }

    @Test
    fun `displayCatAge in Serbian uses the correct plural form for years`() {
        assertEquals("1 godina", displayCatAge(2025, null, AppLocale.SR, today))
        assertEquals("2 godine", displayCatAge(2024, null, AppLocale.SR, today))
        assertEquals("5 godina", displayCatAge(2021, null, AppLocale.SR, today))
        assertEquals("21 godina", displayCatAge(2005, null, AppLocale.SR, today))
        assertEquals("22 godine", displayCatAge(2004, null, AppLocale.SR, today))
    }

    @Test
    fun `displayCatAge in Serbian uses the correct plural form for months`() {
        assertEquals("1 mesec", displayCatAge(2026, 8, AppLocale.SR, today))
        assertEquals("2 meseca", displayCatAge(2026, 7, AppLocale.SR, today))
        assertEquals("8 meseci", displayCatAge(2026, 1, AppLocale.SR, today))
    }

    @Test
    fun `displayCatName returns the name in the requested locale`() {
        val name = LocalizedText(ru = "Феликс", en = "Felix", sr = "Feliks")

        assertEquals("Феликс", displayCatName(name, AppLocale.RU))
        assertEquals("Felix", displayCatName(name, AppLocale.EN))
        assertEquals("Feliks", displayCatName(name, AppLocale.SR))
    }

    @Test
    fun `displayCatName falls back to another locale when the requested one is missing`() {
        assertEquals("Felix", displayCatName(LocalizedText(en = "Felix"), AppLocale.SR))
    }

    @Test
    fun `displayCatName shows the unnamed placeholder when name is missing`() {
        assertEquals("Unnamed", displayCatName(null, AppLocale.EN))
    }

    @Test
    fun `catSlug lowercases the cat name`() {
        assertEquals("nami", catSlug("Nami", 3))
    }

    @Test
    fun `catSlug replaces non-alphanumeric characters and trims edge hyphens`() {
        assertEquals("mama-cat", catSlug(" Mama Cat! ", 1))
    }

    @Test
    fun `catSlug falls back to the numeric id when name is missing`() {
        assertEquals("5", catSlug(null, 5))
    }

    @Test
    fun `catSlug falls back to the numeric id when name has no usable characters`() {
        assertEquals("5", catSlug("!!!", 5))
    }
}
