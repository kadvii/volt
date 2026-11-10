package com.kadvii.vault.core.security

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.collection.LruCache
import com.kadvii.vault.domain.model.VaultFile
import com.kadvii.vault.domain.usecase.DecryptVaultFileUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object EncryptedMediaHelper {

    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = (maxMemoryKb / 8).coerceIn(4096, 20480) // Between 4 MB and 20 MB

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

    fun clearMemoryCache() {
        memoryCache.evictAll()
    }

    suspend fun loadDecryptedBitmap(
        file: VaultFile,
        decryptVaultFileUseCase: DecryptVaultFileUseCase,
        targetWidth: Int = 512,
        targetHeight: Int = 512
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${file.id}_${file.updatedAt}_${targetWidth}x${targetHeight}"

        // 1. Check in-memory LRU cache first
        memoryCache.get(cacheKey)?.let { return@withContext it }

        try {
            val bytes = decryptVaultFileUseCase.getBytes(file)

            // Decode dimensions first for memory-efficient sampling
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            // Calculate sample size
            options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
            options.inJustDecodeBounds = false
            // Use RGB_565 for small thumbnails to save 50% memory
            if (targetWidth <= 256 && targetHeight <= 256) {
                options.inPreferredConfig = Bitmap.Config.RGB_565
            }

            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}
