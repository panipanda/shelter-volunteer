package org.proanima.shelter.views

import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.header
import kotlinx.html.id
import kotlinx.html.nav
import kotlinx.html.span
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.service.NavItem
import java.util.ResourceBundle

// localeHref: where the language links go; by default the same page of the public site in the other language.
fun FlowContent.navigation(locale: AppLocale, currentPath: String, localeHref: ((AppLocale) -> String)? = null) {
    val prefix = "/${locale.code}"
    val messages = messagesFor(locale)

    // More entries than fit one row beside the wordmark: the header is two rows (the wordmark and the controls, then
    // the menu) at every width, as it already is on a phone. The menu says so for all languages at once, so that the
    // header does not change height when the language is switched.
    val menu = SiteMenu.current(locale)
    header(classes = if (menu.twoRows) "site-header nav-two-rows" else "site-header") {
        div(classes = "wrap site-header-row") {
            // The wordmark is live text. The logo mark is not in the header yet; it will go before the text.
            a(href = prefix, classes = "logo") { +"Pro Anima" }

            nav(classes = "main-nav") {
                attributes["aria-label"] = messages.t("nav.main")
                menu.items.forEach { navItem(it, currentPath, messages) }
            }

            localeSwitcher(locale, localeHref ?: { "/${it.code}${currentPath.removePrefix("/${locale.code}")}" }, messages.t("nav.language"))
            themeToggle(messages.t("nav.theme"))
        }
    }
}

// An entry without anything below it is a plain link. An entry with a drop-down is a group:
//  - the label opens the drop-down when it has no page of its own (a button, so that the keyboard reaches it);
//  - with a page of its own the label stays a link, and a small button beside it opens the drop-down. That button is
//    hidden until the script that works it has run;
//  - the drop-down opens by hover and by focus without any script (see main.css), and by click with it (nav-menu.js).
// Only one level below is drawn: the menu does not go deeper (MAX_MENU_DEPTH).
private fun FlowContent.navItem(item: NavItem, currentPath: String, messages: ResourceBundle) {
    val href = item.href
    if (item.children.isEmpty()) {
        if (href != null) {
            navLink(href, item.label, currentPath, item.matchChildren)
        }
        return
    }

    val subId = "nav-sub-" + item.id.replace(Regex("[^A-Za-z0-9]+"), "-")
    val onBranch = item.children.any { isCurrentPage(it, currentPath) }
    div(classes = "nav-group") {
        if (href != null) {
            navLink(href, item.label, currentPath, item.matchChildren, onBranch)
            button(type = ButtonType.button, classes = "nav-toggle nav-toggle-only") {
                attributes["hidden"] = "hidden"
                attributes["aria-expanded"] = "false"
                attributes["aria-controls"] = subId
                attributes["aria-label"] = messages.t("nav.submenu", item.label)
                chevron()
            }
        } else {
            button(type = ButtonType.button, classes = if (onBranch) "nav-toggle nav-label nav-branch" else "nav-toggle nav-label") {
                attributes["aria-expanded"] = "false"
                attributes["aria-controls"] = subId
                +item.label
                chevron()
            }
        }
        div(classes = "nav-sub") {
            id = subId
            item.children.forEach { child ->
                child.href?.let { navLink(it, child.label, currentPath, child.matchChildren) }
            }
        }
    }
}

private fun FlowContent.chevron() {
    span(classes = "nav-chevron") {
        attributes["aria-hidden"] = "true"
    }
}

private fun isCurrentPage(item: NavItem, currentPath: String): Boolean {
    val href = item.href ?: return false
    return currentPath == href || (item.matchChildren && currentPath.startsWith("$href/"))
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

// The current page is marked with aria-current. A parent whose drop-down holds the current page is only underlined
// (onBranch): the person is not on the parent's own page.
// A page below this one counts as "current" only where it makes sense (a cat's card inside /cats), not for every link.
private fun FlowContent.navLink(href: String, label: String, currentPath: String, matchChildren: Boolean = false, onBranch: Boolean = false) {
    val isCurrent = currentPath == href || (matchChildren && currentPath.startsWith("$href/"))

    a(href = href, classes = if (onBranch && !isCurrent) "nav-branch" else null) {
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
