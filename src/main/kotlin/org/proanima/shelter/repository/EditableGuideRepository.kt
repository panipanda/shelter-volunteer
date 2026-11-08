package org.proanima.shelter.repository

import org.proanima.shelter.model.AppLocale

// The key under which the edited text of the guide is kept, one text per language (the whole guide in the
// markdown that content/volunteer-guide.md uses).
const val GUIDE_MARKDOWN_KEY = "guide.markdown"

// The guide as edited in the admin panel, in the languages where it was edited; in the others, the guide that
// comes with the code.
class EditableGuideRepository(
    private val shipped: GuideRepository,
    private val content: SiteContentRepository
) : GuideRepository {

    override fun findGuideMarkdown(locale: AppLocale): GuideContent {
        val edited = content.load().text(GUIDE_MARKDOWN_KEY, locale.code)
        return if (edited != null) GuideContent(edited, locale.code) else shipped.findGuideMarkdown(locale)
    }
}
