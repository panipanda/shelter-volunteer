package org.proanima.shelter

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.http.content.staticResources
import org.proanima.shelter.routes.healthRoutes
import org.proanima.shelter.routes.catRoutes
import org.proanima.shelter.service.CatService
import org.proanima.shelter.repository.JsonCatRepository
import org.proanima.shelter.routes.visitRoutes
import org.proanima.shelter.service.VisitService
import org.proanima.shelter.repository.JsonVisitRepository

fun main() {
    embeddedServer(
        factory = Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    val catService = CatService(JsonCatRepository())
    val visitService = VisitService(JsonVisitRepository())

    routing {
        healthRoutes()
        catRoutes(catService)
        visitRoutes(visitService)
        staticResources("/images", "static/images")
    }
}