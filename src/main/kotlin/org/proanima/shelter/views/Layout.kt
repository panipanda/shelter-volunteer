package org.proanima.shelter.views

import kotlinx.html.HTML
import kotlinx.html.MAIN
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.footer
import kotlinx.html.head
import kotlinx.html.lang
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.title
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale

const val TELEGRAM_CHANNEL_URL = "https://t.me/proanima_belgrade"

// Общий skeleton для всех страниц: <head>/nav/<main>/footer/CSS-link в одном месте,
// чтобы не повторять его в каждом Views-файле. contentLang отдельно от locale —
// нужен GuideViews, у которой контент ещё не переведён, а роут уже локализован.
// mainClass задаёт ширину контента: "page" — узкая читаемая колонка для внутренних
// страниц, "home" — без ограничений, главная сама кладёт секции в .wrap и растягивает
// цветную полосу на всю ширину.
fun HTML.pageLayout(
    locale: AppLocale,
    pageTitle: String,
    currentPath: String,
    contentLang: String = locale.code,
    mainClass: String = "page",
    content: MAIN.() -> Unit
) {
    val messages = messagesFor(locale)

    lang = contentLang
    head {
        meta(charset = "UTF-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        meta(name = "color-scheme", content = "light dark")
        title { +pageTitle }
        link(rel = "icon", href = "/icons/favicon-32.png", type = "image/png") {
            attributes["sizes"] = "32x32"
        }
        link(rel = "icon", href = "/icons/favicon-192.png", type = "image/png") {
            attributes["sizes"] = "192x192"
        }
        link(rel = "apple-touch-icon", href = "/icons/apple-touch-icon.png")
        link(rel = "preload", href = "/fonts/ubuntu-mono-400-latin.woff2", type = "font/woff2") {
            attributes["as"] = "font"
            attributes["crossorigin"] = "anonymous"
        }
        link(rel = "stylesheet", href = "/styles/main.css", type = "text/css")
    }
    body {
        navigation(locale, currentPath)
        main(classes = mainClass) {
            content()
        }
        footer {
            div(classes = "wrap") {
                p {
                    +"${messages.t("footer.text")} · "
                    a(href = TELEGRAM_CHANNEL_URL) { +messages.t("footer.telegram") }
                }
            }
        }
    }
}
