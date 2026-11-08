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
import org.proanima.shelter.model.Section
import org.proanima.shelter.model.SectionField
import org.proanima.shelter.model.SectionItem
import org.proanima.shelter.service.itemSlug
import org.proanima.shelter.views.sectionItemPage

// A form with a long text in three languages is bigger than a cat's: a Cyrillic letter takes six bytes in a form.
private const val MAX_ITEM_FORM_BYTES = 900_000L

private fun itemKind(admin: AdminSetup, section: Section) = PetKind<SectionItem>(
    urlName = "sections/${section.id}/items",
    filePrefix = "item",
    id = SectionItem::id,
    title = ::itemTitle,
    slug = ::itemSlug,
    photoUrls = SectionItem::photoUrls,
    withPhotoUrls = { item, urls -> item.copy(photoUrls = urls, updatedAt = admin.nowText()) },
    published = SectionItem::published,
    // Only the entries of this section: an address with another section's entry in it is a 404.
    all = { admin.sections.findItems().filter { it.sectionId == section.id } },
    save = admin.sections::saveItem,
    delete = admin.sections::deleteItem,
    newValues = { ItemForm.newValues(section, admin.today()) },
    valuesOf = { item -> ItemForm.valuesOf(section, item) },
    validate = { values, existing, others -> ItemForm.validate(values, section, existing, others, admin.nowText()) },
    formPage = { page -> adminItemFormPage(section, page) },
    previewPage = { item, locale, path, preview ->
        sectionItemPage(section, item, locale, path, preview, messages = admin.content.load().bundle(locale))
    },
    deleteQuestion = { "Удалить запись «$it»?" },
    listHref = "/admin/sections/${section.id}",
    maxFormBytes = MAX_ITEM_FORM_BYTES
)

private fun AdminSetup.sectionOf(call: ApplicationCall): Section? {
    val wanted = call.parameters["sid"]?.toIntOrNull() ?: return null
    return sections.findSections().firstOrNull { it.id == wanted }
}

private suspend inline fun AdminSetup.withSection(call: ApplicationCall, block: (Section) -> Unit) {
    val section = sectionOf(call)
    if (section == null) call.respond(HttpStatusCode.NotFound) else block(section)
}

private suspend inline fun withField(call: ApplicationCall, section: Section, block: (SectionField) -> Unit) {
    val field = section.fields.firstOrNull { it.key == call.parameters["key"] }
    if (field == null) call.respond(HttpStatusCode.NotFound) else block(field)
}

internal fun Route.sectionAdminRoutes(admin: AdminSetup) {
    route("/sections") {
        get {
            admin.withSession(call) { session ->
                val items = admin.sections.findItems()
                val summaries = admin.sections.findSections().map { section ->
                    val own = items.filter { it.sectionId == section.id }
                    SectionSummary(section, own.size, own.count { it.published })
                }
                call.respondHtml { adminSectionsIndexPage(summaries, session.csrf, call.request.queryParameters["msg"]) }
            }
        }

        get("/new") {
            admin.withSession(call) { session ->
                val preset = SectionPresets.byId(call.request.queryParameters["preset"])
                if (preset == null) {
                    call.respondHtml { adminSectionPresetPage(session.csrf) }
                } else {
                    val values = SectionForm.newValues(preset.section, admin.sections.findSections())
                    call.respondHtml { adminSectionFormPage(FormPage(null, values, emptyMap(), session.csrf), preset.id) }
                }
            }
        }

        post {
            admin.withForm(call) { session, values ->
                val preset = SectionPresets.byId(values["preset"]) ?: SectionPresets.blank
                val result = SectionForm.validate(values, existing = null, template = preset.section, others = admin.sections.findSections())
                val section = result.value
                if (section == null || !result.ok) {
                    call.respondHtml(HttpStatusCode.UnprocessableEntity) {
                        adminSectionFormPage(FormPage(null, values, result.errors, session.csrf), preset.id)
                    }
                } else {
                    val saved = admin.sections.saveSection(section)
                    call.seeOther("/admin/sections/${saved.id}?msg=section-created")
                }
            }
        }

        route("/{sid}") {
            get {
                admin.withSession(call) { session ->
                    admin.withSection(call) { section ->
                        val items = admin.sections.findItems().filter { it.sectionId == section.id }
                        call.respondHtml { adminSectionItemsPage(section, items, session.csrf, call.request.queryParameters["msg"]) }
                    }
                }
            }

            settingsRoutes(admin)
            tagRoutes(admin)
            fieldRoutes(admin)
            deleteRoutes(admin)
            petRoutes(admin, "/items") { call -> admin.sectionOf(call)?.let { itemKind(admin, it) } }
        }
    }
}

private fun Route.settingsRoutes(admin: AdminSetup) {
    route("/settings") {
        get {
            admin.withSession(call) { session ->
                admin.withSection(call) { section ->
                    val page = FormPage(section, SectionForm.valuesOf(section), emptyMap(), session.csrf, call.request.queryParameters["msg"])
                    call.respondHtml { adminSectionFormPage(page, presetId = null) }
                }
            }
        }

        post {
            admin.withForm(call) { session, values ->
                admin.withSection(call) { section ->
                    val others = admin.sections.findSections().filter { it.id != section.id }
                    val result = SectionForm.validate(values, section, section, others)
                    val changed = result.value
                    if (changed == null || !result.ok) {
                        call.respondHtml(HttpStatusCode.UnprocessableEntity) {
                            adminSectionFormPage(FormPage(section, values, result.errors, session.csrf), presetId = null)
                        }
                    } else {
                        admin.sections.saveSection(changed)
                        call.seeOther("/admin/sections/${section.id}/settings?msg=settings-saved")
                    }
                }
            }
        }
    }
}

