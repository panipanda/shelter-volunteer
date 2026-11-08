package org.proanima.shelter.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DatabaseUrlTest {

    @Test
    fun `a Neon connection string is split into a JDBC url and credentials`() {
        val config = parseDatabaseUrl("postgresql://shelter_owner:s3cret@ep-cool-123456.eu-central-1.aws.neon.tech/shelter?sslmode=require")

        assertEquals("jdbc:postgresql://ep-cool-123456.eu-central-1.aws.neon.tech:5432/shelter?sslmode=require", config.jdbcUrl)
        assertEquals("shelter_owner", config.username)
        assertEquals("s3cret", config.password)
    }

    @Test
    fun `the postgres scheme and an explicit port work too`() {
        val config = parseDatabaseUrl("postgres://user:pw@db.example.org:6543/app")

        assertEquals("jdbc:postgresql://db.example.org:6543/app", config.jdbcUrl)
    }

    @Test
    fun `channel_binding is dropped and other options stay`() {
        val config = parseDatabaseUrl("postgresql://u:p@h.example.org/db?sslmode=require&channel_binding=require&connect_timeout=5")

        assertEquals("jdbc:postgresql://h.example.org:5432/db?sslmode=require&connect_timeout=5", config.jdbcUrl)
    }

    @Test
    fun `percent signs in the password are decoded and a plus stays a plus`() {
        val config = parseDatabaseUrl("postgresql://u:a%40b%2Fc+d@h.example.org/db")

        assertEquals("a@b/c+d", config.password)
    }

    @Test
    fun `quotes left over from a copied env file line are ignored`() {
        listOf("\"postgresql://u:p@h.example.org/db?sslmode=require\"", "'postgresql://u:p@h.example.org/db?sslmode=require'", "  postgresql://u:p@h.example.org/db?sslmode=require \n").forEach { value ->
            assertEquals("jdbc:postgresql://h.example.org:5432/db?sslmode=require", parseDatabaseUrl(value).jdbcUrl, value)
        }
    }

    @Test
    fun `a JDBC url is passed through`() {
        val config = parseDatabaseUrl("jdbc:h2:mem:x")

        assertEquals("jdbc:h2:mem:x", config.jdbcUrl)
        assertNull(config.username)
    }

    @Test
    fun `wrong strings are refused without repeating the password`() {
        listOf("mysql://u:topsecret@h/db", "postgresql://u:topsecret@/db", "postgresql://u:topsecret@h", "not a url with topsecret").forEach { url ->
            val error = assertFailsWith<IllegalArgumentException>(url) { parseDatabaseUrl(url) }
            assertFalse("topsecret" in error.message.orEmpty(), error.message)
        }
    }
}
