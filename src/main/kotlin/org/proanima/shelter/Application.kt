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
import org.proanima.shelter.repository.JsonVisitRepository
import org.proanima.shelter.repository.MarkdownGuideRepository
import org.proanima.shelter.routes.catRoutes
import org.proanima.shelter.routes.healthRoutes
import org.proanima.shelter.routes.visitRoutes
import org.proanima.shelter.routes.guideRoutes
import org.proanima.shelter.routes.homeRoutes
import org.proanima.shelter.service.CatService
import org.proanima.shelter.service.GuideService
import org.proanima.shelter.service.VisitService
import java.io.File

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
    configureRoutes(
        catService = CatService(JsonCatRepository()),
        visitService = VisitService(JsonVisitRepository()),
        guideService = GuideService(MarkdownGuideRepository())
    )
}

fun Application.configureRoutes(
    catService: CatService,
    visitService: VisitService,
    guideService: GuideService
) {
    routing {
        get("/") {
            call.respondRedirect("/${AppLocale.default.code}")
        }

        healthRoutes()
        staticFiles("/images", File("data/images"))
        staticResources("/styles", "static/styles")
        staticResources("/scripts", "static/scripts")

        AppLocale.entries.forEach { locale ->
            route("/${locale.code}") {
                homeRoutes(catService, visitService, locale)
                catRoutes(catService, locale)
                visitRoutes(visitService, locale)
                guideRoutes(guideService, locale)
            }
        }
    }
}