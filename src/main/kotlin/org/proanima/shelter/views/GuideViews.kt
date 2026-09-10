package org.proanima.shelter.views

import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.li
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.title
import kotlinx.html.ul
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.service.GuideBlock

// lang намеренно "en", а не locale.code: content/volunteer-guide.md пока существует
// только в английской версии (ru/sr ещё не переведены), lang должен отражать реальный
// язык контента, а не язык роута — см. docs/decisions.md
fun HTML.guidePage(locale: AppLocale, blocks: List<GuideBlock>) {
    lang = "en"
    head {
        meta(charset = "UTF-8")
        title { +"Volunteer guide" }
    }
    body {
        navigation(locale)

        main {
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
}
