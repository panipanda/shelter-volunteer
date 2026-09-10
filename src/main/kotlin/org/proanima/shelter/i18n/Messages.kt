package org.proanima.shelter.i18n

import org.proanima.shelter.model.AppLocale
import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

fun messagesFor(locale: AppLocale): ResourceBundle =
    ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag(locale.code))

fun ResourceBundle.t(key: String, vararg args: Any): String {
    val pattern = getString(key)
    // MessageFormat.format(pattern, args) статик-метод форматирует числа по locale JVM по умолчанию,
    // а не по locale бандла — здесь берём locale бандла явно, иначе "1000" в ru-контексте
    // отформатируется как попало на хостовой машине
    return if (args.isEmpty()) pattern else MessageFormat(pattern, locale).format(args)
}
