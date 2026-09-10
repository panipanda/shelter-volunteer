package org.proanima.shelter.routes

import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.service.CatService
import org.proanima.shelter.views.catDetailsPage
import org.proanima.shelter.views.catsListPage

fun Route.catRoutes(catService: CatService, locale: AppLocale) {
    get("/cats") {
        val cats = catService.getAllCats()

        call.respondHtml {
            catsListPage(cats, locale)
        }
    }

    get("/cats/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()

        if (id == null) {
            call.respondText("Invalid cat id")
            return@get
        }

        val cat = catService.getCatById(id)

        if (cat == null) {
            call.respondText("Cat not found")
        } else {
            call.respondHtml {
                catDetailsPage(cat, locale)
            }
        }
    }
}
