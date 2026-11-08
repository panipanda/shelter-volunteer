package org.proanima.shelter.admin

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageProcessingTest {

    private fun process(bytes: ByteArray) = ImageProcessing.processNow(bytes)

    private fun sizeOf(jpeg: ByteArray) = decode(jpeg).let { it.width to it.height }

    private fun rejection(bytes: ByteArray): String = assertFailsWith<PhotoRejected> { process(bytes) }.message.orEmpty()

    // ---- sizes: the same rules as tools/make-card-images.py

    @Test
    fun `a big landscape photo becomes 1600 wide and a card that covers 440 by 550`() {
        val result = process(jpegBytes(3000, 2000))

        val (width, height) = sizeOf(result.original)
        assertEquals(1600, width)
        assertTrue(height in 1066..1067, "height $height")
        val (cardWidth, cardHeight) = sizeOf(result.card)
        assertTrue(cardWidth in 824..826, "card width $cardWidth")
        assertEquals(550, cardHeight)
    }

    @Test
    fun `a big portrait photo becomes 1600 high and a card 440 wide`() {
        val result = process(jpegBytes(2000, 3000))

        val (width, height) = sizeOf(result.original)
        assertTrue(width in 1066..1067, "width $width")
        assertEquals(1600, height)
        val (cardWidth, cardHeight) = sizeOf(result.card)
        assertEquals(440, cardWidth)
        assertTrue(cardHeight in 660..661, "card height $cardHeight")
    }

    @Test
    fun `a small photo is not scaled up`() {
        val result = process(jpegBytes(800, 600))

        assertEquals(800 to 600, sizeOf(result.original))
        val (cardWidth, cardHeight) = sizeOf(result.card)
        assertTrue(cardWidth in 732..734 && cardHeight == 550, "card $cardWidth x $cardHeight")

        val tiny = process(jpegBytes(400, 500))
        assertEquals(400 to 500, sizeOf(tiny.original))
        assertEquals(400 to 500, sizeOf(tiny.card))
    }

    @Test
    fun `a photo of 24 megapixels is read in a reduced size and comes out right`() {
        val result = process(jpegBytes(6000, 4000))

        val (width, height) = sizeOf(result.original)
        assertEquals(1600, width)
        assertTrue(height in 1066..1067, "height $height")
    }

    // ---- what comes out

    @Test
    fun `both copies are progressive JPEG files`() {
        val result = process(pngBytes(1200, 900))

        listOf(result.original, result.card).forEach { jpeg ->
            assertEquals(0xFF.toByte() to 0xD8.toByte(), jpeg[0] to jpeg[1])
            val progressive = (0 until jpeg.size - 1).any { jpeg[it] == 0xFF.toByte() && jpeg[it + 1] == 0xC2.toByte() }
            assertTrue(progressive)
        }
    }

    @Test
    fun `a transparent PNG is put on white`() {
        val transparent = BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB)

        val result = decode(process(encode(transparent, "png")).original)

        val pixel = Color(result.getRGB(300, 300))
        assertTrue(pixel.red > 245 && pixel.green > 245 && pixel.blue > 245, pixel.toString())
    }

    @Test
    fun `nothing of the original metadata is kept, the shooting place included`() {
        val marked = withExifOrientation(jpegBytes(900, 600), 1, extra = "GPS-LATITUDE-44.8125N".toByteArray())
        assertContains(String(marked, Charsets.ISO_8859_1), "GPS-LATITUDE")

        val result = process(marked)

        listOf(result.original, result.card).forEach { jpeg ->
            val text = String(jpeg, Charsets.ISO_8859_1)
            assertFalse("GPS-LATITUDE" in text)
            assertFalse("Exif" in text)
        }
    }

    // ---- turning by the EXIF tag: where the top left corner of the upright picture was stored

    @Test
    fun `every EXIF orientation is turned the right way up`() {
        // The upright picture has a red block at its top left. Stored the way the camera held it, that block is at:
        // 1 top left, 2 top right, 3 bottom right, 4 bottom left (these four keep the size),
        // 5 top left, 6 bottom left, 7 bottom right, 8 top right (these four are stored on their side).
        val corners = mapOf(
            1 to "TL", 2 to "TR", 3 to "BR", 4 to "BL",
            5 to "TL", 6 to "BL", 7 to "BR", 8 to "TR"
        )
        corners.forEach { (orientation, corner) ->
            val (w, h) = if (orientation <= 4) 800 to 600 else 600 to 800
            val stored = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            val graphics = stored.createGraphics()
            graphics.color = Color(235, 235, 235)
            graphics.fillRect(0, 0, w, h)
            graphics.color = Color(220, 0, 0)
            val x = if (corner.endsWith("L")) 0 else w - 120
            val y = if (corner.startsWith("T")) 0 else h - 120
            graphics.fillRect(x, y, 120, 120)
            graphics.dispose()

            val result = decode(process(withExifOrientation(encode(stored, "jpeg"), orientation)).original)

            assertEquals(800 to 600, result.width to result.height, "orientation $orientation")
            val topLeft = Color(result.getRGB(50, 50))
            assertTrue(topLeft.red > 170 && topLeft.green < 90 && topLeft.blue < 90, "orientation $orientation: top left is $topLeft")
            val bottomRight = Color(result.getRGB(750, 550))
            assertTrue(bottomRight.green > 170, "orientation $orientation: bottom right is $bottomRight")
        }
    }

    @Test
    fun `a photo without an EXIF tag, or with a bad one, is left as it is`() {
        assertEquals(1, ImageProcessing.exifOrientation(jpegBytes(300, 300)))
        assertEquals(6, ImageProcessing.exifOrientation(withExifOrientation(jpegBytes(300, 300), 6)))
        assertEquals(1, ImageProcessing.exifOrientation(withExifOrientation(jpegBytes(300, 300), 0)))
        assertEquals(1, ImageProcessing.exifOrientation(withExifOrientation(jpegBytes(300, 300), 9)))
    }

    @Test
    fun `reading the EXIF block of garbage never fails`() {
        val random = Random(42)
        repeat(300) {
            val garbage = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte()) + random.nextBytes(random.nextInt(0, 60))
            assertTrue(ImageProcessing.exifOrientation(garbage) in 1..8)
        }
        // A block that announces more than the file holds, cut in every place.
        val genuine = withExifOrientation(jpegBytes(300, 300), 6)
        for (cut in 2..60) {
            assertTrue(ImageProcessing.exifOrientation(genuine.copyOf(cut)) in 1..8, "cut at $cut")
        }
    }

    // ---- what is refused, and what the person is told

    @Test
    fun `what is not a JPEG or PNG is refused by its content, whatever its name`() {
        assertContains(rejection("just text".toByteArray()), "JPEG и PNG")
        assertContains(rejection("GIF89a".toByteArray() + ByteArray(100)), "JPEG и PNG")
        assertContains(rejection(ByteArray(40) + "ftypheic".toByteArray() + ByteArray(100)), "JPEG и PNG")
        assertContains(rejection("RIFF".toByteArray() + ByteArray(4) + "WEBP".toByteArray() + ByteArray(100)), "JPEG и PNG")
        assertContains(rejection(ByteArray(0)), "пустой")
    }

    @Test
    fun `a file over 8 MB is refused before anything is decoded`() {
        val big = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(PhotoLimits.MAX_FILE_BYTES)

        assertContains(rejection(big), "больше 8 МБ")
    }

    @Test
    fun `a picture that claims a huge size is refused from its header alone`() {
        // 6000 x 6000 = 36 megapixels: over the limit, and no pixel is ever decoded.
        assertContains(rejection(pngHeaderOnly(6000, 6000)), "мегапикселей")
    }

    @Test
    fun `a picture that is too small is refused`() {
        assertContains(rejection(jpegBytes(200, 200)), "маленькое")
        assertContains(rejection(jpegBytes(2000, 250)), "маленькое")
    }

    @Test
    fun `a damaged file is refused with a message, not with an error`() {
        val whole = jpegBytes(1200, 900)
        val damaged = whole.copyOf(whole.size / 2)
        val outcome = runCatching { process(damaged) }
        assertTrue(outcome.isSuccess || outcome.exceptionOrNull() is PhotoRejected, outcome.exceptionOrNull().toString())

        assertContains(rejection(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + Random(1).nextBytes(500)), "Не удалось прочитать")
        assertContains(rejection(pngBytes(600, 600).let { it.copyOf(40) }), "Не удалось прочитать")
    }

    @Test
    fun `the type is taken from the first bytes`() {
        assertEquals("jpeg", ImageProcessing.sniff(jpegBytes(300, 300)))
        assertEquals("png", ImageProcessing.sniff(pngBytes(300, 300)))
        assertNull(ImageProcessing.sniff("GIF89a....".toByteArray()))
        assertNull(ImageProcessing.sniff(ByteArray(2)))
    }

    // ---- one at a time

    @Test
    fun `several photos processed at once all come out right`() = runBlocking {
        val sources = (1..4).map { jpegBytes(1800 + it * 100, 1200) }

        val results = sources.map { async { ImageProcessing.process(it) } }.awaitAll()

        results.forEach { result ->
            assertNotNull(result)
            assertEquals(1600, sizeOf(result.original).first)
        }
    }
}
