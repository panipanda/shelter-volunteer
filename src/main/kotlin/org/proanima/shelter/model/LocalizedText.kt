package org.proanima.shelter.model

import kotlinx.serialization.Serializable

@Serializable
data class LocalizedText(
    val ru: String? = null,
    val en: String? = null,
    val sr: String? = null
) {
    // ru в приоритете, но на старте полнее всего заполнен en — фолбэк идёт через него
    fun forLocale(locale: AppLocale): String =
        valueFor(locale) ?: en ?: ru ?: sr ?: ""

    private fun valueFor(locale: AppLocale): String? = when (locale) {
        AppLocale.RU -> ru
        AppLocale.EN -> en
        AppLocale.SR -> sr
    }
}
