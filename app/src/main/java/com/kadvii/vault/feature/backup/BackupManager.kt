package com.kadvii.vault.feature.backup

import com.kadvii.vault.core.security.AesGcmCipherService
import com.kadvii.vault.core.security.AndroidKeystoreManager
import com.kadvii.vault.core.security.KeystoreManager
import com.kadvii.vault.core.security.PasswordHashHelper
import com.kadvii.vault.data.database.VaultFileDao
import com.kadvii.vault.data.database.VaultFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.spec.SecretKeySpec

data class BackupMetadata(
    val timestamp: Long,
    val fileCount: Int,
    val isDecoy: Boolean
)

data class BackupResult(
    val fileCount: Int,
    val totalBytes: Long
)

data class RestoreResult(
    val restoredFilesCount: Int
)

class BackupManager(
    private val vaultFileDao: VaultFileDao,
    private val cipherService: AesGcmCipherService,
    private val keystoreManager: KeystoreManager,
    private val filesDir: File
) {

    companion object {
        val MAGIC_HEADER = byteArrayOf('V'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte(), '1'.code.toByte())
        const val SALT_SIZE = 32
        const val PBKDF2_ITERATIONS = 100_000
        const val BACKUP_FORMAT_VERSION = 2
    }

    private fun getKeyAlias(isDecoy: Boolean): String {
        return if (isDecoy) {
            AndroidKeystoreManager.DECOY_VAULT_KEY_ALIAS
        } else {
            AndroidKeystoreManager.MASTER_VAULT_KEY_ALIAS
        }
    }

    suspend fun createBackup(
        passphrase: String,
        outputStream: OutputStream,
        isDecoy: Boolean
    ): BackupResult = withContext(Dispatchers.IO) {
        val salt = PasswordHashHelper.generateSalt()
        val derivedKeyBytes = PasswordHashHelper.hashPin(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val backupKey = SecretKeySpec(derivedKeyBytes, "AES")

        // 1. Write Header: 4 bytes Magic + 32 bytes Salt
        outputStream.write(MAGIC_HEADER)
        outputStream.write(salt)

        // 2. Prepare Zip Payload in temp file
        val files = vaultFileDao.getAllFiles(isDecoy).first()
        val tempZipFile = File.createTempFile("backup_payload_", ".tmp", filesDir)
        val localKey = keystoreManager.getOrCreateKey(getKeyAlias(isDecoy))

        try {
            ZipOutputStream(FileOutputStream(tempZipFile)).use { zipOut ->
                val manifestArray = JSONArray()

                files.forEach { fileEntity ->
                    val diskFile = File(fileEntity.encryptedPath)
                    if (diskFile.exists()) {
                        // Decrypt local file in memory/stream and stream into zip entry
                        val entry = ZipEntry("files/${fileEntity.uuid}")
                        zipOut.putNextEntry(entry)

                        val md = MessageDigest.getInstance("SHA-256")
                        // Temporary decrypted stream to compute hash and write to zip
                        val tempDecrypted = File.createTempFile("backup_plain_", ".tmp", filesDir)
                        try {
                            cipherService.decryptFileToFileAtomically(diskFile, tempDecrypted, localKey)
                            FileInputStream(tempDecrypted).use { plainIn ->
                                val buffer = ByteArray(AesGcmCipherService.BUFFER_SIZE)
                                var bytesRead: Int
                                while (plainIn.read(buffer).also { bytesRead = it } != -1) {
                                    zipOut.write(buffer, 0, bytesRead)
                                    md.update(buffer, 0, bytesRead)
                                }
                            }
                        } finally {
                            if (tempDecrypted.exists()) tempDecrypted.delete()
                        }
                        zipOut.closeEntry()

                        val sha256Hex = md.digest().joinToString("") { "%02x".format(it) }

                        val fileObj = JSONObject().apply {
                            put("uuid", fileEntity.uuid)
                            put("fileName", fileEntity.fileName)
                            put("fileType", fileEntity.fileType)
                            put("mimeType", fileEntity.mimeType)
                            put("fileSizeBytes", fileEntity.fileSizeBytes)
                            put("createdAt", fileEntity.createdAt)
                            put("isFavorite", fileEntity.isFavorite)
                            put("isDecoy", fileEntity.isDecoy)
                            put("sha256", sha256Hex)
                        }
                        manifestArray.put(fileObj)
                    }
                }

                val manifestWrapper = JSONObject().apply {
                    put("formatVersion", BACKUP_FORMAT_VERSION)
                    put("timestamp", System.currentTimeMillis())
                    put("isDecoy", isDecoy)
                    put("files", manifestArray)
                }

                val manifestEntry = ZipEntry("manifest.json")
                zipOut.putNextEntry(manifestEntry)
                zipOut.write(manifestWrapper.toString().toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }

            // 3. Encrypt zip file into outputStream
            FileInputStream(tempZipFile).use { zipIn ->
                cipherService.encryptStream(zipIn, outputStream, backupKey)
            }

            BackupResult(fileCount = files.size, totalBytes = tempZipFile.length())
        } finally {
            if (tempZipFile.exists()) tempZipFile.delete()
        }
    }

    suspend fun restoreBackup(
        passphrase: String,
        inputStream: InputStream,
        isDecoy: Boolean
    ): RestoreResult = withContext(Dispatchers.IO) {
        // 1. Read and verify Magic Header
        val magic = ByteArray(MAGIC_HEADER.size)
        var totalMagicRead = 0
        while (totalMagicRead < MAGIC_HEADER.size) {
            val count = inputStream.read(magic, totalMagicRead, MAGIC_HEADER.size - totalMagicRead)
            if (count == -1) break
            totalMagicRead += count
        }
        if (totalMagicRead != MAGIC_HEADER.size || !magic.contentEquals(MAGIC_HEADER)) {
            throw IllegalArgumentException("Invalid backup file: Unrecognized format or corrupted header")
        }

        // 2. Read Salt
        val salt = ByteArray(SALT_SIZE)
        var totalSaltRead = 0
        while (totalSaltRead < SALT_SIZE) {
            val count = inputStream.read(salt, totalSaltRead, SALT_SIZE - totalSaltRead)
            if (count == -1) break
            totalSaltRead += count
        }
        if (totalSaltRead != SALT_SIZE) {
            throw IllegalArgumentException("Invalid backup file: Incomplete salt")
        }

        // 3. Derive Key
        val derivedKeyBytes = PasswordHashHelper.hashPin(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val backupKey = SecretKeySpec(derivedKeyBytes, "AES")

        // 4. Decrypt Zip payload to temp file
        val tempZipFile = File.createTempFile("restore_payload_", ".tmp", filesDir)
        try {
            FileOutputStream(tempZipFile).use { zipOut ->
                cipherService.decryptStream(inputStream, zipOut, backupKey)
            }

            // 5. Read Zip and re-encrypt files with destination Keystore key
            val storageDir = if (isDecoy) File(filesDir, "vault_decoy") else File(filesDir, "vault_primary")
            if (!storageDir.exists()) storageDir.mkdirs()
            val destKey = keystoreManager.getOrCreateKey(getKeyAlias(isDecoy))

            var manifestJson: String? = null
            val extractedPlainFiles = mutableMapOf<String, File>()

            ZipInputStream(FileInputStream(tempZipFile)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "manifest.json" -> {
                            val out = ByteArrayOutputStream()
                            zipIn.copyTo(out)
                            manifestJson = String(out.toByteArray(), Charsets.UTF_8)
                        }
                        entry.name.startsWith("files/") -> {
                            val uuidName = entry.name.removePrefix("files/").removeSuffix(".enc")
                            val tempPlain = File.createTempFile("restore_plain_", ".tmp", filesDir)
                            FileOutputStream(tempPlain).use { out ->
                                zipIn.copyTo(out)
                            }
                            extractedPlainFiles[uuidName] = tempPlain
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            var restoredCount = 0

            // 6. Restore Database records and re-encrypt
            if (manifestJson != null) {
                val manifestObj = try {
                    JSONObject(manifestJson)
                } catch (_: Exception) {
                    null
                }

                val filesArray = when {
                    manifestObj?.has("files") == true -> manifestObj.getJSONArray("files")
                    else -> JSONArray(manifestJson) // V1 fallback
                }

                for (i in 0 until filesArray.length()) {
                    val obj = filesArray.getJSONObject(i)
                    val uuid = obj.getString("uuid")
                    val plainFile = extractedPlainFiles[uuid] ?: continue
                    val targetEncryptedFile = File(storageDir, "$uuid.enc")

                    try {
                        // Re-encrypt media file using destination hardware Keystore key
                        FileInputStream(plainFile).use { plainIn ->
                            cipherService.encryptStreamToFileAtomically(plainIn, targetEncryptedFile, destKey)
                        }

                        val entity = VaultFileEntity(
                            uuid = uuid,
                            fileName = obj.getString("fileName"),
                            encryptedPath = targetEncryptedFile.absolutePath,
                            fileType = obj.getString("fileType"),
                            fileSizeBytes = targetEncryptedFile.length(),
                            mimeType = obj.optString("mimeType", "application/octet-stream"),
                            albumId = null,
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = System.currentTimeMillis(),
                            isFavorite = obj.optBoolean("isFavorite", false),
                            isDecoy = isDecoy
                        )
                        vaultFileDao.insertFile(entity)
                        restoredCount++
                    } finally {
                        if (plainFile.exists()) plainFile.delete()
                    }
                }
            }

            // Clean up any remaining temporary files
            extractedPlainFiles.values.forEach { if (it.exists()) it.delete() }

            RestoreResult(restoredFilesCount = restoredCount)
        } finally {
            if (tempZipFile.exists()) tempZipFile.delete()
        }
    }
}
