package org.proanima.shelter.admin

import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.UL
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.summary
import kotlinx.html.ul
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.MenuNode
import org.proanima.shelter.model.MenuTree
import org.proanima.shelter.model.Section
import org.proanima.shelter.service.NavItem
import org.proanima.shelter.service.NavMenu
import org.proanima.shelter.service.childrenOf
import org.proanima.shelter.service.node
import org.proanima.shelter.service.parentChoices

// The admin page "Меню сайта": the tree of the menu, with what can be done to every entry. Plain forms that work
// without JavaScript, like the rest of the admin panel.

private val MENU_FLASH = mapOf(
    "moved" to "Порядок сохранён.",
    "placed" to "Пункт перенесён.",
    "hidden" to "Пункт скрыт из меню. Сама страница по-прежнему открывается по адресу и по ссылкам с других страниц.",
    "shown" to "Пункт снова в меню.",
    "group-created" to "Группа создана. Перенесите в неё нужные пункты.",
    "group-saved" to "Название группы сохранено.",
    "group-deleted" to "Группа удалена. Всё, что в ней было, встало на её место."
)

private val LANGUAGE_ORDER = listOf(AppLocale.RU, AppLocale.EN, AppLocale.SR)

private fun NavItem.allIds(): Set<String> = setOf(id) + children.flatMap { it.allIds() }

private fun domId(nodeId: String) = "n-" + nodeId.replace(Regex("[^A-Za-z0-9]+"), "-")

// The menu as one line of text: "Животные ▸ Кошки, Собаки · Гайд · Визиты ▸ Архив визитов".
private fun NavMenu.asText(): String =
    if (items.isEmpty()) "в меню ничего нет" else items.joinToString(" · ") { item ->
        if (item.children.isEmpty()) item.label else item.label + " ▸ " + item.children.joinToString(", ") { it.label }
    }

// menus: what visitors get in each language, to show what they will not see and why.
fun HTML.adminMenuPage(tree: MenuTree, sections: List<Section>, menus: Map<AppLocale, NavMenu>, csrf: String, flashCode: String?) =
    adminPage("Меню сайта", csrf, AdminSection.MENU) {
        h1 { +"Меню сайта" }
        flash(flashCode, MENU_FLASH)
        p(classes = "admin-note") {
            +"Здесь вы решаете, где в меню сайта стоят страницы, разделы и группы. Меню в два уровня: пункт верхнего уровня и выпадающий список под ним. "
            +"Адреса страниц от этого не меняются: «Кошки» остаются по адресу /ru/cats, в какой бы группе они ни стояли."
        }

        div(classes = "admin-section") {
            h2 { +"Как это видят посетители" }
            ul(classes = "menu-preview") {
                LANGUAGE_ORDER.forEach { language ->
                    li {
                        strong { +"${language.code.uppercase()}: " }
                        +(menus[language]?.asText() ?: "")
                    }
                }
            }
            val twoRows = menus.values.any { it.twoRows }
            p(classes = "admin-note") {
                +(if (twoRows) "В шапке сайта меню во втором ряду: в верхнем уровне больше пяти пунктов." else "В шапке сайта меню в одной строке с названием: в верхнем уровне не больше пяти пунктов.")
            }
        }

        div(classes = "admin-section") {
            div(classes = "admin-section-head") {
                h2 { +"Пункты меню" }
                a(href = "/admin/menu/groups/new", classes = "button button-secondary") { +"Добавить группу" }
            }
            p(classes = "admin-note") {
                +"Группа — папка без своей страницы, только выпадающий список. Скрытый пункт уходит из меню вместе со всем, что внутри него; "
                +"сама страница остаётся на сайте: её по-прежнему открывают по адресу и по ссылкам с главной и из подвала."
            }
            ul(classes = "admin-list") {
                menuRows(tree, null, 1, sections, menus, csrf)
            }
        }
    }

