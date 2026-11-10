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

    override fun getFileById(id: Long): Flow<VaultFile?> {
        return vaultFileDao.getFileById(id).map { it?.toDomain() }
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
    }

    override suspend fun deleteFile(file: VaultFile): Boolean {
        val diskFile = File(file.encryptedPath)
        if (diskFile.exists()) {
            diskFile.delete()
        }
        vaultFileDao.deleteFileById(file.id)
        return true
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

    override suspend fun deleteAlbum(albumId: Long) {
        albumDao.deleteAlbumById(albumId)
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
}

