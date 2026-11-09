package com.quitkelly.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ChatAttachment(
    val kind: String,
    val name: String,
    val mime: String,
    val path: String,
    val pageCount: Int = 0
)

data class PendingAttachment(
    val id: String = UUID.randomUUID().toString(),
    val kind: String,
    val name: String,
    val mime: String,
    val path: String,
    val extractedText: String = "",
    val pageCount: Int = 0
)

class AttachmentStore(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun importImages(uris: List<Uri>): List<Result<PendingAttachment>> =
        withContext(Dispatchers.IO) {
            uris.map { uri -> runCatching { importInternal(uri, forceImage = true) } }
        }

    suspend fun importFile(uri: Uri): Result<PendingAttachment> = withContext(Dispatchers.IO) {
        runCatching { importInternal(uri, forceImage = false) }
    }

    private fun importInternal(uri: Uri, forceImage: Boolean): PendingAttachment {
        val displayName = queryDisplayName(uri) ?: "附件"
        val mime = resolver.getType(uri) ?: guessMime(displayName)
        val isImage = forceImage || mime.startsWith("image/") || isImageExtension(displayName)
        val isPdf = !isImage && (mime == "application/pdf" || displayName.endsWith(".pdf", true))
        val isText = !isImage && !isPdf && (mime.startsWith("text/") || isTextExtension(displayName))
        require(isImage || isPdf || isText) {
            "暂不支持该文件类型，请发送图片、PDF 或文本文件（txt/csv/md/json 等）"
        }

        val dir = File(appContext.filesDir, "attachments").apply { mkdirs() }
        val safeName = displayName
            .replace(Regex("[^\\w.\\-\\u4e00-\\u9fa5]"), "_")
            .take(80)
            .ifBlank { "attachment" }
        val target = File(dir, "${UUID.randomUUID()}_$safeName")

        try {
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("无法读取所选文件")

            val sizeMb = target.length() / 1024.0 / 1024.0
            val limitMb = if (isImage || isPdf) 30.0 else 5.0
            require(sizeMb <= limitMb) {
                "文件过大（${"%.1f".format(sizeMb)} MB），单个文件请控制在 ${limitMb.toInt()} MB 以内"
            }

            val kind = when {
                isImage -> "image"
                isPdf -> "pdf"
                else -> "text"
            }
            val text = if (kind == "text") {
                runCatching { target.readText().take(MAX_TEXT_CHARS) }
                    .getOrElse { throw IllegalArgumentException("无法读取文本文件内容") }
            } else {
                ""
            }
            val pages = if (kind == "pdf") pdfPageCount(target) else 0

            return PendingAttachment(
                kind = kind,
                name = displayName,
                mime = mime,
                path = target.absolutePath,
                extractedText = text,
                pageCount = pages
            )
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
    }

    fun toJson(attachments: List<PendingAttachment>): String {
        return json.encodeToString(
            attachments.map {
                ChatAttachment(
                    kind = it.kind,
                    name = it.name,
                    mime = it.mime,
                    path = it.path,
                    pageCount = it.pageCount
                )
            }
        )
    }

    fun fromJson(raw: String): List<ChatAttachment> {
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<ChatAttachment>>(raw) }
            .getOrDefault(emptyList())
    }

    fun buildTextContext(attachments: List<PendingAttachment>): String {
        return buildString {
            attachments.forEach { attachment ->
                when (attachment.kind) {
                    "text" -> {
                        appendLine()
                        appendLine("【文本文件：${attachment.name}】")
                        appendLine(attachment.extractedText)
                    }

                    "pdf" -> {
                        appendLine()
                        appendLine(
                            "【PDF 文件：${attachment.name}，共 ${attachment.pageCount} 页，" +
                                "已附上前 ${minOf(attachment.pageCount, PDF_MAX_PAGES)} 页图片】"
                        )
                    }

                    "image" -> {
                        appendLine()
                        appendLine("【图片：${attachment.name}】")
                    }
                }
            }
        }
    }

    suspend fun buildImageDataUrls(attachments: List<PendingAttachment>): List<String> =
        withContext(Dispatchers.IO) {
            val urls = mutableListOf<String>()
            attachments.forEach { attachment ->
                when (attachment.kind) {
                    "image" -> encodeImage(attachment.path)?.let { urls += it }
                    "pdf" -> urls += encodePdfPages(attachment.path)
                }
            }
            urls.take(MAX_IMAGES_PER_REQUEST)
        }

    fun loadThumbnail(path: String, maxSize: Int = 320): Bitmap? = decodeSampled(path, maxSize)

    private fun encodeImage(path: String): String? {
        val bitmap = decodeSampled(path, 1600) ?: return null
        return compressToDataUrl(bitmap)
    }

    private fun encodePdfPages(path: String): List<String> {
        val result = mutableListOf<String>()
        val file = File(path)
        val pfd = runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrNull() ?: return result
        val renderer = runCatching { PdfRenderer(pfd) }.getOrElse {
            pfd.close()
            return result
        }
        try {
            val pageCount = minOf(renderer.pageCount, PDF_MAX_PAGES)
            for (index in 0 until pageCount) {
                val page = runCatching { renderer.openPage(index) }.getOrNull() ?: continue
                try {
                    val scale = (1600f / maxOf(page.width, page.height))
                        .coerceAtMost(1f)
                        .coerceAtLeast(0.2f)
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    result += compressToDataUrl(bitmap)
                } finally {
                    page.close()
                }
            }
        } finally {
            renderer.close()
            pfd.close()
        }
        return result
    }

    private fun pdfPageCount(file: File): Int {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(pfd)
        } catch (t: Throwable) {
            pfd.close()
            throw t
        }
        return try {
            renderer.pageCount
        } finally {
            renderer.close()
            pfd.close()
        }
    }

    private fun compressToDataUrl(bitmap: Bitmap): String {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
        bitmap.recycle()
        val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    private fun decodeSampled(path: String, maxSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxSize) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(path, options) ?: return null
        val maxDimension = maxOf(decoded.width, decoded.height)
        if (maxDimension <= maxSize) return decoded

        val ratio = maxSize.toFloat() / maxDimension
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
    }

    private fun guessMime(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "csv" -> "text/csv"
            "json" -> "application/json"
            "md" -> "text/markdown"
            "html", "htm" -> "text/html"
            "xml" -> "text/xml"
            "txt", "log", "yaml", "yml" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private fun isImageExtension(name: String): Boolean {
        return name.substringAfterLast('.', "").lowercase() in
            setOf("jpg", "jpeg", "png", "gif", "webp")
    }

    private fun isTextExtension(name: String): Boolean {
        return name.substringAfterLast('.', "").lowercase() in
            setOf("txt", "md", "csv", "json", "log", "xml", "html", "htm", "yaml", "yml")
    }

    companion object {
        const val MAX_ATTACHMENTS = 4
        private const val MAX_TEXT_CHARS = 20_000
        private const val PDF_MAX_PAGES = 3
        private const val MAX_IMAGES_PER_REQUEST = 12
    }
}
