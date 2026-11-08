package org.proanima.shelter.views

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.aside
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.p
import kotlinx.html.ul
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.plain
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.service.GuideBlock
import java.util.ResourceBundle

// contentLang comes from GuideService/GuideRepository, not from the route's locale:
// until an sr translation of content/volunteer-guide.md exists, the real content language
// for /sr/guide is en, and lang must reflect that — see docs/decisions.md
fun HTML.guidePage(
    locale: AppLocale,
    blocks: List<GuideBlock>,
    contentLang: String,
    currentPath: String,
    messages: ResourceBundle = messagesFor(locale)
) {
    pageLayout(
        locale,
        pageTitle = messages.t("nav.guide"),
        currentPath = currentPath,
        contentLang = contentLang,
        messages = messages
    ) {
        div(classes = "guide") {
            blocks.forEach { block ->
                when (block) {
                    is GuideBlock.Heading -> when (block.level) {
                        1 -> {
                            h1 { +block.text }
                            tableOfContents(blocks, messages.t("guide.toc"))
                            img(src = messages.plain(SiteSettings.GUIDE_PHOTO), alt = messages.t("guide.photo.alt"), classes = "guide-photo")
                        }
                        2 -> h2 {
                            block.id?.let { id = it }
                            +block.text
                        }
                        else -> h3 {
                            block.id?.let { id = it }
                            +block.text
                        }
                    }
                    is GuideBlock.Paragraph -> p { inlineText(block.text) }
                    is GuideBlock.BulletList -> ul {
                        block.items.forEach { item -> li { inlineText(item) } }
                    }
                    is GuideBlock.Callout -> aside(classes = "callout") { p { inlineText(block.text) } }
                }
            }
        }
    }
}

// Links to the main parts of the guide (the level-2 headings that have an anchor).
private fun FlowContent.tableOfContents(blocks: List<GuideBlock>, label: String) {
    val parts = blocks.filterIsInstance<GuideBlock.Heading>().filter { it.level == 2 && it.id != null }
    if (parts.isEmpty()) {
        return
    }

    nav(classes = "guide-toc") {
        attributes["aria-label"] = label
        parts.forEach { part -> a(href = "#${part.id}") { +part.text } }
    }
}
