package org.proanima.shelter.admin

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.sessions.SessionSerializer
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

private const val MIN_PASSWORD_LENGTH = 12
private const val MIN_SECRET_LENGTH = 32

// The admin panel is on only when it has both a password and a secret for signing the session cookie.
// secureCookie is true everywhere except a local run over plain http (ADMIN_COOKIE_SECURE=false).
class AdminConfig(val password: String, val sessionSecret: String, val secureCookie: Boolean = true)

// No variables at all means "no admin panel". One of the two, or a weak value, is a mistake that must be
// seen at once, so it stops the start (a failed deploy leaves the previous version running). Messages never
// contain the values.
fun adminConfigFromEnv(env: (String) -> String? = System::getenv): AdminConfig? {
    val password = env("ADMIN_PASSWORD")?.takeIf { it.isNotBlank() }
    val secret = env("SESSION_SECRET")?.takeIf { it.isNotBlank() }
    if (password == null && secret == null) {
        return null
    }
    check(password != null && secret != null) { "ADMIN_PASSWORD and SESSION_SECRET must be set together" }
    check(password.length >= MIN_PASSWORD_LENGTH) { "ADMIN_PASSWORD is too short: use at least $MIN_PASSWORD_LENGTH characters" }
    check(secret.length >= MIN_SECRET_LENGTH) { "SESSION_SECRET is too short: use at least $MIN_SECRET_LENGTH characters" }
    return AdminConfig(password, secret, secureCookie = env("ADMIN_COOKIE_SECURE")?.trim()?.lowercase() != "false")
}

// What the signed cookie holds: a random token for the forms (so that a foreign site cannot post to the
// admin on the person's behalf) and when the login happened (so that a session ends after SESSION_LIFETIME).
class AdminSession(val csrf: String, val issuedAtEpochSecond: Long)

val SESSION_LIFETIME: Duration = Duration.ofHours(12)

// Plain text instead of reflection: ktor's default session serializer reads class metadata with the
// kotlin-reflect that ktor ships, which is older than this project's Kotlin.
private object AdminSessionSerializer : SessionSerializer<AdminSession> {
    override fun serialize(session: AdminSession): String = "${session.csrf}.${session.issuedAtEpochSecond}"

    override fun deserialize(text: String): AdminSession {
        val (csrf, issuedAt) = text.split('.', limit = 2)
        return AdminSession(csrf, issuedAt.toLong())
    }
}

fun Application.installAdminSessions(config: AdminConfig) {
    install(Sessions) {
        cookie<AdminSession>(SESSION_COOKIE) {
            serializer = AdminSessionSerializer
            transform(SessionTransportTransformerMessageAuthentication(sha256(config.sessionSecret)))
            cookie.path = "/admin"
            cookie.httpOnly = true
            cookie.secure = config.secureCookie
            cookie.maxAgeInSeconds = SESSION_LIFETIME.seconds
            cookie.extensions["SameSite"] = "Lax"
        }
    }
}

const val SESSION_COOKIE = "shelter_admin"

private val random = SecureRandom()

fun newCsrfToken(): String {
    val bytes = ByteArray(32).also(random::nextBytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

// Both sides are hashed first, so the comparison takes the same time whatever the password is and
// whatever its length.
fun passwordMatches(entered: String, expected: String): Boolean =
    MessageDigest.isEqual(sha256(entered), sha256(expected))

fun tokensMatch(sent: String?, expected: String): Boolean =
    sent != null && MessageDigest.isEqual(sha256(sent), sha256(expected))

private fun sha256(text: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())

// A wrong password costs one second, and the wrong attempts of everyone go through the same gate one at a
// time: guessing is limited to about one try a second in total, however many requests arrive. The correct
// password never waits, so a bot hammering the page cannot lock the administrator out. When more than
// maxWaiting wrong attempts are already queued, the next ones are turned away at once.
class FailedLoginThrottle(private val penaltyMillis: Long = 1000, private val maxWaiting: Int = 5) {
    private val gate = Mutex()
    private val waiting = AtomicInteger()

    // false: too many attempts are waiting, answer "try later" without waiting.
    suspend fun penalize(): Boolean {
        if (waiting.incrementAndGet() > maxWaiting) {
            waiting.decrementAndGet()
            return false
        }
        try {
            gate.withLock { delay(penaltyMillis) }
        } finally {
            waiting.decrementAndGet()
        }
        return true
    }
}
