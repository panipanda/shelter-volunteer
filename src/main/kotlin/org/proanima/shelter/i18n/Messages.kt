package org.proanima.shelter.i18n

import org.proanima.shelter.model.AppLocale
import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

fun messagesFor(locale: AppLocale): ResourceBundle =
    ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag(locale.code))

fun ResourceBundle.t(key: String, vararg args: Any): String {
    val pattern = getString(key)
    // The static MessageFormat.format(pattern, args) formats numbers using the JVM default locale,
    // not the bundle's locale, so we pass the bundle's locale explicitly; otherwise "1000" in a ru context
    // would be formatted arbitrarily depending on the host machine
    return if (args.isEmpty()) pattern else MessageFormat(pattern, locale).format(args)
}
