package org.proanima.shelter

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import org.proanima.shelter.repository.JsonCatRepository
import org.proanima.shelter.repository.JsonVisitRepository
import org.proanima.shelter.routes.catRoutes
import org.proanima.shelter.routes.healthRoutes
import org.proanima.shelter.routes.visitRoutes
import org.proanima.shelter.routes.guideRoutes
import org.proanima.shelter.routes.homeRoutes
import org.proanima.shelter.service.CatService
import org.proanima.shelter.service.VisitService

fun main() {
    embeddedServer(
        factory = Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    configureRoutes(
        catService = CatService(JsonCatRepository()),
        visitService = VisitService(JsonVisitRepository())
    )
}

fun Application.configureRoutes(
    catService: CatService,
    visitService: VisitService
) {
    routing {
        homeRoutes()
        healthRoutes()
        catRoutes(catService)
        visitRoutes(visitService)
        guideRoutes()
        staticResources("/images", "static/images")
    }
}