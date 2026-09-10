package org.proanima.shelter.service

import kotlin.test.Test
import kotlin.test.assertEquals

class GuideContentTest {

    @Test
    fun `parses heading levels`() {
        val blocks = parseGuideMarkdown(
            """
            # Title
            ## Section
            """.trimIndent()
        )

        assertEquals(
            listOf(
                GuideBlock.Heading(level = 1, text = "Title"),
                GuideBlock.Heading(level = 2, text = "Section")
            ),
            blocks
        )
    }

    @Test
    fun `joins multiline paragraph with a space`() {
        val blocks = parseGuideMarkdown(
            """
            First line
            second line

            New paragraph
            """.trimIndent()
        )

        assertEquals(
            listOf(
                GuideBlock.Paragraph("First line second line"),
                GuideBlock.Paragraph("New paragraph")
            ),
            blocks
        )
    }

    @Test
    fun `parses a bullet list`() {
        val blocks = parseGuideMarkdown(
            """
            - one
            - two
            - three
            """.trimIndent()
        )

        assertEquals(
            listOf(GuideBlock.BulletList(listOf("one", "two", "three"))),
            blocks
        )
    }

    @Test
    fun `heading breaks an in-progress bullet list and paragraph`() {
        val blocks = parseGuideMarkdown(
            """
            Intro text
            - item one
            ## Next section
            """.trimIndent()
        )

        assertEquals(
            listOf(
                GuideBlock.Paragraph("Intro text"),
                GuideBlock.BulletList(listOf("item one")),
                GuideBlock.Heading(level = 2, text = "Next section")
            ),
            blocks
        )
    }
}
