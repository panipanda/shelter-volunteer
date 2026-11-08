package org.proanima.shelter.routes

import io.ktor.client.request.get
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.proanima.shelter.configureRoutes
import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.Cat
import org.proanima.shelter.model.CatLocation
import org.proanima.shelter.model.Dog
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.repository.CatRepository
import org.proanima.shelter.repository.DogRepository
import org.proanima.shelter.repository.GuideContent
import org.proanima.shelter.repository.GuideRepository
import org.proanima.shelter.service.CatService
import org.proanima.shelter.service.DogService
import org.proanima.shelter.service.GuideService
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// A dog has the same facts as a cat (age, health, adoption stage, how to take it) and the page shows each of them
// when it is filled in, and nothing when it is not.
class DogProfileTest {

    private fun dog(id: Int = 1, name: String = "Rex", ruName: String = "Рекс", block: Dog.() -> Dog = { this }) = Dog(
        id = id,
        name = LocalizedText(ru = ruName, en = name, sr = name),
        description = LocalizedText(ru = "Весёлый пёс", en = "A cheerful dog", sr = "Veseo pas"),
        photoUrls = emptyList()
    ).block()

    private fun ApplicationTestBuilder.site(vararg dogs: Dog) {
        application {
            configureRoutes(
                catService = CatService(object : CatRepository {
                    override fun findAll(): List<Cat> = emptyList()
                    override fun findById(id: Int): Cat? = null
                }),
                guideService = GuideService(object : GuideRepository {
                    override fun findGuideMarkdown(locale: AppLocale): GuideContent = GuideContent("# Guide", locale.code)
                }),
                dogService = DogService(object : DogRepository {
                    override fun findAll(): List<Dog> = dogs.toList()
                })
            )
        }
    }

    private val thisYear = LocalDate.now().year

    // ---- the page of one dog

    @Test
    fun `a dog with nothing but a name and a description looks as it always did`() = testApplication {
        site(dog())

        val body = client.get("/ru/dogs/rex").plainText()
        val main = body.substringAfter("<main").substringBefore("</main>")

        assertFalse("status-line" in main)
        assertFalse("callout" in main)
        assertFalse("Здоровье" in main)
        assertFalse("Возраст" in main)
        assertFalse("t.me" in main)
        assertContains(main, "Где сейчас")
        assertContains(main, "В приюте")
    }

