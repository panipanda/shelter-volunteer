package org.proanima.shelter.routes

import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.proanima.shelter.repository.JsonCatRepository
import org.proanima.shelter.service.CatService
import org.proanima.shelter.service.displayCatName

fun Route.catRoutes() {
    val catService = CatService(JsonCatRepository())

    get("/cats") {
        val cats = catService.getAllCats()

        val response = cats.joinToString(separator = "\n") { cat ->
            displayCatName(cat.name)
        }

        call.respondText(response)
    }
}