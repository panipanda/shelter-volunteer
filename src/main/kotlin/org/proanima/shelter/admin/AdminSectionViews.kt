package org.proanima.shelter.admin

import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.code
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.fieldSet
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.summary
import kotlinx.html.ul
import org.proanima.shelter.model.ChoiceOption
import org.proanima.shelter.model.FieldType
import org.proanima.shelter.model.Section
import org.proanima.shelter.model.SectionField
import org.proanima.shelter.model.SectionItem
import org.proanima.shelter.model.SectionLayout

// The admin pages of the sections: the list of sections, a section's settings, its fields and tags, and its entries.
// Plain pages like the rest of the admin panel: forms that work without JavaScript.

private val SECTIONS_FLASH = mapOf(
    "section-created" to "Раздел создан. Добавьте первую запись.",
    "settings-saved" to "Настройки сохранены.",
    "section-deleted" to "Раздел удалён.",
    "field-added" to "Поле добавлено.",
    "field-saved" to "Поле сохранено.",
    "field-deleted" to "Поле удалено.",
    "moved" to "Порядок сохранён.",
    "tags-saved" to "Метки сохранены."
)

internal val ITEM_FLASH = mapOf(
    "created" to "Запись добавлена.",
    "saved" to "Изменения сохранены.",
    "deleted" to "Запись удалена."
)

internal val FIELD_TYPE_NAMES = mapOf(
    FieldType.TEXT to "Одна строка",
    FieldType.LONGTEXT to "Несколько предложений",
    FieldType.MARKDOWN to "Текст с оформлением",
    FieldType.DATE to "Дата",
    FieldType.CHOICE to "Выбор из списка"
)

private val FIELD_TYPE_HINTS = mapOf(
    FieldType.TEXT to "Короткое значение на трёх языках: место, имя, число слов. На странице стоит в списке фактов с названием поля.",
    FieldType.LONGTEXT to "Абзац-другой на трёх языках без оформления: итог выезда, краткое описание. Пустая строка начинает новый абзац.",
    FieldType.MARKDOWN to "Длинный текст на трёх языках с заголовками, списками и ссылками, как гайд. Для статей и отчётов.",
    FieldType.DATE to "Дата, одна для всех языков.",
    FieldType.CHOICE to "Одно значение из списка, который вы составляете сами: статус выезда, сложность. Названия вариантов на трёх языках."
)

private fun layoutName(layout: SectionLayout) = when (layout) {
    SectionLayout.CARDS -> "Карточки с обложкой, как у кошек"
    SectionLayout.LIST -> "Строки: дата, название и то, что отмечено «в списке»"
}

internal fun sectionTitle(section: Section) = section.title.ru?.ifBlank { null } ?: section.title.en?.ifBlank { null } ?: "Без названия"

internal fun itemTitle(item: SectionItem) = item.title.ru?.ifBlank { null } ?: item.title.en?.ifBlank { null } ?: "Без названия"

private fun fieldTitle(field: SectionField) = field.label.ru?.ifBlank { null } ?: field.label.en?.ifBlank { null } ?: "Без названия"

// place: where the section stands in the menu, in words.
class SectionSummary(val section: Section, val total: Int, val published: Int, val place: String = "верхний уровень")

