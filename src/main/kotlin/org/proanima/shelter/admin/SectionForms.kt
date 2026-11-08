package org.proanima.shelter.admin

import org.proanima.shelter.model.ChoiceOption
import org.proanima.shelter.model.FieldType
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.model.Section
import org.proanima.shelter.model.SectionField
import org.proanima.shelter.model.SectionItem
import org.proanima.shelter.model.SectionLayout
import org.proanima.shelter.service.itemSlug
import org.proanima.shelter.service.isSafeGuideLink
import org.proanima.shelter.service.petSlug
import java.time.DateTimeException
import java.time.LocalDate

// The forms of the sections: the section's settings, one of its fields, its tags and one of its entries. Like the
// cat and dog forms, each is a flat map of field name to what was typed, read back into the model; a form with a
// mistake comes back as typed, with the mistakes by field name.

// With drop-downs the menu has room for many; this only stops a runaway.
const val MAX_SECTIONS = 12
const val MAX_FIELDS = 12
const val MAX_TAGS = 24
const val MAX_CHOICE_OPTIONS = 12

private const val MAX_SECTION_TITLE = 80
private const val MAX_MENU_TITLE = 24
private const val MAX_INTRO = 600
private const val MAX_SLUG = 40
private const val MAX_FIELD_LABEL = 60
private const val MAX_OPTION_NAME = 60
private const val MAX_ITEM_TITLE = 120
private const val MAX_TEXT_VALUE = 300
private const val MAX_LONG_TEXT = 3_000
private const val MAX_MARKDOWN_TEXT = 20_000
private const val EARLIEST_YEAR = 2000
private const val LATEST_YEAR = 2100
private const val MAX_KEY_LENGTH = 30

// The addresses that already mean something on the site.
private val RESERVED_SLUGS = setOf("cats", "dogs", "guide", "visits", "help")

private val SLUG_PATTERN = Regex("[a-z0-9]+(-[a-z0-9]+)*")

private fun slugOf(text: String?): String =
    text.orEmpty().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(MAX_KEY_LENGTH).trim('-')

object SectionForm {

    fun valuesOf(section: Section?): Map<String, String> = buildMap {
        putAll(localizedValues("title", section?.title))
        putAll(localizedValues("menuTitle", section?.menuTitle))
        putAll(localizedValues("intro", section?.intro))
        put("slug", section?.slug.orEmpty())
        put("layout", (section?.layout ?: SectionLayout.CARDS).name)
        put("useDate", (section?.useDate ?: false).toString())
        put("usePhotos", (section?.usePhotos ?: true).toString())
    }

    // A new section starts from a preset (its fields and tags come along). Where it stands in the menu is the menu's
    // business (see SectionPlace), not a setting of the section.
    fun newValues(preset: Section): Map<String, String> = valuesOf(preset)

    // existing: the section being edited (null for a new one); template: what a new one is made from (its fields and
    // tags); others: every other section.
    fun validate(values: Map<String, String>, existing: Section?, template: Section, others: List<Section>): FormResult<Section> {
        val form = FormReader(values)
        val title = form.localized("title", MAX_SECTION_TITLE, requireRu = true)
        val menuTitle = form.localized("menuTitle", MAX_MENU_TITLE, requireRu = false)
        val intro = form.localized("intro", MAX_INTRO, requireRu = false)
        val layout = form.choice("layout", SectionLayout.CARDS)
        val useDate = form.yesNo("useDate")
        val usePhotos = form.yesNo("usePhotos")
        val slug = form.text("slug").lowercase().ifEmpty { slugOf(title?.en) }
        when {
            slug.isEmpty() -> form.fail("slug", "Укажите адрес латиницей, например articles. Или заполните английское название: адрес возьмётся из него.")
            slug.length > MAX_SLUG || !SLUG_PATTERN.matches(slug) ->
                form.fail("slug", "Адрес: латинские буквы и цифры, слова через дефис, не длиннее $MAX_SLUG знаков.")
            slug in RESERVED_SLUGS -> form.fail("slug", "Адрес «$slug» уже занят страницей сайта. Выберите другой.")
            others.any { it.slug == slug } -> form.fail("slug", "Раздел с таким адресом уже есть.")
        }
        if (existing == null && others.size >= MAX_SECTIONS) {
            form.fail("title_ru", "Разделов уже $MAX_SECTIONS: больше создать нельзя. Уберите ненужный раздел.")
        }

        if (form.errors.isNotEmpty()) {
            return FormResult(null, form.errors)
        }
        val base = existing ?: template
        return FormResult(
            base.copy(
                id = existing?.id ?: 0,
                slug = slug,
                title = title ?: LocalizedText(),
                menuTitle = menuTitle,
                intro = intro,
                layout = layout,
                // The order the menu uses until its tree is saved: the sections as they were made.
                position = existing?.position ?: ((others.maxOfOrNull { it.position } ?: 0) + 1),
                useDate = useDate,
                usePhotos = usePhotos
            ),
            emptyMap()
        )
    }
}

