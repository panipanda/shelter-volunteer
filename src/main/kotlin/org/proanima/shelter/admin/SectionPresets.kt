package org.proanima.shelter.admin

import org.proanima.shelter.model.ChoiceOption
import org.proanima.shelter.model.FieldType
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.model.Section
import org.proanima.shelter.model.SectionField
import org.proanima.shelter.model.SectionLayout

// What a new section can start from. A preset is only a filled-in set of the same settings, fields and tags that the
// admin panel lets anyone make by hand; after the section is made it is an ordinary one that can be changed in every
// part.
class SectionPreset(
    val id: String,
    val name: String,
    val description: String,
    val section: Section,
    // The entry of the menu the section is offered under (the archive of visits under "Visits"); null is the top level.
    val menuParent: String? = null
)

private fun text(ru: String, en: String, sr: String) = LocalizedText(ru = ru, en = en, sr = sr)

private fun option(key: String, ru: String, en: String, sr: String) = ChoiceOption(key, text(ru, en, sr))

object SectionPresets {

    val articles = SectionPreset(
        id = "articles",
        name = "Полезные статьи",
        description = "Статьи про уход за кошками и собаками: дата, краткое описание, текст с заголовками и списками, обложка и метки.",
        section = Section(
            id = 0,
            slug = "articles",
            title = text("Полезные статьи", "Useful articles", "Korisni članci"),
            menuTitle = text("Статьи", "Articles", "Članci"),
            intro = text(
                "Короткие статьи про уход за кошками и собаками.",
                "Short articles about caring for cats and dogs.",
                "Kratki članci o nezi mačaka i pasa."
            ),
            layout = SectionLayout.CARDS,
            useDate = true,
            usePhotos = true,
            tags = listOf(
                option("cats", "Кошки", "Cats", "Mačke"),
                option("dogs", "Собаки", "Dogs", "Psi"),
                option("health", "Здоровье", "Health", "Zdravlje"),
                option("care", "Уход", "Care", "Nega")
            ),
            fields = listOf(
                SectionField("f1", FieldType.LONGTEXT, text("Кратко", "Summary", "Ukratko"), required = true, inList = true, showLabel = false),
                SectionField("f2", FieldType.MARKDOWN, text("Текст", "Text", "Tekst"), required = true, showLabel = false)
            ),
            nextKeyNumber = 3
        )
    )

    val visits = SectionPreset(
        id = "visits",
        name = "Архив визитов",
        description = "Как прошли выезды: дата, статус (состоялся, отменён, дополнительный), итог, короткий отчёт и фотографии. В меню встаёт под «Визиты».",
        menuParent = "page:visits",
        section = Section(
            id = 0,
            slug = "archive",
            title = text("Архив визитов", "Visit archive", "Arhiva poseta"),
            menuTitle = text("Архив", "Archive", "Arhiva"),
            intro = text(
                "Как прошли наши выезды в приют: что сделали и кому помогли. Отменённые выезды тоже здесь: мы пишем как есть.",
                "How our trips to the shelter went: what we did and who we helped. Cancelled trips are here too: we tell it as it was.",
                "Kako su prošli naši izlasci u azil: šta smo radili i kome smo pomogli. Otkazani izlasci su takođe ovde: pišemo onako kako je bilo."
            ),
            layout = SectionLayout.LIST,
            useDate = true,
            usePhotos = true,
            fields = listOf(
                SectionField(
                    "f1", FieldType.CHOICE, text("Статус", "Status", "Status"), required = true, inList = true,
                    options = listOf(
                        option("held", "Состоялся", "Took place", "Održan"),
                        option("cancelled", "Отменён", "Cancelled", "Otkazan"),
                        option("extra", "Дополнительный выезд", "Extra trip", "Dodatni izlazak")
                    )
                ),
                SectionField("f2", FieldType.LONGTEXT, text("Итог", "Result", "Rezultat"), required = true, inList = true),
                SectionField("f3", FieldType.MARKDOWN, text("Короткий отчёт", "Short report", "Kratak izveštaj"))
            ),
            nextKeyNumber = 4
        )
    )

    val blank = SectionPreset(
        id = "blank",
        name = "Пустой раздел",
        description = "Всё придумываете сами: название, поля, метки. Поля добавляются после того, как раздел создан.",
        section = Section(id = 0, slug = "", title = LocalizedText(), layout = SectionLayout.CARDS, useDate = false, usePhotos = true)
    )

    val all = listOf(articles, visits, blank)

    fun byId(id: String?): SectionPreset? = all.firstOrNull { it.id == id }
}
