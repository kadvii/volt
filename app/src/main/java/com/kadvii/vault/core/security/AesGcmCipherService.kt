package com.kadvii.vault.core.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.InvalidAlgorithmParameterException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AesGcmCipherService {

    companion object {
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
        const val BUFFER_SIZE = 64 * 1024 // 64 KB chunks
    }

    private val secureRandom = SecureRandom()

    fun generateIv(): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        return iv
    }

    /**
     * Encrypts plaintext from inputStream into outputStream with a 12-byte IV.
     * Output format: [12 bytes IV][Ciphertext + 16 bytes GCM tag]
     */
    fun encryptStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        secretKey: SecretKey
    ) {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val iv: ByteArray = try {
            val generatedIv = generateIv()
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, generatedIv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
            generatedIv
        } catch (_: InvalidAlgorithmParameterException) {
            // If the provider enforces its own IV generation
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            cipher.iv ?: generateIv()
        }

        outputStream.write(iv)

        val cipherOutputStream = CipherOutputStream(outputStream, cipher)
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            cipherOutputStream.write(buffer, 0, bytesRead)
        }
        cipherOutputStream.flush()
        cipherOutputStream.close()
    }

    /**
     * Decrypts ciphertext from inputStream into outputStream.
     * Reads the 12-byte IV from the start, initializes AES-GCM, and validates the authentication tag.
     * Throws SecurityException if the tag does not match or the ciphertext is tampered.
     */
    fun decryptStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        secretKey: SecretKey
    ) {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        var totalIvRead = 0
        while (totalIvRead < GCM_IV_LENGTH_BYTES) {
            val count = inputStream.read(iv, totalIvRead, GCM_IV_LENGTH_BYTES - totalIvRead)
            if (count == -1) {
                throw SecurityException("Invalid encrypted file: IV header is incomplete")
            }
            totalIvRead += count
        }

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        try {
            val cipherInputStream = CipherInputStream(inputStream, cipher)
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (cipherInputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
        } catch (e: Exception) {
            if (e is AEADBadTagException || e.cause is AEADBadTagException) {
                throw SecurityException("Cryptographic verification failed: file ciphertext was altered or tampered", e)
            }
            throw e
        }
    }

    /**
     * Atomically encrypts a file to a destination file.
     * Writes to destination.tmp first, verifies, then renames to destination.
     */
    fun encryptFileAtomically(
        sourceFile: File,
        destinationFile: File,
        secretKey: SecretKey
    ) {
        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.tmp")
        try {
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(tempFile).use { output ->
                    encryptStream(input, output, secretKey)
                }
            }
            if (!tempFile.renameTo(destinationFile)) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            throw e
        }
    }

    /**
     * Atomically encrypts an input stream to a destination file.
     */
    fun encryptStreamToFileAtomically(
        inputStream: InputStream,
        destinationFile: File,
        secretKey: SecretKey
    ) {
        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.tmp")
        try {
            FileOutputStream(tempFile).use { output ->
                encryptStream(inputStream, output, secretKey)
            }
            if (!tempFile.renameTo(destinationFile)) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            throw e
        }
    }

    /**
     * Decrypts an encrypted file directly into a byte array in memory (for thumbnails, photos).
     */
    fun decryptFileToByteArray(
        encryptedFile: File,
        secretKey: SecretKey
    ): ByteArray {
        val output = ByteArrayOutputStream()
        FileInputStream(encryptedFile).use { input ->
            decryptStream(input, output, secretKey)
        }
        return output.toByteArray()
    }

    /**
     * Returns an InputStream for the decrypted content in memory.
     */
    fun getDecryptedStream(
        encryptedFile: File,
        secretKey: SecretKey
    ): InputStream {
        val bytes = decryptFileToByteArray(encryptedFile, secretKey)
        return ByteArrayInputStream(bytes)
    }
}
