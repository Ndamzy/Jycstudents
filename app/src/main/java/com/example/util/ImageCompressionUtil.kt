package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageCompressionUtil {

  suspend fun compressUriToBase64(
    context: Context,
    uri: Uri,
    maxDimension: Int = 420,
    quality: Int = 72,
  ): String? =
    withContext(Dispatchers.IO) {
      try {
        val rawStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        val original = rawStream.use { BitmapFactory.decodeStream(it) } ?: return@withContext null
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
        original.recycle()
        Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
      } catch (_: Exception) {
        null
      }
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
}
