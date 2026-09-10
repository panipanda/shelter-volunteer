package org.proanima.shelter.model

enum class AppLocale(val code: String) {
    RU("ru"),
    EN("en"),
    SR("sr");

    companion object {
        val default = RU

        fun fromCode(code: String?): AppLocale? = entries.firstOrNull { it.code == code }
    }
}