private fun UL.menuRows(
    tree: MenuTree,
    parent: String?,
    depth: Int,
    sections: List<Section>,
    menus: Map<AppLocale, NavMenu>,
    csrf: String
) {
    val siblings = tree.childrenOf(parent)
    siblings.forEachIndexed { index, node ->
        menuRow(tree, node, depth, index > 0, index < siblings.lastIndex, sections, menus, csrf)
        menuRows(tree, node.id, depth + 1, sections, menus, csrf)
    }
}

// Why a visitor does not see an entry, or in which languages.
private fun notSeen(tree: MenuTree, node: MenuNode, sections: List<Section>, menus: Map<AppLocale, NavMenu>): List<String> {
    if (node.hidden) {
        return listOf("скрыт из меню")
    }
    var ancestor = node.parent?.let { tree.node(it) }
    while (ancestor != null) {
        if (ancestor.hidden) {
            return listOf("скрыт вместе с «${menuNodeLabel(ancestor, sections)}»")
        }
        ancestor = ancestor.parent?.let { tree.node(it) }
    }
    val missing = LANGUAGE_ORDER.filter { language -> menus[language]?.items?.any { node.id in it.allIds() } != true }
    return when {
        missing.isEmpty() -> emptyList()
        missing.size == LANGUAGE_ORDER.size -> listOf(
            if (node.isSection) "посетители не видят: нет опубликованных записей" else "посетители не видят: в группе нечего показать"
        )
        else -> listOf("нет в меню на языках: " + missing.joinToString(", ") { it.code.uppercase() })
    }
}

private fun UL.menuRow(
    tree: MenuTree,
    node: MenuNode,
    depth: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    sections: List<Section>,
    menus: Map<AppLocale, NavMenu>,
    csrf: String
) {
    val label = menuNodeLabel(node, sections)
    val below = tree.childrenOf(node.id)
    li(classes = if (depth > 1) "admin-row menu-row menu-row-child" else "admin-row menu-row") {
        id = domId(node.id)
        div(classes = "admin-row-main") {
            span(classes = "admin-row-title") { +label }
            span(classes = "badge") { +menuKindName(node) }
            notSeen(tree, node, sections, menus).forEach { reason ->
                span(classes = if (node.hidden) "badge badge-draft" else "badge badge-gap") { +reason }
            }
        }
        if (below.isNotEmpty() && !node.hidden) {
            p(classes = "admin-row-meta") {
                +"Внутри пунктов: ${below.size}. Если скрыть этот пункт, они уйдут из меню вместе с ним."
            }
        }
        div(classes = "row-actions menu-actions") {
            if (canMoveUp) actionForm("/admin/menu/move", csrf, node.id, "direction" to "up", label = "↑ Выше", about = "Выше: $label")
            if (canMoveDown) actionForm("/admin/menu/move", csrf, node.id, "direction" to "down", label = "↓ Ниже", about = "Ниже: $label")
            actionForm(
                "/admin/menu/visibility", csrf, node.id, "hidden" to (!node.hidden).toString(),
                label = if (node.hidden) "Показать в меню" else "Скрыть из меню", about = (if (node.hidden) "Показать в меню: " else "Скрыть из меню: ") + label
            )
            if (node.isGroup) {
                a(href = "/admin/menu/groups/${node.id.removePrefix("group:")}", classes = "link-button") { +"Переименовать" }
                a(href = "/admin/menu/groups/${node.id.removePrefix("group:")}/delete", classes = "link-button photo-delete") { +"Удалить" }
            }
            if (node.isSection) {
                a(href = "/admin/sections/${node.id.removePrefix("section:")}/settings", classes = "link-button") { +"Настройки раздела" }
            }
            // Where the entry may go. Nothing to offer for an entry that can only stay where it is.
            val choices = tree.parentChoices(node.id)
            if (choices.size > 1 || choices.firstOrNull() != node.parent) {
                details(classes = "menu-move-details") {
                    summary { +"Перенести…" }
                    form(action = "/admin/menu/parent", method = FormMethod.post, classes = "menu-move") {
                        csrfField(csrf)
                        input(type = InputType.hidden, name = "node") { value = node.id }
                        val selectId = "to-${domId(node.id)}"
                        label { htmlFor = selectId; +"Перенести «$label» в: " }
                        select {
                            this.id = selectId
                            this.name = "parent"
                            placeOptions(tree, sections, choices).forEach { (optionValue, text) ->
                                option {
                                    this.value = optionValue
                                    selected = optionValue == node.parent.orEmpty()
                                    +text
                                }
                            }
                        }
                        button(type = ButtonType.submit, classes = "button button-secondary") { +"Перенести" }
                    }
                }
            }
        }
    }
}

