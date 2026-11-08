package org.proanima.shelter.admin

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminAuthTest {

    private val password = "a-long-enough-password"
    private val secret = "s".repeat(32)

    private fun env(vararg pairs: Pair<String, String>): (String) -> String? = pairs.toMap()::get

    @Test
    fun `no variables means no admin panel`() {
        assertNull(adminConfigFromEnv(env()))
        assertNull(adminConfigFromEnv(env("ADMIN_PASSWORD" to "  ", "SESSION_SECRET" to "")))
    }

    @Test
    fun `both variables make a config, and the cookie is secure unless switched off`() {
        val config = adminConfigFromEnv(env("ADMIN_PASSWORD" to password, "SESSION_SECRET" to secret))!!
        assertEquals(password, config.password)
        assertTrue(config.secureCookie)

        val local = adminConfigFromEnv(env("ADMIN_PASSWORD" to password, "SESSION_SECRET" to secret, "ADMIN_COOKIE_SECURE" to "false"))!!
        assertFalse(local.secureCookie)
    }

    @Test
    fun `one variable, or a weak one, stops the start without printing the value`() {
        listOf(
            env("ADMIN_PASSWORD" to password),
            env("SESSION_SECRET" to secret),
            env("ADMIN_PASSWORD" to "short", "SESSION_SECRET" to secret),
            env("ADMIN_PASSWORD" to password, "SESSION_SECRET" to "tooshort")
        ).forEach { variables ->
            val error = assertFailsWith<IllegalStateException> { adminConfigFromEnv(variables) }
            assertFalse(password in error.message.orEmpty() || "short" in error.message.orEmpty().replace("too short", ""), error.message)
        }
    }

    @Test
    fun `passwords and tokens are compared as a whole`() {
        assertTrue(passwordMatches(password, password))
        assertFalse(passwordMatches(password + "x", password))
        assertFalse(passwordMatches("", password))
        assertTrue(tokensMatch("abc", "abc"))
        assertFalse(tokensMatch(null, "abc"))
        assertFalse(tokensMatch("abd", "abc"))
    }

    @Test
    fun `form tokens are long and different every time`() {
        val tokens = (1..50).map { newCsrfToken() }.toSet()

        assertEquals(50, tokens.size)
        assertTrue(tokens.all { it.length >= 43 && Regex("[A-Za-z0-9_-]+").matches(it) })
        assertNotEquals(newCsrfToken(), newCsrfToken())
    }

    @Test
    fun `the throttle lets a few wrong attempts wait their turn and turns the rest away`() = runBlocking {
        val throttle = FailedLoginThrottle(penaltyMillis = 150, maxWaiting = 2)

        val results = (1..5).map { async { throttle.penalize() } }.awaitAll()

        assertEquals(2, results.count { it })
        assertEquals(3, results.count { !it })
        // Once the queue has drained, attempts are accepted again.
        assertTrue(throttle.penalize())
    }
}