private fun Route.tagRoutes(admin: AdminSetup) {
    route("/tags") {
        get {
            admin.withSession(call) { session ->
                admin.withSection(call) { section ->
                    call.respondHtml { adminTagsPage(section, FormPage(section, TagsForm.valuesOf(section), emptyMap(), session.csrf)) }
                }
            }
        }

        post {
            admin.withForm(call) { session, values ->
                admin.withSection(call) { section ->
                    val result = TagsForm.validate(values, section)
                    val changed = result.value
                    if (changed == null || !result.ok) {
                        call.respondHtml(HttpStatusCode.UnprocessableEntity) {
                            adminTagsPage(section, FormPage(section, values, result.errors, session.csrf))
                        }
                    } else {
                        admin.sections.saveSection(changed)
                        // A tag that was taken off the list is taken off the entries that carried it.
                        val kept = changed.tags.map { it.key }.toSet()
                        admin.sections.findItems()
                            .filter { it.sectionId == section.id && it.tags.any { tag -> tag !in kept } }
                            .forEach { admin.sections.saveItem(it.copy(tags = it.tags.filter { tag -> tag in kept })) }
                        call.seeOther("/admin/sections/${section.id}/settings?msg=tags-saved")
                    }
                }
            }
        }
    }
}

private fun Route.fieldRoutes(admin: AdminSetup) {
    route("/fields") {
        get("/new") {
            admin.withSession(call) { session ->
                admin.withSection(call) { section ->
                    call.respondHtml { adminFieldFormPage(section, FormPage(null, FieldForm.newValues(), emptyMap(), session.csrf)) }
                }
            }
        }

        post {
            admin.withForm(call) { session, values ->
                admin.withSection(call) { section ->
                    val result = FieldForm.validate(values, section, existing = null)
                    val changed = result.value
                    if (changed == null || !result.ok) {
                        call.respondHtml(HttpStatusCode.UnprocessableEntity) {
                            adminFieldFormPage(section, FormPage(null, values, result.errors, session.csrf))
                        }
                    } else {
                        admin.sections.saveSection(changed)
                        call.seeOther("/admin/sections/${section.id}/settings?msg=field-added")
                    }
                }
            }
        }

        route("/{key}") {
            get {
                admin.withSession(call) { session ->
                    admin.withSection(call) { section ->
                        withField(call, section) { field ->
                            call.respondHtml { adminFieldFormPage(section, FormPage(field, FieldForm.valuesOf(field), emptyMap(), session.csrf)) }
                        }
                    }
                }
            }

            post {
                admin.withForm(call) { session, values ->
                    admin.withSection(call) { section ->
                        withField(call, section) { field ->
                            val result = FieldForm.validate(values, section, existing = field)
                            val changed = result.value
                            if (changed == null || !result.ok) {
                                call.respondHtml(HttpStatusCode.UnprocessableEntity) {
                                    adminFieldFormPage(section, FormPage(field, values, result.errors, session.csrf))
                                }
                            } else {
                                admin.sections.saveSection(changed)
                                call.seeOther("/admin/sections/${section.id}/settings?msg=field-saved")
                            }
                        }
                    }
                }
            }

            get("/delete") {
                admin.withSession(call) { session ->
                    admin.withSection(call) { section ->
                        withField(call, section) { field -> call.respondHtml { adminFieldDeletePage(section, field, session.csrf) } }
                    }
                }
            }

            post("/delete") {
                admin.withForm(call) { _, _ ->
                    admin.withSection(call) { section ->
                        withField(call, section) { field ->
                            admin.sections.saveSection(section.copy(fields = section.fields.filter { it.key != field.key }))
                            call.seeOther("/admin/sections/${section.id}/settings?msg=field-deleted")
                        }
                    }
                }
            }

            // One step up or down in the order of the fields.
            post("/move") {
                admin.withForm(call) { _, values ->
                    admin.withSection(call) { section ->
                        withField(call, section) { field ->
                            val fields = section.fields.toMutableList()
                            val index = fields.indexOfFirst { it.key == field.key }
                            val target = when (values["direction"]) {
                                "up" -> index - 1
                                "down" -> index + 1
                                else -> index
                            }
                            if (target in fields.indices && target != index) {
                                fields.add(target, fields.removeAt(index))
                                admin.sections.saveSection(section.copy(fields = fields))
                            }
                            call.seeOther("/admin/sections/${section.id}/settings?msg=moved")
                        }
                    }
                }
            }
        }
    }
}

private fun Route.deleteRoutes(admin: AdminSetup) {
    route("/delete") {
        get {
            admin.withSession(call) { session ->
                admin.withSection(call) { section ->
                    val count = admin.sections.findItems().count { it.sectionId == section.id }
                    call.respondHtml { adminSectionDeletePage(section, count, session.csrf) }
                }
            }
        }

        post {
            admin.withForm(call) { _, _ ->
                admin.withSection(call) { section ->
                    val photoUrls = admin.sections.findItems().filter { it.sectionId == section.id }.flatMap { it.photoUrls }
                    admin.sections.deleteSection(section.id)
                    // The entries are gone, and only then are their pictures removed.
                    removeUnusedPhotos(admin, photoUrls)
                    call.seeOther("/admin/sections?msg=section-deleted")
                }
            }
        }
    }
}
