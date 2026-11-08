package org.proanima.shelter.model

import kotlinx.serialization.Serializable

// The menu of the site as a tree: which entry stands where. Only the places are kept here; what an entry says (the
// name of a section, the address of a page) stays with the thing itself. The addresses of the pages never depend on
// the tree: /ru/cats stays /ru/cats wherever the menu puts it.

// The pages the code owns. They cannot be removed, but the tree can move them and hide them from the menu.
enum class SystemPage(
    val key: String,
    // The address after the language: /ru/<path>.
    val path: String,
    // The message that names the entry in the menu.
    val labelKey: String,
    // A page below this one (a cat's card inside /cats) counts as being on the page.
    val matchChildren: Boolean
) {
    CATS("cats", "cats", "nav.cats", true),
    DOGS("dogs", "dogs", "nav.dogs", true),

    // The short name: five entries have to fit in one row on a 360 px phone. The page itself keeps the full title.
    GUIDE("guide", "guide", "nav.guide.short", false),
    VISITS("visits", "visits", "nav.visits", true),
    HELP("help", "help", "nav.help", false);

    val nodeId: String get() = "$PAGE_PREFIX$key"

    companion object {
        fun byKey(key: String): SystemPage? = entries.firstOrNull { it.key == key }
    }
}

const val PAGE_PREFIX = "page:"
const val SECTION_PREFIX = "section:"
const val GROUP_PREFIX = "group:"

fun sectionNodeId(sectionId: Int): String = "$SECTION_PREFIX$sectionId"

fun groupNodeId(number: Int): String = "${GROUP_PREFIX}g$number"

// One place in the tree. The id says what the entry is and never changes: "page:cats", "section:3", "group:g2".
@Serializable
data class MenuNode(
    val id: String,
    // The id of the entry this one stands under; null is the top level.
    val parent: String? = null,
    // Among the entries of the same parent, from the smallest up. Spaced by ten, so that a move does not renumber
    // every neighbour.
    val position: Int = 0,
    // Only a group has its own names: it is a folder of the menu with no page of its own.
    val title: LocalizedText? = null,
    // A shorter name for the menu, which has little room; the title is used when there is none.
    val menuTitle: LocalizedText? = null,
    // Hidden from the menu together with everything below it. The page itself stays where it is: it still opens at its
    // address and from the links of other pages.
    val hidden: Boolean = false
) {
    val isGroup: Boolean get() = id.startsWith(GROUP_PREFIX)

    val isSection: Boolean get() = id.startsWith(SECTION_PREFIX)

    val isPage: Boolean get() = id.startsWith(PAGE_PREFIX)

    fun menuLabel(locale: AppLocale): String =
        ((menuTitle?.takeIf { it.isFilledIn(locale) } ?: title) ?: LocalizedText()).forLocale(locale)
}

// The stored document. Old documents are read with the defaults, so a field added later breaks nothing.
@Serializable
data class MenuTree(
    val nodes: List<MenuNode> = emptyList(),
    // The number the next group's key is made from. Only goes up, so a deleted group's key is never reused.
    val nextGroupNumber: Int = 1
)
