package org.proanima.shelter.service

import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.Dog
import org.proanima.shelter.model.LocalizedText
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `displayCatAge with only year known says under a year when born this year`() {
        assertEquals("Under 1 year old", displayCatAge(2026, null, AppLocale.EN, today))
        assertEquals("Младше года", displayCatAge(2026, null, AppLocale.RU, today))
        assertEquals("Mlađa od godinu dana", displayCatAge(2026, null, AppLocale.SR, today))
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
    fun `petSlug lowercases the cat name`() {
        assertEquals("nami", petSlug("Nami", 3))
    }

    @Test
    fun `petSlug replaces non-alphanumeric characters and trims edge hyphens`() {
        assertEquals("mama-cat", petSlug(" Mama Cat! ", 1))
    }

    @Test
    fun `petSlug falls back to the numeric id when name is missing`() {
        assertEquals("5", petSlug(null, 5))
    }

    @Test
    fun `petSlug falls back to the numeric id when name has no usable characters`() {
        assertEquals("5", petSlug("!!!", 5))
    }

    private fun dog(birthYear: Int? = null, birthMonth: Int? = null, age: LocalizedText? = null) = Dog(
        id = 1, name = LocalizedText(en = "Rex"), description = LocalizedText(en = "d"),
        birthYear = birthYear, birthMonth = birthMonth, age = age
    )

    @Test
    fun `a dog's age is counted from the birth year like a cat's`() {
        assertEquals("2 years old", displayDogAge(dog(2024), AppLocale.EN, today))
        assertEquals("8 months old", displayDogAge(dog(2026, 1), AppLocale.EN, today))
        assertEquals("2 года", displayDogAge(dog(2024), AppLocale.RU, today))
        assertEquals("2 godine", displayDogAge(dog(2024), AppLocale.SR, today))
    }

    @Test
    fun `a dog's age falls back to the words, and the birth year wins over them`() {
        val words = LocalizedText(ru = "Около 3 лет", en = "About 3 years")

        assertEquals("About 3 years", displayDogAge(dog(age = words), AppLocale.EN, today))
        assertEquals("Около 3 лет", displayDogAge(dog(age = words), AppLocale.RU, today))
        // The language that has no words takes another one, like every localized text.
        assertEquals("About 3 years", displayDogAge(dog(age = words), AppLocale.SR, today))
        assertEquals("5 лет", displayDogAge(dog(2021, age = words), AppLocale.RU, today))
    }

    @Test
    fun `a dog with no age known has none, not an invented one`() {
        assertNull(displayDogAge(dog(), AppLocale.RU, today))
        assertNull(displayDogAge(dog(age = LocalizedText(ru = "  ")), AppLocale.RU, today))
        assertNull(displayDogAge(dog(age = LocalizedText()), AppLocale.RU, today))
    }

    @Test
    fun `the adoption stages are worded for a dog, masculine in Serbian`() {
        assertEquals("Reserved", displayAdoptionStage(AdoptionStage.RESERVED, AppLocale.EN, forDog = true))
        assertEquals("Забронирована", displayAdoptionStage(AdoptionStage.RESERVED, AppLocale.RU, forDog = true))
        assertEquals("Rezervisan", displayAdoptionStage(AdoptionStage.RESERVED, AppLocale.SR, forDog = true))
        assertEquals("Udomljen", displayAdoptionStage(AdoptionStage.ADOPTED, AppLocale.SR, forDog = true))
        // The cats' wording is unchanged.
        assertEquals("Rezervisana", displayAdoptionStage(AdoptionStage.RESERVED, AppLocale.SR))
        assertEquals("Udomljena", displayAdoptionStage(AdoptionStage.ADOPTED, AppLocale.SR))
    }

    @Test
    fun `every stage has a dog wording in every language`() {
        AppLocale.entries.forEach { locale ->
            AdoptionStage.entries.forEach { stage ->
                val text = displayAdoptionStage(stage, locale, forDog = true)
                assertTrue(text.isNotBlank() && !text.startsWith("dog."), "$locale $stage: $text")
            }
        }
    }
}
