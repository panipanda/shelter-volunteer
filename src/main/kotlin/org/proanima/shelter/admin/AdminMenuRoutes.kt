package org.proanima.shelter.admin

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.GROUP_PREFIX
import org.proanima.shelter.model.MenuNode
import org.proanima.shelter.model.MenuTree
import org.proanima.shelter.model.groupNodeId
import org.proanima.shelter.model.sectionNodeId
import org.proanima.shelter.service.MenuService
import org.proanima.shelter.service.childrenOf
import org.proanima.shelter.service.movedUnder
import org.proanima.shelter.service.node
import org.proanima.shelter.service.settleMenuTree
import org.proanima.shelter.service.shifted
import org.proanima.shelter.service.without
import org.proanima.shelter.service.withGroupRenamed
import org.proanima.shelter.service.withHidden
import org.proanima.shelter.service.withNewGroup

// The tree as the site really has it now: what was saved, made safe against the pages and sections that exist.
internal fun AdminSetup.currentMenuTree(): MenuTree = settleMenuTree(menu.load(), sections.findSections())

// Saves the tree after a change. A site that never saved its menu and has not changed it keeps having no saved tree:
// its menu is the default one, and the first real change is what writes it down.
internal fun AdminSetup.saveMenuTree(before: MenuTree, after: MenuTree) {
    val stored = menu.load()
    if (stored == null && after == before) {
        return
    }
    if (after != stored) {
        menu.save(after)
    }
}

// The places a section may be put in the menu, for its form. null: a section that is being made.
internal fun AdminSetup.sectionPlace(sectionId: Int?): SectionPlace = SectionPlace(currentMenuTree(), sections.findSections(), sectionId)

// Puts a section where the form asked. A section that was just made stands at the end of the top level.
internal fun AdminSetup.placeSection(sectionId: Int, parent: String?) {
    val tree = currentMenuTree()
    val placed = tree.movedUnder(sectionNodeId(sectionId), parent) ?: return
    saveMenuTree(tree, placed)
}

// A section that is deleted leaves the tree, and what stood under it moves up. Nothing to do for a menu never saved.
internal fun AdminSetup.forgetSection(sectionId: Int) {
    if (menu.load() == null) {
        return
    }
    val tree = currentMenuTree()
    saveMenuTree(tree, tree.without(sectionNodeId(sectionId)))
}

private suspend fun ApplicationCall.refuse(csrf: String, text: String) {
    respondHtml(HttpStatusCode.UnprocessableEntity) {
        adminMessagePage("Так сделать нельзя", text, "/admin/menu", "← Меню сайта", csrf)
    }
}

private fun back(flash: String, nodeId: String? = null): String =
    "/admin/menu?msg=$flash" + (nodeId?.let { "#n-" + it.replace(Regex("[^A-Za-z0-9]+"), "-") } ?: "")

