package org.proanima.shelter.model

import kotlinx.serialization.Serializable

// A section is a part of the site that the admin panel creates and fills: a list of entries with a page each
// (useful articles, the archive of visits). What an entry holds is decided by the section: its fields are made in
// the admin panel, so a new kind of section needs no code.

@Serializable
enum class FieldType {
    // One line of text, in three languages (a place, a name of someone).
    TEXT,

    // A few sentences without formatting, in three languages (a short result of a visit).
    LONGTEXT,

    // A text with headings, lists and links, in three languages (an article). The same marks as the guide.
    MARKDOWN,

    // A date, the same in every language.
    DATE,

    // One of the options listed for the field, named in three languages ("held", "cancelled").
    CHOICE
}

@Serializable
enum class SectionLayout {
    // Cards with a cover photo, like the cats.
    CARDS,

    // Plain rows: date, title and what the fields marked for the list say. For things that are read, not looked at.
    LIST
}

// An option of a choice field, or a tag of the section. The key never changes, whatever the name is changed to:
// the entries remember the key.
@Serializable
data class ChoiceOption(
    val key: String,
    val name: LocalizedText
)

@Serializable
data class SectionField(
    // Made when the field is added and never reused, so a deleted field's old values cannot come back under a new one.
    val key: String,
    val type: FieldType,
    val label: LocalizedText,
    val required: Boolean = false,
    // The value is shown on the card or in the row of the list, not only on the entry's own page.
    val inList: Boolean = false,
    // Whether the label stands over a long text as a heading. A short value always has its label next to it.
    val showLabel: Boolean = true,
    val options: List<ChoiceOption> = emptyList()
)

@Serializable
data class Section(
    val id: Int,
    // The address: /ru/<slug>. Changing it breaks the links people already have.
    val slug: String,
    val title: LocalizedText,
    // A shorter name for the menu, which has little room; the title is used when there is none.
    val menuTitle: LocalizedText? = null,
    val intro: LocalizedText? = null,
    val layout: SectionLayout = SectionLayout.CARDS,
    // The menu lists sections from the smallest number up, then in the order they were made.
    val position: Int = 0,
    // Entries have a date, newest first.
    val useDate: Boolean = false,
    val usePhotos: Boolean = true,
    // The tags an entry can be given. With none, the section has no tags.
    val tags: List<ChoiceOption> = emptyList(),
    val fields: List<SectionField> = emptyList(),
    // The number the next field and option keys are made from. Only goes up.
    val nextKeyNumber: Int = 1
)

@Serializable
data class SectionItem(
    val id: Int,
    val sectionId: Int,
    val title: LocalizedText,
    // ISO date (2026-11-08); a string so that the stored document stays plain.
    val date: String? = null,
    val photoUrls: List<String> = emptyList(),
    // Keys of the section's tags.
    val tags: List<String> = emptyList(),
    // The values of the fields by the field's key: texts in three languages, and plain values (a date, the key of
    // a chosen option) that are the same in every language.
    val texts: Map<String, LocalizedText> = emptyMap(),
    val plain: Map<String, String> = emptyMap(),
    // A draft is seen only in the admin panel.
    val published: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
