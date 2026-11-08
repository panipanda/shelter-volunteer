package org.proanima.shelter.admin

import org.proanima.shelter.service.ALL_LANGUAGES
import org.proanima.shelter.service.ContentKey
import org.proanima.shelter.service.GuideBlock
import org.proanima.shelter.service.SiteContent
import org.proanima.shelter.service.isSafeGuideLink
import org.proanima.shelter.service.parseGuideMarkdown
import java.net.URI

// errors: the name of the form field to a message in Russian. changes: only what differs from what is stored now;
// a null text means "the text that comes with the code again".
class PageFormResult(val changes: Map<ContentKey, String?>, val errors: Map<String, String>) {
    val ok: Boolean get() = errors.isEmpty()
}

private const val MAX_WEB_ADDRESS = 300
private val TELEGRAM_NAME = Regex("^[A-Za-z0-9_]{4,32}$")
internal val MARKDOWN_LINK = Regex("""\[[^\]]+]\(([^)\s]+)\)""")

// The form of a page is a flat map of field name to the text the person typed ("home.hero.headline_ru",
// "help.donate.url"). A field with a stored text also has a checkbox "reset:<name>" that brings the shipped text back.
object PageForm {

    private const val RESET_PREFIX = "reset:"

    fun resetName(name: String) = "$RESET_PREFIX$name"

    // What the form shows: the text the site has now, edited or shipped.
    fun valuesOf(page: ContentPage, content: SiteContent, defaults: ContentDefaults): Map<String, String> = buildMap {
        page.fields.forEach { field ->
            languagesOf(field).forEach { language ->
                put(field.formName(language), content.entries[ContentKey(field.key, language)] ?: defaults.of(field, language))
            }
        }
    }

    // The fields whose text was edited (they have the "bring back the shipped text" checkbox).
    fun editedNames(page: ContentPage, content: SiteContent): Set<String> = buildSet {
        page.fields.forEach { field ->
            languagesOf(field).forEach { language ->
                if (ContentKey(field.key, language) in content.entries) add(field.formName(language))
            }
        }
    }

    fun validate(page: ContentPage, typed: Map<String, String>, defaults: ContentDefaults, current: SiteContent): PageFormResult {
        val errors = linkedMapOf<String, String>()
        val changes = linkedMapOf<ContentKey, String?>()

        page.fields.forEach { field ->
            languagesOf(field).forEach { language ->
                val name = field.formName(language)
                val reading = if (typed[resetName(name)] != null) Reading.Shipped else read(field, language, typed[name].orEmpty(), defaults)
                val key = ContentKey(field.key, language)
                when (reading) {
                    is Reading.Invalid -> errors[name] = reading.message
                    is Reading.Shipped -> if (key in current.entries) changes[key] = null
                    is Reading.Edited -> if (current.entries[key] != reading.text) changes[key] = reading.text
                }
            }
        }
        return PageFormResult(if (errors.isEmpty()) changes else emptyMap(), errors)
    }

    private fun languagesOf(field: ContentField) = if (field.localized) CONTENT_LANGUAGES else listOf(ALL_LANGUAGES)

    private sealed interface Reading {
        // Empty, or the same as the shipped text: nothing is stored and the shipped text applies.
        data object Shipped : Reading

        class Edited(val text: String) : Reading

        class Invalid(val message: String) : Reading
    }

    private fun read(field: ContentField, language: String, raw: String, defaults: ContentDefaults): Reading {
        val shipped = defaults.of(field, language)
        return when (field) {
            is ContentField.Text -> readText(field, raw, shipped)
            is ContentField.Lines -> readLines(field, raw, shipped)
            is ContentField.Markdown -> readMarkdown(field, raw, shipped)
            is ContentField.Plain -> readPlain(field, raw, shipped)
        }
    }

    private fun tooLong(max: Int) = Reading.Invalid("Слишком длинный текст: не больше $max знаков.")

    private fun readText(field: ContentField.Text, raw: String, shipped: String): Reading {
        val text = cleanText(raw)
        if (text.isEmpty()) return Reading.Shipped
        if (text.length > field.maxLength) return tooLong(field.maxLength)
        field.placeholders.forEach { placeholder ->
            if (text.split(placeholder).size != 2) {
                return Reading.Invalid("В тексте должно остаться $placeholder, ровно один раз: на это место сайт что-то подставляет.")
            }
        }
        if (field.placeholders.isNotEmpty() && field.placeholders.fold(text) { rest, placeholder -> rest.replace(placeholder, "") }.any { it == '{' || it == '}' }) {
            return Reading.Invalid("Фигурные скобки здесь нельзя, кроме ${field.placeholders.joinToString(", ")}.")
        }
        return if (text == cleanText(shipped)) Reading.Shipped else Reading.Edited(text)
    }

    private fun readLines(field: ContentField.Lines, raw: String, shipped: String): Reading {
        val items = raw.lines().map(::cleanText).filter { it.isNotEmpty() }
        if (items.isEmpty()) return Reading.Shipped
        if (items.size > field.maxItems) return Reading.Invalid("Слишком много пунктов: не больше ${field.maxItems}.")
        items.firstOrNull { it.length > field.maxItemLength }?.let {
            return Reading.Invalid("Пункт «${it.take(30)}…» длиннее ${field.maxItemLength} знаков.")
        }
        val joined = items.joinToString("\n")
        return if (joined == shipped.lines().map(::cleanText).filter { it.isNotEmpty() }.joinToString("\n")) Reading.Shipped else Reading.Edited(joined)
    }

    private fun readMarkdown(field: ContentField.Markdown, raw: String, shipped: String): Reading {
        val text = normalizeMarkdown(raw)
        if (text.isEmpty()) return Reading.Shipped
        if (text.length > field.maxLength) return tooLong(field.maxLength)
        if (parseGuideMarkdown(text).none { it is GuideBlock.Heading && it.level == 1 }) {
            return Reading.Invalid("Нужна строка-заголовок, которая начинается с «# »: на ней держатся название, оглавление и фото страницы.")
        }
        MARKDOWN_LINK.findAll(text).map { it.groupValues[1] }.firstOrNull { href -> !isSafeGuideLink(href) }?.let { href ->
            return Reading.Invalid("Ссылка «${href.take(40)}» не подходит: адрес должен начинаться с https://")
        }
        return if (text == normalizeMarkdown(shipped)) Reading.Shipped else Reading.Edited(text)
    }

    private fun readPlain(field: ContentField.Plain, raw: String, shipped: String): Reading {
        val typed = cleanText(raw)
        if (typed.isEmpty()) return Reading.Shipped
        val value = when (field.format) {
            PlainFormat.TELEGRAM_NAME -> {
                val name = typed.removePrefix("https://").removePrefix("http://").removePrefix("t.me/").removePrefix("@")
                if (!TELEGRAM_NAME.matches(name)) {
                    return Reading.Invalid("Имя в Telegram: от 4 до 32 латинских букв, цифр или подчёркиваний, без пробелов.")
                }
                name
            }
            PlainFormat.WEB_ADDRESS -> {
                val host = runCatching { URI(typed).host }.getOrNull()
                if (!typed.startsWith("https://") || typed.length > MAX_WEB_ADDRESS || host.isNullOrBlank() || typed.any { it.isWhitespace() }) {
                    return Reading.Invalid("Нужен полный адрес, который начинается с https://")
                }
                typed
            }
        }
        return if (value == shipped) Reading.Shipped else Reading.Edited(value)
    }
}
