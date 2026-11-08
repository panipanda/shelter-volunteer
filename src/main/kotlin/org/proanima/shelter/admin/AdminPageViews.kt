package org.proanima.shelter.admin

import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormEncType
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
import kotlinx.html.img
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.summary
import kotlinx.html.textArea
import kotlinx.html.ul
import org.proanima.shelter.model.AppLocale

// The pages of the site whose texts and photos are edited here (see ContentCatalog).

class PageSummary(val page: ContentPage, val editedCount: Int)

fun HTML.adminPagesIndexPage(pages: List<PageSummary>, csrf: String) = adminPage("Страницы сайта", csrf, AdminSection.PAGES) {
    h1 { +"Страницы сайта" }
    p(classes = "admin-note") {
        +"Здесь меняются тексты и фото главной страницы, гайда, визитов и страницы «Как помочь» на трёх языках. "
        +"Изменения появляются на сайте сразу после сохранения. Всё, что вы не меняли, остаётся таким, каким было."
    }
    ul(classes = "admin-list") {
        pages.forEach { summary ->
            li(classes = "admin-row") {
                div(classes = "admin-row-main") {
                    a(href = "/admin/pages/${summary.page.id}", classes = "admin-row-title") { +summary.page.title }
                    if (summary.editedCount > 0) {
                        span(classes = "badge badge-published") { +"изменено: ${summary.editedCount}" }
                    }
                }
                p(classes = "admin-row-meta") { +summary.page.summary }
                siteLinks(summary.page)
            }
        }
    }
}

// How the page looks on the site now, in each language.
private fun FlowContent.siteLinks(page: ContentPage) {
    p(classes = "admin-row-links") {
        +"Как на сайте: "
        AppLocale.entries.forEachIndexed { index, locale ->
            if (index > 0) +" · "
            a(href = "/${locale.code}${page.publicPath}") { +locale.code.uppercase() }
        }
    }
}

private val PAGE_FLASH = mapOf("saved" to "Изменения сохранены. На сайте они уже есть.")

// Everything the form of a page needs. values: the text of every field as typed or as it is now; edited: the fields
// that have a stored text (they can be brought back to the shipped one); photoUrl: the page's photo now.
class PageFormView(
    val page: ContentPage,
    val values: Map<String, String>,
    val errors: Map<String, String>,
    val edited: Set<String>,
    val defaults: ContentDefaults,
    val photoUrl: String?,
    val photoEdited: Boolean,
    val csrf: String,
    val flashCode: String?
)

fun HTML.adminPageFormPage(view: PageFormView) = adminPage(view.page.title, view.csrf, AdminSection.PAGES) {
    val page = view.page
    p(classes = "admin-back") { a(href = "/admin/pages") { +"← Страницы сайта" } }
    h1 { +page.title }
    flash(view.flashCode, PAGE_FLASH)
    pageErrorSummary(view)

    page.note?.let { note ->
        p(classes = "admin-note") {
            +note
            page.noteLinkPageId?.let { target ->
                ContentCatalog.find(target)?.let { +" "; a(href = "/admin/pages/$target") { +"Открыть «${it.title}»" } }
            }
        }
    }
    p(classes = "admin-note") {
        +"Если оставить поле пустым или вернуть исходный текст, на сайте будет исходный текст. "
        +"Поля, где текст уже изменён, помечены, и там есть галочка «Вернуть исходный текст»."
    }

    form(action = "/admin/pages/${page.id}", method = FormMethod.post, classes = "admin-form") {
        csrfField(view.csrf)
        page.sections.forEach { section ->
            section(classes = "admin-section") {
                h2 { +section.title }
                section.note?.let { p(classes = "admin-note") { +it } }
                if (section.showsPhoto && page.photo != null) {
                    photoSummary(view, page.photo)
                }
                section.fields.forEach { field -> contentField(field, view) }
            }
        }
        div(classes = "form-actions") {
            button(type = ButtonType.submit, classes = "button") { +"Сохранить" }
            a(href = "/admin/pages") { +"К списку страниц" }
        }
    }
    siteLinks(page)
}

