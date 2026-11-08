package org.proanima.shelter.admin

import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormEncType
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
import java.net.URLEncoder
import java.util.Locale

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
            div(classes = "password-row") {
                input(type = InputType.password, name = "password") {
                    id = "f-password"
                    attributes["autocomplete"] = "current-password"
                    attributes["required"] = "required"
                    attributes["autofocus"] = "autofocus"
                }
                // Hidden until /scripts/admin-password.js reveals it: without JavaScript the button could do nothing.
                button(type = ButtonType.button, classes = "link-button password-toggle") {
                    attributes["hidden"] = "hidden"
                    attributes["aria-controls"] = "f-password"
                    attributes["data-password-toggle"] = "true"
                    attributes["data-label-show"] = SHOW_PASSWORD
                    attributes["data-label-hide"] = HIDE_PASSWORD
                    +SHOW_PASSWORD
                }
            }
        }
        div(classes = "form-actions") {
            button(type = ButtonType.submit, classes = "button") { +"Войти" }
        }
    }
    script(src = "/scripts/admin-password.js") { attributes["defer"] = "defer" }
}

private const val SHOW_PASSWORD = "Показать пароль"
private const val HIDE_PASSWORD = "Скрыть пароль"

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

fun HTML.adminDashboardPage(cats: List<Cat>, dogs: List<Dog>, csrf: String, flashCode: String?, photoBytes: Long = 0) =
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
                                displayCatAge(cat.birthYear, cat.birthMonth, AppLocale.RU),
                                photoCount(cat.photoUrls)
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
                        p(classes = "admin-row-meta") {
                            +listOf(displayCatLocation(dog.location, AppLocale.RU), photoCount(dog.photoUrls)).joinToString(" · ")
                        }
                        previewLinks("/admin/dogs/${dog.id}/preview")
                    }
                }
            }
        }

        storageNote(photoBytes)

        section(classes = "admin-section") {
            h2 { +"Резервная копия" }
            p(classes = "admin-note") {
                +"Один архив: тексты всех кошек и собак (на трёх языках, с черновиками) и все их фото. "
                +"Сохраните его у себя раз в месяц и после больших правок: по нему сайт можно восстановить. "
                +"Архив собирается на ходу и может занять минуту, не закрывайте вкладку."
            }
            form(action = "/admin/export", method = FormMethod.post) {
                csrfField(csrf)
                button(type = ButtonType.submit, classes = "button button-secondary") { +"Скачать копию (zip)" }
            }
        }
    }

private fun photoCount(photoUrls: List<String>) = if (photoUrls.isEmpty()) "без фото" else "фото: ${photoUrls.size}"

private const val DATABASE_LIMIT_BYTES = 1024L * 1024 * 1024

private fun megabytes(bytes: Long): String = String.format(Locale.forLanguageTag("ru"), "%.1f", bytes / 1024.0 / 1024.0)

