package org.proanima.shelter.admin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// A photo that was uploaded: the big one for the page and the lightbox, and the smaller copy for cards and
// thumbnails (see cardPhotoUrl). Both are JPEG, with no metadata: a phone's GPS position does not leave the phone.
class ProcessedPhoto(val original: ByteArray, val card: ByteArray)

// The message is shown to the person who uploaded the file, so it is in Russian and says what to do.
class PhotoRejected(message: String) : Exception(message)

object PhotoLimits {
    const val MAX_FILE_BYTES = 8 * 1024 * 1024

    // Counted from the picture's header, before it is decoded. The JPEG decoder keeps a progressive picture
    // whole outside the Java heap, and the free hosting plan has 512 MB for everything.
    const val MAX_PIXELS = 30_000_000L

    const val MIN_SHORT_SIDE = 300
    const val MAX_FILES_PER_UPLOAD = 5
    const val MAX_PHOTOS_PER_PET = 12

    // Five files of the largest size, plus the form's own fields.
    const val MAX_UPLOAD_REQUEST_BYTES = MAX_FILES_PER_UPLOAD * MAX_FILE_BYTES.toLong() + 1024 * 1024

    const val ORIGINAL_LONG_SIDE = 1600
    const val CARD_WIDTH = 440
    const val CARD_HEIGHT = 550
    const val ORIGINAL_QUALITY = 0.85f
    const val CARD_QUALITY = 0.82f
}

// The same rules as tools/make-card-images.py, which made the card copies of the photos that were already
// there: the whole picture, scaled down so that it still covers 440x550, never scaled up.
object ImageProcessing {

    // One picture at a time: decoding and scaling a large photo takes tens of megabytes, and the hosting
    // plan has little memory. The others wait their turn.
    private val gate = Mutex()

    init {
        System.setProperty("java.awt.headless", "true")
    }

    suspend fun process(bytes: ByteArray): ProcessedPhoto = gate.withLock {
        withContext(Dispatchers.Default) { processNow(bytes) }
    }

    internal fun processNow(bytes: ByteArray): ProcessedPhoto {
        if (bytes.isEmpty()) {
            throw PhotoRejected("Файл пустой.")
        }
        if (bytes.size > PhotoLimits.MAX_FILE_BYTES) {
            throw PhotoRejected("Файл больше ${PhotoLimits.MAX_FILE_BYTES / 1024 / 1024} МБ.")
        }
        val format = sniff(bytes)
            ?: throw PhotoRejected("Принимаются только JPEG и PNG. Фото в другом формате (HEIC, WebP, GIF) сначала сохраните как JPEG.")
        try {
            val upright = decodeUpright(bytes, format)
            val original = scaleDown(upright, PhotoLimits.ORIGINAL_LONG_SIDE.toDouble() / max(upright.width, upright.height))
            val card = scaleDown(
                original,
                max(PhotoLimits.CARD_WIDTH.toDouble() / original.width, PhotoLimits.CARD_HEIGHT.toDouble() / original.height)
            )
            return ProcessedPhoto(encodeJpeg(original, PhotoLimits.ORIGINAL_QUALITY), encodeJpeg(card, PhotoLimits.CARD_QUALITY))
        } catch (e: OutOfMemoryError) {
            throw PhotoRejected("Изображение слишком большое для обработки. Уменьшите его и загрузите снова.")
        }
    }

