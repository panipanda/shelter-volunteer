package org.proanima.shelter.routes

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.proanima.shelter.configureRoutes
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.Cat
import org.proanima.shelter.model.Dog
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.module
import org.proanima.shelter.repository.CatRepository
import org.proanima.shelter.repository.DogRepository
import org.proanima.shelter.repository.GuideContent
import org.proanima.shelter.repository.GuideRepository
import org.proanima.shelter.service.CatService
import org.proanima.shelter.service.DogService
import org.proanima.shelter.service.GuideService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DogRoutesTest {

    @Test
    fun `GET dogs is the heading and the dog cards, like the list of cats`() = testApplication {
        application { module() }

        listOf("ru", "en", "sr").forEach { code ->
            val response = client.get("/$code/dogs")
            val body = response.plainText()
            val main = body.substringAfter("<main").substringBefore("</main>")

            assertEquals(HttpStatusCode.OK, response.status, code)
            assertEquals(1, Regex("<h1>").findAll(main).count(), code)
            assertEquals(0, Regex("<h2>|<section>|<p>|<ul>").findAll(main).count(), "$code: only the heading and the cards")
            assertFalse(body.contains("https://t.me/dashafirman"), "$code: the sign-up is on the home page")
            assertFalse(body.contains("https://linktr.ee/svora.volunteers"), "$code: donations live on the help page")
        }
    }

    @Test
    fun `a dog page is built like a cat page`() = testApplication {
        application { module() }

        val body = client.get("/ru/dogs/sava").plainText()

        assertTrue(hasTag(body, "main", """class="page cat-page""""))
        assertTrue(body.contains("""<div class="cat-head">"""))
        assertTrue(hasTag(body, "div", """class="cat-profile""""))
        assertEquals("Сава · Pro Anima", Regex("<title>\\s*(.*?)\\s*</title>").find(body)!!.groupValues[1])
    }

    @Test
    fun `home page mentions Svora and links to the dogs page`() = testApplication {
        application { module() }

        val body = client.get("/ru").plainText()

        assertTrue(body.contains("Волонтёрский центр Pro Anima · Svora.Volunteers"))
        assertTrue(body.contains("""href="/ru/dogs""""))
        assertTrue(body.contains("https://www.instagram.com/svora.volunteers/"))
    }

    @Test
    fun `dogs page shows dog cards with localized names and photos`() = testApplication {
        application { module() }

        val ru = client.get("/ru/dogs").plainText()
        assertTrue(ru.contains("Сава"))
        assertTrue(ru.contains("Мила"))
        assertTrue(ru.contains("/images/sava-card.jpg"))
        assertTrue(ru.contains("/images/mila-card.jpg"))

        val en = client.get("/en/dogs").plainText()
        assertTrue(en.contains("Sava"))
        assertTrue(en.contains("Mila"))
    }

    @Test
    fun `dog cards link to dog pages`() = testApplication {
        application { module() }

        val body = client.get("/ru/dogs").plainText()

        assertTrue(body.contains("""href="/ru/dogs/sava""""))
        assertTrue(body.contains("""href="/ru/dogs/mila""""))
    }

    @Test
    fun `GET dog page shows the dog in every locale`() = testApplication {
        application { module() }

        val ru = client.get("/ru/dogs/sava")
        val ruBody = ru.plainText()
        assertEquals(HttpStatusCode.OK, ru.status)
        assertTrue(ruBody.contains("<h1>Сава</h1>"))
        assertTrue(ruBody.contains("Около 3 лет"))
        assertTrue(ruBody.contains("Дружелюбный и ласковый пёс."))
        assertTrue(ruBody.contains("В приюте"))
        assertTrue(ruBody.contains("""href="/ru/dogs""""))
        assertTrue(ruBody.contains("/images/sava.jpg"))

        assertTrue(client.get("/en/dogs/mila").plainText().contains("A kind, people-oriented dog."))
        assertTrue(client.get("/sr/dogs/sava").plainText().contains("Prijateljski i umiljat pas."))
    }

    @Test
    fun `a dog without photos gets the dog placeholder, never the cat one`() = testApplication {
        val dogs = listOf(Dog(id = 1, name = LocalizedText(en = "Rex"), description = LocalizedText(en = "A dog")))
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
                    override fun findAll(): List<Dog> = dogs
                })
            )
        }

        listOf("/en/dogs", "/en/dogs/rex").forEach { path ->
            val body = client.get(path).plainText()
            assertTrue(body.contains("/images/default-dog.svg"), path)
            assertFalse(body.contains("default-cat"), path)
        }
    }

    @Test
    fun `GET unknown dog returns 404`() = testApplication {
        application { module() }

        assertEquals(HttpStatusCode.NotFound, client.get("/ru/dogs/nobody").status)
    }
}
