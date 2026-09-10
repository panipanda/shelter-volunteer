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
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale

fun Route.homeRoutes(locale: AppLocale) {
    get {
        val prefix = "/${locale.code}"
        val messages = messagesFor(locale)

        call.respondHtml {
            lang = locale.code
            head {
                title { +messages.t("home.title") }
            }
            body {
                h1 { +messages.t("home.title") }

                p {
                    +messages.t("home.intro")
                }

                ul {
                    li {
                        a(href = "$prefix/cats") { +messages.t("home.link.cats") }
                    }
                    li {
                        a(href = "$prefix/guide") { +messages.t("home.link.guide") }
                    }
                    li {
                        a(href = "$prefix/visits") { +messages.t("home.link.visits") }
                    }
                    li {
                        a(href = "$prefix/visits/archive") { +messages.t("home.link.archive") }
                    }
                }
            }
        }
    }
}