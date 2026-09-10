package org.proanima.shelter.service

import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.repository.GuideRepository

class GuideService(private val repository: GuideRepository) {

    fun getGuideBlocks(locale: AppLocale): List<GuideBlock> {
        return parseGuideMarkdown(repository.findGuideMarkdown(locale))
    }
}
