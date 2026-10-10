package org.proanima.shelter.service

sealed interface GuideBlock {
    data class Heading(val level: Int, val text: String) : GuideBlock
    data class Paragraph(val text: String) : GuideBlock
    data class BulletList(val items: List<String>) : GuideBlock
    data class Callout(val text: String) : GuideBlock
}

// A minimal markdown parser for exactly what volunteer-guide.md actually uses:
// headings # and ##, paragraphs separated by a blank line, lists via "- ", a callout via "> ". Full markdown
// (bold text, links, nested lists) is not supported — not needed, the guide is simple.
fun parseGuideMarkdown(markdown: String): List<GuideBlock> {
    val blocks = mutableListOf<GuideBlock>()
    val paragraphLines = mutableListOf<String>()
    val bulletItems = mutableListOf<String>()
    val calloutLines = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphLines.isNotEmpty()) {
            blocks += GuideBlock.Paragraph(paragraphLines.joinToString(" "))
            paragraphLines.clear()
        }
    }

    fun flushBulletList() {
        if (bulletItems.isNotEmpty()) {
            blocks += GuideBlock.BulletList(bulletItems.toList())
            bulletItems.clear()
        }
    }

    fun flushCallout() {
        if (calloutLines.isNotEmpty()) {
            blocks += GuideBlock.Callout(calloutLines.joinToString(" "))
            calloutLines.clear()
        }
    }

    markdown.lines().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.isEmpty() -> {
                flushParagraph()
                flushBulletList()
                flushCallout()
            }
            line.startsWith("## ") -> {
                flushParagraph()
                flushBulletList()
                flushCallout()
                blocks += GuideBlock.Heading(level = 2, text = line.removePrefix("## ").trim())
            }
            line.startsWith("# ") -> {
                flushParagraph()
                flushBulletList()
                flushCallout()
                blocks += GuideBlock.Heading(level = 1, text = line.removePrefix("# ").trim())
            }
            line.startsWith("> ") -> {
                flushParagraph()
                flushBulletList()
                calloutLines += line.removePrefix("> ").trim()
            }
            line.startsWith("- ") -> {
                flushParagraph()
                flushCallout()
                bulletItems += line.removePrefix("- ").trim()
            }
            else -> {
                flushBulletList()
                flushCallout()
                paragraphLines += line
            }
        }
    }
    flushParagraph()
    flushBulletList()
    flushCallout()

    return blocks
}