// Rows of options, for the tags of a section and for the choices of a choice field: "opt_1_ru", "opt_1_en", "opt_1_sr"
// and the key the row had ("opt_1_key"). A row with no name at all is not an option (clearing a row removes the
// option); a row's key stays with it whatever its name is changed to.
internal object OptionRows {
    const val EXTRA_ROWS = 3

    fun valuesOf(options: List<ChoiceOption>, extraRows: Int = EXTRA_ROWS): Map<String, String> = buildMap {
        options.forEachIndexed { index, option -> putRow(index + 1, option) }
        repeat(extraRows) { putRow(options.size + it + 1, null) }
    }

    private fun MutableMap<String, String>.putRow(number: Int, option: ChoiceOption?) {
        put("opt_${number}_key", option?.key.orEmpty())
        localizedValues("opt_$number", option?.name).forEach { (name, value) -> put(name, value) }
    }

    // The rows to draw: the numbers present in the values.
    fun rowNumbers(values: Map<String, String>): List<Int> =
        values.keys.mapNotNull { Regex("opt_(\\d+)_key").matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }.sorted()

    // The options typed, with keys for the new ones; the counter has moved on past the keys it made.
    class Parsed(val options: List<ChoiceOption>, val nextKeyNumber: Int)

    fun parse(form: FormReader, values: Map<String, String>, existing: List<ChoiceOption>, nextKeyNumber: Int, max: Int, what: String): Parsed {
        val known = existing.map { it.key }.toSet()
        val taken = mutableSetOf<String>()
        var counter = nextKeyNumber
        val result = mutableListOf<ChoiceOption>()
        rowNumbers(values).forEach { number ->
            val name = form.localized("opt_$number", MAX_OPTION_NAME, requireRu = false) ?: return@forEach
            if (name.ru.isNullOrBlank()) {
                form.fail("opt_${number}_ru", "Заполните русское название.")
            }
            val typedKey = values["opt_${number}_key"].orEmpty()
            val key = if (typedKey in known && taken.add(typedKey)) {
                typedKey
            } else {
                // Not a key this list ever had: a new one. The English name makes a readable one.
                val readable = slugOf(name.en)
                val fresh = if (readable.isNotEmpty() && readable !in known && readable !in taken) readable else "o${counter++}"
                taken += fresh
                fresh
            }
            result += ChoiceOption(key, name)
        }
        if (result.size > max) {
            form.fail("opt_1_ru", "Слишком много: $what — не больше $max.")
        }
        return Parsed(result, counter)
    }
}

object TagsForm {

    fun valuesOf(section: Section): Map<String, String> = OptionRows.valuesOf(section.tags)

    fun validate(values: Map<String, String>, section: Section): FormResult<Section> {
        val form = FormReader(values)
        val parsed = OptionRows.parse(form, values, section.tags, section.nextKeyNumber, MAX_TAGS, "меток")
        if (form.errors.isNotEmpty()) {
            return FormResult(null, form.errors)
        }
        return FormResult(section.copy(tags = parsed.options, nextKeyNumber = parsed.nextKeyNumber), emptyMap())
    }
}

