package org.proanima.shelter.admin

import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.Cat
import org.proanima.shelter.model.CatLocation
import org.proanima.shelter.model.Dog
import org.proanima.shelter.model.LocalizedText
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminFormsTest {

    private val today = LocalDate.of(2026, 11, 8)
    private val now = "2026-11-08T11:00:00+01:00"

    private fun values(vararg changes: Pair<String, String>) = CatForm.newValues() + mapOf("description_ru" to "Добрая") + changes

    private fun validate(vararg changes: Pair<String, String>, existing: Cat? = null, others: List<Cat> = emptyList()) =
        CatForm.validate(values(*changes), existing, others, now, today)

    private fun cat(id: Int, nameEn: String?) = Cat(
        id = id, name = nameEn?.let { LocalizedText(en = it) }, description = LocalizedText(ru = "d"),
        location = CatLocation.IN_SHELTER, adoptionStage = AdoptionStage.NONE, createdAt = now, updatedAt = now
    )

    @Test
    fun `the minimum is a Russian description, and the cat is a draft`() {
        val result = validate()

        assertTrue(result.ok, result.errors.toString())
        val cat = result.value!!
        assertFalse(cat.published)
        assertNull(cat.name)
        assertNull(cat.birthYear)
        assertEquals(LocalizedText(ru = "Добрая"), cat.description)
    }

    @Test
    fun `without a Russian description the form is refused, and so is a description that is too long`() {
        assertEquals("Заполните русский текст.", validate("description_ru" to "  ").errors["description_ru"])
        assertEquals(
            "Слишком длинный текст: не больше 3000 знаков.",
            validate("description_en" to "x".repeat(3001)).errors["description_en"]
        )
        assertTrue(validate("description_en" to "x".repeat(3000)).ok)
    }

    @Test
    fun `an empty language is no text, not an empty string`() {
        val cat = validate("name_ru" to "Муся", "name_en" to "  ", "medicalStatus_sr" to "").value!!

        assertEquals(LocalizedText(ru = "Муся"), cat.name)
        assertNull(cat.medicalStatus)
    }

    @Test
    fun `birth year and month are checked against each other and against today`() {
        assertTrue(validate("birthYear" to "2025", "birthMonth" to "3").ok)
        assertTrue(validate("birthYear" to "2026", "birthMonth" to "11").ok)
        assertTrue(validate("birthYear" to "2025").ok)

        assertEquals("Дата рождения не может быть в будущем.", validate("birthYear" to "2026", "birthMonth" to "12").errors["birthMonth"])
        assertEquals("Месяц без года указать нельзя.", validate("birthMonth" to "3").errors["birthMonth"])
        assertTrue("birthYear" in validate("birthYear" to "2027").errors)
        assertTrue("birthYear" in validate("birthYear" to "1999").errors)
        assertTrue("birthYear" in validate("birthYear" to "две тысячи").errors)
        assertTrue("birthMonth" in validate("birthYear" to "2025", "birthMonth" to "0").errors)
        assertTrue("birthMonth" in validate("birthYear" to "2025", "birthMonth" to "13").errors)
    }

    @Test
    fun `a value that is not in the list is an error and not silently replaced`() {
        assertTrue("location" in validate("location" to "ON_THE_MOON").errors)
        assertTrue("adoptionStage" in validate("adoptionStage" to "").errors)
        assertTrue("published" in validate("published" to "maybe").errors)
    }

    @Test
    fun `the English name must make an address of its own`() {
        val others = listOf(cat(1, "Barsik"), cat(2, null))

        assertTrue("name_en" in validate("name_en" to "barsik", others = others).errors)
        assertTrue("name_en" in validate("name_en" to "Barsik!", others = others).errors)
        assertTrue(validate("name_en" to "Barsik 2", others = others).ok)
        // A cat is not in conflict with itself.
        assertTrue(validate("name_en" to "Barsik", existing = cat(1, "Barsik"), others = emptyList()).ok)
    }

    @Test
    fun `the photos and the creation time of an existing cat are kept`() {
        val existing = cat(5, "Tom").copy(photoUrls = listOf("/images/tom.jpg"), createdAt = "2025-01-01T00:00:00+01:00")

        val cat = validate("name_en" to "Tom", existing = existing).value!!

        assertEquals(5, cat.id)
        assertEquals(listOf("/images/tom.jpg"), cat.photoUrls)
        assertEquals("2025-01-01T00:00:00+01:00", cat.createdAt)
        assertEquals(now, cat.updatedAt)
    }

    @Test
    fun `values of a saved cat fill the form and read back as the same cat`() {
        val saved = cat(7, "Tom").copy(
            name = LocalizedText(ru = "Том", en = "Tom"), birthYear = 2024, birthMonth = 5,
            medicalStatus = LocalizedText(ru = "Здоров"), adoptionInstruction = LocalizedText(sr = "Javite se"),
            published = true, adoptionStage = AdoptionStage.RESERVED, location = CatLocation.AT_HOME
        )

        val back = CatForm.validate(CatForm.valuesOf(saved), saved, emptyList(), now, today).value!!

        assertEquals(saved.copy(description = LocalizedText(ru = "d"), updatedAt = now), back)
    }

    @Test
    fun `a dog needs a Russian name and description, an age is optional`() {
        fun dog(vararg changes: Pair<String, String>) =
            DogForm.validate(DogForm.newValues() + mapOf("name_ru" to "Рекс", "description_ru" to "Пёс") + changes, null, emptyList())

        assertTrue(dog().ok)
        assertTrue("name_ru" in dog("name_ru" to "").errors)
        assertTrue("description_ru" in dog("description_ru" to "").errors)
        assertEquals(LocalizedText(ru = "3 года"), dog("age_ru" to "3 года").value!!.age)
        assertNull(dog().value!!.age)
        assertFalse(dog().value!!.published)
    }

    @Test
    fun `two dogs cannot share an English name`() {
        val existing = Dog(id = 1, name = LocalizedText(ru = "Рекс", en = "Rex"), description = LocalizedText(ru = "d"))
        val form = DogForm.newValues() + mapOf("name_ru" to "Другой", "name_en" to "REX", "description_ru" to "d")

        assertTrue("name_en" in DogForm.validate(form, null, listOf(existing)).errors)
    }

    @Test
    fun `translation gaps are found only where there is a Russian text to translate`() {
        val full = LocalizedText(ru = "а", en = "a", sr = "a")

        assertEquals(emptyList(), translationGaps(listOf(full, null)))
        assertEquals(listOf("en", "sr"), translationGaps(listOf(full, LocalizedText(ru = "б"))))
        assertEquals(listOf("sr"), translationGaps(listOf(LocalizedText(ru = "б", en = "b"))))
        assertEquals(emptyList(), translationGaps(listOf(LocalizedText(en = "only English"))))
    }

    @Test
    fun `line breaks become a space and the text is trimmed`() {
        assertEquals("a b c", cleanText("  a\r\n\r\n b \n c  "))
        assertEquals("a  b", cleanText("a  b"))
    }
}
