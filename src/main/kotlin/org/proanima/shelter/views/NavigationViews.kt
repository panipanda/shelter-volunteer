package org.proanima.shelter.views

import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.nav
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale

fun FlowContent.navigation(locale: AppLocale) {
    val prefix = "/${locale.code}"
    val messages = messagesFor(locale)

    nav {
        a(href = "$prefix/cats") { +messages.t("nav.cats") }
        +" | "
        a(href = "$prefix/guide") { +messages.t("nav.guide") }
        +" | "
        a(href = "$prefix/visits") { +messages.t("nav.visits") }
        +" | "
        a(href = "$prefix/visits/archive") { +messages.t("nav.archive") }
    }
}