    // "jpeg" or "png" by the first bytes of the file, never by its name or the type the browser claims.
    internal fun sniff(bytes: ByteArray): String? = when {
        bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "jpeg"
        bytes.size > 8 && bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE) -> "png"
        else -> null
    }

    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    // Reads the picture in a reduced size (the decoder can skip pixels while reading, which is what keeps a
    // 25-megapixel phone photo from filling the memory), turns it the way the EXIF tag says, and puts it on
    // white so that a transparent PNG becomes a plain JPEG.
    private fun decodeUpright(bytes: ByteArray, format: String): BufferedImage {
        val reader = ImageIO.getImageReadersByFormatName(format).asSequence().firstOrNull()
            ?: throw PhotoRejected("Сервер не умеет читать такой формат.")
        try {
            ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { stream ->
                reader.setInput(stream, true, true)
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                if (width.toLong() * height > PhotoLimits.MAX_PIXELS) {
                    throw PhotoRejected("Слишком большое изображение: $width×$height. Допустимо до ${PhotoLimits.MAX_PIXELS / 1_000_000} мегапикселей.")
                }
                if (min(width, height) < PhotoLimits.MIN_SHORT_SIDE) {
                    throw PhotoRejected("Слишком маленькое изображение: $width×$height. Короткая сторона должна быть не меньше ${PhotoLimits.MIN_SHORT_SIDE} пикселей.")
                }
                val skip = max(1, max(width, height) / PhotoLimits.ORIGINAL_LONG_SIDE)
                val param = reader.defaultReadParam.apply { setSourceSubsampling(skip, skip, 0, 0) }
                val decoded = reader.read(0, param)
                val orientation = if (format == "jpeg") exifOrientation(bytes) else 1
                return orient(decoded, orientation)
            }
        } catch (e: PhotoRejected) {
            throw e
        } catch (e: IOException) {
            throw PhotoRejected("Не удалось прочитать файл: он повреждён или в неподдерживаемом виде (например, CMYK).")
        } catch (e: RuntimeException) {
            throw PhotoRejected("Не удалось прочитать файл: он повреждён или в неподдерживаемом виде.")
        } finally {
            reader.dispose()
        }
    }

    // Reads the orientation tag (1..8) from the EXIF block of a JPEG; 1 (as it is) when there is none or the
    // block is damaged. Every read is checked against the end of the data.
    internal fun exifOrientation(jpeg: ByteArray): Int {
        fun u8(at: Int) = if (at in jpeg.indices) jpeg[at].toInt() and 0xFF else -1
        fun u16(at: Int, little: Boolean): Int {
            val a = u8(at)
            val b = u8(at + 1)
            return if (a < 0 || b < 0) -1 else if (little) b shl 8 or a else a shl 8 or b
        }
        fun u32(at: Int, little: Boolean): Long {
            val high = u16(if (little) at + 2 else at, little)
            val low = u16(if (little) at else at + 2, little)
            return if (high < 0 || low < 0) -1 else (high.toLong() shl 16) or low.toLong()
        }

        var at = 2
        while (at + 4 <= jpeg.size && u8(at) == 0xFF) {
            val marker = u8(at + 1)
            if (marker == 0xDA || marker == 0xD9) break
            val length = u16(at + 2, little = false)
            if (length < 2) break
            val isExif = marker == 0xE1 && length >= 16 && at + 10 <= jpeg.size &&
                String(jpeg, at + 4, 4, Charsets.ISO_8859_1) == "Exif" && u8(at + 8) == 0 && u8(at + 9) == 0
            if (isExif) {
                val tiff = at + 10
                val end = min(at + 2 + length, jpeg.size)
                val little = when (u16(tiff, little = false)) {
                    0x4949 -> true
                    0x4D4D -> false
                    else -> return 1
                }
                if (u16(tiff + 2, little) != 42) return 1
                val ifd = tiff + u32(tiff + 4, little).toInt()
                if (ifd < tiff || ifd + 2 > end) return 1
                val entries = u16(ifd, little)
                for (index in 0 until entries) {
                    val entry = ifd + 2 + index * 12
                    if (entry + 12 > end) return 1
                    if (u16(entry, little) == 0x0112 && u16(entry + 2, little) == 3) {
                        return u16(entry + 8, little).takeIf { it in 1..8 } ?: 1
                    }
                }
                return 1
            }
            at += 2 + length
        }
        return 1
    }

    // Draws the picture on white, turned as the orientation says: 1 as it is, 2 mirrored, 3 upside down,
    // 4 mirrored top to bottom, 5 transposed, 6 turned right, 7 transversed, 8 turned left.
    private fun orient(source: BufferedImage, orientation: Int): BufferedImage {
        val w = source.width.toDouble()
        val h = source.height.toDouble()
        val transform = when (orientation) {
            2 -> AffineTransform(-1.0, 0.0, 0.0, 1.0, w, 0.0)
            3 -> AffineTransform(-1.0, 0.0, 0.0, -1.0, w, h)
            4 -> AffineTransform(1.0, 0.0, 0.0, -1.0, 0.0, h)
            5 -> AffineTransform(0.0, 1.0, 1.0, 0.0, 0.0, 0.0)
            6 -> AffineTransform(0.0, 1.0, -1.0, 0.0, h, 0.0)
            7 -> AffineTransform(0.0, -1.0, -1.0, 0.0, h, w)
            8 -> AffineTransform(0.0, -1.0, 1.0, 0.0, 0.0, w)
            else -> AffineTransform()
        }
        val turned = orientation in 5..8
        val result = BufferedImage(
            if (turned) source.height else source.width,
            if (turned) source.width else source.height,
            BufferedImage.TYPE_INT_RGB
        )
        val graphics = result.createGraphics()
        try {
            graphics.color = java.awt.Color.WHITE
            graphics.fillRect(0, 0, result.width, result.height)
            graphics.drawImage(source, transform, null)
        } finally {
            graphics.dispose()
        }
        return result
    }

    // Scales by a factor below 1; a factor of 1 or more leaves the picture alone (nothing is scaled up).
    private fun scaleDown(source: BufferedImage, factor: Double): BufferedImage {
        if (factor >= 1.0) {
            return source
        }
        val targetWidth = max(1, (source.width * factor).roundToInt())
        val targetHeight = max(1, (source.height * factor).roundToInt())
        var current = source
        // Halving first averages 2x2 pixels at a time. One big jump to a small size would skip pixels and
        // leave jagged edges.
        while (current.width / 2 >= targetWidth && current.height / 2 >= targetHeight) {
            current = draw(current, current.width / 2, current.height / 2, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        }
        if (current.width != targetWidth || current.height != targetHeight) {
            current = draw(current, targetWidth, targetHeight, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        }
        return current
    }

    private fun draw(source: BufferedImage, width: Int, height: Int, interpolation: Any): BufferedImage {
        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = result.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.drawImage(source, 0, 0, width, height, null)
        } finally {
            graphics.dispose()
        }
        return result
    }

    private fun encodeJpeg(image: BufferedImage, quality: Float): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val output = ByteArrayOutputStream()
        try {
            ImageIO.createImageOutputStream(output).use { stream ->
                writer.output = stream
                val param = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = quality
                    progressiveMode = ImageWriteParam.MODE_DEFAULT
                }
                writer.write(null, IIOImage(image, null, null), param)
            }
        } finally {
            writer.dispose()
        }
        return output.toByteArray()
    }
}
