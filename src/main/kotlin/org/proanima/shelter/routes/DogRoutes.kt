package org.proanima.shelter.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.service.DogService
import org.proanima.shelter.views.dogDetailsPage
import org.proanima.shelter.views.dogsPage
import org.proanima.shelter.views.respondPage

fun Route.dogRoutes(dogService: DogService, locale: AppLocale) {
    get("/dogs") {
        call.respondPage(locale) {
            dogsPage(dogService.getAllDogs(), locale, call.request.path())
        }
    }

    get("/dogs/{slug}") {
        val dog = dogService.getDogBySlug(call.parameters["slug"]!!)

        if (dog == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respondPage(locale) {
                dogDetailsPage(dog, locale, call.request.path())
            }
        }
    }
}