fun HTML.adminSectionsIndexPage(sections: List<SectionSummary>, csrf: String, flashCode: String?) =
    adminPage("Разделы", csrf, AdminSection.SECTIONS) {
        h1 { +"Разделы" }
        flash(flashCode, SECTIONS_FLASH)
        p(classes = "admin-note") {
            +"Раздел — часть сайта, которую вы создаёте и наполняете сами: полезные статьи, архив визитов. "
            +"В меню сайта раздел появляется, как только в нём есть хоть одна опубликованная запись; пустого раздела посетители не видят."
        }

        div(classes = "admin-section-head") {
            h2 { +"Все разделы" }
            if (sections.size < MAX_SECTIONS) {
                a(href = "/admin/sections/new", classes = "button button-secondary") { +"Создать раздел" }
            }
        }
        if (sections.isEmpty()) {
            p(classes = "admin-note") { +"Пока ни одного раздела. «Создать раздел» предложит заготовки: статьи и архив визитов." }
        }
        ul(classes = "admin-list") {
            sections.forEach { summary ->
                val section = summary.section
                li(classes = "admin-row") {
                    div(classes = "admin-row-main") {
                        a(href = "/admin/sections/${section.id}", classes = "admin-row-title") { +sectionTitle(section) }
                        if (summary.published > 0) {
                            span(classes = "badge badge-published") { +"в меню сайта" }
                        } else {
                            span(classes = "badge badge-draft") { +"пока не видно" }
                        }
                    }
                    p(classes = "admin-row-meta") {
                        +"Адрес: /ru/${section.slug} · записей: ${summary.total}, опубликовано: ${summary.published} · место в меню: ${summary.place}"
                    }
                    p(classes = "admin-row-links") {
                        a(href = "/admin/sections/${section.id}") { +"Записи" }
                        +" · "
                        a(href = "/admin/sections/${section.id}/settings") { +"Настройки" }
                        if (summary.published > 0) {
                            +" · "
                            a(href = "/ru/${section.slug}") { +"На сайте" }
                        }
                    }
                }
            }
        }
    }

fun HTML.adminSectionPresetPage(csrf: String) = adminPage("Новый раздел", csrf, AdminSection.SECTIONS) {
    p(classes = "admin-back") { a(href = "/admin/sections") { +"← Разделы" } }
    h1 { +"Новый раздел" }
    p(classes = "admin-note") {
        +"С чего начать? Заготовка заполняет настройки, поля и метки; потом в разделе можно изменить всё."
    }
    ul(classes = "admin-list") {
        SectionPresets.all.forEach { preset ->
            li(classes = "admin-row") {
                div(classes = "admin-row-main") {
                    a(href = "/admin/sections/new?preset=${preset.id}", classes = "admin-row-title") { +preset.name }
                }
                p(classes = "admin-row-meta") { +preset.description }
            }
        }
    }
}

// The settings of a section. A new section carries the preset it was started from along in a hidden field.
internal fun HTML.adminSectionFormPage(page: FormPage<Section>, presetId: String?, place: SectionPlace) {
    val section = page.existing
    val heading = if (section == null) "Новый раздел" else "Настройки: ${sectionTitle(section)}"
    adminPage(heading, page.csrf, AdminSection.SECTIONS) {
        p(classes = "admin-back") {
            if (section == null) a(href = "/admin/sections/new") { +"← Заготовки" } else a(href = "/admin/sections/${section.id}") { +"← ${sectionTitle(section)}" }
        }
        h1 { +heading }
        flash(page.flashCode, SECTIONS_FLASH)
        errorSummary(page.errors, ::sectionFieldLabel)
        form(action = if (section == null) "/admin/sections" else "/admin/sections/${section.id}/settings", method = FormMethod.post, classes = "admin-form") {
            csrfField(page.csrf)
            if (presetId != null) {
                input(type = InputType.hidden, name = "preset") { value = presetId }
            }
            localizedField("Название", "title", page.values, page.errors, multiline = false, required = true, hint = "Заголовок страницы раздела.")
            localizedField(
                "Короткое название для меню", "menuTitle", page.values, page.errors, multiline = false,
                hint = "Меню тесное: если название длинное, напишите короче («Статьи», «Архив»). Можно не заполнять: возьмётся название."
            )
            localizedField(
                "Вступление", "intro", page.values, page.errors, multiline = true, rows = 3,
                hint = "Одна-две фразы под заголовком списка. Можно не заполнять."
            )
            div(classes = "field") {
                label { htmlFor = "f-slug"; +"Адрес" }
                p(classes = "field-hint") {
                    +"Латинские буквы, цифры и дефис: раздел откроется по адресу /ru/адрес. Если оставить пустым, адрес возьмётся из английского названия. "
                    +"Потом менять не стоит: ссылки, которые уже разошлись, перестанут открываться."
                }
                input(type = InputType.text, name = "slug") {
                    id = "f-slug"
                    value = page.values["slug"].orEmpty()
                    attributes["autocapitalize"] = "none"
                    attributes["spellcheck"] = "false"
                    markInvalid(this, page.errors, "slug")
                }
                fieldError(page.errors, "slug")
            }
            selectField("Вид списка", "layout", SectionLayout.entries.map { it.name to layoutName(it) }, page.values, page.errors)
            div(classes = "field-row") {
                selectField("У записей есть дата", "useDate", yesNoOptions(), page.values, page.errors, hint = "Записи идут от новых к старым.")
                selectField("У записей есть фотографии", "usePhotos", yesNoOptions(), page.values, page.errors)
            }
            selectField(
                "Где в меню", "parent", place.options, page.values, page.errors,
                hint = "На верхнем уровне или в выпадающем списке под другим пунктом. Порядок среди соседей и группы настраиваются на странице «Меню сайта»."
            )
            div(classes = "form-actions") {
                button(type = ButtonType.submit, classes = "button") { +(if (section == null) "Создать раздел" else "Сохранить") }
                a(href = if (section == null) "/admin/sections/new" else "/admin/sections/${section.id}") { +"Отмена" }
            }
        }

        if (section != null) {
            fieldsBlock(section, page.csrf)
            tagsBlock(section)
            p(classes = "admin-danger-link") { a(href = "/admin/sections/${section.id}/delete") { +"Удалить раздел…" } }
        }
    }
}

