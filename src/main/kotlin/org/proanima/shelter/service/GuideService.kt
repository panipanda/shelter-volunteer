package org.proanima.shelter.service

import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.repository.GuideRepository

data class GuidePageContent(val blocks: List<GuideBlock>, val lang: String)

class GuideService(private val repository: GuideRepository) {

    fun getGuideBlocks(locale: AppLocale): GuidePageContent {
        val content = repository.findGuideMarkdown(locale)
        return GuidePageContent(parseGuideMarkdown(content.markdown), content.lang)
    }
}
