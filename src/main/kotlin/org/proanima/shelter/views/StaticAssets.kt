package org.proanima.shelter.views

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

// The stylesheets are cached by browsers for an hour (see CachingHeaders in Application.kt). Under a fixed address
// that means a page changed in a deploy can meet the previous stylesheet for up to an hour: new markup, old
// styles. The address therefore carries a short fingerprint of the file, "/styles/main.css?v=1a2b3c4d": a changed
// file is a new address and is fetched at once, an unchanged one stays cached.
object StaticAssets {

    private val versions = ConcurrentHashMap<String, String>()

    // path: where the file is served, for example "/styles/main.css". A file that cannot be found keeps its plain
    // address, so a missing file never breaks a page.
    fun versioned(path: String): String {
        val version = versions.getOrPut(path) { fingerprint(path) }
        return if (version.isEmpty()) path else "$path?v=$version"
    }

    private fun fingerprint(path: String): String {
        val bytes = StaticAssets::class.java.getResourceAsStream("/static$path")?.use { it.readBytes() } ?: return ""
        return MessageDigest.getInstance("SHA-256").digest(bytes).take(4).joinToString("") { "%02x".format(it) }
    }
}
