package org.proanima.shelter.admin

import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.Cat
import org.proanima.shelter.model.CatLocation
import org.proanima.shelter.model.Dog
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.service.petSlug
import java.time.LocalDate

// The admin panel is in Russian; the texts of the site are kept in all three languages.
val CONTENT_LANGUAGES = listOf("ru", "en", "sr")

private const val MAX_NAME = 80
private const val MAX_AGE_TEXT = 80
private const val MAX_DESCRIPTION = 3000
private const val MAX_FACT = 1500
private const val EARLIEST_BIRTH_YEAR = 2000

// A form is a flat map of field name to the text the person typed: "name_ru", "name_en", "birthYear", ...
// It is kept as typed, so that a form with a mistake can be shown again with everything still in it.
// errors: field name to a message in Russian.
class FormResult<T>(val value: T?, val errors: Map<String, String>) {
    val ok: Boolean get() = value != null && errors.isEmpty()
}

// The site prints a description as one paragraph, so line breaks would only be lost; they become a space.
fun cleanText(raw: String): String = raw.replace(Regex("\\s*[\\r\\n]+\\s*"), " ").trim()

// Languages in which some text has its Russian version but not this one.
fun translationGaps(texts: List<LocalizedText?>): List<String> =
    CONTENT_LANGUAGES.filter { language ->
        language != "ru" && texts.any { text -> text != null && !text.ru.isNullOrBlank() && valueIn(text, language).isNullOrBlank() }
    }

fun valueIn(text: LocalizedText?, language: String): String? = when (language) {
    "ru" -> text?.ru
    "en" -> text?.en
    "sr" -> text?.sr
    else -> null
}

internal class FormReader(private val values: Map<String, String>) {
    val errors = linkedMapOf<String, String>()

    fun text(name: String): String = cleanText(values[name].orEmpty())

    fun fail(name: String, message: String) {
        errors.putIfAbsent(name, message)
    }

    // The three language versions of one text. Null when all three are empty. `clean` says what is done to what was
    // typed: by default line breaks become spaces; a long text keeps its paragraphs.
    fun localized(base: String, maxLength: Int, requireRu: Boolean, clean: (String) -> String = ::cleanText): LocalizedText? {
        val texts = CONTENT_LANGUAGES.associateWith { clean(values["${base}_$it"].orEmpty()) }
        texts.forEach { (language, text) ->
            if (text.length > maxLength) {
                fail("${base}_$language", "Слишком длинный текст: не больше $maxLength знаков.")
            }
        }
        if (requireRu && texts.getValue("ru").isEmpty()) {
            fail("${base}_ru", "Заполните русский текст.")
        }
        if (texts.values.all { it.isEmpty() }) {
            return null
        }
        return LocalizedText(
            ru = texts.getValue("ru").ifEmpty { null },
            en = texts.getValue("en").ifEmpty { null },
            sr = texts.getValue("sr").ifEmpty { null }
        )
    }

    inline fun <reified E : Enum<E>> choice(name: String, default: E): E {
        val entered = text(name)
        return enumValues<E>().firstOrNull { it.name == entered } ?: default.also { fail(name, "Выберите значение из списка.") }
    }

    fun published(): Boolean = when (text("published")) {
        "true" -> true
        "false" -> false
        else -> false.also { fail("published", "Выберите статус из списка.") }
    }

    // A yes/no choice made with a select ("true" or "false").
    fun yesNo(name: String): Boolean = when (text(name)) {
        "true" -> true
        "false" -> false
        else -> false.also { fail(name, "Выберите «Да» или «Нет».") }
    }

    // A checkbox: ticked sends its value, unticked sends nothing.
    fun ticked(name: String): Boolean = text(name) == "true"

    // The birth year and month of a cat or a dog; both may be empty, a month needs a year, and neither may be in the
    // future. A mistake is recorded under the field it belongs to and the value comes back null.
    fun birth(today: LocalDate): Pair<Int?, Int?> {
        val year = text("birthYear").takeIf { it.isNotEmpty() }?.let { entered ->
            entered.toIntOrNull()?.takeIf { it in EARLIEST_BIRTH_YEAR..today.year }
                ?: null.also { fail("birthYear", "Укажите число от $EARLIEST_BIRTH_YEAR до ${today.year}.") }
        }
        val month = text("birthMonth").takeIf { it.isNotEmpty() }?.let { entered ->
            entered.toIntOrNull()?.takeIf { it in 1..12 }
                ?: null.also { fail("birthMonth", "Выберите месяц из списка.") }
        }
        if (month != null && year == null && "birthYear" !in errors) {
            fail("birthMonth", "Месяц без года указать нельзя.")
        }
        if (year != null && month != null && (year > today.year || (year == today.year && month > today.monthValue))) {
            fail("birthMonth", "Дата рождения не может быть в будущем.")
        }
        return year to month
    }
}

internal fun localizedValues(base: String, text: LocalizedText?): List<Pair<String, String>> =
    CONTENT_LANGUAGES.map { "${base}_$it" to valueIn(text, it).orEmpty() }

// A long text keeps its paragraphs (a blank line between them); everything else about its spacing is tidied.
internal fun cleanParagraphs(raw: String): String =
    raw.replace("\r\n", "\n").replace('\r', '\n').lines().joinToString("\n") { it.trim() }.replace(Regex("\n{3,}"), "\n\n").trim()

// A markdown text keeps its line breaks as they are (a list is lines), only the ends are tidied.
internal fun cleanMarkdown(raw: String): String =
    raw.replace("\r\n", "\n").replace('\r', '\n').lines().joinToString("\n") { it.trimEnd() }.trim()

