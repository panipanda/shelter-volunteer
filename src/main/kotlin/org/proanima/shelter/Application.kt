package org.proanima.shelter

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import org.proanima.shelter.routes.healthRoutes
import org.proanima.shelter.routes.catRoutes

fun main() {
    embeddedServer(
        factory = Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    routing {
        healthRoutes()
        catRoutes()
    }
}