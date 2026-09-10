package org.proanima.shelter.service

sealed interface GuideBlock {
    data class Heading(val level: Int, val text: String) : GuideBlock
    data class Paragraph(val text: String) : GuideBlock
    data class BulletList(val items: List<String>) : GuideBlock
}

// Минимальный парсер markdown под ровно то, что реально используется в volunteer-guide.md:
// заголовки # и ##, абзацы через пустую строку, списки через "- ". Полноценный markdown
// (жирный текст, ссылки, вложенные списки) не поддерживается — не нужен, гайд простой.
fun parseGuideMarkdown(markdown: String): List<GuideBlock> {
    val blocks = mutableListOf<GuideBlock>()
    val paragraphLines = mutableListOf<String>()
    val bulletItems = mutableListOf<String>()

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

    markdown.lines().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.isEmpty() -> {
                flushParagraph()
                flushBulletList()
            }
            line.startsWith("## ") -> {
                flushParagraph()
                flushBulletList()
                blocks += GuideBlock.Heading(level = 2, text = line.removePrefix("## ").trim())
            }
            line.startsWith("# ") -> {
                flushParagraph()
                flushBulletList()
                blocks += GuideBlock.Heading(level = 1, text = line.removePrefix("# ").trim())
            }
            line.startsWith("- ") -> {
                flushParagraph()
                bulletItems += line.removePrefix("- ").trim()
            }
            else -> {
                flushBulletList()
                paragraphLines += line
            }
        }
    }
    flushParagraph()
    flushBulletList()

    return blocks
}
