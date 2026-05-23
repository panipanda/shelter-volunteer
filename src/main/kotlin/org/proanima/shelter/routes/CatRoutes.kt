package org.proanima.shelter.routes

import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.proanima.shelter.service.CatService
import org.proanima.shelter.views.renderCatDetailsPage
import org.proanima.shelter.views.renderCatsListPage

fun Route.catRoutes(catService: CatService) {
    get("/cats") {
        val cats = catService.getAllCats()
        val html = renderCatsListPage(cats)

        call.respondText(html, contentType = ContentType.Text.Html)
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
            val html = renderCatDetailsPage(cat)
            call.respondText(html, contentType = ContentType.Text.Html)
        }
    }
}