    @Test
    fun `everything that is filled in is on the page`() = testApplication {
        site(
            dog {
                copy(
                    birthYear = thisYear - 3,
                    medicalStatus = LocalizedText(ru = "Привит, стерилизован", en = "Vaccinated, neutered", sr = "Vakcinisan"),
                    adoptionStage = AdoptionStage.FOR_ADOPTION,
                    adoptionInstruction = LocalizedText(ru = "Пишите координатору: @Nata_Rogava", en = "Write to @Nata_Rogava")
                )
            }
        )

        val ru = client.get("/ru/dogs/rex").plainText()

        assertContains(ru, """<p class="status-line">""")
        assertContains(ru, "Ждёт свою семью")
        assertContains(ru, "3 года")
        assertContains(ru, "Здоровье")
        assertContains(ru, "Привит, стерилизован")
        assertContains(ru, """href="https://t.me/Nata_Rogava"""")
        assertContains(ru, "Написать координатору в Telegram")

        val en = client.get("/en/dogs/rex").plainText()
        assertContains(en, "Available for adoption")
        assertContains(en, "Vaccinated, neutered")
        assertContains(en, "Health")
    }

    @Test
    fun `a text that is missing in a language falls back like the other texts of the site`() = testApplication {
        site(dog { copy(medicalStatus = LocalizedText(ru = "Только по-русски")) })

        assertContains(client.get("/sr/dogs/rex").plainText(), "Только по-русски")
    }

    @Test
    fun `the instruction without a Telegram name is shown as text, and only while the dog waits for a family`() = testApplication {
        val text = LocalizedText(ru = "Приезжайте в приют в субботу", en = "Come on Saturday")
        site(
            dog(1, "Waiting", "Ждёт") { copy(adoptionStage = AdoptionStage.FOR_ADOPTION, adoptionInstruction = text) },
            dog(2, "Reserved", "Бронь") { copy(adoptionStage = AdoptionStage.RESERVED, adoptionInstruction = text) },
            dog(3, "Adopted", "Дома") { copy(adoptionStage = AdoptionStage.ADOPTED, adoptionInstruction = text) },
            dog(4, "Nothing", "Никто") { copy(adoptionStage = AdoptionStage.NONE, adoptionInstruction = text) }
        )

        val waiting = client.get("/ru/dogs/waiting").plainText()
        assertContains(waiting, """<aside class="callout">""")
        assertContains(waiting, "Приезжайте в приют в субботу")
        // The footer links to the shelter's channel on every page; the dog's own part has no contact.
        assertFalse("t.me" in waiting.substringAfter("<main").substringBefore("</main>"))
        listOf("reserved", "adopted", "nothing").forEach { slug ->
            assertFalse("Приезжайте в приют" in client.get("/ru/dogs/$slug").plainText(), slug)
        }
    }

    @Test
    fun `a dog waiting for a family without an instruction shows the status and no invented sentence`() = testApplication {
        site(dog { copy(adoptionStage = AdoptionStage.FOR_ADOPTION) })

        val main = client.get("/ru/dogs/rex").plainText().substringAfter("<main").substringBefore("</main>")

        assertContains(main, "Ждёт свою семью")
        assertFalse("callout" in main)
        assertFalse("Свяжитесь с координатором" in main)
        assertFalse("t.me" in main)
    }

    @Test
    fun `a blank text counts as not filled in`() = testApplication {
        site(
            dog {
                copy(
                    medicalStatus = LocalizedText(ru = "  "), age = LocalizedText(ru = ""),
                    adoptionStage = AdoptionStage.FOR_ADOPTION, adoptionInstruction = LocalizedText(ru = " ")
                )
            }
        )

        val main = client.get("/ru/dogs/rex").plainText().substringAfter("<main").substringBefore("</main>")

        assertFalse("Здоровье" in main)
        assertFalse("Возраст" in main)
        assertFalse("callout" in main)
    }

    @Test
    fun `the age comes from the birth year, else from the words, and the birth year wins`() = testApplication {
        site(
            dog(1, "Born", "Родился") { copy(birthYear = thisYear - 5, age = LocalizedText(ru = "Около 3 лет")) },
            dog(2, "Words", "Слова") { copy(age = LocalizedText(ru = "Около 3 лет", en = "About 3 years")) },
            dog(3, "Unknown", "Неизвестно")
        )

        val born = client.get("/ru/dogs/born").plainText()
        assertContains(born, "5 лет")
        assertFalse("Около 3 лет" in born)

        assertContains(client.get("/ru/dogs/words").plainText(), "Около 3 лет")
        assertContains(client.get("/en/dogs/words").plainText(), "About 3 years")
        assertFalse("Возраст" in client.get("/ru/dogs/unknown").plainText().substringAfter("<main"))
    }

    @Test
    fun `the status line is worded for a dog in every language, masculine in Serbian`() = testApplication {
        site(
            dog(1, "Reserved", "Бронь") { copy(adoptionStage = AdoptionStage.RESERVED) },
            dog(2, "Adopted", "Дома") { copy(adoptionStage = AdoptionStage.ADOPTED) }
        )

        assertContains(client.get("/ru/dogs/reserved").plainText(), "Забронирована")
        assertContains(client.get("/en/dogs/reserved").plainText(), "Reserved")
        assertContains(client.get("/sr/dogs/reserved").plainText(), "Rezervisan<")
        assertContains(client.get("/sr/dogs/adopted").plainText(), "Udomljen<")
        assertFalse("Udomljena" in client.get("/sr/dogs/adopted").plainText())
    }

    // ---- the list

    @Test
    fun `a reserved dog says so on its card, and the age is on the card`() = testApplication {
        site(
            dog(1, "Reserved", "Бронь") { copy(adoptionStage = AdoptionStage.RESERVED, birthYear = thisYear - 2) },
            dog(2, "Free", "Свободный") { copy(adoptionStage = AdoptionStage.FOR_ADOPTION) }
        )

        val main = client.get("/ru/dogs").plainText().substringAfter("<main").substringBefore("</main>")
        val cards = main.split("""<a href="/ru/dogs/""").drop(1)

        val reserved = cards.single { it.startsWith("reserved") }
        assertContains(reserved, "Забронирована")
        assertContains(reserved, "2 года")
        // A dog that waits is in the main list, which is the status: its card does not repeat it.
        assertFalse("Ждёт свою семью" in cards.single { it.startsWith("free") })
    }

    @Test
    fun `adopted dogs are in a section of their own at the bottom, with a heading`() = testApplication {
        site(
            dog(1, "Home", "Дома") { copy(adoptionStage = AdoptionStage.ADOPTED) },
            dog(2, "Waiting", "Ждёт") { copy(adoptionStage = AdoptionStage.FOR_ADOPTION) }
        )

        listOf("ru" to "Собаки, нашедшие дом", "en" to "Dogs who found a home", "sr" to "Psi koji su pronašli dom").forEach { (code, heading) ->
            val main = client.get("/$code/dogs").plainText().substringAfter("<main").substringBefore("</main>")
            val (top, bottom) = main.split("<h2>", limit = 2).let { it[0] to it[1] }

            assertContains(bottom, heading, message = code)
            assertContains(top, "/$code/dogs/waiting", message = code)
            assertFalse("/$code/dogs/home" in top, code)
            assertContains(bottom, "/$code/dogs/home", message = code)
            assertEquals(1, Regex("<h2>").findAll(main).count(), code)
        }
    }

    @Test
    fun `a draft stays off the page whatever else is filled in`() = testApplication {
        site(dog { copy(published = false, adoptionStage = AdoptionStage.FOR_ADOPTION) })

        assertEquals(404, client.get("/ru/dogs/rex").status.value)
        assertFalse("Рекс" in client.get("/ru/dogs").plainText())
    }

    @Test
    fun `a dog away from the shelter shows where on the card and the page`() = testApplication {
        site(dog { copy(location = CatLocation.IN_FOSTERHOME) })

        assertTrue("На передержке" in client.get("/ru/dogs").plainText())
        assertTrue("На передержке" in client.get("/ru/dogs/rex").plainText())
    }
}