private fun yesNoOptions() = listOf("true" to "Да", "false" to "Нет")

private val SECTION_FIELD_LABELS = mapOf(
    "title" to "Название",
    "menuTitle" to "Короткое название для меню",
    "intro" to "Вступление",
    "slug" to "Адрес",
    "layout" to "Вид списка",
    "useDate" to "У записей есть дата",
    "usePhotos" to "У записей есть фотографии",
    "parent" to "Где в меню"
)

private fun sectionFieldLabel(field: String): String = languageLabel(field, SECTION_FIELD_LABELS)

// "title_en" -> "Название (English)"; a name with no language -> its own label.
private fun languageLabel(field: String, labels: Map<String, String>): String {
    val base = field.substringBeforeLast('_')
    val language = field.substringAfterLast('_', "").takeIf { it in CONTENT_LANGUAGES }
    val label = labels[if (language != null) base else field] ?: field
    return if (language == null) label else "$label (${LANGUAGE_NAMES.getValue(language)})"
}

private fun FlowContent.fieldsBlock(section: Section, csrf: String) {
    val base = "/admin/sections/${section.id}"
    div(classes = "admin-section") {
        div(classes = "admin-section-head") {
            h2 { +"Поля записи" }
            if (section.fields.size < MAX_FIELDS) {
                a(href = "$base/fields/new", classes = "button button-secondary") { +"Добавить поле" }
            }
        }
        p(classes = "admin-note") {
            +"Название у записи есть всегда. Остальное — поля, которые вы составляете сами. Порядок полей здесь — порядок на странице записи и в форме."
        }
        if (section.fields.isEmpty()) {
            p(classes = "admin-note") { +"Полей пока нет: у записи только название." }
        }
        ul(classes = "admin-list") {
            section.fields.forEachIndexed { index, field ->
                li(classes = "admin-row") {
                    div(classes = "admin-row-main") {
                        a(href = "$base/fields/${field.key}", classes = "admin-row-title") { +fieldTitle(field) }
                        span(classes = "badge") { +FIELD_TYPE_NAMES.getValue(field.type) }
                        if (field.required) span(classes = "badge") { +"обязательное" }
                        if (field.inList) span(classes = "badge") { +"в списке" }
                    }
                    div(classes = "row-actions") {
                        if (index > 0) moveButton(base, field, "up", "← Выше", csrf)
                        if (index < section.fields.lastIndex) moveButton(base, field, "down", "Ниже →", csrf)
                        a(href = "$base/fields/${field.key}", classes = "link-button") { +"Изменить" }
                        a(href = "$base/fields/${field.key}/delete", classes = "link-button photo-delete") { +"Удалить" }
                    }
                }
            }
        }
    }
}

private fun FlowContent.moveButton(base: String, field: SectionField, direction: String, label: String, csrf: String) {
    form(action = "$base/fields/${field.key}/move", method = FormMethod.post) {
        csrfField(csrf)
        input(type = InputType.hidden, name = "direction") { value = direction }
        button(type = ButtonType.submit, classes = "link-button") { +label }
    }
}

