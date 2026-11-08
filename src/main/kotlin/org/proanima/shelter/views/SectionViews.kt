package org.proanima.shelter.views

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.ul
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.FieldType
import org.proanima.shelter.model.Section
import org.proanima.shelter.model.SectionField
import org.proanima.shelter.model.SectionItem
import org.proanima.shelter.model.SectionLayout
import org.proanima.shelter.service.displayItemDate
import org.proanima.shelter.service.fieldDisplayValue
import org.proanima.shelter.service.itemSlug
import org.proanima.shelter.service.parseGuideMarkdown
import org.proanima.shelter.service.shortened
import org.proanima.shelter.service.tagsOf
import java.util.ResourceBundle

// The pages of the sections made in the admin panel: the list of a section and the page of one entry. They look
// like the cats' pages (the same cards, the same gallery, the same facts list), so a new section needs no new look.

private val BLOCK_TYPES = setOf(FieldType.LONGTEXT, FieldType.MARKDOWN)

private fun Section.itemHref(locale: AppLocale, item: SectionItem) = "/${locale.code}/$slug/${itemSlug(item)}"

// shown: the section's entries that are visible in this language, all of them; the tag filter narrows them here.
fun HTML.sectionPage(
    section: Section,
    shown: List<SectionItem>,
    locale: AppLocale,
    currentPath: String,
    activeTag: String?,
    messages: ResourceBundle = messagesFor(locale),
    alternates: List<AppLocale> = AppLocale.entries,
    localeHref: ((AppLocale) -> String)? = null
) {
    val title = section.title.forLocale(locale)
    val intro = section.intro?.forLocale(locale)?.takeIf { it.isNotBlank() }
    // Only the tags that an entry in this language carries: a filter with nothing behind it is a dead end.
    val tagsInUse = section.tags.filter { tag -> shown.any { tag.key in it.tags } }
    val activeKey = activeTag?.takeIf { key -> tagsInUse.any { it.key == key } }
    val items = if (activeKey == null) shown else shown.filter { activeKey in it.tags }

    pageLayout(
        locale,
        pageTitle = title,
        currentPath = currentPath,
        description = intro,
        messages = messages,
        alternates = alternates,
        localeHref = localeHref
    ) {
        h1 { +title }
        if (intro != null) {
            p(classes = "list-note") { +intro }
        }
        if (tagsInUse.isNotEmpty()) {
            nav(classes = "tag-filter") {
                attributes["aria-label"] = messages.t("section.tags.label")
                tagLink("/${locale.code}/${section.slug}", messages.t("section.tags.all"), activeKey == null)
                tagsInUse.forEach { tag ->
                    tagLink("/${locale.code}/${section.slug}?tag=${tag.key}", tag.name.forLocale(locale), tag.key == activeKey)
                }
            }
        }
        when (section.layout) {
            SectionLayout.CARDS -> entryCards(section, items, locale)
            SectionLayout.LIST -> entryRows(section, items, locale)
        }
    }
}

private fun FlowContent.tagLink(href: String, label: String, current: Boolean) {
    a(href = href, classes = "tag") {
        if (current) {
            attributes["aria-current"] = "true"
        }
        +label
    }
}

// What the card or the row says besides the title: the date and the short values the section marked for the list,
// and, apart, the long texts marked for it.
private class ListedValues(val short: List<String>, val long: List<String>)

private fun listedValues(section: Section, item: SectionItem, locale: AppLocale): ListedValues {
    val short = mutableListOf<String>()
    if (section.useDate) {
        displayItemDate(item.date, locale)?.let { short += it }
    }
    val long = mutableListOf<String>()
    section.fields.filter { it.inList }.forEach { field ->
        val value = fieldDisplayValue(field, item, locale) ?: return@forEach
        when (field.type) {
            FieldType.LONGTEXT -> long += shortened(value)
            FieldType.MARKDOWN -> Unit
            else -> short += value
        }
    }
    return ListedValues(short, long)
}

private fun FlowContent.entryCards(section: Section, items: List<SectionItem>, locale: AppLocale) {
    div(classes = "cat-list") {
        items.forEachIndexed { index, item ->
            val title = item.title.forLocale(locale)
            val listed = listedValues(section, item, locale)
            a(href = section.itemHref(locale, item), classes = "cat-card") {
                if (section.usePhotos) {
                    item.photoUrls.firstOrNull()?.let { cardPhoto(it, title, index) }
                }
                span(classes = "cat-card-name") { +title }
                if (listed.short.isNotEmpty()) {
                    span(classes = "cat-card-meta") { +listed.short.joinToString(" · ") }
                }
                listed.long.forEach { span(classes = "cat-card-meta") { +it } }
            }
        }
    }
}