private fun FlowContent.actionForm(action: String, csrf: String, nodeId: String, extra: Pair<String, String>, label: String, about: String) {
    form(action = action, method = FormMethod.post) {
        csrfField(csrf)
        input(type = InputType.hidden, name = "node") { value = nodeId }
        input(type = InputType.hidden, name = extra.first) { value = extra.second }
        button(type = ButtonType.submit, classes = "link-button") {
            attributes["aria-label"] = about
            +label
        }
    }
}

private val GROUP_FIELD_LABELS = mapOf("title" to "Название", "menuTitle" to "Короткое название для меню")

// A new group (existing == null) or the names of one that is there.
fun HTML.adminMenuGroupFormPage(existing: MenuNode?, values: Map<String, String>, errors: Map<String, String>, csrf: String) {
    val heading = if (existing == null) "Новая группа" else "Группа «${existing.title?.ru ?: existing.title?.en ?: existing.id}»"
    adminPage(heading, csrf, AdminSection.MENU) {
        p(classes = "admin-back") { a(href = "/admin/menu") { +"← Меню сайта" } }
        h1 { +heading }
        errorSummary(errors) { field -> groupFieldLabel(field) }
        p(classes = "admin-note") {
            +"Группа — это папка в меню: у неё нет своей страницы, только выпадающий список. После создания перенесите в неё нужные пункты на странице «Меню сайта»."
        }
        form(
            action = if (existing == null) "/admin/menu/groups" else "/admin/menu/groups/${existing.id.removePrefix("group:")}",
            method = FormMethod.post, classes = "admin-form"
        ) {
            csrfField(csrf)
            localizedField("Название", "title", values, errors, multiline = false, required = true, hint = "Например «Животные приюта».")
            localizedField(
                "Короткое название для меню", "menuTitle", values, errors, multiline = false,
                hint = "Меню тесное: если название длинное, напишите короче («Животные»). Можно не заполнять: возьмётся название."
            )
            div(classes = "form-actions") {
                button(type = ButtonType.submit, classes = "button") { +(if (existing == null) "Создать группу" else "Сохранить") }
                a(href = "/admin/menu") { +"Отмена" }
            }
        }
    }
}

private fun groupFieldLabel(field: String): String {
    val base = field.substringBeforeLast('_')
    val language = field.substringAfterLast('_', "").takeIf { it in CONTENT_LANGUAGES }
    val label = GROUP_FIELD_LABELS[if (language != null) base else field] ?: field
    return if (language == null) label else "$label (${LANGUAGE_NAMES.getValue(language)})"
}

fun HTML.adminMenuGroupDeletePage(group: MenuNode, insideLabels: List<String>, csrf: String) =
    adminPage("Удаление группы", csrf, AdminSection.MENU) {
        h1 { +"Удалить группу «${group.title?.ru ?: group.title?.en ?: group.id}»?" }
        p {
            +"Группа исчезнет из меню. "
            if (insideLabels.isEmpty()) {
                +"Внутри неё ничего нет."
            } else {
                +"То, что в ней лежит (${insideLabels.joinToString(", ") { "«$it»" }}), не удаляется: оно встанет на место группы, в конец меню."
            }
        }
        form(action = "/admin/menu/groups/${group.id.removePrefix("group:")}/delete", method = FormMethod.post, classes = "admin-form") {
            csrfField(csrf)
            div(classes = "form-actions") {
                button(type = ButtonType.submit, classes = "button") { +"Да, удалить группу" }
                a(href = "/admin/menu") { +"Нет, вернуться" }
            }
        }
    }
