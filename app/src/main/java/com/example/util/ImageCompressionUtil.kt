package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ValidatedImageResult(
  val base64: String? = null,
  val errorMessage: String? = null,
)

object ImageCompressionUtil {

  const val MAX_RAW_UPLOAD_BYTES = 2L * 1024L * 1024L // 2 MB

  suspend fun validateAndCompressUri(
    context: Context,
    uri: Uri,
    maxDimension: Int = 460,
    quality: Int = 72,
    maxRawBytes: Long = MAX_RAW_UPLOAD_BYTES,
  ): ValidatedImageResult =
    withContext(Dispatchers.IO) {
      try {
        val mime = context.contentResolver.getType(uri)?.lowercase()
        if (mime != null && mime !in listOf("image/jpeg", "image/jpg", "image/png", "image/webp")) {
          return@withContext ValidatedImageResult(
            errorMessage = "Unsupported image format ($mime). Please select a JPG or PNG image."
          )
        }

        val rawBytes =
          context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            val baos = ByteArrayOutputStream()
            var total = 0L
            while (true) {
              val read = input.read(buffer)
              if (read <= 0) break
              total += read
              if (total > maxRawBytes) {
                return@withContext ValidatedImageResult(
                  errorMessage = "Image exceeds maximum allowed size of 2MB. Please choose a smaller file."
                )
              }
              baos.write(buffer, 0, read)
            }
            baos.toByteArray()
          }
            ?: return@withContext ValidatedImageResult(
              errorMessage = "Could not open the selected image file."
            )

        val decoded =
          BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            ?: return@withContext ValidatedImageResult(
              errorMessage = "Invalid image file. Please select a valid JPG or PNG photograph."
            )

        val rotated = applyExifOrientation(decoded, ByteArrayInputStream(rawBytes))
        val encoded = scaleAndEncodeToBase64(rotated, maxDimension = maxDimension, quality = quality)
        if (encoded.length > 330_000) {
          val tighter = scaleAndEncodeToBase64(rotated, maxDimension = 340, quality = 60)
          rotated.recycle()
          return@withContext ValidatedImageResult(base64 = tighter)
        }
        rotated.recycle()
        ValidatedImageResult(base64 = encoded)
      } catch (e: Exception) {
        ValidatedImageResult(
          errorMessage = e.localizedMessage ?: "Failed to process selected image."
        )
      }
    }

  suspend fun compressUriToBase64(
    context: Context,
    uri: Uri,
    maxDimension: Int = 420,
    quality: Int = 72,
  ): String? = validateAndCompressUri(context, uri, maxDimension, quality).base64

  suspend fun compressFileToBase64(
    file: File,
    maxDimension: Int = 460,
    quality: Int = 74,
  ): String? =
    withContext(Dispatchers.IO) {
      try {
        if (!file.exists()) return@withContext null
        val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
        val rotated =
          file.inputStream().use { stream -> applyExifOrientation(decoded, stream) }
        val encoded = scaleAndEncodeToBase64(rotated, maxDimension = maxDimension, quality = quality)
        rotated.recycle()
        encoded
      } catch (_: Exception) {
        null
      }
    }

  private fun applyExifOrientation(bitmap: Bitmap, stream: java.io.InputStream): Bitmap {
    return try {
      val exif = ExifInterface(stream)
      val orientation =
        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
      val matrix = Matrix()
      when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        else -> return bitmap
      }
      val transformed =
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
      if (transformed !== bitmap) bitmap.recycle()
      transformed
    } catch (_: Exception) {
      bitmap
    }
  }

  private fun scaleAndEncodeToBase64(
    original: Bitmap,
    maxDimension: Int,
    quality: Int,
  ): String {
    val ratio =
      minOf(
        maxDimension.toFloat() / original.width.coerceAtLeast(1),
        maxDimension.toFloat() / original.height.coerceAtLeast(1),
        1f,
      )
    val targetW = (original.width * ratio).toInt().coerceAtLeast(1)
    val targetH = (original.height * ratio).toInt().coerceAtLeast(1)
    val scaled =
      if (targetW != original.width || targetH != original.height) {
        Bitmap.createScaledBitmap(original, targetW, targetH, true)
      } else {
        original
      }
    val baos = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
    if (scaled !== original) scaled.recycle()
    return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
  }

  fun decodeBase64ToBitmap(base64: String): Bitmap? {
    if (base64.isBlank()) return null
    return try {
      val bytes = Base64.decode(base64, Base64.DEFAULT)
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
      null
    }
  }

  fun saveBase64ImageToCacheFile(
    context: Context,
    base64: String,
    fileNamePrefix: String,
  ): File? {
    val bitmap = decodeBase64ToBitmap(base64) ?: return null
    return try {
      val dir = File(context.cacheDir, "exports")
      if (!dir.exists()) dir.mkdirs()
      val safePrefix = fileNamePrefix.replace(Regex("[^a-zA-Z0-9_-]"), "_")
      val outFile = File(dir, "${safePrefix}_photo.jpg")
      FileOutputStream(outFile).use { fos ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
      }
      outFile
    } catch (_: Exception) {
      null
    }
  }
}
