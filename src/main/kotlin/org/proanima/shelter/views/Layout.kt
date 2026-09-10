package org.proanima.shelter.views

import kotlinx.html.HTML
import kotlinx.html.MAIN
import kotlinx.html.body
import kotlinx.html.footer
import kotlinx.html.head
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.title
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale

// Общий skeleton для всех страниц: <head>/nav/<main>/footer/CSS-link в одном месте,
// чтобы не повторять его в каждом Views-файле. contentLang отдельно от locale —
// нужен GuideViews, у которой контент ещё не переведён, а роут уже локализован.
fun HTML.pageLayout(
    locale: AppLocale,
    pageTitle: String,
    contentLang: String = locale.code,
    content: MAIN.() -> Unit
) {
    val messages = messagesFor(locale)

    lang = contentLang
    head {
        meta(charset = "UTF-8")
        title { +pageTitle }
        link(rel = "stylesheet", href = "/styles/main.css", type = "text/css")
    }
    body {
        navigation(locale)
        main {
            content()
        }
        footer {
            p { +messages.t("footer.text") }
        }
    }
}
