package org.proanima.shelter.views

import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.header
import kotlinx.html.nav
import kotlinx.html.span
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import java.util.ResourceBundle

// localeHref: where the language links go; by default the same page of the public site in the other language.
fun FlowContent.navigation(locale: AppLocale, currentPath: String, localeHref: ((AppLocale) -> String)? = null) {
    val prefix = "/${locale.code}"
    val messages = messagesFor(locale)

    // Seven or more entries do not fit one row beside the wordmark: with sections in the menu the header is two rows
    // (the wordmark and the controls, then the menu) at every width, as it already is on a phone.
    val sectionEntries = SiteMenu.entries()
    header(classes = if (sectionEntries.isEmpty()) "site-header" else "site-header has-sections") {
        div(classes = "wrap site-header-row") {
            // The wordmark is live text. The logo mark is not in the header yet; it will go before the text.
            a(href = prefix, classes = "logo") { +"Pro Anima" }

            nav(classes = "main-nav") {
                attributes["aria-label"] = messages.t("nav.main")
                navLink("$prefix/cats", messages.t("nav.cats"), currentPath, matchChildren = true)
                navLink("$prefix/dogs", messages.t("nav.dogs"), currentPath, matchChildren = true)
                // The short name: five items have to fit in one row on a 360 px phone. The page itself keeps the full title.
                navLink("$prefix/guide", messages.t("nav.guide.short"), currentPath)
                navLink("$prefix/visits", messages.t("nav.visits"), currentPath, matchChildren = true)
                navLink("$prefix/help", messages.t("nav.help"), currentPath)
                // The sections made in the admin panel come after the fixed pages.
                sectionEntries.forEach { navLink(it.href, it.label, currentPath, matchChildren = true) }
            }

            localeSwitcher(locale, localeHref ?: { "/${it.code}${currentPath.removePrefix("/${locale.code}")}" }, messages.t("nav.language"))
            themeToggle(messages.t("nav.theme"))
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
// The visible text is the short code. The full name in its own language is the tooltip and the accessible
// name, and lang/hreflang tell a screen reader which voice to use for it.
private fun FlowContent.localeSwitcher(locale: AppLocale, hrefFor: (AppLocale) -> String, label: String) {
    nav(classes = "lang-switch") {
        attributes["aria-label"] = label
        AppLocale.entries.forEach { entryLocale ->
            if (entryLocale == locale) {
                span(classes = "lang-switch-current") {
                    attributes["aria-current"] = "true"
                    attributes["lang"] = entryLocale.code
                    attributes["title"] = entryLocale.nativeName
                    +entryLocale.code.uppercase()
                }
            } else {
                a(href = hrefFor(entryLocale)) {
                    attributes["lang"] = entryLocale.code
                    attributes["hreflang"] = entryLocale.code
                    attributes["title"] = entryLocale.nativeName
                    attributes["aria-label"] = entryLocale.nativeName
                    +entryLocale.code.uppercase()
                }
            }
        }
    }
}