object FieldForm {

    fun newValues(): Map<String, String> = valuesOf(null) + mapOf("type" to FieldType.TEXT.name, "inList" to "false", "showLabel" to "true")

    fun valuesOf(field: SectionField?): Map<String, String> = buildMap {
        putAll(localizedValues("label", field?.label))
        put("type", (field?.type ?: FieldType.TEXT).name)
        put("required", (field?.required ?: false).toString())
        put("inList", (field?.inList ?: false).toString())
        put("showLabel", (field?.showLabel ?: true).toString())
        putAll(OptionRows.valuesOf(field?.options.orEmpty()))
    }

    // existing: the field being edited (null for a new one); its type cannot change, since the entries already hold
    // values of that type. The result is the section with the field added or replaced.
    fun validate(values: Map<String, String>, section: Section, existing: SectionField?): FormResult<Section> {
        val form = FormReader(values)
        val label = form.localized("label", MAX_FIELD_LABEL, requireRu = true)
        val type = existing?.type ?: form.choice("type", FieldType.TEXT)
        val required = form.ticked("required")
        val inList = form.ticked("inList")
        val showLabel = form.ticked("showLabel")
        if (inList && type == FieldType.MARKDOWN) {
            form.fail("inList", "Текст с оформлением в списке не показывается: для списка возьмите «Несколько предложений».")
        }
        if (existing == null && section.fields.size >= MAX_FIELDS) {
            form.fail("label_ru", "Полей уже $MAX_FIELDS: больше форма не вместит. Уберите ненужное.")
        }

        var options = emptyList<ChoiceOption>()
        var nextKeyNumber = section.nextKeyNumber
        if (type == FieldType.CHOICE) {
            val parsed = OptionRows.parse(form, values, existing?.options.orEmpty(), nextKeyNumber, MAX_CHOICE_OPTIONS, "вариантов")
            options = parsed.options
            nextKeyNumber = parsed.nextKeyNumber
            if (options.size < 2 && form.errors.keys.none { it.startsWith("opt_") }) {
                form.fail("opt_1_ru", "Нужно хотя бы два варианта, из которых выбирают.")
            }
        }

        if (form.errors.isNotEmpty()) {
            return FormResult(null, form.errors)
        }
        val key = existing?.key ?: "f${nextKeyNumber++}"
        val field = SectionField(
            key = key,
            type = type,
            label = label ?: LocalizedText(),
            required = required,
            inList = inList,
            showLabel = showLabel,
            options = options
        )
        val fields = if (existing == null) section.fields + field else section.fields.map { if (it.key == key) field else it }
        return FormResult(section.copy(fields = fields, nextKeyNumber = nextKeyNumber), emptyMap())
    }
}

// Names of the form fields of an entry's own fields: "f_f1_ru" for a text, "f_f2" for a date or a choice.
internal fun fieldFormName(field: SectionField, language: String? = null): String =
    if (language == null) "f_${field.key}" else "f_${field.key}_$language"

internal fun tagFormName(key: String) = "tag_$key"

object ItemForm {

    fun newValues(section: Section, today: LocalDate): Map<String, String> =
        valuesOf(section, null) + mapOf("published" to "false", "date" to if (section.useDate) today.toString() else "")

    fun valuesOf(section: Section, item: SectionItem?): Map<String, String> = buildMap {
        putAll(localizedValues("title", item?.title))
        put("date", item?.date.orEmpty())
        section.fields.forEach { field ->
            when (field.type) {
                FieldType.TEXT, FieldType.LONGTEXT, FieldType.MARKDOWN ->
                    localizedValues(fieldFormName(field), item?.texts?.get(field.key)).forEach { (name, value) -> put(name, value) }
                FieldType.DATE, FieldType.CHOICE -> put(fieldFormName(field), item?.plain?.get(field.key).orEmpty())
            }
        }
        section.tags.forEach { tag -> put(tagFormName(tag.key), (item != null && tag.key in item.tags).toString()) }
        put("published", (item?.published ?: true).toString())
    }

