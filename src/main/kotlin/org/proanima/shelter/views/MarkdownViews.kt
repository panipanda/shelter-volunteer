package org.proanima.shelter.views

import kotlinx.html.FlowContent
import kotlinx.html.FlowOrPhrasingContent
import kotlinx.html.a
import kotlinx.html.aside
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.strong
import kotlinx.html.ul
import org.proanima.shelter.service.GuideBlock
import org.proanima.shelter.service.isSafeGuideLink

// What the guide's markdown understands, drawn as HTML: paragraphs, lists, call-outs, and in the text [links](url)
// and **bold**. The guide page and the entries of the sections share it.
private val INLINE_MARK = Regex("""\*\*([^*]+)\*\*|\[([^\]]+)]\(([^)\s]+)\)""")

// Renders plain text where [text](url) becomes a link and **text** becomes bold; the rest is escaped by the html builder.
internal fun FlowOrPhrasingContent.inlineText(text: String) {
    var position = 0
    INLINE_MARK.findAll(text).forEach { match ->
        +text.substring(position, match.range.first)
        val bold = match.groups[1]?.value
        if (bold != null) {
            strong { +bold }
        } else {
            val href = match.groupValues[3]
            if (isSafeGuideLink(href)) {
                a(href = href) { +match.groupValues[2] }
            } else {
                +match.groupValues[2]
            }
        }
        position = match.range.last + 1
    }
    +text.substring(position)
}

// The body of an entry. The page's own title is the h1, so "#" and "##" both become h2 and "###" becomes h3.
internal fun FlowContent.markdownBody(blocks: List<GuideBlock>) {
    blocks.forEach { block ->
        when (block) {
            is GuideBlock.Heading -> if (block.level >= 3) {
                h3 {
                    block.id?.let { id = it }
                    +block.text
                }
            } else {
                h2 {
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
