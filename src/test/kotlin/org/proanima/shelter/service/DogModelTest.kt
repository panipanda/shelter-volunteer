package org.proanima.shelter.service

import kotlinx.serialization.json.Json
import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.CatLocation
import org.proanima.shelter.model.Dog
import org.proanima.shelter.model.LocalizedText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The dogs of data/dogs.json and the rows of the database were written before a dog had a stage, health or a birth
// year. They must read as they always did.
class DogModelTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a dog written before the new fields existed reads with empty values, an unchanged age and no stage`() {
        val old = """
            {"id": 1, "name": {"ru": "Сава", "en": "Sava"}, "age": {"ru": "Около 3 лет"},
             "description": {"ru": "Дружелюбный пёс."}, "photoUrls": ["/images/sava.jpg"]}
        """.trimIndent()

        val dog = json.decodeFromString<Dog>(old)

        assertNull(dog.birthYear)
        assertNull(dog.birthMonth)
        assertNull(dog.medicalStatus)
        assertNull(dog.adoptionInstruction)
        assertEquals(AdoptionStage.NONE, dog.adoptionStage)
        assertEquals(CatLocation.IN_SHELTER, dog.location)
        assertEquals(LocalizedText(ru = "Около 3 лет"), dog.age)
        assertTrue(dog.published)
    }

    @Test
    fun `a dog with all its fields survives writing and reading`() {
        val dog = Dog(
            id = 4, name = LocalizedText(ru = "Рекс", en = "Rex", sr = "Reks"), description = LocalizedText(ru = "Пёс"),
            birthYear = 2022, birthMonth = 9, age = LocalizedText(ru = "Около 4 лет"),
            medicalStatus = LocalizedText(ru = "Здоров", en = "Healthy"),
            adoptionStage = AdoptionStage.RESERVED, adoptionInstruction = LocalizedText(sr = "Javite se @Nata_Rogava"),
            location = CatLocation.IN_FOSTERHOME, published = false, photoUrls = listOf("/images/rex.jpg")
        )

        val back = json.decodeFromString<Dog>(Json { encodeDefaults = true }.encodeToString(Dog.serializer(), dog))

        assertEquals(dog, back)
    }
}
