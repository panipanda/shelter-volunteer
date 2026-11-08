package org.proanima.shelter.model

import kotlinx.serialization.Serializable

@Serializable
data class LocalizedText(
    val ru: String? = null,
    val en: String? = null,
    val sr: String? = null
) {
    // ru takes priority, but en is the most complete at launch, so the fallback goes through it
    fun forLocale(locale: AppLocale): String =
        valueFor(locale) ?: en ?: ru ?: sr ?: ""

    // Whether there is a text written for this language itself, not one borrowed from another by forLocale.
    fun isFilledIn(locale: AppLocale): Boolean = !valueFor(locale).isNullOrBlank()

    private fun valueFor(locale: AppLocale): String? = when (locale) {
        AppLocale.RU -> ru
        AppLocale.EN -> en
        AppLocale.SR -> sr
    }
}
