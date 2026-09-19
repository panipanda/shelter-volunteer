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
            // Текст логотипа скрыт от скринридеров: имя ссылки уже задаёт alt картинки.
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

// /visits — префикс /visits/archive, поэтому дочерние пути считаем «текущими» только там,
// где это осмысленно (карточка кошки внутри /cats), а не для всех ссылок подряд.
private fun FlowContent.navLink(href: String, label: String, currentPath: String, matchChildren: Boolean = false) {
    val isCurrent = currentPath == href || (matchChildren && currentPath.startsWith("$href/"))

    a(href = href) {
        if (isCurrent) {
            attributes["aria-current"] = "page"
        }
        +label
    }
}

// Меняем только языковой префикс, оставляя остальной путь как есть (/ru/cats/1 -> /en/cats/1) —
// структура путей одинаковая во всех локалях, так что достаточно снять текущий префикс.
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