private fun FlowContent.entryRows(section: Section, items: List<SectionItem>, locale: AppLocale) {
    ul(classes = "entry-list") {
        items.forEach { item ->
            val listed = listedValues(section, item, locale)
            li(classes = "entry-row") {
                a(href = section.itemHref(locale, item), classes = "entry-title") { +item.title.forLocale(locale) }
                if (listed.short.isNotEmpty()) {
                    p(classes = "entry-meta") { +listed.short.joinToString(" · ") }
                }
                listed.long.forEach { p(classes = "entry-text") { +it } }
            }
        }
    }
}

// alternates and localeHref: an entry written in one language has no page in the others, so their language links
// lead to the home page and the page says which languages it has.
fun HTML.sectionItemPage(
    section: Section,
    item: SectionItem,
    locale: AppLocale,
    currentPath: String,
    preview: PagePreview? = null,
    messages: ResourceBundle = messagesFor(locale),
    alternates: List<AppLocale> = AppLocale.entries,
    localeHref: ((AppLocale) -> String)? = null
) {
    val title = item.title.forLocale(locale)
    val photoUrls = if (section.usePhotos) item.photoUrls else emptyList()
    val description = section.fields
        .filter { it.type == FieldType.LONGTEXT || it.type == FieldType.TEXT }
        .firstNotNullOfOrNull { fieldDisplayValue(it, item, locale) }
        ?.let { shortened(it, 200) }

    pageLayout(
        locale,
        pageTitle = title,
        currentPath = currentPath,
        mainClass = if (photoUrls.isEmpty()) "page" else "page cat-page",
        description = description,
        ogImage = photoUrls.firstOrNull(),
        preview = preview,
        messages = messages,
        alternates = alternates,
        localeHref = localeHref
    ) {
        p(classes = "back") { a(href = "/${locale.code}/${section.slug}") { +"← ${section.title.forLocale(locale)}" } }
        h1 { +title }
        entryMeta(section, item, locale)

        if (photoUrls.isEmpty()) {
            div(classes = "entry-body") { entryFields(section, item, locale) }
        } else {
            div(classes = "cat-profile") {
                photoGallery(photoUrls, title, messages)
                div(classes = "cat-info") { entryFields(section, item, locale) }
            }
        }
    }
}

// The date and the tags under the title. A tag leads to the section's list narrowed to it.
private fun FlowContent.entryMeta(section: Section, item: SectionItem, locale: AppLocale) {
    val date = if (section.useDate) displayItemDate(item.date, locale) else null
    val tags = section.tagsOf(item)
    if (date == null && tags.isEmpty()) {
        return
    }
    div(classes = "entry-head") {
        if (date != null) {
            span(classes = "entry-meta") { +date }
        }
        tags.forEach { tag ->
            a(href = "/${locale.code}/${section.slug}?tag=${tag.key}", classes = "tag") { +tag.name.forLocale(locale) }
        }
    }
}

// The fields in the order the section lists them. Short values (a line of text, a date, a choice) gather in one
// facts list with their labels; a long text stands as paragraphs, under its label as a heading unless the field
// says otherwise.
private fun FlowContent.entryFields(section: Section, item: SectionItem, locale: AppLocale) {
    val facts = mutableListOf<Pair<String, String>>()

    fun flushFacts() {
        if (facts.isEmpty()) return
        dl(classes = "facts-list") { facts.forEach { (label, value) -> fact(label, value) } }
        facts.clear()
    }

    section.fields.forEach { field ->
        val value = fieldDisplayValue(field, item, locale) ?: return@forEach
        if (field.type !in BLOCK_TYPES) {
            facts += field.label.forLocale(locale) to value
            return@forEach
        }
        flushFacts()
        blockLabel(field, locale)
        if (field.type == FieldType.MARKDOWN) {
            markdownBody(parseGuideMarkdown(value))
        } else {
            // A few sentences without formatting; a blank line starts a new paragraph.
            value.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }.forEach { p { +it } }
        }
    }
    flushFacts()
}

private fun FlowContent.blockLabel(field: SectionField, locale: AppLocale) {
    val label = field.label.forLocale(locale)
    if (field.showLabel && label.isNotBlank()) {
        h2 { +label }
    }
}
