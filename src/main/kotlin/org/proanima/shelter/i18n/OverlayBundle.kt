package org.proanima.shelter.i18n

import java.util.Collections
import java.util.Enumeration
import java.util.Locale
import java.util.ResourceBundle

private val NUMBERED_PLACEHOLDER = Regex("""\{\d""")

// The texts of a page with the ones that were edited in the admin panel laid on top. A key that was not edited
// reads exactly as it does from the properties files, so a site nobody has edited is the site it always was.
// A key that only exists in the edited texts (a list or a setting, see ListKey and PlainSetting) is found too.
internal class OverlayBundle(
    private val base: ResourceBundle,
    private val edited: Map<String, String>
) : ResourceBundle() {

    override fun handleGetObject(key: String): Any? {
        val text = edited[key] ?: return if (base.containsKey(key)) base.getObject(key) else null
        // A pattern that takes arguments goes through MessageFormat, where a single quote starts a quoted part.
        // A person typing "we're" must not make half of the sentence disappear, so the quote is doubled here.
        return if (takesArguments(key)) text.replace("'", "''") else text
    }

    override fun getKeys(): Enumeration<String> = Collections.enumeration(base.keySet() + edited.keys)

    override fun getLocale(): Locale = base.locale

    private fun takesArguments(key: String) = base.containsKey(key) && NUMBERED_PLACEHOLDER.containsMatchIn(base.getString(key))
}

// A list of items kept in one edited text, one item per line. Without an edit the items are the separate
// messages the page had before lists could be edited.
class ListKey(val key: String, val defaultKeys: List<String>)

// A value that is the same in every language (a Telegram name, a link) with the value the site has without an edit.
class PlainSetting(val key: String, val default: String)

fun ResourceBundle.lines(list: ListKey): List<String> {
    if (containsKey(list.key)) {
        val items = getString(list.key).lines().map(String::trim).filter(String::isNotEmpty)
        if (items.isNotEmpty()) {
            return items
        }
    }
    return list.defaultKeys.map { t(it) }
}

fun ResourceBundle.plain(setting: PlainSetting): String =
    if (containsKey(setting.key)) getString(setting.key) else setting.default
