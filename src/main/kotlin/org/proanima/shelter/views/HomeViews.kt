package org.proanima.shelter.views

import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.li
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.title
import kotlinx.html.ul
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale

fun HTML.homePage(locale: AppLocale) {
    val prefix = "/${locale.code}"
    val messages = messagesFor(locale)

    lang = locale.code
    head {
        meta(charset = "UTF-8")
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
