package org.proanima.shelter.repository

import org.proanima.shelter.model.AppLocale
import java.io.File

class MarkdownGuideRepository(
    private val basePath: String = "content/volunteer-guide"
) : GuideRepository {

    override fun findGuideMarkdown(locale: AppLocale): GuideContent {
        val localized = File("$basePath.${locale.code}.md")
        if (localized.exists()) {
            return GuideContent(localized.readText(), locale.code)
        }

        // If there is no translation for the locale, we deliberately fall back to the English version without a suffix,
        // rather than to an invented translation
        return GuideContent(File("$basePath.md").readText(), AppLocale.EN.code)
    }
}
