package com.selfwallet.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Receipt photos live in the app's private storage: files/receipts/<uuid>.jpg. */
object Receipts {
    private fun dir(context: Context) = File(context.filesDir, "receipts").apply { mkdirs() }

    /** A new empty file plus a content Uri the camera app can write to. */
    fun newCaptureTarget(context: Context): Pair<File, Uri> {
        val file = File(dir(context), "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return file to uri
    }

    /** Copies a picked image into private storage, scaled down to keep the app small. */
    suspend fun importFrom(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val target = File(dir(context), "${UUID.randomUUID()}.jpg")
        runCatching {
            val bmp = context.contentResolver.openInputStream(uri)?.use { decodeScaled(it.readBytes(), 1800) } ?: return@runCatching null
            target.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            target.absolutePath
        }.getOrNull()
    }

    /** Shrinks a fresh camera photo in place and fixes its rotation. */
    suspend fun normalize(path: String) = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            val bytes = file.readBytes()
            val rotation = runCatching {
                when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }.getOrDefault(0f)
            var bmp = decodeScaled(bytes, 1800) ?: return@runCatching
            if (rotation != 0f) {
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
            }
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }
    }

    suspend fun load(path: String, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { decodeScaled(File(path).readBytes(), maxSide) }.getOrNull()
    }

    fun exists(path: String?) = path != null && File(path).exists()

    fun delete(path: String?) {
        if (path != null) runCatching { File(path).delete() }
    }

    fun shareUri(context: Context, path: String): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", File(path))

    private fun decodeScaled(bytes: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
