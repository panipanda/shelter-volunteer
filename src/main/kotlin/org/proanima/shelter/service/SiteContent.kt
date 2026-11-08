package org.proanima.shelter.service

import org.proanima.shelter.i18n.OverlayBundle
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.model.AppLocale
import java.util.ResourceBundle
import java.util.concurrent.ConcurrentHashMap

// A text that has the same value in every language (a link, a Telegram name, a photo address) is stored with this
// language.
const val ALL_LANGUAGES = "all"

data class ContentKey(val key: String, val language: String = ALL_LANGUAGES)

// The texts and settings of the site's pages that were changed in the admin panel. Everything not in here is the
// text that comes with the code (the messages_*.properties files and content/volunteer-guide*.md). The snapshot is
// never changed after it is made: an edit makes a new one.
class SiteContent(val entries: Map<ContentKey, String>) {

    private val bundles = ConcurrentHashMap<AppLocale, ResourceBundle>()

    fun text(key: String, language: String): String? = entries[ContentKey(key, language)]

    fun plain(key: String): String? = entries[ContentKey(key, ALL_LANGUAGES)]

    // The messages of a page in this language: the edited ones where there are any, the shipped ones elsewhere.
    fun bundle(locale: AppLocale): ResourceBundle {
        if (entries.isEmpty()) {
            return messagesFor(locale)
        }
        return bundles.getOrPut(locale) {
            val edited = entries.filterKeys { it.language == locale.code || it.language == ALL_LANGUAGES }
                .mapKeys { it.key.key }
            OverlayBundle(messagesFor(locale), edited)
        }
    }

    companion object {
        val EMPTY = SiteContent(emptyMap())
    }
}
