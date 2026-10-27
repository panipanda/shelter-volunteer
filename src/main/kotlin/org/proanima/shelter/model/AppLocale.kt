package org.proanima.shelter.model

// nativeName is the language's name in itself: it is what a reader of that language recognises in the switcher.
enum class AppLocale(val code: String, val nativeName: String) {
    RU("ru", "Русский"),
    EN("en", "English"),
    SR("sr", "Srpski");

    companion object {
        val default = RU
    }
}
