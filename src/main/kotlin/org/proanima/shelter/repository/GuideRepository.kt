package org.proanima.shelter.repository

import org.proanima.shelter.model.AppLocale

data class GuideContent(val markdown: String, val lang: String)

interface GuideRepository {
    fun findGuideMarkdown(locale: AppLocale): GuideContent
}