    // existing: the entry being edited (null for a new one); others: every other entry of the section, drafts included.
    // The photos and the creation time are not in the form and stay as they were.
    fun validate(values: Map<String, String>, section: Section, existing: SectionItem?, others: List<SectionItem>, now: String): FormResult<SectionItem> {
        val form = FormReader(values)
        val title = form.localized("title", MAX_ITEM_TITLE, requireRu = true)
        val published = form.published()

        val date = if (section.useDate) readDate(form, "date", form.text("date"), required = true) else null

        val texts = linkedMapOf<String, LocalizedText>()
        val plain = linkedMapOf<String, String>()
        section.fields.forEach { field ->
            when (field.type) {
                FieldType.TEXT -> form.localized(fieldFormName(field), MAX_TEXT_VALUE, requireRu = field.required)
                FieldType.LONGTEXT -> form.localized(fieldFormName(field), MAX_LONG_TEXT, requireRu = field.required, clean = ::cleanParagraphs)
                FieldType.MARKDOWN -> markdown(form, field)
                FieldType.DATE -> {
                    readDate(form, fieldFormName(field), form.text(fieldFormName(field)), field.required)?.let { plain[field.key] = it }
                    null
                }
                FieldType.CHOICE -> {
                    val name = fieldFormName(field)
                    val chosen = form.text(name)
                    when {
                        chosen.isEmpty() -> if (field.required) form.fail(name, "Выберите значение.")
                        field.options.none { it.key == chosen } -> form.fail(name, "Выберите значение из списка.")
                        else -> plain[field.key] = chosen
                    }
                    null
                }
            }?.let { texts[field.key] = it }
        }
        val tags = section.tags.filter { form.ticked(tagFormName(it.key)) }.map { it.key }

        // The address of the entry's page is made from its English title; two entries of one section must not share it.
        val id = existing?.id ?: 0
        title?.en?.takeIf { it.isNotBlank() }?.let { english ->
            if (petSlug(english, id) in others.map { itemSlug(it) }) {
                form.fail("title_en", "Страница с таким адресом уже есть: английское название совпадает с другой записью. Добавьте к нему, например, цифру.")
            }
        }

        if (form.errors.isNotEmpty()) {
            return FormResult(null, form.errors)
        }
        return FormResult(
            SectionItem(
                id = id,
                sectionId = section.id,
                title = title ?: LocalizedText(),
                date = date,
                photoUrls = existing?.photoUrls.orEmpty(),
                tags = tags,
                texts = texts,
                plain = plain,
                published = published,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            ),
            emptyMap()
        )
    }

    private fun markdown(form: FormReader, field: SectionField): LocalizedText? {
        val text = form.localized(fieldFormName(field), MAX_MARKDOWN_TEXT, requireRu = field.required, clean = ::cleanMarkdown)
        // A link that is not a web or mail address (above all "javascript:") never gets onto the site.
        CONTENT_LANGUAGES.forEach { language ->
            val typed = valueIn(text, language).orEmpty()
            MARKDOWN_LINK.findAll(typed).map { it.groupValues[1] }.firstOrNull { !isSafeGuideLink(it) }?.let { href ->
                form.fail("${fieldFormName(field)}_$language", "Ссылка «${href.take(40)}» не подходит: адрес должен начинаться с https://")
            }
        }
        return text
    }

    private fun readDate(form: FormReader, name: String, typed: String, required: Boolean): String? {
        if (typed.isEmpty()) {
            if (required) form.fail(name, "Укажите дату.")
            return null
        }
        val date = try {
            LocalDate.parse(typed)
        } catch (e: DateTimeException) {
            form.fail(name, "Укажите дату целиком: день, месяц и год.")
            return null
        }
        if (date.year !in EARLIEST_YEAR..LATEST_YEAR) {
            form.fail(name, "Год должен быть от $EARLIEST_YEAR до $LATEST_YEAR.")
            return null
        }
        return date.toString()
    }
}
