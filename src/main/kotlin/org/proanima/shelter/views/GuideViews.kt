package org.proanima.shelter.views

import kotlinx.html.HTML
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.service.GuideBlock

// contentLang приходит из GuideService/GuideRepository, а не из locale роута:
// пока sr-перевод content/volunteer-guide.md не существует, для /sr/guide реальный
// язык контента — en, и lang должен отражать это — см. docs/decisions.md
fun HTML.guidePage(locale: AppLocale, blocks: List<GuideBlock>, contentLang: String, currentPath: String) {
    val messages = messagesFor(locale)
    pageLayout(locale, pageTitle = messages.t("nav.guide"), currentPath = currentPath, contentLang = contentLang) {
        blocks.forEach { block ->
            when (block) {
                is GuideBlock.Heading -> if (block.level == 1) {
                    h1 { +block.text }
                } else {
                    h2 { +block.text }
                }
                is GuideBlock.Paragraph -> p { +block.text }
                is GuideBlock.BulletList -> ul {
                    block.items.forEach { item -> li { +item } }
                }
            }
        }
    }
}
