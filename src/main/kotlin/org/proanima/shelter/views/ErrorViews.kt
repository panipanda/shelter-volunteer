package org.proanima.shelter.views

import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.h1
import kotlinx.html.p
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale

fun HTML.notFoundPage(locale: AppLocale) = errorPage(locale, "error.notFound")

fun HTML.serverErrorPage(locale: AppLocale) = errorPage(locale, "error.serverError")

// The page is rendered for any URL, including ones with no locale prefix, so the current path is
// always the locale root: the language switcher then links to the home pages instead of to broken URLs.
private fun HTML.errorPage(locale: AppLocale, keyPrefix: String) {
    val messages = messagesFor(locale)
    val title = messages.t("$keyPrefix.title")

    pageLayout(locale, pageTitle = title, currentPath = "/${locale.code}", indexable = false) {
        h1 { +title }
        p { +messages.t("$keyPrefix.text") }
        p { a(href = "/${locale.code}") { +messages.t("error.backHome") } }
    }
}
