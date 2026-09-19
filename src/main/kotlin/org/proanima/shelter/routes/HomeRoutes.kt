package org.proanima.shelter.routes

import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.request.path
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.service.CatService
import org.proanima.shelter.service.VisitService
import org.proanima.shelter.views.homePage

private const val HOME_CATS_LIMIT = 3

fun Route.homeRoutes(catService: CatService, visitService: VisitService, locale: AppLocale) {
    get {
        val cats = catService.getAvailableCats().take(HOME_CATS_LIMIT)
        val nextVisit = visitService.getNextCatVisit()

        call.respondHtml {
            homePage(cats, nextVisit, locale, call.request.path())
        }
    }
}