private fun FlowContent.pageErrorSummary(view: PageFormView) {
    if (view.errors.isEmpty()) return
    val labels = view.page.fields.flatMap { field ->
        (if (field.localized) CONTENT_LANGUAGES else listOf("")).map { language ->
            field.formName(language) to if (field.localized) "${field.label} (${LANGUAGE_NAMES.getValue(language)})" else field.label
        }
    }.toMap()
    div(classes = "admin-alert") {
        attributes["role"] = "alert"
        p { +"Не сохранено. Проверьте поля:" }
        ul {
            view.errors.forEach { (name, message) ->
                li { a(href = "#f-$name") { +"${labels[name] ?: name}: $message" } }
            }
        }
    }
}

// The photo is changed on a page of its own: it reloads the page, which would throw away the text typed into this form.
private fun FlowContent.photoSummary(view: PageFormView, photo: PagePhoto) {
    div(classes = "field") {
        span(classes = "field-label") {
            +photo.title
            if (view.photoEdited) {
                +" "
                span(classes = "badge badge-published") { +"своё фото" }
            }
        }
        view.photoUrl?.let { img(src = it, alt = "Фото, которое сейчас на сайте", classes = "admin-page-photo") }
        p(classes = "field-hint") { +photo.hint }
        p { a(href = "/admin/pages/${view.page.id}/photo") { +"Заменить фото" } }
        p(classes = "field-hint") { +"Текст ниже не сохраняется при переходе: сначала нажмите «Сохранить»." }
    }
}

private fun FlowContent.contentField(field: ContentField, view: PageFormView) {
    when (field) {
        is ContentField.Text -> localizedContentField(field, view, multiline = field.rows > 1, rows = field.rows)
        is ContentField.Lines -> localizedContentField(field, view, multiline = true, rows = 5)
        is ContentField.Markdown -> {
            localizedContentField(field, view, multiline = true, rows = 24, extraClass = "markdown-field")
            markdownHelp()
        }
        is ContentField.Plain -> plainContentField(field, view)
    }
}

private fun FlowContent.localizedContentField(
    field: ContentField,
    view: PageFormView,
    multiline: Boolean,
    rows: Int,
    extraClass: String? = null
) {
    fieldSet(classes = "field-group") {
        legend { +field.label }
        field.hint?.let { p(classes = "field-hint") { +it } }
        CONTENT_LANGUAGES.forEach { language ->
            val name = field.formName(language)
            div(classes = "field lang-field") {
                label {
                    htmlFor = "f-$name"
                    span(classes = "lang-tag") { +language.uppercase() }
                    +LANGUAGE_NAMES.getValue(language)
                }
                if (multiline) {
                    textArea(rows = rows.toString(), classes = extraClass) {
                        this.id = "f-$name"
                        this.name = name
                        markInvalid(this, view.errors, name)
                        +view.values[name].orEmpty()
                    }
                } else {
                    input(type = InputType.text, name = name) {
                        this.id = "f-$name"
                        this.value = view.values[name].orEmpty()
                        markInvalid(this, view.errors, name)
                    }
                }
                fieldError(view.errors, name)
                if (name in view.edited) {
                    editedNote(name, shippedPreview(field, view.defaults.of(field, language)))
                }
            }
        }
    }
}

private fun FlowContent.plainContentField(field: ContentField.Plain, view: PageFormView) {
    val name = field.formName("")
    div(classes = "field") {
        label { htmlFor = "f-$name"; +field.label }
        field.hint?.let { p(classes = "field-hint") { +it } }
        input(type = InputType.text, name = name) {
            this.id = "f-$name"
            this.value = view.values[name].orEmpty()
            markInvalid(this, view.errors, name)
        }
        fieldError(view.errors, name)
        if (name in view.edited) {
            editedNote(name, shippedPreview(field, view.defaults.of(field, "")))
        }
    }
}

// What is shown as "the shipped text": a short text in full, a list as its items, the whole guide not at all.
private fun shippedPreview(field: ContentField, shipped: String): String? = when (field) {
    is ContentField.Markdown -> null
    is ContentField.Lines -> shipped.lines().joinToString("; ")
    else -> shipped
}