internal fun Route.menuAdminRoutes(admin: AdminSetup) {
    route("/menu") {
        get {
            admin.withSession(call) { session ->
                val tree = admin.currentMenuTree()
                val service = MenuService(admin.sections, admin.menu)
                val menus = AppLocale.entries.associateWith { service.build(it) }
                call.respondHtml { adminMenuPage(tree, admin.sections.findSections(), menus, session.csrf, call.request.queryParameters["msg"]) }
            }
        }

        // One step up or down among the neighbours.
        post("/move") {
            admin.withForm(call) { session, values ->
                val tree = admin.currentMenuTree()
                val id = values["node"].orEmpty()
                if (tree.node(id) == null) {
                    call.refuse(session.csrf, "Такого пункта в меню уже нет. Откройте страницу заново.")
                } else {
                    admin.saveMenuTree(tree, tree.shifted(id, up = values["direction"] == "up"))
                    call.seeOther(back("moved", id))
                }
            }
        }

        // To the end of the list under another entry, or to the top level.
        post("/parent") {
            admin.withForm(call) { session, values ->
                val tree = admin.currentMenuTree()
                val id = values["node"].orEmpty()
                val wanted = values["parent"].orEmpty().trim().ifEmpty { null }
                val moved = if (tree.node(id) == null) null else tree.movedUnder(id, wanted)
                if (moved == null) {
                    call.refuse(session.csrf, "Сюда этот пункт перенести нельзя: меню в два уровня, а пункт нельзя поместить внутрь самого себя или того, что в нём лежит.")
                } else {
                    admin.saveMenuTree(tree, moved)
                    call.seeOther(back("placed", id))
                }
            }
        }

        post("/visibility") {
            admin.withForm(call) { session, values ->
                val tree = admin.currentMenuTree()
                val id = values["node"].orEmpty()
                val hide = values["hidden"] == "true"
                if (tree.node(id) == null) {
                    call.refuse(session.csrf, "Такого пункта в меню уже нет. Откройте страницу заново.")
                } else {
                    admin.saveMenuTree(tree, tree.withHidden(id, hide))
                    call.seeOther(back(if (hide) "hidden" else "shown", id))
                }
            }
        }

        groupRoutes(admin)
    }
}

private suspend inline fun withGroup(admin: AdminSetup, call: ApplicationCall, block: (MenuTree, MenuNode) -> Unit) {
    val tree = admin.currentMenuTree()
    val group = tree.node(GROUP_PREFIX + call.parameters["key"].orEmpty())?.takeIf { it.isGroup }
    if (group == null) call.respond(HttpStatusCode.NotFound) else block(tree, group)
}

private fun Route.groupRoutes(admin: AdminSetup) {
    route("/groups") {
        get("/new") {
            admin.withSession(call) { session ->
                call.respondHtml { adminMenuGroupFormPage(null, MenuGroupForm.valuesOf(null), emptyMap(), session.csrf) }
            }
        }

        post {
            admin.withForm(call) { session, values ->
                val result = MenuGroupForm.validate(values)
                val names = result.value
                if (names == null || !result.ok) {
                    call.respondHtml(HttpStatusCode.UnprocessableEntity) { adminMenuGroupFormPage(null, values, result.errors, session.csrf) }
                } else {
                    val tree = admin.currentMenuTree()
                    admin.menu.save(tree.withNewGroup(names.title, names.menuTitle))
                    call.seeOther(back("group-created", groupNodeId(tree.nextGroupNumber)))
                }
            }
        }

        route("/{key}") {
            get {
                admin.withSession(call) { session ->
                    withGroup(admin, call) { _, group ->
                        call.respondHtml { adminMenuGroupFormPage(group, MenuGroupForm.valuesOf(group), emptyMap(), session.csrf) }
                    }
                }
            }

            post {
                admin.withForm(call) { session, values ->
                    withGroup(admin, call) { tree, group ->
                        val result = MenuGroupForm.validate(values)
                        val names = result.value
                        if (names == null || !result.ok) {
                            call.respondHtml(HttpStatusCode.UnprocessableEntity) { adminMenuGroupFormPage(group, values, result.errors, session.csrf) }
                        } else {
                            admin.saveMenuTree(tree, tree.withGroupRenamed(group.id, names.title, names.menuTitle))
                            call.seeOther(back("group-saved", group.id))
                        }
                    }
                }
            }

            get("/delete") {
                admin.withSession(call) { session ->
                    withGroup(admin, call) { tree, group ->
                        val inside = tree.childrenOf(group.id).map { menuNodeLabel(it, admin.sections.findSections()) }
                        call.respondHtml { adminMenuGroupDeletePage(group, inside, session.csrf) }
                    }
                }
            }

            post("/delete") {
                admin.withForm(call) { _, _ ->
                    withGroup(admin, call) { tree, group ->
                        admin.saveMenuTree(tree, tree.without(group.id))
                        call.seeOther(back("group-deleted"))
                    }
                }
            }
        }
    }
}
