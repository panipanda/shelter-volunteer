package org.proanima.shelter.admin

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import javax.imageio.ImageIO

// Pictures made on the spot for the tests: no files, so nothing to commit and nothing that can go stale.

// A smooth colour gradient: it compresses well, so even a large one stays a small file.
fun testImage(width: Int, height: Int, type: Int = BufferedImage.TYPE_INT_RGB): BufferedImage {
    val image = BufferedImage(width, height, type)
    val graphics = image.createGraphics()
    for (y in 0 until height step 8) {
        graphics.color = Color(200, (y * 255 / height.coerceAtLeast(1)).coerceIn(0, 255), 120)
        graphics.fillRect(0, y, width, 8)
    }
    graphics.dispose()
    return image
}

fun encode(image: BufferedImage, format: String): ByteArray =
    ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()

fun jpegBytes(width: Int, height: Int): ByteArray = encode(testImage(width, height), "jpeg")

fun pngBytes(width: Int, height: Int): ByteArray = encode(testImage(width, height), "png")

fun decode(bytes: ByteArray): BufferedImage = ImageIO.read(ByteArrayInputStream(bytes))

// An EXIF block with only the orientation tag (big-endian, the way many cameras write it), put right after the
// start of a JPEG. extra is more bytes inside the block, for tests that look for what must not survive.
fun withExifOrientation(jpeg: ByteArray, orientation: Int, extra: ByteArray = ByteArray(0)): ByteArray {
    val tiff = byteArrayOf(
        'M'.code.toByte(), 'M'.code.toByte(), 0, 42, 0, 0, 0, 8,
        0, 1,
        0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, orientation.toByte(), 0, 0,
        0, 0, 0, 0
    )
    val exif = "Exif".toByteArray() + byteArrayOf(0, 0) + tiff + extra
    val length = exif.size + 2
    val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), (length and 0xFF).toByte()) + exif
    return jpeg.copyOfRange(0, 2) + segment + jpeg.copyOfRange(2, jpeg.size)
}

// Only the header of a PNG, with the size of a huge picture: enough for a reader to learn the size and nothing
// to decode. The server must refuse it on the size alone.
fun pngHeaderOnly(width: Int, height: Int): ByteArray {
    val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    val header = ByteBuffer.allocate(13).putInt(width).putInt(height).put(8).put(2).put(0).put(0).put(0).array()
    val type = "IHDR".toByteArray()
    val crc = CRC32().also { it.update(type); it.update(header) }.value.toInt()
    return signature + ByteBuffer.allocate(4).putInt(13).array() + type + header + ByteBuffer.allocate(4).putInt(crc).array()
}
