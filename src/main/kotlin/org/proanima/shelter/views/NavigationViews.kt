package org.proanima.shelter.views

import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.img
import kotlinx.html.nav
import kotlinx.html.span
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale

fun FlowContent.navigation(locale: AppLocale, currentPath: String) {
    val prefix = "/${locale.code}"
    val messages = messagesFor(locale)

    nav {
        a(href = prefix, classes = "logo") {
            img(src = "/icons/logo.png", alt = messages.t("nav.home"), classes = "logo-image")
        }
        a(href = "$prefix/cats") { +messages.t("nav.cats") }
        +" | "
        a(href = "$prefix/guide") { +messages.t("nav.guide") }
        +" | "
        a(href = "$prefix/visits") { +messages.t("nav.visits") }
        +" | "
        a(href = "$prefix/visits/archive") { +messages.t("nav.archive") }
    }

    localeSwitcher(locale, currentPath)
}

// Меняем только языковой префикс, оставляя остальной путь как есть (/ru/cats/1 -> /en/cats/1) —
// структура путей одинаковая во всех локалях, так что достаточно снять текущий префикс.
private fun FlowContent.localeSwitcher(locale: AppLocale, currentPath: String) {
    val pathSuffix = currentPath.removePrefix("/${locale.code}")

    nav(classes = "lang-switch") {
        AppLocale.entries.forEachIndexed { index, entryLocale ->
            if (index > 0) {
                +" · "
            }
            if (entryLocale == locale) {
                span(classes = "lang-switch-current") { +entryLocale.code.uppercase() }
            } else {
                a(href = "/${entryLocale.code}$pathSuffix") { +entryLocale.code.uppercase() }
            }
        }
    }
}
