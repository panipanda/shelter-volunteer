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
            DogForm.validate(DogForm.newValues() + mapOf("name_ru" to "Рекс", "description_ru" to "Пёс") + changes, null, emptyList(), today)

        assertTrue(dog().ok)
        assertTrue("name_ru" in dog("name_ru" to "").errors)
        assertTrue("description_ru" in dog("description_ru" to "").errors)
        assertEquals(LocalizedText(ru = "3 года"), dog("age_ru" to "3 года").value!!.age)
        assertNull(dog().value!!.age)
        assertFalse(dog().value!!.published)
    }

    // ---- a dog has the same fields as a cat

    private fun dogForm(vararg changes: Pair<String, String>) =
        DogForm.newValues() + mapOf("name_ru" to "Рекс", "description_ru" to "Пёс") + changes

    @Test
    fun `a new dog form starts as a draft that is up for adoption, like a new cat`() {
        val dog = DogForm.newValues()
        val cat = CatForm.newValues()

        assertEquals("false", dog["published"])
        assertEquals("FOR_ADOPTION", dog["adoptionStage"])
        assertEquals(cat["adoptionStage"], dog["adoptionStage"])
        assertEquals(cat["location"], dog["location"])
        // Every field of a cat's form, except the cat-only name rule, is in a dog's.
        assertEquals(cat.keys, dog.keys - setOf("age_ru", "age_en", "age_sr"))
    }

    @Test
    fun `the adoption fields of a dog are read, trimmed and stored`() {
        val dog = DogForm.validate(
            dogForm(
                "birthYear" to " 2023 ", "birthMonth" to "4", "age_ru" to "Около 3 лет",
                "medicalStatus_ru" to "Привит,\n стерилизован", "medicalStatus_en" to "Vaccinated",
                "adoptionStage" to "RESERVED",
                "adoptionInstruction_ru" to "Пишите @Nata_Rogava", "adoptionInstruction_sr" to ""
            ),
            null, emptyList(), today
        ).value!!

        assertEquals(2023, dog.birthYear)
        assertEquals(4, dog.birthMonth)
        assertEquals(LocalizedText(ru = "Около 3 лет"), dog.age)
        assertEquals(LocalizedText(ru = "Привит, стерилизован", en = "Vaccinated"), dog.medicalStatus)
        assertEquals(AdoptionStage.RESERVED, dog.adoptionStage)
        assertEquals(LocalizedText(ru = "Пишите @Nata_Rogava"), dog.adoptionInstruction)
    }

    @Test
    fun `the new dog fields are optional, and empty ones are no text`() {
        val dog = DogForm.validate(dogForm("adoptionStage" to "NONE"), null, emptyList(), today).value!!

        assertNull(dog.birthYear)
        assertNull(dog.birthMonth)
        assertNull(dog.medicalStatus)
        assertNull(dog.adoptionInstruction)
        assertEquals(AdoptionStage.NONE, dog.adoptionStage)
    }

    @Test
    fun `a dog's birth date and stage are checked`() {
        fun errors(vararg changes: Pair<String, String>) = DogForm.validate(dogForm(*changes), null, emptyList(), today).errors

        assertTrue("birthYear" in errors("birthYear" to "1999"))
        assertTrue("birthYear" in errors("birthYear" to "2027"))
        assertTrue("birthYear" in errors("birthYear" to "три"))
        assertTrue("birthMonth" in errors("birthMonth" to "13", "birthYear" to "2024"))
        assertEquals("Месяц без года указать нельзя.", errors("birthMonth" to "3")["birthMonth"])
        assertEquals("Дата рождения не может быть в будущем.", errors("birthYear" to "2026", "birthMonth" to "12")["birthMonth"])
        assertTrue(errors("birthYear" to "2026", "birthMonth" to "11").isEmpty())
        assertTrue("adoptionStage" in errors("adoptionStage" to "SOLD"))
        assertTrue("adoptionStage" in errors("adoptionStage" to ""))
        assertEquals("Слишком длинный текст: не больше 1500 знаков.", errors("medicalStatus_ru" to "x".repeat(1501))["medicalStatus_ru"])
        assertEquals("Слишком длинный текст: не больше 1500 знаков.", errors("adoptionInstruction_en" to "x".repeat(1501))["adoptionInstruction_en"])
        assertTrue(errors("medicalStatus_ru" to "x".repeat(1500)).isEmpty())
    }

    @Test
    fun `a dog and a cat are refused for the same birth mistakes with the same messages`() {
        val mistakes = listOf(
            mapOf("birthYear" to "1999"), mapOf("birthYear" to "2027"), mapOf("birthYear" to "x"),
            mapOf("birthMonth" to "3"), mapOf("birthMonth" to "13", "birthYear" to "2024"),
            mapOf("birthYear" to "2026", "birthMonth" to "12")
        )

        mistakes.forEach { mistake ->
            val dog = DogForm.validate(dogForm() + mistake, null, emptyList(), today).errors.filterKeys { it.startsWith("birth") }
            val cat = CatForm.validate(values() + mistake, null, emptyList(), now, today).errors.filterKeys { it.startsWith("birth") }

            assertTrue(dog.isNotEmpty(), mistake.toString())
            assertEquals(cat, dog, mistake.toString())
        }
    }

    @Test
    fun `a saved dog fills the form and reads back as the same dog`() {
        val saved = Dog(
            id = 7, name = LocalizedText(ru = "Рекс", en = "Rex"), description = LocalizedText(ru = "Пёс"),
            birthYear = 2023, birthMonth = 5, age = LocalizedText(ru = "Около 3 лет"),
            medicalStatus = LocalizedText(ru = "Здоров"), adoptionInstruction = LocalizedText(sr = "Javite se"),
            adoptionStage = AdoptionStage.FOR_ADOPTION, location = CatLocation.AT_HOME, published = true,
            photoUrls = listOf("/images/rex.jpg")
        )

        val back = DogForm.validate(DogForm.valuesOf(saved), saved, emptyList(), today).value!!

        assertEquals(saved, back)
    }

    @Test
    fun `a dog saved before the new fields existed fills the form with empty values and keeps its age words`() {
        val old = Dog(id = 3, name = LocalizedText(ru = "Сава", en = "Sava"), description = LocalizedText(ru = "d"), age = LocalizedText(ru = "Около 3 лет"))

        val form = DogForm.valuesOf(old)

        assertEquals("", form["birthYear"])
        assertEquals("", form["medicalStatus_ru"])
        assertEquals("NONE", form["adoptionStage"])
        assertEquals("Около 3 лет", form["age_ru"])
        assertEquals(old, DogForm.validate(form, old, emptyList(), today).value)
    }

    @Test
    fun `translation gaps of a dog count its health and instruction too`() {
        val ru = LocalizedText(ru = "а")
        val full = LocalizedText(ru = "а", en = "a", sr = "a")

        assertEquals(emptyList(), translationGaps(listOf(full, full, full, null, null)))
        assertEquals(listOf("en", "sr"), translationGaps(listOf(full, full, ru, null)))
    }

    @Test
    fun `two dogs cannot share an English name`() {
        val existing = Dog(id = 1, name = LocalizedText(ru = "Рекс", en = "Rex"), description = LocalizedText(ru = "d"))
        val form = DogForm.newValues() + mapOf("name_ru" to "Другой", "name_en" to "REX", "description_ru" to "d")

        assertTrue("name_en" in DogForm.validate(form, null, listOf(existing), today).errors)
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
