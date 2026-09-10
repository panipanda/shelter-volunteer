package org.proanima.shelter.views

import org.proanima.shelter.model.AppLocale

fun renderNavigation(locale: AppLocale): String {
    val prefix = "/${locale.code}"

    return """
    <nav>
        <a href="$prefix/cats">Cats</a> |
        <a href="$prefix/guide">Volunteer guide</a> |
        <a href="$prefix/visits">Upcoming visits</a> |
        <a href="$prefix/visits/archive">Visit archive</a>
    </nav>
    """.trimIndent()
}
