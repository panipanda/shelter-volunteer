package org.proanima.shelter.routes

import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.request.path
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.service.CatService
import org.proanima.shelter.service.nextScheduledVisit
import org.proanima.shelter.views.homePage
import java.time.Clock
import java.time.LocalDateTime

private const val HOME_CATS_LIMIT = 3

fun Route.homeRoutes(catService: CatService, locale: AppLocale, clock: Clock) {
    get {
        val cats = catService.getAvailableCats().take(HOME_CATS_LIMIT)
        val nextVisit = nextScheduledVisit(LocalDateTime.now(clock))

        call.respondHtml {
            homePage(cats, nextVisit, locale, call.request.path())
        }
    }
}
