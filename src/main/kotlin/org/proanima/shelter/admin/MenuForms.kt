package org.proanima.shelter.admin

import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.model.MenuNode
import org.proanima.shelter.model.MenuTree
import org.proanima.shelter.model.Section
import org.proanima.shelter.model.SystemPage
import org.proanima.shelter.model.sectionNodeId
import org.proanima.shelter.service.MAX_MENU_DEPTH
import org.proanima.shelter.service.node
import org.proanima.shelter.service.parentChoices
import org.proanima.shelter.service.depthOf

// The forms and the wording of the menu tree in the admin panel.

private const val MAX_GROUP_TITLE = 40
private const val MAX_GROUP_MENU_TITLE = 24

// How the admin panel names an entry of the menu: in Russian, whatever the language of the site is.
internal fun menuNodeLabel(node: MenuNode, sections: List<Section>): String = when {
    node.isPage -> SystemPage.entries.firstOrNull { it.nodeId == node.id }?.let { messagesFor(AppLocale.RU).t(it.labelKey) } ?: node.id
    node.isSection -> sections.firstOrNull { sectionNodeId(it.id) == node.id }?.let(::sectionTitle) ?: "Раздел"
    else -> node.title?.ru?.ifBlank { null } ?: node.title?.en?.ifBlank { null } ?: node.title?.sr?.ifBlank { null } ?: "Группа"
}

internal fun menuKindName(node: MenuNode): String = when {
    node.isPage -> "страница"
    node.isSection -> "раздел"
    else -> "группа"
}

// "Визиты › Архив визитов": where an entry stands, from the top level down.
internal fun MenuTree.pathLabel(id: String, sections: List<Section>): String {
    val names = ArrayDeque<String>()
    var current = node(id)
    while (current != null) {
        names.addFirst(menuNodeLabel(current, sections))
        current = current.parent?.let { node(it) }
    }
    return names.joinToString(" › ")
}

// The options of a "where in the menu" select: the value is the id of the entry to stand under, empty for the top level.
internal fun placeOptions(tree: MenuTree, sections: List<Section>, choices: List<String?>): List<Pair<String, String>> =
    choices.map { id ->
        if (id == null) {
            "" to "Верхний уровень меню"
        } else {
            val node = tree.node(id)
            id to "Внутри «${node?.let { menuNodeLabel(it, sections) } ?: id}»" + if (node?.isGroup == true) " (группа)" else ""
        }
    }

// The places a section can be put in the menu, for the "where in the menu" select of its form: the top level, and
// every entry it may be put under. A section that is not in the tree yet is new.
internal class SectionPlace(private val tree: MenuTree, sections: List<Section>, sectionId: Int?) {

    // null is the top level.
    val choices: List<String?> = if (sectionId == null) {
        listOf<String?>(null) + tree.nodes.filter { tree.depthOf(it.id) + 1 <= MAX_MENU_DEPTH }.map { it.id }
    } else {
        tree.parentChoices(sectionNodeId(sectionId))
    }

    val options: List<Pair<String, String>> = placeOptions(tree, sections, choices)

    // Where the section stands now; empty for the top level and for a section that is new.
    val current: String = sectionId?.let { tree.node(sectionNodeId(it))?.parent }.orEmpty()

    // What the form asked for: an entry's id, null for the top level.
    fun wanted(values: Map<String, String>): String? = values["parent"].orEmpty().trim().ifEmpty { null }

    // Whether the place is one of those offered. The form can be forged or out of date.
    fun offers(wanted: String?): Boolean = wanted in choices
}

internal const val PLACE_ERROR = "Выберите место из списка."

// The name of a group: three languages, the Russian one required, and a shorter one for the menu, which has little room.
object MenuGroupForm {

    fun valuesOf(group: MenuNode?): Map<String, String> =
        (localizedValues("title", group?.title) + localizedValues("menuTitle", group?.menuTitle)).toMap()

    fun validate(values: Map<String, String>): FormResult<Names> {
        val form = FormReader(values)
        val title = form.localized("title", MAX_GROUP_TITLE, requireRu = true)
        val menuTitle = form.localized("menuTitle", MAX_GROUP_MENU_TITLE, requireRu = false)
        if (form.errors.isNotEmpty() || title == null) {
            return FormResult(null, form.errors)
        }
        return FormResult(Names(title, menuTitle), emptyMap())
    }

    class Names(val title: LocalizedText, val menuTitle: LocalizedText?)
}