private fun FlowContent.tagsBlock(section: Section) {
    div(classes = "admin-section") {
        div(classes = "admin-section-head") {
            h2 { +"Метки" }
            a(href = "/admin/sections/${section.id}/tags", classes = "button button-secondary") { +"Изменить метки" }
        }
        if (section.tags.isEmpty()) {
            p(classes = "admin-note") {
                +"Меток нет. Метки — темы, которыми помечают записи («Кошки», «Здоровье»): на сайте по ним можно отобрать записи."
            }
        } else {
            p { +section.tags.joinToString(", ") { it.name.ru?.ifBlank { null } ?: it.name.en ?: it.key } }
        }
    }
}

// One page of a field: its name, its kind and the flags. The kind of a field that exists cannot be changed, because
// the entries already hold values of that kind.
fun HTML.adminFieldFormPage(section: Section, page: FormPage<SectionField>) {
    val field = page.existing
    val heading = if (field == null) "Новое поле" else "Поле: ${fieldTitle(field)}"
    val settings = "/admin/sections/${section.id}/settings"
    adminPage(heading, page.csrf, AdminSection.SECTIONS) {
        p(classes = "admin-back") { a(href = settings) { +"← Настройки: ${sectionTitle(section)}" } }
        h1 { +heading }
        errorSummary(page.errors, ::fieldFormLabel)
        form(
            action = if (field == null) "/admin/sections/${section.id}/fields" else "/admin/sections/${section.id}/fields/${field.key}",
            method = FormMethod.post,
            classes = "admin-form"
        ) {
            csrfField(page.csrf)
            localizedField(
                "Название поля", "label", page.values, page.errors, multiline = false, required = true,
                hint = "Так поле называется в форме записи и (для коротких значений) на странице записи."
            )
            if (field == null) {
                selectField(
                    "Что в поле", "type", FieldType.entries.map { it.name to FIELD_TYPE_NAMES.getValue(it) }, page.values, page.errors,
                    hint = "Выбрать можно один раз: потом тип не меняется."
                )
                typeHelp()
            } else {
                div(classes = "field") {
                    span(classes = "field-label") { +"Что в поле" }
                    p { +FIELD_TYPE_NAMES.getValue(field.type) }
                    p(classes = "field-hint") { +(FIELD_TYPE_HINTS.getValue(field.type) + " Тип уже не изменить: в записях лежат значения этого типа.") }
                }
            }
            div(classes = "field") {
                checkbox("required", "Обязательное: запись без него не сохранится", page.values)
                checkbox("inList", "Показывать в списке (на карточке или в строке)", page.values)
                fieldError(page.errors, "inList")
                checkbox("showLabel", "Показывать название поля над длинным текстом", page.values)
            }
            if (field == null || field.type == FieldType.CHOICE) {
                optionRows(
                    "Варианты", page.values, page.errors,
                    "Только для «Выбор из списка». Пустая строка не считается вариантом; чтобы убрать вариант, очистите его название. Нужно хотя бы два."
                )
            }
            div(classes = "form-actions") {
                button(type = ButtonType.submit, classes = "button") { +"Сохранить" }
                a(href = settings) { +"Отмена" }
            }
        }
    }
}

private fun FlowContent.typeHelp() {
    details(classes = "markdown-help") {
        summary { +"Какие бывают поля" }
        ul {
            FieldType.entries.forEach { type ->
                li { strong { +FIELD_TYPE_NAMES.getValue(type) }; +" — ${FIELD_TYPE_HINTS.getValue(type)}" }
            }
        }
    }
}

private val FIELD_FORM_LABELS = mapOf(
    "label" to "Название поля",
    "type" to "Что в поле",
    "inList" to "Показывать в списке"
)

private fun fieldFormLabel(field: String): String {
    val number = Regex("opt_(\\d+)_(ru|en|sr)").matchEntire(field)
    if (number != null) {
        return "Вариант ${number.groupValues[1]} (${LANGUAGE_NAMES.getValue(number.groupValues[2])})"
    }
    return languageLabel(field, FIELD_FORM_LABELS)
}

private fun FlowContent.checkbox(name: String, text: String, values: Map<String, String>) {
    label(classes = "reset-option") {
        input(type = InputType.checkBox, name = name) {
            id = "f-$name"
            value = "true"
            checked = values[name] == "true"
        }
        +text
    }
}

