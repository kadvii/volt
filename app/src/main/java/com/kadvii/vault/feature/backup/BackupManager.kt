package com.kadvii.vault.feature.backup

import com.kadvii.vault.core.security.AesGcmCipherService
import com.kadvii.vault.core.security.PasswordHashHelper
import com.kadvii.vault.data.database.VaultFileDao
import com.kadvii.vault.data.database.VaultFileEntity
import com.kadvii.vault.domain.model.VaultFile
import com.kadvii.vault.domain.model.VaultFileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
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
    private val filesDir: File
) {

    companion object {
        val MAGIC_HEADER = byteArrayOf('V'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte(), '1'.code.toByte())
        const val SALT_SIZE = 32
        const val PBKDF2_ITERATIONS = 100_000
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

        // 2. Prepare Zip Payload in memory / temp file
        val files = vaultFileDao.getAllFiles(isDecoy).first()
        val tempZipFile = File.createTempFile("backup_payload_", ".tmp", filesDir)

        try {
            ZipOutputStream(FileOutputStream(tempZipFile)).use { zipOut ->
                // Manifest JSON
                val manifestArray = JSONArray()
                files.forEach { fileEntity ->
                    val fileObj = JSONObject().apply {
                        put("uuid", fileEntity.uuid)
                        put("fileName", fileEntity.fileName)
                        put("fileType", fileEntity.fileType)
                        put("mimeType", fileEntity.mimeType)
                        put("fileSizeBytes", fileEntity.fileSizeBytes)
                        put("createdAt", fileEntity.createdAt)
                        put("isFavorite", fileEntity.isFavorite)
                        put("isDecoy", fileEntity.isDecoy)
                    }
                    manifestArray.put(fileObj)

                    // Write encrypted file content
                    val diskFile = File(fileEntity.encryptedPath)
                    if (diskFile.exists()) {
                        val entry = ZipEntry("files/${fileEntity.uuid}.enc")
                        zipOut.putNextEntry(entry)
                        FileInputStream(diskFile).use { fileIn ->
                            fileIn.copyTo(zipOut)
                        }
                        zipOut.closeEntry()
                    }
                }

                val manifestEntry = ZipEntry("manifest.json")
                zipOut.putNextEntry(manifestEntry)
                zipOut.write(manifestArray.toString().toByteArray(Charsets.UTF_8))
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
        if (inputStream.read(magic) != MAGIC_HEADER.size || !magic.contentEquals(MAGIC_HEADER)) {
            throw IllegalArgumentException("Invalid backup file: Unrecognized format or corrupted header")
        }

        // 2. Read Salt
        val salt = ByteArray(SALT_SIZE)
        if (inputStream.read(salt) != SALT_SIZE) {
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

            // 5. Read Zip and restore files
            val storageDir = if (isDecoy) File(filesDir, "vault_decoy") else File(filesDir, "vault_primary")
            if (!storageDir.exists()) storageDir.mkdirs()

            var restoredCount = 0
            val fileEntitiesToInsert = mutableListOf<VaultFileEntity>()
            var manifestJson: String? = null

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
                            val fileName = entry.name.removePrefix("files/")
                            val targetFile = File(storageDir, fileName)
                            FileOutputStream(targetFile).use { out ->
                                zipIn.copyTo(out)
                            }
                            restoredCount++
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            // 6. Restore Database records
            if (manifestJson != null) {
                val array = JSONArray(manifestJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val uuid = obj.getString("uuid")
                    val targetPath = File(storageDir, "$uuid.enc").absolutePath

                    val entity = VaultFileEntity(
                        uuid = uuid,
                        fileName = obj.getString("fileName"),
                        encryptedPath = targetPath,
                        fileType = obj.getString("fileType"),
                        fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                        mimeType = obj.optString("mimeType", "application/octet-stream"),
                        albumId = null,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = System.currentTimeMillis(),
                        isFavorite = obj.optBoolean("isFavorite", false),
                        isDecoy = isDecoy
                    )
                    vaultFileDao.insertFile(entity)
                }
            }

            RestoreResult(restoredFilesCount = restoredCount)
        } finally {
            if (tempZipFile.exists()) tempZipFile.delete()
        }
    }
}

