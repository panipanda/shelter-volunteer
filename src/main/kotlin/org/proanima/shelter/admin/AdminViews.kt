package org.proanima.shelter.admin

import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.MAIN
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.fieldSet
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.lang
import kotlinx.html.legend
import kotlinx.html.li
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.section
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.textArea
import kotlinx.html.title
import kotlinx.html.ul
import kotlinx.html.unsafe
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.Cat
import org.proanima.shelter.model.CatLocation
import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.Dog
import org.proanima.shelter.service.cardPhotoUrl
import org.proanima.shelter.service.displayAdoptionStage
import org.proanima.shelter.service.displayCatAge
import org.proanima.shelter.service.displayCatLocation
import org.proanima.shelter.views.THEME_INIT_SCRIPT

// The admin panel is for the people who run the site, in Russian only. Its pages are plain: the colours,
// fonts and buttons of the site, forms that work without JavaScript, nothing for search engines.

// csrf == null: nobody is logged in (the login page), so there is no menu.
fun HTML.adminPage(pageTitle: String, csrf: String?, content: MAIN.() -> Unit) {
    lang = "ru"
    head {
        meta(charset = "UTF-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        meta(name = "color-scheme", content = "light dark")
        meta(name = "robots", content = "noindex, nofollow")
        title { +"$pageTitle · Админка" }
        link(rel = "icon", href = "/icons/favicon-32.png", type = "image/png")
        link(rel = "stylesheet", href = "/styles/main.css", type = "text/css")
        link(rel = "stylesheet", href = "/styles/admin.css", type = "text/css")
        script { unsafe { +THEME_INIT_SCRIPT } }
    }
    body {
        header(classes = "admin-header") {
            div(classes = "wrap admin-header-row") {
                a(href = "/admin", classes = "admin-brand") { +"Pro Anima · админка" }
                if (csrf != null) {
                    div(classes = "admin-header-actions") {
                        a(href = "/ru") { +"На сайт" }
                        form(action = "/admin/logout", method = FormMethod.post) {
                            csrfField(csrf)
                            button(type = ButtonType.submit, classes = "link-button") { +"Выйти" }
                        }
                    }
                }
            }
        }
        main(classes = "wrap admin-main") { content() }
    }
}

private fun FlowContent.csrfField(csrf: String) {
    input(type = InputType.hidden, name = "_csrf") { value = csrf }
}

fun HTML.adminLoginPage(error: String?) = adminPage("Вход", csrf = null) {
    h1 { +"Вход" }
    if (error != null) {
        p(classes = "admin-alert") {
            attributes["role"] = "alert"
            +error
        }
    }
    form(action = "/admin/login", method = FormMethod.post, classes = "admin-form") {
        div(classes = "field") {
            label { htmlFor = "f-password"; +"Пароль" }
            input(type = InputType.password, name = "password") {
                id = "f-password"
                attributes["autocomplete"] = "current-password"
                attributes["required"] = "required"
                attributes["autofocus"] = "autofocus"
            }
        }
        div(classes = "form-actions") {
            button(type = ButtonType.submit, classes = "button") { +"Войти" }
        }
    }
}

fun HTML.adminMessagePage(heading: String, text: String, backHref: String, backLabel: String, csrf: String?) =
    adminPage(heading, csrf) {
        h1 { +heading }
        p { +text }
        p { a(href = backHref) { +backLabel } }
    }

private val FLASH_MESSAGES = mapOf(
    "created" to "Запись добавлена.",
    "saved" to "Изменения сохранены.",
    "deleted" to "Запись удалена."
)

private fun FlowContent.flash(code: String?) {
    FLASH_MESSAGES[code]?.let { message ->
        p(classes = "admin-flash") {
            attributes["role"] = "status"
            +message
        }
    }
}

private fun FlowContent.statusBadges(published: Boolean, gaps: List<String>) {
    span(classes = if (published) "badge badge-published" else "badge badge-draft") {
        +(if (published) "Опубликовано" else "Черновик")
    }
    gaps.forEach { language ->
        span(classes = "badge badge-gap") {
            attributes["title"] = "Нет перевода на этот язык: на сайте для него будет показан другой язык"
            +"нет $language"
        }
    }
}

internal fun catTitle(cat: Cat) = cat.name?.ru?.ifBlank { null } ?: cat.name?.en?.ifBlank { null } ?: "Без имени"

internal fun dogTitle(dog: Dog) = dog.name.ru?.ifBlank { null } ?: dog.name.en?.ifBlank { null } ?: "Без имени"

fun HTML.adminDashboardPage(cats: List<Cat>, dogs: List<Dog>, csrf: String, flashCode: String?) =
    adminPage("Кошки и собаки", csrf) {
        h1 { +"Кошки и собаки" }
        flash(flashCode)
        p(classes = "admin-note") {
            +"Черновики видны только здесь. Чтобы кошка или собака появилась на сайте, переведите её в статус «Опубликовано»."
        }

        section(classes = "admin-section") {
            div(classes = "admin-section-head") {
                h2 { +"Кошки" }
                a(href = "/admin/cats/new", classes = "button button-secondary") { +"Добавить кошку" }
            }
            if (cats.isEmpty()) {
                p(classes = "admin-note") { +"Пока никого нет." }
            }
            ul(classes = "admin-list") {
                cats.forEach { cat ->
                    li(classes = "admin-row") {
                        div(classes = "admin-row-main") {
                            a(href = "/admin/cats/${cat.id}", classes = "admin-row-title") { +catTitle(cat) }
                            statusBadges(cat.published, translationGaps(listOf(cat.name, cat.description, cat.medicalStatus, cat.adoptionInstruction)))
                        }
                        p(classes = "admin-row-meta") {
                            +listOf(
                                displayCatLocation(cat.location, AppLocale.RU),
                                displayAdoptionStage(cat.adoptionStage, AppLocale.RU),
                                displayCatAge(cat.birthYear, cat.birthMonth, AppLocale.RU)
                            ).joinToString(" · ")
                        }
                        previewLinks("/admin/cats/${cat.id}/preview")
                    }
                }
            }
        }

        section(classes = "admin-section") {
            div(classes = "admin-section-head") {
                h2 { +"Собаки" }
                a(href = "/admin/dogs/new", classes = "button button-secondary") { +"Добавить собаку" }
            }
            if (dogs.isEmpty()) {
                p(classes = "admin-note") { +"Пока никого нет." }
            }
            ul(classes = "admin-list") {
                dogs.forEach { dog ->
                    li(classes = "admin-row") {
                        div(classes = "admin-row-main") {
                            a(href = "/admin/dogs/${dog.id}", classes = "admin-row-title") { +dogTitle(dog) }
                            statusBadges(dog.published, translationGaps(listOf(dog.name, dog.age, dog.description)))
                        }
                        p(classes = "admin-row-meta") { +displayCatLocation(dog.location, AppLocale.RU) }
                        previewLinks("/admin/dogs/${dog.id}/preview")
                    }
                }
            }
        }
    }

// How the page will look on the site, whatever the status: a draft has no address of its own there.
private fun FlowContent.previewLinks(base: String) {
    p(classes = "admin-row-links") {
        +"Как на сайте: "
        AppLocale.entries.forEachIndexed { index, locale ->
            if (index > 0) +" · "
            a(href = "$base?lang=${locale.code}") { +locale.code.uppercase() }
        }
    }
}

// Everything a form page needs: the record (null for a new one), the values as typed, the mistakes found,
// and the message to show once after a save.
class FormPage<T>(
    val existing: T?,
    val values: Map<String, String>,
    val errors: Map<String, String>,
    val csrf: String,
    val flashCode: String? = null
)

private val LANGUAGE_NAMES = mapOf("ru" to "Русский", "en" to "English", "sr" to "Srpski")

private val MONTHS = listOf(
    "январь", "февраль", "март", "апрель", "май", "июнь",
    "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"
)

private fun markInvalid(input: kotlinx.html.CommonAttributeGroupFacade, errors: Map<String, String>, name: String) {
    if (name in errors) {
        input.attributes["aria-invalid"] = "true"
        input.attributes["aria-describedby"] = "err-$name"
    }
}

private fun FlowContent.fieldError(errors: Map<String, String>, name: String) {
    errors[name]?.let { message ->
        p(classes = "field-error") {
            id = "err-$name"
            +message
        }
    }
}

private val FIELD_LABELS = mapOf(
    "name" to "Имя",
    "age" to "Возраст",
    "description" to "Описание",
    "medicalStatus" to "Здоровье",
    "adoptionInstruction" to "Как забрать кошку",
    "birthYear" to "Год рождения",
    "birthMonth" to "Месяц рождения",
    "location" to "Где живёт",
    "adoptionStage" to "Пристройство",
    "published" to "Статус"
)

// "description_en" -> "Описание (English)"
private fun fieldLabel(field: String): String {
    val base = field.substringBeforeLast('_')
    val language = field.substringAfterLast('_', "").takeIf { it in CONTENT_LANGUAGES }
    val label = FIELD_LABELS[if (language != null) base else field] ?: field
    return if (language == null) label else "$label (${LANGUAGE_NAMES.getValue(language)})"
}

// What is wrong, with a link to each field: the form is long, and a red field can be far below.
private fun FlowContent.errorSummary(errors: Map<String, String>) {
    if (errors.isEmpty()) return
    div(classes = "admin-alert") {
        attributes["role"] = "alert"
        p { +"Не сохранено. Проверьте поля:" }
        ul {
            errors.forEach { (field, message) ->
                li { a(href = "#f-$field") { +"${fieldLabel(field)}: $message" } }
            }
        }
    }
}

// One text in three languages, one row per language.
private fun FlowContent.localizedField(
    legend: String,
    base: String,
    values: Map<String, String>,
    errors: Map<String, String>,
    multiline: Boolean,
    required: Boolean = false,
    hint: String? = null,
    rows: Int = 3
) {
    fieldSet(classes = "field-group") {
        legend { +legend }
        if (hint != null) {
            p(classes = "field-hint") { +hint }
        }
        CONTENT_LANGUAGES.forEach { language ->
            val name = "${base}_$language"
            val value = values[name].orEmpty()
            val noTranslation = language != "ru" && value.isBlank() && !values["${base}_ru"].isNullOrBlank()
            div(classes = "field lang-field") {
                label {
                    htmlFor = "f-$name"
                    span(classes = "lang-tag") { +language.uppercase() }
                    +LANGUAGE_NAMES.getValue(language)
                    if (required && language == "ru") span(classes = "required") { +" (обязательно)" }
                }
                if (multiline) {
                    textArea(rows = rows.toString()) {
                        this.id = "f-$name"
                        this.name = name
                        markInvalid(this, errors, name)
                        +value
                    }
                } else {
                    input(type = InputType.text, name = name) {
                        this.id = "f-$name"
                        this.value = value
                        markInvalid(this, errors, name)
                    }
                }
                if (noTranslation) {
                    p(classes = "field-hint") { +"Не заполнено: на сайте для этого языка будет показан другой язык." }
                }
                fieldError(errors, name)
            }
        }
    }
}

private fun FlowContent.selectField(
    label: String,
    name: String,
    options: List<Pair<String, String>>,
    values: Map<String, String>,
    errors: Map<String, String>,
    hint: String? = null
) {
    div(classes = "field") {
        label { htmlFor = "f-$name"; +label }
        if (hint != null) {
            p(classes = "field-hint") { +hint }
        }
        select {
            this.id = "f-$name"
            this.name = name
            markInvalid(this, errors, name)
            options.forEach { (optionValue, text) ->
                option {
                    this.value = optionValue
                    selected = values[name] == optionValue
                    +text
                }
            }
        }
        fieldError(errors, name)
    }
}

private val STATUS_OPTIONS = listOf(
    "false" to "Черновик: виден только в админке",
    "true" to "Опубликовано: видно на сайте"
)

private fun <T> FlowContent.formFrame(
    page: FormPage<T>,
    action: String,
    deleteHref: String?,
    previewBase: String?,
    content: FlowContent.() -> Unit
) {
    flash(page.flashCode)
    errorSummary(page.errors)
    form(action = action, method = FormMethod.post, classes = "admin-form") {
        csrfField(page.csrf)
        selectField("Статус", "published", STATUS_OPTIONS, page.values, page.errors)
        content()
        div(classes = "form-actions") {
            button(type = ButtonType.submit, classes = "button") { +"Сохранить" }
            a(href = "/admin") { +"К списку" }
        }
    }
    if (previewBase != null) {
        previewLinks(previewBase)
    }
    if (deleteHref != null) {
        p(classes = "admin-danger-link") { a(href = deleteHref) { +"Удалить…" } }
    }
}

private fun FlowContent.currentPhotos(photoUrls: List<String>, name: String) {
    div(classes = "field") {
        span(classes = "field-label") { +"Фотографии" }
        if (photoUrls.isEmpty()) {
            p(classes = "field-hint") { +"Фотографий пока нет: на сайте показывается заглушка." }
        } else {
            div(classes = "admin-photos") {
                photoUrls.forEach { url -> img(src = cardPhotoUrl(url), alt = name, classes = "admin-photo") }
            }
        }
        p(classes = "field-hint") { +"Загрузка и порядок фотографий появятся в админке на следующем этапе." }
    }
}

fun HTML.catFormPage(page: FormPage<Cat>) {
    val cat = page.existing
    val heading = if (cat == null) "Новая кошка" else catTitle(cat)
    adminPage(heading, page.csrf) {
        h1 { +heading }
        formFrame(
            page,
            action = if (cat == null) "/admin/cats" else "/admin/cats/${cat.id}",
            deleteHref = cat?.let { "/admin/cats/${it.id}/delete" },
            previewBase = cat?.let { "/admin/cats/${it.id}/preview" }
        ) {
            localizedField("Имя", "name", page.values, page.errors, multiline = false, hint = "Можно оставить пустым, если у кошки пока нет имени. Адрес страницы делается из английского имени.")
            div(classes = "field-row") {
                div(classes = "field") {
                    label { htmlFor = "f-birthYear"; +"Год рождения" }
                    input(type = InputType.number, name = "birthYear") {
                        id = "f-birthYear"
                        value = page.values["birthYear"].orEmpty()
                        attributes["inputmode"] = "numeric"
                        markInvalid(this, page.errors, "birthYear")
                    }
                    fieldError(page.errors, "birthYear")
                }
                selectField(
                    "Месяц рождения",
                    "birthMonth",
                    listOf("" to "не указан") + MONTHS.mapIndexed { index, month -> (index + 1).toString() to month },
                    page.values,
                    page.errors
                )
            }
            localizedField("Описание", "description", page.values, page.errors, multiline = true, required = true, rows = 5, hint = "Одним абзацем: переносы строк на сайте не сохраняются.")
            localizedField("Здоровье", "medicalStatus", page.values, page.errors, multiline = true, hint = "Прививки, анализы, стерилизация. Можно не заполнять.")
            selectField(
                "Где живёт", "location",
                CatLocation.entries.map { it.name to displayCatLocation(it, AppLocale.RU) },
                page.values, page.errors
            )
            selectField(
                "Пристройство", "adoptionStage",
                AdoptionStage.entries.map { it.name to displayAdoptionStage(it, AppLocale.RU) },
                page.values, page.errors,
                hint = "«Пристроена» переносит кошку в отдельный раздел внизу списка."
            )
            localizedField("Как забрать кошку", "adoptionInstruction", page.values, page.errors, multiline = true, hint = "Если пусто, на сайте будет общая фраза «порядок пока не определён».")
            currentPhotos(cat?.photoUrls.orEmpty(), heading)
        }
    }
}

fun HTML.dogFormPage(page: FormPage<Dog>) {
    val dog = page.existing
    val heading = if (dog == null) "Новая собака" else dogTitle(dog)
    adminPage(heading, page.csrf) {
        h1 { +heading }
        formFrame(
            page,
            action = if (dog == null) "/admin/dogs" else "/admin/dogs/${dog.id}",
            deleteHref = dog?.let { "/admin/dogs/${it.id}/delete" },
            previewBase = dog?.let { "/admin/dogs/${it.id}/preview" }
        ) {
            localizedField("Имя", "name", page.values, page.errors, multiline = false, required = true, hint = "Адрес страницы делается из английского имени.")
            localizedField("Возраст", "age", page.values, page.errors, multiline = false, hint = "Словами, например «Около 3 лет». Можно не заполнять.")
            localizedField("Описание", "description", page.values, page.errors, multiline = true, required = true, rows = 5, hint = "Одним абзацем: переносы строк на сайте не сохраняются.")
            selectField(
                "Где живёт", "location",
                CatLocation.entries.map { it.name to displayCatLocation(it, AppLocale.RU) },
                page.values, page.errors
            )
            currentPhotos(dog?.photoUrls.orEmpty(), heading)
        }
    }
}

fun HTML.adminDeleteConfirmPage(question: String, action: String, backHref: String, csrf: String) =
    adminPage("Удаление", csrf) {
        h1 { +question }
        p {
            +"Запись и её фотографии будут удалены без возможности восстановления. "
            +"Если нужно просто убрать её с сайта, переведите её в черновик."
        }
        form(action = action, method = FormMethod.post, classes = "admin-form") {
            csrfField(csrf)
            div(classes = "form-actions") {
                button(type = ButtonType.submit, classes = "button") { +"Да, удалить" }
                a(href = backHref) { +"Нет, вернуться" }
            }
        }
    }