// The address of a pet's page is made from its English name. Two pets with the same English name would
// share one address and the second would never be reachable.
private fun FormReader.checkSlug(name: LocalizedText?, otherSlugs: List<String>, id: Int, field: String) {
    val english = name?.en
    if (english.isNullOrBlank()) {
        return
    }
    val slug = petSlug(english, id)
    if (slug in otherSlugs) {
        fail(field, "Страница с таким адресом уже есть: английское имя совпадает с чьим-то ещё. Добавьте к нему, например, цифру.")
    }
}

object CatForm {

    fun newValues(): Map<String, String> = valuesOf(null) + mapOf(
        "location" to CatLocation.IN_SHELTER.name,
        "adoptionStage" to AdoptionStage.FOR_ADOPTION.name,
        "published" to "false"
    )

    fun valuesOf(cat: Cat?): Map<String, String> = buildMap {
        putAll(localizedValues("name", cat?.name))
        put("birthYear", cat?.birthYear?.toString().orEmpty())
        put("birthMonth", cat?.birthMonth?.toString().orEmpty())
        putAll(localizedValues("description", cat?.description))
        putAll(localizedValues("medicalStatus", cat?.medicalStatus))
        putAll(localizedValues("adoptionInstruction", cat?.adoptionInstruction))
        put("location", (cat?.location ?: CatLocation.IN_SHELTER).name)
        put("adoptionStage", (cat?.adoptionStage ?: AdoptionStage.NONE).name)
        put("published", (cat?.published ?: true).toString())
    }

    // existing: the cat being edited (null for a new one); others: every other cat, drafts included.
    // The photos and the creation time are not in the form and stay as they were.
    fun validate(values: Map<String, String>, existing: Cat?, others: List<Cat>, now: String, today: LocalDate): FormResult<Cat> {
        val form = FormReader(values)
        val name = form.localized("name", MAX_NAME, requireRu = false)
        val description = form.localized("description", MAX_DESCRIPTION, requireRu = true)
        val medicalStatus = form.localized("medicalStatus", MAX_FACT, requireRu = false)
        val adoptionInstruction = form.localized("adoptionInstruction", MAX_FACT, requireRu = false)
        val location = form.choice("location", CatLocation.IN_SHELTER)
        val stage = form.choice("adoptionStage", AdoptionStage.NONE)
        val published = form.published()
        form.checkSlug(name, others.map { petSlug(it.name?.en, it.id) }, existing?.id ?: 0, "name_en")
        val (year, month) = form.birth(today)

        if (form.errors.isNotEmpty()) {
            return FormResult(null, form.errors)
        }
        return FormResult(
            Cat(
                id = existing?.id ?: 0,
                name = name,
                birthYear = year,
                birthMonth = month,
                description = description ?: LocalizedText(),
                medicalStatus = medicalStatus,
                photoUrls = existing?.photoUrls.orEmpty(),
                location = location,
                adoptionStage = stage,
                adoptionInstruction = adoptionInstruction,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                published = published
            ),
            emptyMap()
        )
    }
}

// The same fields as a cat's, in the same order; the differences are that a dog's name is required (its page
// address is made from it) and that a dog also has its age in words, for a dog whose birth year nobody knows.
object DogForm {

    fun newValues(): Map<String, String> = valuesOf(null) + mapOf(
        "location" to CatLocation.IN_SHELTER.name,
        "adoptionStage" to AdoptionStage.FOR_ADOPTION.name,
        "published" to "false"
    )

    fun valuesOf(dog: Dog?): Map<String, String> = buildMap {
        putAll(localizedValues("name", dog?.name))
        put("birthYear", dog?.birthYear?.toString().orEmpty())
        put("birthMonth", dog?.birthMonth?.toString().orEmpty())
        putAll(localizedValues("age", dog?.age))
        putAll(localizedValues("description", dog?.description))
        putAll(localizedValues("medicalStatus", dog?.medicalStatus))
        putAll(localizedValues("adoptionInstruction", dog?.adoptionInstruction))
        put("location", (dog?.location ?: CatLocation.IN_SHELTER).name)
        put("adoptionStage", (dog?.adoptionStage ?: AdoptionStage.NONE).name)
        put("published", (dog?.published ?: true).toString())
    }

    fun validate(values: Map<String, String>, existing: Dog?, others: List<Dog>, today: LocalDate): FormResult<Dog> {
        val form = FormReader(values)
        val name = form.localized("name", MAX_NAME, requireRu = true)
        val age = form.localized("age", MAX_AGE_TEXT, requireRu = false)
        val description = form.localized("description", MAX_DESCRIPTION, requireRu = true)
        val medicalStatus = form.localized("medicalStatus", MAX_FACT, requireRu = false)
        val adoptionInstruction = form.localized("adoptionInstruction", MAX_FACT, requireRu = false)
        val location = form.choice("location", CatLocation.IN_SHELTER)
        val stage = form.choice("adoptionStage", AdoptionStage.NONE)
        val published = form.published()
        form.checkSlug(name, others.map { petSlug(it.name.en, it.id) }, existing?.id ?: 0, "name_en")
        val (year, month) = form.birth(today)

        if (form.errors.isNotEmpty()) {
            return FormResult(null, form.errors)
        }
        return FormResult(
            Dog(
                id = existing?.id ?: 0,
                name = name ?: LocalizedText(),
                birthYear = year,
                birthMonth = month,
                age = age,
                description = description ?: LocalizedText(),
                medicalStatus = medicalStatus,
                photoUrls = existing?.photoUrls.orEmpty(),
                location = location,
                adoptionStage = stage,
                adoptionInstruction = adoptionInstruction,
                published = published
            ),
            emptyMap()
        )
    }
}
