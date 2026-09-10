package org.proanima.shelter.repository

import org.proanima.shelter.model.AppLocale
import java.io.File

class MarkdownGuideRepository(
    private val basePath: String = "content/volunteer-guide"
) : GuideRepository {

    override fun findGuideMarkdown(locale: AppLocale): String {
        val localized = File("$basePath.${locale.code}.md")
        if (localized.exists()) {
            return localized.readText()
        }

        // ru/sr переводов пока нет — намеренно падаем на English-версию без суффикса,
        // а не на выдуманный перевод; появится content/volunteer-guide.ru.md — подхватится сам
        return File("$basePath.md").readText()
    }
}
