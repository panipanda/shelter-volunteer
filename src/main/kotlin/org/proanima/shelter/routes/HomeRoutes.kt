package org.proanima.shelter.routes

import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.title
import kotlinx.html.ul
import org.proanima.shelter.model.AppLocale

fun Route.homeRoutes(locale: AppLocale) {
    get {
        val prefix = "/${locale.code}"

        call.respondHtml {
            head {
                title { +"Pro Anima Volunteer Hub" }
            }
            body {
                h1 { +"Pro Anima Volunteer Hub" }

                p {
                    +"A simple information website for people helping with the cat area at Pro Anima shelter."
                }

                ul {
                    li {
                        a(href = "$prefix/cats") { +"Cat catalogue" }
                    }
                    li {
                        a(href = "$prefix/guide") { +"Volunteer guide" }
                    }
                    li {
                        a(href = "$prefix/visits") { +"Upcoming visits" }
                    }
                    li {
                        a(href = "$prefix/visits/archive") { +"Visit archive" }
                    }
                }
            }
        }
    }
}