package org.proanima.shelter.views

fun renderNavigation(): String =
    """
    <nav>
        <a href="/cats">Cats</a> |
        <a href="/guide">Volunteer guide</a> |
        <a href="/visits">Upcoming visits</a> |
        <a href="/visits/archive">Visit archive</a>
    </nav>
    """.trimIndent()