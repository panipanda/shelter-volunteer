package org.proanima.shelter.service

sealed interface GuideBlock {
    data class Heading(val level: Int, val text: String, val id: String? = null) : GuideBlock
    data class Paragraph(val text: String) : GuideBlock
    data class BulletList(val items: List<String>) : GuideBlock
    data class Callout(val text: String) : GuideBlock
}

// A minimal markdown parser for exactly what volunteer-guide.md actually uses:
// headings #, ## and ### (optionally with an anchor: "## Title {#id}"), paragraphs separated by a blank line,
// lists via "- ", a callout via "> ". Inline [text](url) links stay as is in the text and are turned into
// <a> by the view. Other markdown (bold text, nested lists) is not supported — not needed, the guide is simple.
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

    fun flushAll() {
        flushParagraph()
        flushBulletList()
        flushCallout()
    }

    markdown.lines().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.isEmpty() -> flushAll()
            line.startsWith("### ") -> {
                flushAll()
                blocks += heading(3, line.removePrefix("### "))
            }
            line.startsWith("## ") -> {
                flushAll()
                blocks += heading(2, line.removePrefix("## "))
            }
            line.startsWith("# ") -> {
                flushAll()
                blocks += heading(1, line.removePrefix("# "))
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
    flushAll()

    return blocks
}

private val HEADING_ANCHOR = Regex("""\s*\{#([A-Za-z0-9_-]+)}\s*$""")

private fun heading(level: Int, rawText: String): GuideBlock.Heading {
    val anchor = HEADING_ANCHOR.find(rawText) ?: return GuideBlock.Heading(level, rawText.trim())
    return GuideBlock.Heading(level, rawText.removeRange(anchor.range).trim(), anchor.groupValues[1])
}