private fun FlowContent.editedNote(name: String, shipped: String?) {
    p(classes = "field-hint") {
        +"Изменено."
        if (shipped != null) {
            +" Исходный текст: «$shipped»."
        }
    }
    label(classes = "reset-option") {
        input(type = InputType.checkBox, name = PageForm.resetName(name))
        +"Вернуть исходный текст"
    }
}

private fun FlowContent.markdownHelp() {
    details(classes = "markdown-help") {
        summary { +"Как оформлять текст гайда" }
        ul {
            li { code { +"# Название" }; +" — заголовок страницы. Он должен быть один и стоять первой строкой: на нём держатся оглавление и фото." }
            li {
                code { +"## Раздел {#metka}" }
                +" — заголовок раздела. Слово в фигурных скобках после # — метка (латинские буквы, цифры, дефис): "
                +"раздел с меткой попадает в оглавление, без метки — нет."
            }
            li { code { +"### Подраздел" }; +" — заголовок поменьше." }
            li { code { +"- пункт" }; +" — список: каждый пункт с новой строки и со знаком «-» в начале." }
            li { code { +"> текст" }; +" — выделенный блок (как «В приюте три вольера…»)." }
            li { code { +"[слова](https://адрес)" }; +" — ссылка. Адрес начинается с https://" }
            li { +"Абзацы разделяются пустой строкой. Жирный шрифт, курсив и таблицы не поддерживаются." }
        }
    }
}

// The page where a photo of a page is changed.
class PagePhotoView(
    val page: ContentPage,
    val photo: PagePhoto,
    val photoUrl: String,
    val custom: Boolean,
    val csrf: String,
    val flashCode: String?,
    val problems: List<String>,
    val usedBytes: Long
)

private val PHOTO_PAGE_FLASH = mapOf(
    "uploaded" to "Фото заменено. На сайте оно уже есть.",
    "reset" to "Возвращено исходное фото."
)

fun HTML.adminPagePhotoPage(view: PagePhotoView) = adminPage("${view.photo.title}: ${view.page.title}", view.csrf, AdminSection.PAGES) {
    val base = "/admin/pages/${view.page.id}"
    p(classes = "admin-back") { a(href = base) { +"← ${view.page.title}" } }
    h1 { +view.photo.title }
    flash(view.flashCode, PHOTO_PAGE_FLASH)
    if (view.problems.isNotEmpty()) {
        div(classes = "admin-alert") {
            attributes["role"] = "alert"
            p { +"Не загрузилось:" }
            ul { view.problems.forEach { li { +it } } }
        }
    }

    img(src = view.photoUrl, alt = "Фото, которое сейчас на сайте", classes = "admin-page-photo")
    p(classes = "admin-note") { +(if (view.custom) "Сейчас на сайте загруженное вами фото." else "Сейчас на сайте исходное фото.") }

    // The token field comes first on purpose: the server reads it before the file.
    form(action = "$base/photo", encType = FormEncType.multipartFormData, method = FormMethod.post, classes = "admin-form") {
        csrfField(view.csrf)
        div(classes = "field") {
            label { htmlFor = "f-photo"; +"Новое фото" }
            input(type = InputType.file, name = "photo") {
                id = "f-photo"
                attributes["accept"] = "image/jpeg,image/png"
            }
            p(classes = "field-hint") {
                +view.photo.hint
                +" JPEG или PNG, до 8 МБ. Снимок с iPhone выбирайте как JPEG («Самые совместимые»). Место съёмки из файла удаляется."
            }
        }
        div(classes = "form-actions") {
            button(type = ButtonType.submit, classes = "button") { +"Загрузить" }
        }
    }

    if (view.custom) {
        h2 { +"Вернуть исходное фото" }
        p(classes = "admin-note") { +"Загруженное вами фото будет удалено, а на сайте снова появится то, что было при создании сайта." }
        form(action = "$base/photo/reset", method = FormMethod.post, classes = "admin-form") {
            csrfField(view.csrf)
            button(type = ButtonType.submit, classes = "button button-secondary") { +"Вернуть исходное фото" }
        }
    }
    storageNote(view.usedBytes)
}
