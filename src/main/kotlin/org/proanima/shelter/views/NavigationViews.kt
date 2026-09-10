package org.proanima.shelter.views

import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale

fun renderNavigation(locale: AppLocale): String {
    val prefix = "/${locale.code}"
    val messages = messagesFor(locale)

    return """
    <nav>
        <a href="$prefix/cats">${messages.t("nav.cats")}</a> |
        <a href="$prefix/guide">${messages.t("nav.guide")}</a> |
        <a href="$prefix/visits">${messages.t("nav.visits")}</a> |
        <a href="$prefix/visits/archive">${messages.t("nav.archive")}</a>
    </nav>
    """.trimIndent()
}
