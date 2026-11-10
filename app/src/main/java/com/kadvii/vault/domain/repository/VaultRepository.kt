package com.kadvii.vault.domain.repository

import com.kadvii.vault.domain.model.Album
import com.kadvii.vault.domain.model.VaultFile
import com.kadvii.vault.domain.model.VaultFileType
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.InputStream

interface VaultRepository {
    fun getFiles(fileType: VaultFileType? = null, albumId: Long? = null, isDecoy: Boolean): Flow<List<VaultFile>>
    fun getFavoriteFiles(isDecoy: Boolean): Flow<List<VaultFile>>
    fun getFileById(id: Long, isDecoy: Boolean? = null): Flow<VaultFile?>
    fun getAlbums(isDecoy: Boolean): Flow<List<Album>>

    suspend fun saveEncryptedFile(
        sourceInputStream: InputStream,
        originalName: String,
        mimeType: String,
        fileType: VaultFileType,
        albumId: Long? = null,
        isDecoy: Boolean
    ): Long

    suspend fun deleteFile(file: VaultFile): Boolean
    suspend fun toggleFavorite(fileId: Long, isFavorite: Boolean)
    suspend fun moveToAlbum(fileId: Long, albumId: Long?)
    suspend fun createAlbum(name: String, isDecoy: Boolean): Long
    suspend fun deleteAlbum(albumId: Long, isDecoy: Boolean)

    fun getDecryptedBytes(file: VaultFile): ByteArray
    fun getDecryptedStream(file: VaultFile): InputStream
    suspend fun decryptToFile(file: VaultFile, destinationFile: File)
    suspend fun checkDuplicate(fileName: String, fileSizeBytes: Long, isDecoy: Boolean): Boolean
    suspend fun cleanupOrphanedFiles(isDecoy: Boolean): Int
}
