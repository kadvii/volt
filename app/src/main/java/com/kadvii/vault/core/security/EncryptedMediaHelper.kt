package com.kadvii.vault.core.security

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kadvii.vault.domain.model.VaultFile
import com.kadvii.vault.domain.usecase.DecryptVaultFileUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object EncryptedMediaHelper {

    suspend fun loadDecryptedBitmap(
        file: VaultFile,
        decryptVaultFileUseCase: DecryptVaultFileUseCase,
        targetWidth: Int = 512,
        targetHeight: Int = 512
    ): Bitmap? = withContext(Dispatchers.IO) {
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

            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
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

