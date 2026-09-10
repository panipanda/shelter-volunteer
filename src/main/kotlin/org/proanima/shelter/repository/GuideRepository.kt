package org.proanima.shelter.repository

import org.proanima.shelter.model.AppLocale

interface GuideRepository {
    fun findGuideMarkdown(locale: AppLocale): String
}