// Rows of options in three languages: the tags of a section, the choices of a field. A row already has its key, which
// stays with it when the name is changed.
private fun FlowContent.optionRows(legendText: String, values: Map<String, String>, errors: Map<String, String>, hint: String) {
    fieldSet(classes = "field-group") {
        legend { +legendText }
        p(classes = "field-hint") { +hint }
        OptionRows.rowNumbers(values).forEach { number ->
            div(classes = "option-row") {
                input(type = InputType.hidden, name = "opt_${number}_key") { value = values["opt_${number}_key"].orEmpty() }
                CONTENT_LANGUAGES.forEach { language ->
                    val name = "opt_${number}_$language"
                    div(classes = "field lang-field") {
                        span(classes = "lang-tag") {
                            attributes["aria-hidden"] = "true"
                            +language.uppercase()
                        }
                        input(type = InputType.text, name = name) {
                            id = "f-$name"
                            value = values[name].orEmpty()
                            attributes["aria-label"] = "Вариант $number, ${LANGUAGE_NAMES.getValue(language)}"
                            markInvalid(this, errors, name)
                        }
                        fieldError(errors, name)
                    }
                }
            }
        }
    }
}

fun HTML.adminTagsPage(section: Section, page: FormPage<Section>) {
    val settings = "/admin/sections/${section.id}/settings"
    adminPage("Метки: ${sectionTitle(section)}", page.csrf, AdminSection.SECTIONS) {
        p(classes = "admin-back") { a(href = settings) { +"← Настройки: ${sectionTitle(section)}" } }
        h1 { +"Метки" }
        errorSummary(page.errors, ::fieldFormLabel)
        p(classes = "admin-note") {
            +"Метки — темы, которыми помечают записи. На странице раздела посетители видят список меток и по нажатию отбирают записи; "
            +"в форме записи метки отмечаются галочками. Метка, которой не пользуется ни одна запись, посетителям не видна."
        }
        form(action = "/admin/sections/${section.id}/tags", method = FormMethod.post, classes = "admin-form") {
            csrfField(page.csrf)
            optionRows(
                "Метки", page.values, page.errors,
                "Название на трёх языках в строке. Чтобы убрать метку, очистите её название: с записей она снимется. Незаполненные строки не считаются."
            )
            div(classes = "form-actions") {
                button(type = ButtonType.submit, classes = "button") { +"Сохранить" }
                a(href = settings) { +"Отмена" }
            }
        }
    }
}

fun HTML.adminSectionDeletePage(section: Section, itemCount: Int, csrf: String) =
    adminPage("Удаление раздела", csrf, AdminSection.SECTIONS) {
        h1 { +"Удалить раздел «${sectionTitle(section)}»?" }
        p {
            +"Раздел исчезнет с сайта и из меню вместе со всеми записями ($itemCount) и их фотографиями. Вернуть их будет нельзя. "
            +"Если раздел нужен позже, переведите записи в черновики."
        }
        form(action = "/admin/sections/${section.id}/delete", method = FormMethod.post, classes = "admin-form") {
            csrfField(csrf)
            div(classes = "form-actions") {
                button(type = ButtonType.submit, classes = "button") { +"Да, удалить раздел" }
                a(href = "/admin/sections/${section.id}/settings") { +"Нет, вернуться" }
            }
        }
    }

fun HTML.adminFieldDeletePage(section: Section, field: SectionField, csrf: String) =
    adminPage("Удаление поля", csrf, AdminSection.SECTIONS) {
        h1 { +"Удалить поле «${fieldTitle(field)}»?" }
        p {
            +"Поле исчезнет из формы записи и со страниц сайта. Уже введённые значения останутся в базе, но показываться не будут, "
            +"и вернуть поле с ними будет нельзя: новое поле с таким же названием начнётся пустым."
        }
        form(action = "/admin/sections/${section.id}/fields/${field.key}/delete", method = FormMethod.post, classes = "admin-form") {
            csrfField(csrf)
            div(classes = "form-actions") {
                button(type = ButtonType.submit, classes = "button") { +"Да, удалить поле" }
                a(href = "/admin/sections/${section.id}/settings") { +"Нет, вернуться" }
            }
        }
    }

