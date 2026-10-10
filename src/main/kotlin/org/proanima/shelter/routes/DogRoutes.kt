package org.proanima.shelter.routes

import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.request.path
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.repository.DogRepository
import org.proanima.shelter.views.dogsPage

fun Route.dogRoutes(dogRepository: DogRepository, locale: AppLocale) {
    get("/dogs") {
        call.respondHtml {
            dogsPage(dogRepository.findAll(), locale, call.request.path())
        }
    }
}
