package org.proanima.shelter.service

import org.proanima.shelter.model.GROUP_PREFIX
import org.proanima.shelter.model.MenuNode
import org.proanima.shelter.model.MenuTree
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.model.Section
import org.proanima.shelter.model.SystemPage
import org.proanima.shelter.model.groupNodeId
import org.proanima.shelter.model.sectionNodeId

// The menu tree as plain functions over plain data, so that every rule of it can be tested without a page or a
// database. Two kinds of function live here:
//  - settleMenuTree turns whatever was stored (nothing, an old document, a document from a backup) into a tree that is
//    safe to draw and to edit: every page and section is in it once, nothing points at a thing that is gone, and no
//    chain of parents is cyclic or deeper than the menu allows;
//  - the editing functions take a settled tree and give back a settled tree.

// The levels the menu draws: the top level and one drop-down below it. A drop-down inside a drop-down is bad on a
// touch screen and from the keyboard. The model keeps any depth, so this one number is all that holds the menu back.
const val MAX_MENU_DEPTH = 2

private const val STEP = 10

// A menu of more entries than this does not fit one row beside the wordmark below about 900 px (measured with six).
const val MAX_ONE_ROW_ITEMS = 5

// The tree the site really has. Nothing stored (a site that never saved a tree) gives the menu the site had before the
// tree existed: the five pages, then the sections in their own order.
fun settleMenuTree(stored: MenuTree?, sections: List<Section>): MenuTree {
    val sectionIds = sections.mapTo(HashSet()) { sectionNodeId(it.id) }

    // The first of two entries with one id wins; an entry whose page, section or name is gone is dropped.
    val known = LinkedHashMap<String, MenuNode>()
    stored?.nodes?.forEach { node ->
        if (node.id !in known && targetExists(node, sectionIds)) {
            known[node.id] = node
        }
    }

    // A parent that is gone, a cycle, or a chain deeper than the menu allows puts the entry on the top level.
    val placed = known.values.map { node ->
        val depth = depthInStored(node, known)
        if (depth == null || depth > MAX_MENU_DEPTH || (node.parent != null && node.parent !in known)) node.copy(parent = null) else node
    }.toMutableList()

    // What has no entry yet goes to the end of the top level: the pages in their own order, then the sections.
    var last = placed.filter { it.parent == null }.maxOfOrNull { it.position } ?: 0
    val have = placed.mapTo(HashSet()) { it.id }
    val missing = SystemPage.entries.map { it.nodeId } + sections.sortedWith(sectionOrder).map { sectionNodeId(it.id) }
    missing.filter { it !in have }.forEach { id ->
        last += STEP
        placed += MenuNode(id = id, position = last)
    }

    val highestGroup = placed.filter { it.isGroup }.maxOfOrNull { it.id.removePrefix(GROUP_PREFIX).removePrefix("g").toIntOrNull() ?: 0 } ?: 0
    return layOut(placed, maxOf(stored?.nextGroupNumber ?: 1, highestGroup + 1))
}

private val sectionOrder: Comparator<Section> = compareBy<Section>({ it.position }, { it.id })

private fun targetExists(node: MenuNode, sectionIds: Set<String>): Boolean = when {
    node.isPage -> SystemPage.entries.any { it.nodeId == node.id }
    node.isSection -> node.id in sectionIds
    node.isGroup -> node.title.hasAnyText()
    else -> false
}

private fun LocalizedText?.hasAnyText(): Boolean = this != null && listOf(ru, en, sr).any { !it.isNullOrBlank() }

// How many levels the entry sits on by the parents it was stored with; null for a chain that comes back to itself.
// A parent that is not in the document ends the chain: that entry is a top-level one.
private fun depthInStored(node: MenuNode, known: Map<String, MenuNode>): Int? {
    val seen = hashSetOf(node.id)
    var depth = 1
    var current = node
    while (true) {
        val parent = current.parent?.let { known[it] } ?: return depth
        if (!seen.add(parent.id)) return null
        depth++
        current = parent
    }
}

// Sorts the entries into the order they are drawn in (each one followed by what is below it) and spaces the positions
// of every set of neighbours by ten again.
private fun layOut(nodes: List<MenuNode>, nextGroupNumber: Int): MenuTree {
    val byParent = nodes.groupBy { it.parent }
    val ordered = ArrayList<MenuNode>(nodes.size)
    fun add(parent: String?) {
        byParent[parent].orEmpty().sortedWith(compareBy({ it.position }, { it.id })).forEachIndexed { index, node ->
            ordered += node.copy(position = (index + 1) * STEP)
            add(node.id)
        }
    }
    add(null)
    return MenuTree(ordered, nextGroupNumber)
}

