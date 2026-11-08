package org.proanima.shelter.service

import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.ChoiceOption
import org.proanima.shelter.model.FieldType
import org.proanima.shelter.model.Section
import org.proanima.shelter.model.SectionField
import org.proanima.shelter.model.SectionItem
import org.proanima.shelter.repository.SectionRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.DateTimeParseException
import java.util.Locale

// What the public pages may show. An entry is shown in a language only when its title is written in that language,
// so a page is never half a language: the title is what a visitor reads first. A draft is never shown, and a section
// with nothing to show does not exist for visitors (no empty page; the menu leaves it out too, see MenuService).
class SectionService(private val repository: SectionRepository) {

    // The section at this address, if it has anything to show in this language.
    fun findSection(slug: String, locale: AppLocale): Section? =
        repository.findSections().firstOrNull { it.slug == slug }?.takeIf { publishedItems(it, locale).isNotEmpty() }

    // Newest first: by date where the section has dates, then by the order the entries were made.
    fun publishedItems(section: Section, locale: AppLocale): List<SectionItem> =
        repository.findItems()
            .filter { it.sectionId == section.id && it.isShownIn(locale) }
            .sortedWith(compareByDescending<SectionItem> { if (section.useDate) it.date.orEmpty() else "" }.thenByDescending { it.id })

    fun findItem(section: Section, slug: String, locale: AppLocale): SectionItem? =
        publishedItems(section, locale).firstOrNull { itemSlug(it) == slug }

    // The languages the section's page exists in.
    fun localesOf(section: Section): List<AppLocale> = AppLocale.entries.filter { publishedItems(section, it).isNotEmpty() }
}

fun SectionItem.isShownIn(locale: AppLocale): Boolean = published && title.isFilledIn(locale)

// The languages the entry's page exists in.
fun SectionItem.shownLocales(): List<AppLocale> = AppLocale.entries.filter { isShownIn(it) }

// The address of an entry is made from its English title, like a cat's from its name; the id is the way out for an
// entry that has no English title.
fun itemSlug(item: SectionItem): String = petSlug(item.title.en, item.id)

fun Section.menuLabel(locale: AppLocale): String =
    (menuTitle?.takeIf { it.isFilledIn(locale) } ?: title).forLocale(locale)

// The tags of the section that the entry carries, in the order the section lists them.
fun Section.tagsOf(item: SectionItem): List<ChoiceOption> = tags.filter { it.key in item.tags }

// A long date in the language of the page. For Serbian the Latin script, as everywhere on the site.
fun displayItemDate(iso: String?, locale: AppLocale): String? {
    val date = try {
        LocalDate.parse(iso ?: return null)
    } catch (e: DateTimeParseException) {
        return null
    }
    val javaLocale = Locale.forLanguageTag(if (locale == AppLocale.SR) "sr-Latn" else locale.code)
    return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(javaLocale))
}

// What a field says for one entry in one language, ready to print; null when the entry has nothing in it.
fun fieldDisplayValue(field: SectionField, item: SectionItem, locale: AppLocale): String? = when (field.type) {
    FieldType.TEXT, FieldType.LONGTEXT, FieldType.MARKDOWN ->
        item.texts[field.key]?.forLocale(locale)?.takeIf { it.isNotBlank() }
    FieldType.DATE -> displayItemDate(item.plain[field.key], locale)
    FieldType.CHOICE -> field.options.firstOrNull { it.key == item.plain[field.key] }?.name?.forLocale(locale)?.takeIf { it.isNotBlank() }
}

private const val LIST_TEXT_LIMIT = 220

// A long text on a card or in a row is cut at a word, so that the list stays a list.
fun shortened(text: String, limit: Int = LIST_TEXT_LIMIT): String {
    if (text.length <= limit) {
        return text
    }
    val cut = text.take(limit)
    return cut.substringBeforeLast(' ', cut).trimEnd(',', ';', ':', '-', '—', ' ') + "…"
}
