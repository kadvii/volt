package com.kadvii.vault.data.repository

import com.kadvii.vault.core.security.AesGcmCipherService
import com.kadvii.vault.core.security.AndroidKeystoreManager
import com.kadvii.vault.core.security.KeystoreManager
import com.kadvii.vault.data.database.AlbumDao
import com.kadvii.vault.data.database.AlbumEntity
import com.kadvii.vault.data.database.VaultFileDao
import com.kadvii.vault.data.database.VaultFileEntity
import com.kadvii.vault.domain.model.Album
import com.kadvii.vault.domain.model.VaultFile
import com.kadvii.vault.domain.model.VaultFileType
import com.kadvii.vault.domain.repository.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID

class VaultRepositoryImpl(
    private val vaultFileDao: VaultFileDao,
    private val albumDao: AlbumDao,
    private val cipherService: AesGcmCipherService,
    private val keystoreManager: KeystoreManager,
    private val filesDir: File
) : VaultRepository {

    private fun getStorageDirectory(isDecoy: Boolean): File {
        val dir = if (isDecoy) {
            File(filesDir, "vault_decoy")
        } else {
            File(filesDir, "vault_primary")
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getKeyAlias(isDecoy: Boolean): String {
        return if (isDecoy) {
            AndroidKeystoreManager.DECOY_VAULT_KEY_ALIAS
        } else {
            AndroidKeystoreManager.MASTER_VAULT_KEY_ALIAS
        }
    }

    override fun getFiles(
        fileType: VaultFileType?,
        albumId: Long?,
        isDecoy: Boolean
    ): Flow<List<VaultFile>> {
        return when {
            albumId != null -> {
                vaultFileDao.getFilesByAlbum(albumId, isDecoy).map { entities ->
                    entities.map { it.toDomain() }
                }
            }
            fileType != null -> {
                vaultFileDao.getFilesByType(fileType.name, isDecoy).map { entities ->
                    entities.map { it.toDomain() }
                }
            }
            else -> {
                vaultFileDao.getAllFiles(isDecoy).map { entities ->
                    entities.map { it.toDomain() }
                }
            }
        }
    }

    override fun getFavoriteFiles(isDecoy: Boolean): Flow<List<VaultFile>> {
        return vaultFileDao.getFavoriteFiles(isDecoy).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getFileById(id: Long, isDecoy: Boolean?): Flow<VaultFile?> {
        val flow = if (isDecoy != null) {
            vaultFileDao.getFileByIdAndDecoy(id, isDecoy)
        } else {
            vaultFileDao.getFileById(id)
        }
        return flow.map { it?.toDomain() }
    }

    override fun getAlbums(isDecoy: Boolean): Flow<List<Album>> {
        return albumDao.getAlbums(isDecoy).map { entities ->
            entities.map { entity ->
                val count = vaultFileDao.countFilesInAlbum(entity.id, isDecoy)
                entity.toDomain(fileCount = count)
            }
        }
    }

    override suspend fun saveEncryptedFile(
        sourceInputStream: InputStream,
        originalName: String,
        mimeType: String,
        fileType: VaultFileType,
        albumId: Long?,
        isDecoy: Boolean
    ): Long = withContext(Dispatchers.IO) {
        val storageDir = getStorageDirectory(isDecoy)
        val uuid = UUID.randomUUID().toString()
        val destinationFile = File(storageDir, "$uuid.enc")
        val key = keystoreManager.getOrCreateKey(getKeyAlias(isDecoy))

        try {
            // Encrypt input stream to destination file atomically
            cipherService.encryptStreamToFileAtomically(sourceInputStream, destinationFile, key)

            val entity = VaultFileEntity(
                uuid = uuid,
                fileName = originalName,
                encryptedPath = destinationFile.absolutePath,
                fileType = fileType.name,
                fileSizeBytes = destinationFile.length(),
                mimeType = mimeType,
                thumbnailPath = null,
                albumId = albumId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isFavorite = false,
                isDecoy = isDecoy
            )

            vaultFileDao.insertFile(entity)
        } catch (e: Exception) {
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            throw e
        }
    }

    override suspend fun deleteFile(file: VaultFile): Boolean = withContext(Dispatchers.IO) {
        val diskFile = File(file.encryptedPath)
        if (diskFile.exists()) {
            diskFile.delete()
        }
        vaultFileDao.deleteFileById(file.id)
        true
    }

    override suspend fun toggleFavorite(fileId: Long, isFavorite: Boolean) {
        vaultFileDao.setFavorite(fileId, isFavorite)
    }

    override suspend fun moveToAlbum(fileId: Long, albumId: Long?) {
        vaultFileDao.moveToAlbum(fileId, albumId)
    }

    override suspend fun createAlbum(name: String, isDecoy: Boolean): Long {
        val entity = AlbumEntity(name = name, isDecoy = isDecoy)
        return albumDao.insertAlbum(entity)
    }

    override suspend fun deleteAlbum(albumId: Long, isDecoy: Boolean) {
        vaultFileDao.clearAlbumForFiles(albumId, isDecoy)
        albumDao.deleteAlbumById(albumId)
    }

    override suspend fun checkDuplicate(fileName: String, fileSizeBytes: Long, isDecoy: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            vaultFileDao.findDuplicate(fileName, fileSizeBytes, isDecoy) != null
        }

    override fun getDecryptedBytes(file: VaultFile): ByteArray {
        val key = keystoreManager.getOrCreateKey(getKeyAlias(file.isDecoy))
        val diskFile = File(file.encryptedPath)
        return cipherService.decryptFileToByteArray(diskFile, key)
    }

    override fun getDecryptedStream(file: VaultFile): InputStream {
        val key = keystoreManager.getOrCreateKey(getKeyAlias(file.isDecoy))
        val diskFile = File(file.encryptedPath)
        return cipherService.getDecryptedStream(diskFile, key)
    }

    override suspend fun decryptToFile(file: VaultFile, destinationFile: File): Unit = withContext(Dispatchers.IO) {
        val key = keystoreManager.getOrCreateKey(getKeyAlias(file.isDecoy))
        val diskFile = File(file.encryptedPath)
        cipherService.decryptFileToFileAtomically(diskFile, destinationFile, key)
    }

    override suspend fun cleanupOrphanedFiles(isDecoy: Boolean): Int = withContext(Dispatchers.IO) {
        val storageDir = getStorageDirectory(isDecoy)
        val knownUuids = vaultFileDao.getAllUuids(isDecoy).toSet()
        var deletedCount = 0

        storageDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".tmp") || file.name.endsWith(".dec_tmp")) {
                file.delete()
                deletedCount++
            } else if (file.name.endsWith(".enc")) {
                val fileUuid = file.name.removeSuffix(".enc")
                if (fileUuid !in knownUuids) {
                    file.delete()
                    deletedCount++
                }
            }
        }
        deletedCount
    }
}
