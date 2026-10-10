package org.proanima.shelter.views

import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.header
import kotlinx.html.img
import kotlinx.html.nav
import kotlinx.html.span
import kotlinx.html.summary
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import java.util.ResourceBundle

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
                navLink("$prefix/dogs", messages.t("nav.dogs"), currentPath)
                navLink("$prefix/guide", messages.t("nav.guide"), currentPath)
                visitsMenu(prefix, messages, currentPath)
            }

            localeSwitcher(locale, currentPath)
            themeToggle(messages.t("nav.theme"))
        }
    }
}

// Both visit pages are still stubs, so they live in a drop-down to keep the main row short.
// <details> works without JS; nav-menu.js only closes it on outside click or Escape.
private fun FlowContent.visitsMenu(prefix: String, messages: ResourceBundle, currentPath: String) {
    val upcoming = "$prefix/visits"
    val archive = "$prefix/visits/archive"

    details(classes = "nav-menu") {
        if (currentPath == upcoming || currentPath == archive) {
            attributes["data-current"] = "true"
        }
        summary { +messages.t("nav.visits.menu") }
        div(classes = "nav-menu-list") {
            navLink(upcoming, messages.t("nav.visits"), currentPath)
            navLink(archive, messages.t("nav.archive"), currentPath)
        }
    }
}

// Icon-only button; the tooltip (title) and the accessible name share one label.
// Hidden by CSS until the head script sets data-theme, so it never shows up without JS.
// The icons are drawn in CSS and swapped by the current theme.
private fun FlowContent.themeToggle(label: String) {
    button(type = ButtonType.button, classes = "theme-toggle") {
        attributes["title"] = label
        attributes["aria-label"] = label
        span(classes = "theme-icon") {
            attributes["aria-hidden"] = "true"
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