fun MenuTree.node(id: String): MenuNode? = nodes.firstOrNull { it.id == id }

// The neighbours under one parent in the order they are drawn in; null is the top level.
fun MenuTree.childrenOf(parent: String?): List<MenuNode> =
    nodes.filter { it.parent == parent }.sortedWith(compareBy({ it.position }, { it.id }))

// The levels the entry sits on: 1 for the top level.
fun MenuTree.depthOf(id: String): Int {
    var depth = 1
    var current = node(id)
    while (current?.parent != null) {
        current = node(current.parent!!)
        depth++
    }
    return depth
}

// The levels of the entry and everything below it: 1 for an entry with nothing under it.
fun MenuTree.heightOf(id: String): Int = 1 + (childrenOf(id).maxOfOrNull { heightOf(it.id) } ?: 0)

fun MenuTree.descendantIds(id: String): Set<String> =
    childrenOf(id).flatMap { setOf(it.id) + descendantIds(it.id) }.toSet()

// Where the entry may be moved to: the top level (null) and every entry that would keep the tree within the depth
// the menu allows and would not put an entry under itself, in the order the tree is drawn in.
fun MenuTree.parentChoices(id: String): List<String?> {
    if (node(id) == null) {
        return emptyList()
    }
    val below = descendantIds(id)
    val height = heightOf(id)
    return listOf<String?>(null) + nodes.filter { it.id != id && it.id !in below && depthOf(it.id) + height <= MAX_MENU_DEPTH }.map { it.id }
}

// Swaps the entry with the neighbour above or below it. At the end of the line nothing moves.
fun MenuTree.shifted(id: String, up: Boolean): MenuTree {
    val current = node(id) ?: return this
    val neighbours = childrenOf(current.parent).map { it.id }.toMutableList()
    val index = neighbours.indexOf(id)
    val target = if (up) index - 1 else index + 1
    if (target !in neighbours.indices) {
        return this
    }
    neighbours[index] = neighbours[target].also { neighbours[target] = neighbours[index] }
    val position = neighbours.withIndex().associate { (i, key) -> key to (i + 1) * STEP }
    return layOut(nodes.map { n -> position[n.id]?.let { n.copy(position = it) } ?: n }, nextGroupNumber)
}

// Moves the entry to the end of the list under the new parent (null: the top level). Null when the move is not
// allowed: under itself, under what is below it, or deeper than the menu allows.
fun MenuTree.movedUnder(id: String, parent: String?): MenuTree? {
    val current = node(id) ?: return null
    if (parent == current.parent) {
        return this
    }
    if (parent !in parentChoices(id)) {
        return null
    }
    val last = childrenOf(parent).maxOfOrNull { it.position } ?: 0
    return layOut(nodes.map { if (it.id == id) it.copy(parent = parent, position = last + STEP) else it }, nextGroupNumber)
}

fun MenuTree.withHidden(id: String, hidden: Boolean): MenuTree =
    if (node(id) == null) this else MenuTree(nodes.map { if (it.id == id) it.copy(hidden = hidden) else it }, nextGroupNumber)

// A new group at the end of the top level.
fun MenuTree.withNewGroup(title: LocalizedText, menuTitle: LocalizedText?): MenuTree {
    val last = childrenOf(null).maxOfOrNull { it.position } ?: 0
    val group = MenuNode(id = groupNodeId(nextGroupNumber), position = last + STEP, title = title, menuTitle = menuTitle)
    return layOut(nodes + group, nextGroupNumber + 1)
}

fun MenuTree.withGroupRenamed(id: String, title: LocalizedText, menuTitle: LocalizedText?): MenuTree =
    if (node(id)?.isGroup != true) this else MenuTree(nodes.map { if (it.id == id) it.copy(title = title, menuTitle = menuTitle) else it }, nextGroupNumber)

// Takes a group or a section out of the tree. What stood under it moves up to its level, to the end, in the same order.
// A page cannot be taken out: it stays.
fun MenuTree.without(id: String): MenuTree {
    val gone = node(id)
    if (gone == null || gone.isPage) {
        return this
    }
    val last = childrenOf(gone.parent).filter { it.id != id }.maxOfOrNull { it.position } ?: 0
    val promoted = childrenOf(id).map { it.id }
    val rest = nodes.filter { it.id != id }.map { node ->
        val index = promoted.indexOf(node.id)
        if (index >= 0) node.copy(parent = gone.parent, position = last + (index + 1) * STEP) else node
    }
    return layOut(rest, nextGroupNumber)
}
