package org.proanima.shelter.repository

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PgPhotoStoreTest {

    private val store = PgPhotoStore(newTestDataSource(), TestClock())

    @Test
    fun `a stored photo comes back byte for byte with its type and hash`() {
        val bytes = ByteArray(300_000) { (it % 251).toByte() }

        store.put("felix.jpg", "image/jpeg", bytes)

        val photo = store.get("felix.jpg")!!
        assertEquals("image/jpeg", photo.contentType)
        assertContentEquals(bytes, photo.bytes)
        assertEquals(sha256Hex(bytes), photo.sha256)
        assertEquals(photo.sha256, store.sha256("felix.jpg"))
    }

    @Test
    fun `the hash of known bytes is the standard SHA-256`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc".toByteArray()))
    }

    @Test
    fun `putting a name again replaces the photo and changes the hash`() {
        store.put("felix.jpg", "image/jpeg", byteArrayOf(1, 2, 3))
        val before = store.sha256("felix.jpg")

        store.put("felix.jpg", "image/png", byteArrayOf(4, 5, 6))

        assertNotEquals(before, store.sha256("felix.jpg"))
        assertEquals("image/png", store.get("felix.jpg")?.contentType)
        assertEquals(setOf("felix.jpg"), store.names())
    }

    @Test
    fun `an unknown name is null, delete tells whether it removed something`() {
        assertNull(store.get("nope.jpg"))
        assertNull(store.sha256("nope.jpg"))
        assertFalse(store.delete("nope.jpg"))

        store.put("a.jpg", "image/jpeg", byteArrayOf(1))
        assertTrue(store.delete("a.jpg"))
        assertEquals(emptySet(), store.names())
    }
}