// The entries of one section.
fun HTML.adminSectionItemsPage(section: Section, items: List<SectionItem>, csrf: String, flashCode: String?) =
    adminPage(sectionTitle(section), csrf, AdminSection.SECTIONS) {
        val base = "/admin/sections/${section.id}"
        p(classes = "admin-back") { a(href = "/admin/sections") { +"← Разделы" } }
        h1 { +sectionTitle(section) }
        flash(flashCode, ITEM_FLASH + SECTIONS_FLASH)
        val published = items.count { it.published }
        p(classes = "admin-note") {
            +if (published == 0) {
                "На сайте раздела пока нет: он появится в меню, когда будет хоть одна опубликованная запись."
            } else {
                "На сайте: /ru/${section.slug}. Черновики видны только здесь."
            }
        }
        p(classes = "admin-row-links") {
            a(href = "$base/settings") { +"Настройки раздела, поля и метки" }
            if (published > 0) {
                +" · "
                a(href = "/ru/${section.slug}") { +"Открыть на сайте" }
            }
        }

        div(classes = "admin-section") {
            div(classes = "admin-section-head") {
                h2 { +"Записи" }
                a(href = "$base/items/new", classes = "button button-secondary") { +"Добавить запись" }
            }
            if (items.isEmpty()) {
                p(classes = "admin-note") { +"Пока ни одной записи." }
            }
            ul(classes = "admin-list") {
                items.sortedWith(compareByDescending<SectionItem> { if (section.useDate) it.date.orEmpty() else "" }.thenByDescending { it.id }).forEach { item ->
                    li(classes = "admin-row") {
                        div(classes = "admin-row-main") {
                            a(href = "$base/items/${item.id}", classes = "admin-row-title") { +itemTitle(item) }
                            itemBadges(section, item)
                        }
                        p(classes = "admin-row-meta") {
                            +listOfNotNull(
                                item.date?.takeIf { section.useDate },
                                section.tags.filter { it.key in item.tags }.joinToString(", ") { it.name.ru ?: it.key }.ifEmpty { null },
                                photoCount(item.photoUrls).takeIf { section.usePhotos }
                            ).joinToString(" · ")
                        }
                        previewLinks("$base/items/${item.id}/preview")
                    }
                }
            }
        }
    }

// A missing translation of the title means there is no page in that language; of another field, that the page shows
// another language there.
private fun FlowContent.itemBadges(section: Section, item: SectionItem) {
    span(classes = if (item.published) "badge badge-published" else "badge badge-draft") {
        +(if (item.published) "Опубликовано" else "Черновик")
    }
    val others = section.fields
        .filter { it.type == FieldType.TEXT || it.type == FieldType.LONGTEXT || it.type == FieldType.MARKDOWN }
        .map { item.texts[it.key] }
    val fieldGaps = translationGaps(others)
    CONTENT_LANGUAGES.filter { it != "ru" }.forEach { language ->
        if (valueIn(item.title, language).isNullOrBlank()) {
            span(classes = "badge badge-gap") {
                attributes["title"] = "Нет названия на этом языке: страницы записи на нём не будет"
                +"нет $language"
            }
        } else if (language in fieldGaps) {
            span(classes = "badge badge-gap") {
                attributes["title"] = "Часть текстов не переведена: на сайте для них будет показан другой язык"
                +"не всё на $language"
            }
        }
    }
}

private val ITEM_FIXED_LABELS = mapOf("title" to "Название", "date" to "Дата", "published" to "Статус")

private fun itemFieldLabel(section: Section): (String) -> String = { name ->
    val field = section.fields.firstOrNull { name == fieldFormName(it) || name.startsWith(fieldFormName(it) + "_") }
    if (field == null) {
        languageLabel(name, ITEM_FIXED_LABELS)
    } else {
        val language = name.substringAfterLast('_', "").takeIf { it in CONTENT_LANGUAGES && name != fieldFormName(field) }
        val label = fieldTitle(field)
        if (language == null) label else "$label (${LANGUAGE_NAMES.getValue(language)})"
    }
}