// The free database holds 1 GB for everything. The pictures are almost all of it, so the number is shown where
// someone adding photos will see it, before the day it runs out.
private fun FlowContent.storageNote(photoBytes: Long) {
    if (photoBytes <= 0) return
    val nearlyFull = photoBytes > DATABASE_LIMIT_BYTES * 8 / 10
    p(classes = if (nearlyFull) "admin-alert" else "admin-note") {
        +"Фотографии занимают ${megabytes(photoBytes)} МБ из 1024 МБ, которые вмещает бесплатная база."
        if (nearlyFull) +" Места остаётся мало: удалите ненужные фото или перейдите на платный тариф."
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

// Photos are changed on a page of their own (it reloads at every step, and the form's unsaved text would be lost).
private fun FlowContent.photosSummary(kindPath: String, id: Int?, photoUrls: List<String>, name: String) {
    div(classes = "field") {
        span(classes = "field-label") { +"Фотографии" }
        if (id == null) {
            p(classes = "field-hint") { +"Фотографии можно добавить после первого сохранения." }
            return@div
        }
        if (photoUrls.isEmpty()) {
            p(classes = "field-hint") { +"Фотографий пока нет: на сайте показывается заглушка." }
        } else {
            div(classes = "admin-photos") {
                photoUrls.take(6).forEach { url -> img(src = cardPhotoUrl(url), alt = name, classes = "admin-photo") }
            }
        }
        p {
            a(href = "/admin/$kindPath/$id/photos") {
                +(if (photoUrls.isEmpty()) "Добавить фотографии" else "Фотографии: ${photoUrls.size}, изменить")
            }
        }
        p(classes = "field-hint") { +"Текст выше не сохраняется при переходе: сначала нажмите «Сохранить»." }
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
            photosSummary("cats", cat?.id, cat?.photoUrls.orEmpty(), heading)
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
            photosSummary("dogs", dog?.id, dog?.photoUrls.orEmpty(), heading)
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

class PhotosPage(
    val kindPath: String,
    val id: Int,
    val title: String,
    val photoUrls: List<String>,
    val csrf: String,
    val flashCode: String?,
    val problems: List<String>,
    val addedCount: Int,
    val usedBytes: Long
)

private val PHOTO_FLASH = mapOf(
    "uploaded" to "Фото добавлены.",
    "moved" to "Порядок сохранён.",
    "removed" to "Фото удалено."
)

private fun FlowContent.photoButton(action: String, label: String) {
    button(type = ButtonType.submit, name = "action", classes = "link-button photo-action") {
        value = action
        +label
    }
}

// What the page that asks "delete this photo?" needs. isCover and isOnly change what the page warns about.
class PhotoDeleteAsk(
    val kindPath: String,
    val id: Int,
    val title: String,
    val photoUrl: String,
    val isCover: Boolean,
    val isOnly: Boolean,
    val csrf: String
)

fun HTML.adminPhotoDeleteConfirmPage(ask: PhotoDeleteAsk) = adminPage("Удаление фото", ask.csrf) {
    val base = "/admin/${ask.kindPath}/${ask.id}"
    h1 { +"Удалить это фото?" }
    p(classes = "admin-back") { +"Запись: "; a(href = base) { +ask.title } }
    img(src = cardPhotoUrl(ask.photoUrl), alt = "Фото, которое будет удалено", classes = "photo-confirm")
    p { +"Фото будет удалено без возможности восстановления. Остальные фото не изменятся." }
    if (ask.isOnly) {
        p(classes = "admin-note") { +"Это единственное фото: на сайте вместо него будет заглушка." }
    } else if (ask.isCover) {
        p(classes = "admin-note") { +"Это обложка: обложкой станет следующее фото." }
    }
    form(action = "$base/photos/update", method = FormMethod.post, classes = "admin-form") {
        csrfField(ask.csrf)
        input(type = InputType.hidden, name = "photo") { value = ask.photoUrl }
        input(type = InputType.hidden, name = "action") { value = "delete" }
        div(classes = "form-actions") {
            button(type = ButtonType.submit, classes = "button") { +"Да, удалить" }
            a(href = "$base/photos") { +"Нет, вернуться" }
        }
    }
}

fun HTML.adminPhotosPage(page: PhotosPage) = adminPage("Фотографии: ${page.title}", page.csrf) {
    val base = "/admin/${page.kindPath}/${page.id}"
    p(classes = "admin-back") { a(href = base) { +"← ${page.title}" } }
    h1 { +"Фотографии" }

    PHOTO_FLASH[page.flashCode]?.let { message ->
        p(classes = "admin-flash") {
            attributes["role"] = "status"
            +message
        }
    }
    if (page.addedCount > 0) {
        p(classes = "admin-flash") {
            attributes["role"] = "status"
            +"Добавлено фото: ${page.addedCount}."
        }
    }
    if (page.problems.isNotEmpty()) {
        div(classes = "admin-alert") {
            attributes["role"] = "alert"
            p { +"Не всё загрузилось:" }
            ul { page.problems.forEach { li { +it } } }
        }
    }

    // The token field comes first on purpose: the server reads it before the first file.
    form(action = "$base/photos", encType = FormEncType.multipartFormData, method = FormMethod.post, classes = "admin-form") {
        csrfField(page.csrf)
        div(classes = "field") {
            label { htmlFor = "f-photos"; +"Добавить фото" }
            input(type = InputType.file, name = "photos") {
                id = "f-photos"
                attributes["multiple"] = "multiple"
                attributes["accept"] = "image/jpeg,image/png"
            }
            p(classes = "field-hint") {
                +"JPEG или PNG, до 8 МБ каждый, до 5 файлов за раз, всего до 12 фото у одной записи. "
                +"Снимок с iPhone выбирайте как JPEG («Самые совместимые»). Место съёмки из файла удаляется."
            }
        }
        div(classes = "form-actions") {
            button(type = ButtonType.submit, classes = "button") { +"Загрузить" }
        }
    }

    h2 { +"Загружено: ${page.photoUrls.size}" }
    p(classes = "admin-note") {
        +"Первое фото — обложка: оно стоит на карточке в списке и открывает страницу. Выбирайте кадр, где хорошо видно мордочку "
        +"и ничто не закрывает животное (решётка, сетка вольера)."
    }
    if (page.photoUrls.isEmpty()) {
        p(classes = "admin-note") { +"Фотографий пока нет: на сайте показывается заглушка." }
    }
    ul(classes = "photo-grid") {
        page.photoUrls.forEachIndexed { index, url ->
            li(classes = "photo-tile") {
                img(src = cardPhotoUrl(url), alt = "Фото ${index + 1}", classes = "photo-thumb")
                span(classes = if (index == 0) "badge badge-published" else "badge") {
                    +(if (index == 0) "Обложка" else "Фото ${index + 1}")
                }
                form(action = "$base/photos/update", method = FormMethod.post, classes = "photo-actions") {
                    csrfField(page.csrf)
                    input(type = InputType.hidden, name = "photo") { value = url }
                    if (index > 0) photoButton("left", "← Раньше")
                    if (index < page.photoUrls.lastIndex) photoButton("right", "Позже →")
                    if (index > 0) photoButton("cover", "Сделать обложкой")
                    // Not a button: deleting asks first, on a page of its own (see adminPhotoDeleteConfirmPage).
                    a(
                        href = "$base/photos/delete?photo=" + URLEncoder.encode(url, Charsets.UTF_8),
                        classes = "link-button photo-action photo-delete"
                    ) { +"Удалить" }
                }
            }
        }
    }
    storageNote(page.usedBytes)
}
