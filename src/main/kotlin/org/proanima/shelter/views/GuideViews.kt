package org.proanima.shelter.views

import kotlinx.html.FlowContent
import kotlinx.html.FlowOrPhrasingContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.aside
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.p
import kotlinx.html.ul
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.service.GuideBlock

// contentLang comes from GuideService/GuideRepository, not from the route's locale:
// until an sr translation of content/volunteer-guide.md exists, the real content language
// for /sr/guide is en, and lang must reflect that — see docs/decisions.md
fun HTML.guidePage(locale: AppLocale, blocks: List<GuideBlock>, contentLang: String, currentPath: String) {
    val messages = messagesFor(locale)
    pageLayout(
        locale,
        pageTitle = messages.t("nav.guide"),
        currentPath = currentPath,
        contentLang = contentLang,
        mainClass = "page page-wide"
    ) {
        div(classes = "guide") {
            blocks.forEach { block ->
                when (block) {
                    is GuideBlock.Heading -> when (block.level) {
                        1 -> {
                            h1 { +block.text }
                            tableOfContents(blocks, messages.t("guide.toc"))
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
                    is GuideBlock.Callout -> aside(classes = "guide-callout") { p { inlineText(block.text) } }
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

private val INLINE_LINK = Regex("""\[([^\]]+)]\(([^)\s]+)\)""")

// Renders plain text where [text](url) becomes a link; the rest is escaped by the html builder.
private fun FlowOrPhrasingContent.inlineText(text: String) {
    var position = 0
    INLINE_LINK.findAll(text).forEach { match ->
        +text.substring(position, match.range.first)
        a(href = match.groupValues[2]) { +match.groupValues[1] }
        position = match.range.last + 1
    }
    +text.substring(position)
}
