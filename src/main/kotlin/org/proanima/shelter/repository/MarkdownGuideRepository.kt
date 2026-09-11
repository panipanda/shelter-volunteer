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

        // sr перевода пока нет — намеренно падаем на English-версию без суффикса,
        // а не на выдуманный перевод; появится content/volunteer-guide.sr.md — подхватится сам
        return GuideContent(File("$basePath.md").readText(), AppLocale.EN.code)
    }
}
