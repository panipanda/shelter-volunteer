package org.proanima.shelter.views

import kotlinx.html.HEAD
import kotlinx.html.HTML
import kotlinx.html.MAIN
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.footer
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.lang
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.title
import kotlinx.html.unsafe
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale

const val SITE_NAME = "Pro Anima"

// og:image, og:url and the canonical link must be absolute, so the public address is a setting: set
// PUBLIC_BASE_URL when the site gets its own domain (see docs/deploy.md).
val PUBLIC_BASE_URL: String =
    (System.getenv("PUBLIC_BASE_URL") ?: "https://shelter-volunteer.onrender.com").trimEnd('/')

// A link preview needs a raster picture, so pages without a photo of their own (and the SVG
// placeholders) share the home page photo.
private const val DEFAULT_SHARE_IMAGE = "/images/home-hero.jpg"

const val TELEGRAM_CHANNEL_URL = "https://t.me/proanima_belgrade"
const val DASHA_TELEGRAM = "dashafirman"
const val SVORA_INSTAGRAM_URL = "https://www.instagram.com/svora.volunteers/"
const val SVORA_LINKTREE_URL = "https://linktr.ee/svora.volunteers"

// Applies the saved theme, or the system one if nothing is saved, to <html data-theme>.
// Storage access is wrapped in try/catch: it throws when site data is blocked.
private const val THEME_INIT_SCRIPT = """
try {
    var saved = localStorage.getItem("theme");
    var dark = saved ? saved === "dark" : matchMedia("(prefers-color-scheme: dark)").matches;
    document.documentElement.dataset.theme = dark ? "dark" : "light";
} catch (e) {}
"""

// Common skeleton for all pages: <head>/nav/<main>/footer/CSS link in one place,
// so it is not repeated in every Views file. contentLang is separate from locale —
// it is needed by GuideViews, whose content is not yet translated while the route is already localized.
// mainClass: "page" is the 720 px text column used by every page; the parameter stays for a page that
// needs a different class on <main>.
// Fonts needed from the first paint: both families (Literata for headings, Source Sans 3 for the
// rest) in latin plus the subset for the locale's alphabet: cyrillic for ru, latin-ext (č, š, ž,
// đ, ć) for sr. Each is one variable file per subset. Without the preload the browser finds them
// only after parsing the CSS and re-renders the text on font-display: swap, shifting the layout
// on every language switch.
private fun preloadedFonts(locale: AppLocale): List<String> {
    val subsets = when (locale) {
        AppLocale.RU -> listOf("latin", "cyrillic")
        AppLocale.SR -> listOf("latin", "latin-ext")
        AppLocale.EN -> listOf("latin")
    }
    return listOf("literata-600", "source-sans-3").flatMap { family -> subsets.map { "$family-$it" } }
}

// Title, canonical address, language alternates and the link preview (Open Graph, which Telegram and
// Instagram read). indexable = false is for the error pages: they are rendered for any URL, so they
// have no address of their own to point at.
private fun HEAD.pageMetadata(
    locale: AppLocale,
    fullTitle: String,
    currentPath: String,
    description: String?,
    ogImage: String?,
    indexable: Boolean
) {
    title { +fullTitle }
    if (description != null) {
        meta(name = "description", content = description)
    }
    if (!indexable) {
        meta(name = "robots", content = "noindex")
        return
    }

    val pathSuffix = currentPath.removePrefix("/${locale.code}")
    val canonical = "$PUBLIC_BASE_URL/${locale.code}$pathSuffix"
    val image = (ogImage?.takeUnless { it.endsWith(".svg") } ?: DEFAULT_SHARE_IMAGE).let { "$PUBLIC_BASE_URL$it" }

    link(rel = "canonical", href = canonical)
    AppLocale.entries.forEach { alternate ->
        link(rel = "alternate", href = "$PUBLIC_BASE_URL/${alternate.code}$pathSuffix") {
            attributes["hreflang"] = alternate.code
        }
    }
    link(rel = "alternate", href = "$PUBLIC_BASE_URL/${AppLocale.default.code}$pathSuffix") {
        attributes["hreflang"] = "x-default"
    }

    listOfNotNull(
        "og:type" to "website",
        "og:site_name" to SITE_NAME,
        "og:title" to fullTitle,
        description?.let { "og:description" to it },
        "og:url" to canonical,
        "og:image" to image
    ).forEach { (property, value) ->
        meta(content = value) { attributes["property"] = property }
    }
    meta(name = "twitter:card", content = "summary_large_image")
}

fun HTML.pageLayout(
    locale: AppLocale,
    pageTitle: String,
    currentPath: String,
    contentLang: String = locale.code,
    mainClass: String = "page",
    description: String? = null,
    ogImage: String? = null,
    indexable: Boolean = true,
    content: MAIN.() -> Unit
) {
    val messages = messagesFor(locale)
    // The brand goes after the page name, so a row of open tabs says where each one is from.
    val fullTitle = if (SITE_NAME in pageTitle) pageTitle else "$pageTitle · $SITE_NAME"

    lang = contentLang
    head {
        meta(charset = "UTF-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        meta(name = "color-scheme", content = "light dark")
        pageMetadata(locale, fullTitle, currentPath, description, ogImage, indexable)
        link(rel = "icon", href = "/icons/favicon-32.png", type = "image/png") {
            attributes["sizes"] = "32x32"
        }
        link(rel = "icon", href = "/icons/favicon-192.png", type = "image/png") {
            attributes["sizes"] = "192x192"
        }
        link(rel = "apple-touch-icon", href = "/icons/apple-touch-icon.png")
        preloadedFonts(locale).forEach { font ->
            link(rel = "preload", href = "/fonts/$font.woff2", type = "font/woff2") {
                attributes["as"] = "font"
                attributes["crossorigin"] = "anonymous"
            }
        }
        link(rel = "stylesheet", href = "/styles/main.css", type = "text/css")
        // Inline and blocking on purpose: the theme must be set before the first paint,
        // otherwise a dark-theme user sees a light flash on every page load.
        script {
            unsafe { +THEME_INIT_SCRIPT }
        }
    }
    body {
        a(href = "#main", classes = "skip-link") { +messages.t("nav.skip") }
        navigation(locale, currentPath)
        main(classes = mainClass) {
            id = "main"
            content()
        }
        footer {
            div(classes = "wrap") {
                p {
                    +"${messages.t("footer.text")} · "
                    a(href = TELEGRAM_CHANNEL_URL) { +messages.t("footer.telegram") }
                    +" · "
                    a(href = SVORA_INSTAGRAM_URL) { +"Svora.Volunteers" }
                    +" · "
                    a(href = "/${locale.code}/help") { +messages.t("footer.help") }
                }
            }
        }
        script(src = "/scripts/theme-toggle.js") {
            attributes["defer"] = "defer"
        }
    }
}