fun HTML.adminItemFormPage(section: Section, page: FormPage<SectionItem>) {
    val item = page.existing
    val base = "/admin/sections/${section.id}"
    val heading = if (item == null) "Новая запись" else itemTitle(item)
    adminPage(heading, page.csrf, AdminSection.SECTIONS) {
        p(classes = "admin-back") { a(href = base) { +"← ${sectionTitle(section)}" } }
        h1 { +heading }
        formFrame(
            page,
            action = if (item == null) "$base/items" else "$base/items/${item.id}",
            deleteHref = item?.let { "$base/items/${it.id}/delete" },
            previewBase = item?.let { "$base/items/${it.id}/preview" },
            backHref = base,
            labelFor = itemFieldLabel(section)
        ) {
            localizedField(
                "Название", "title", page.values, page.errors, multiline = false, required = true,
                hint = "Адрес страницы делается из английского названия. Если на каком-то языке названия нет, страницы на этом языке не будет."
            )
            if (section.useDate) {
                dateField("Дата", "date", page.values, page.errors, required = true)
            }
            section.fields.forEach { field -> itemField(field, page) }
            if (section.tags.isNotEmpty()) {
                tagChecks(section, page.values)
            }
            if (section.usePhotos) {
                photosSummary("sections/${section.id}/items", item?.id, item?.photoUrls.orEmpty(), heading)
            }
        }
    }
}

private fun FlowContent.itemField(field: SectionField, page: FormPage<SectionItem>) {
    val label = fieldTitle(field)
    val name = fieldFormName(field)
    when (field.type) {
        FieldType.TEXT -> localizedField(label, name, page.values, page.errors, multiline = false, required = field.required)
        FieldType.LONGTEXT -> localizedField(
            label, name, page.values, page.errors, multiline = true, required = field.required, rows = 4,
            hint = "Пустая строка начинает новый абзац."
        )
        FieldType.MARKDOWN -> {
            localizedField(label, name, page.values, page.errors, multiline = true, required = field.required, rows = 10, boxClass = "markdown-field article-field")
            articleMarkdownHelp()
        }
        FieldType.DATE -> dateField(label, name, page.values, page.errors, required = field.required)
        FieldType.CHOICE -> selectField(
            label, name,
            (if (field.required) emptyList() else listOf("" to "не выбрано")) + field.options.map { it.key to (it.name.ru ?: it.key) },
            page.values, page.errors
        )
    }
}

private fun FlowContent.dateField(labelText: String, name: String, values: Map<String, String>, errors: Map<String, String>, required: Boolean) {
    div(classes = "field") {
        label {
            htmlFor = "f-$name"
            +labelText
            if (required) span(classes = "required") { +" (обязательно)" }
        }
        input(type = InputType.date, name = name) {
            id = "f-$name"
            value = values[name].orEmpty()
            markInvalid(this, errors, name)
        }
        fieldError(errors, name)
    }
}

private fun FlowContent.tagChecks(section: Section, values: Map<String, String>) {
    fieldSet(classes = "field-group") {
        legend { +"Метки" }
        p(classes = "field-hint") { +"Отметьте темы записи. На сайте по ним можно отобрать записи." }
        section.tags.forEach { tag -> tagCheck(tag, values) }
    }
}

private fun FlowContent.tagCheck(tag: ChoiceOption, values: Map<String, String>) {
    div(classes = "field") {
        checkbox(tagFormName(tag.key), tag.name.ru ?: tag.key, values)
    }
}

private fun FlowContent.articleMarkdownHelp() {
    details(classes = "markdown-help") {
        summary { +"Как оформлять длинный текст" }
        ul {
            li { code { +"## Заголовок" }; +" и " ; code { +"### Заголовок поменьше" }; +" — подзаголовки. Заголовок самой записи ставить не нужно: он уже есть." }
            li { code { +"- пункт" }; +" — список: каждый пункт с новой строки и со знаком «-» в начале." }
            li { code { +"> текст" }; +" — выделенный блок." }
            li { code { +"**слова**" }; +" — жирный шрифт. " ; code { +"[слова](https://адрес)" }; +" — ссылка, адрес начинается с https://" }
            li { +"Абзацы разделяются пустой строкой. Курсив и таблицы не поддерживаются." }
        }
    }
}
