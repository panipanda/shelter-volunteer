package org.proanima.shelter.service

import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.Cat
import org.proanima.shelter.model.CatLocation
import org.proanima.shelter.model.Dog
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.repository.JsonCatRepository
import org.proanima.shelter.repository.JsonDogRepository
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataValidationTest {

    private val imagesDir: File = createTempDirectory("images").toFile().also {
        File(it, "ok.jpg").writeText("x")
        File(it, "ok-card.jpg").writeText("x")
        File(it, "plain.jpg").writeText("x")
        File(it, "placeholder.svg").writeText("x")
        it.deleteOnExit()
    }

    private fun cat(
        id: Int,
        name: String? = "Cat$id",
        birthYear: Int? = 2024,
        birthMonth: Int? = 5,
        photoUrls: List<String> = listOf("/images/ok.jpg")
    ) = Cat(
        id = id,
        name = name?.let { LocalizedText(en = it) },
        birthYear = birthYear,
        birthMonth = birthMonth,
        description = LocalizedText(en = "d"),
        photoUrls = photoUrls,
        location = CatLocation.IN_SHELTER,
        adoptionStage = AdoptionStage.NONE,
        createdAt = "2026-01-01T00:00:00+01:00",
        updatedAt = "2026-01-01T00:00:00+01:00"
    )

    private fun dog(id: Int, name: String = "Dog$id", photoUrls: List<String> = emptyList()) =
        Dog(id = id, name = LocalizedText(en = name), description = LocalizedText(en = "d"), photoUrls = photoUrls)

    @Test
    fun `valid data has no problems`() {
        assertEquals(emptyList(), validatePets(listOf(cat(1), cat(2, birthMonth = null)), listOf(dog(1)), imagesDir))
    }

    @Test
    fun `duplicate ids and slugs are reported`() {
        val problems = validatePets(listOf(cat(1), cat(1)), listOf(dog(1, "Rex"), dog(2, "rex")), imagesDir)

        assertTrue(problems.any { "duplicate id" in it && "cats.json" in it }, problems.toString())
        assertTrue(problems.any { "duplicate slug \"rex\"" in it && "dogs.json" in it }, problems.toString())
    }

    @Test
    fun `birth month outside 1 to 12 is reported`() {
        val problems = validatePets(listOf(cat(1, birthMonth = 13), cat(2, birthMonth = 0)), emptyList(), imagesDir)

        assertEquals(2, problems.size, problems.toString())
    }

    @Test
    fun `birth month without a year is reported`() {
        val problems = validatePets(listOf(cat(1, birthYear = null, birthMonth = 3)), emptyList(), imagesDir)

        assertTrue(problems.single().contains("no birthYear"))
    }

    @Test
    fun `missing photo files are reported`() {
        val problems = validatePets(
            listOf(cat(1, photoUrls = listOf("/images/missing.jpg"))),
            listOf(dog(1, photoUrls = listOf("https://example.com/a.jpg"))),
            imagesDir
        )

        assertTrue(problems.any { "missing.jpg" in it }, problems.toString())
        assertTrue(problems.any { "outside /images/" in it }, problems.toString())
    }

    @Test
    fun `a photo without a card copy is reported with the way to make one`() {
        val problem = validatePets(listOf(cat(1, photoUrls = listOf("/images/plain.jpg"))), emptyList(), imagesDir).single()

        assertTrue("plain-card.jpg" in problem && "tools/make-card-images.py" in problem, problem)
    }

    @Test
    fun `an svg photo needs no card copy`() {
        assertEquals(
            emptyList(),
            validatePets(listOf(cat(1, photoUrls = listOf("/images/placeholder.svg"))), emptyList(), imagesDir)
        )
    }

    @Test
    fun `the real content files are valid`() {
        val problems = validatePets(
            JsonCatRepository().findAll(),
            JsonDogRepository().findAll(),
            File("data/images")
        )

        assertEquals(emptyList(), problems)
    }
}
