package org.proanima.shelter

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.repository.JsonCatRepository
import org.proanima.shelter.repository.JsonDogRepository
import org.proanima.shelter.repository.MarkdownGuideRepository
import org.proanima.shelter.routes.catRoutes
import org.proanima.shelter.routes.dogRoutes
import org.proanima.shelter.routes.healthRoutes
import org.proanima.shelter.routes.visitRoutes
import org.proanima.shelter.routes.guideRoutes
import org.proanima.shelter.routes.homeRoutes
import org.proanima.shelter.service.CatService
import org.proanima.shelter.service.DogService
import org.proanima.shelter.service.GuideService
import org.proanima.shelter.service.validatePets
import java.io.File
import java.time.Clock
import java.time.ZoneId

// The visit schedule follows Belgrade time, not the server's time zone.
private val SHELTER_ZONE = ZoneId.of("Europe/Belgrade")

private val IMAGES_DIR = File("data/images")

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(
        factory = Netty,
        port = port,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    val catRepository = JsonCatRepository()
    val dogRepository = JsonDogRepository()
    // Fail at startup, not on the first request to the broken page.
    val problems = validatePets(catRepository.findAll(), dogRepository.findAll(), IMAGES_DIR)
    check(problems.isEmpty()) { "Invalid content data:\n" + problems.joinToString("\n") { "  - $it" } }

    configureRoutes(
        catService = CatService(catRepository),
        guideService = GuideService(MarkdownGuideRepository()),
        dogService = DogService(dogRepository)
    )
}

fun Application.configureRoutes(
    catService: CatService,
    guideService: GuideService,
    clock: Clock = Clock.system(SHELTER_ZONE),
    dogService: DogService = DogService(JsonDogRepository())
) {
    routing {
        get("/") {
            call.respondRedirect("/${AppLocale.default.code}")
        }

        healthRoutes()
        staticFiles("/images", IMAGES_DIR)
        staticResources("/icons", "static/icons")
        staticResources("/fonts", "static/fonts")
        staticResources("/styles", "static/styles")
        staticResources("/scripts", "static/scripts")

        AppLocale.entries.forEach { locale ->
            route("/${locale.code}") {
                homeRoutes(catService, locale, clock)
                catRoutes(catService, locale)
                dogRoutes(dogService, locale)
                visitRoutes(locale)
                guideRoutes(guideService, locale)
            }
        }
    }
}