package org.proanima.shelter.service

import org.proanima.shelter.model.AppLocale
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
}
