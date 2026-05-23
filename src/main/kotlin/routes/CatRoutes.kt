package org.proanima.shelter.routes

import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.proanima.shelter.service.CatService
import org.proanima.shelter.service.displayCatName

fun Route.catRoutes(catService: CatService) {
    get("/cats") {
        val cats = catService.getAllCats()

        val response = cats.joinToString(separator = "\n") { cat ->
            displayCatName(cat.name)
        }

        call.respondText(response)
    }
}