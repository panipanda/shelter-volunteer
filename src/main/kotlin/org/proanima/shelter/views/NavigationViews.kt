package org.proanima.shelter.views

import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.header
import kotlinx.html.img
import kotlinx.html.nav
import kotlinx.html.span
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale

fun FlowContent.navigation(locale: AppLocale, currentPath: String) {
    val prefix = "/${locale.code}"
    val messages = messagesFor(locale)

    header(classes = "site-header") {
        div(classes = "wrap site-header-row") {
            // The logo text is hidden from screen readers: the link name is already given by the image alt.
            a(href = prefix, classes = "logo") {
                img(src = "/icons/logo.png", alt = messages.t("nav.home"), classes = "logo-image")
                span(classes = "logo-text") {
                    attributes["aria-hidden"] = "true"
                    +"Pro Anima"
                }
            }

            nav(classes = "main-nav") {
                navLink("$prefix/cats", messages.t("nav.cats"), currentPath, matchChildren = true)
                navLink("$prefix/guide", messages.t("nav.guide"), currentPath)
                navLink("$prefix/visits", messages.t("nav.visits"), currentPath)
                navLink("$prefix/visits/archive", messages.t("nav.archive"), currentPath)
            }

            localeSwitcher(locale, currentPath)
        }
    }
}

// /visits is a prefix of /visits/archive, so child paths count as "current" only where
// it makes sense (a cat card inside /cats), not for every link.
private fun FlowContent.navLink(href: String, label: String, currentPath: String, matchChildren: Boolean = false) {
    val isCurrent = currentPath == href || (matchChildren && currentPath.startsWith("$href/"))

    a(href = href) {
        if (isCurrent) {
            attributes["aria-current"] = "page"
        }
        +label
    }
}

// We change only the language prefix and keep the rest of the path as is (/ru/cats/1 -> /en/cats/1) —
// the path structure is the same in all locales, so stripping the current prefix is enough.
private fun FlowContent.localeSwitcher(locale: AppLocale, currentPath: String) {
    val pathSuffix = currentPath.removePrefix("/${locale.code}")

    nav(classes = "lang-switch") {
        AppLocale.entries.forEach { entryLocale ->
            if (entryLocale == locale) {
                span(classes = "lang-switch-current") { +entryLocale.code.uppercase() }
            } else {
                a(href = "/${entryLocale.code}$pathSuffix") { +entryLocale.code.uppercase() }
            }
        }
    }
}
