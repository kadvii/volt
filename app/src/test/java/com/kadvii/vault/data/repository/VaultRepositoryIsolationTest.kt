package com.kadvii.vault.data.repository

import com.kadvii.vault.core.security.AesGcmCipherService
import com.kadvii.vault.core.security.AndroidKeystoreManager
import com.kadvii.vault.data.database.AlbumDao
import com.kadvii.vault.data.database.AlbumEntity
import com.kadvii.vault.data.database.VaultFileDao
import com.kadvii.vault.data.database.VaultFileEntity
import com.kadvii.vault.domain.model.VaultFileType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

class FakeFullVaultFileDao : VaultFileDao {
    val files = mutableListOf<VaultFileEntity>()
    val flow = MutableStateFlow<List<VaultFileEntity>>(emptyList())

    private fun notifyUpdate() {
        flow.value = files.toList()
    }

    override fun getAllFiles(isDecoy: Boolean): Flow<List<VaultFileEntity>> {
        val filteredFlow = MutableStateFlow(files.filter { it.isDecoy == isDecoy })
        return filteredFlow
    }

    override fun getFilesByType(fileType: String, isDecoy: Boolean): Flow<List<VaultFileEntity>> {
        return MutableStateFlow(files.filter { it.fileType == fileType && it.isDecoy == isDecoy })
    }

    override fun getFilesByAlbum(albumId: Long, isDecoy: Boolean): Flow<List<VaultFileEntity>> {
        return MutableStateFlow(files.filter { it.albumId == albumId && it.isDecoy == isDecoy })
    }

    override fun getFavoriteFiles(isDecoy: Boolean): Flow<List<VaultFileEntity>> {
        return MutableStateFlow(files.filter { it.isFavorite && it.isDecoy == isDecoy })
    }

    override fun getFileById(id: Long): Flow<VaultFileEntity?> =
        MutableStateFlow(files.firstOrNull { it.id == id })

    override fun getFileByIdAndDecoy(id: Long, isDecoy: Boolean): Flow<VaultFileEntity?> =
        MutableStateFlow(files.firstOrNull { it.id == id && it.isDecoy == isDecoy })

    override suspend fun getFileByIdSync(id: Long): VaultFileEntity? = files.firstOrNull { it.id == id }

    override suspend fun insertFile(file: VaultFileEntity): Long {
        val assignedId = (files.size + 1).toLong()
        val withId = file.copy(id = assignedId)
        files.add(withId)
        notifyUpdate()
        return assignedId
    }

    override suspend fun updateFile(file: VaultFileEntity) {
        val index = files.indexOfFirst { it.id == file.id }
        if (index != -1) {
            files[index] = file
            notifyUpdate()
        }
    }

    override suspend fun deleteFileById(id: Long) {
        files.removeAll { it.id == id }
        notifyUpdate()
    }

    override suspend fun setFavorite(id: Long, isFavorite: Boolean, timestamp: Long) {
        val index = files.indexOfFirst { it.id == id }
        if (index != -1) {
            files[index] = files[index].copy(isFavorite = isFavorite, updatedAt = timestamp)
            notifyUpdate()
        }
    }

    override suspend fun moveToAlbum(id: Long, albumId: Long?, timestamp: Long) {
        val index = files.indexOfFirst { it.id == id }
        if (index != -1) {
            files[index] = files[index].copy(albumId = albumId, updatedAt = timestamp)
            notifyUpdate()
        }
    }

    override suspend fun clearAlbumForFiles(albumId: Long, isDecoy: Boolean, timestamp: Long) {
        for (i in files.indices) {
            if (files[i].albumId == albumId && files[i].isDecoy == isDecoy) {
                files[i] = files[i].copy(albumId = null, updatedAt = timestamp)
            }
        }
        notifyUpdate()
    }

    override suspend fun findDuplicate(fileName: String, sizeBytes: Long, isDecoy: Boolean): VaultFileEntity? {
        return files.firstOrNull { it.fileName == fileName && it.fileSizeBytes == sizeBytes && it.isDecoy == isDecoy }
    }

    override suspend fun getAllUuids(isDecoy: Boolean): List<String> {
        return files.filter { it.isDecoy == isDecoy }.map { it.uuid }
    }

    override suspend fun countFilesInAlbum(albumId: Long, isDecoy: Boolean): Int {
        return files.count { it.albumId == albumId && it.isDecoy == isDecoy }
    }
}

class FakeFullAlbumDao : AlbumDao {
    val albums = mutableListOf<AlbumEntity>()

    override fun getAlbums(isDecoy: Boolean): Flow<List<AlbumEntity>> {
        return MutableStateFlow(albums.filter { it.isDecoy == isDecoy })
    }

    override suspend fun insertAlbum(album: AlbumEntity): Long {
        val assignedId = (albums.size + 1).toLong()
        albums.add(album.copy(id = assignedId))
        return assignedId
    }

    override suspend fun deleteAlbumById(id: Long) {
        albums.removeAll { it.id == id }
    }

    override suspend fun renameAlbum(id: Long, newName: String) {
        val index = albums.indexOfFirst { it.id == id }
        if (index != -1) {
            albums[index] = albums[index].copy(name = newName)
        }
    }
}

class VaultRepositoryIsolationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fakeFileDao: FakeFullVaultFileDao
    private lateinit var fakeAlbumDao: FakeFullAlbumDao
    private lateinit var cipherService: AesGcmCipherService
    private lateinit var keystoreManager: AndroidKeystoreManager
    private lateinit var repository: VaultRepositoryImpl

    @Before
    fun setUp() {
        fakeFileDao = FakeFullVaultFileDao()
        fakeAlbumDao = FakeFullAlbumDao()
        cipherService = AesGcmCipherService()
        keystoreManager = AndroidKeystoreManager()
        repository = VaultRepositoryImpl(
            vaultFileDao = fakeFileDao,
            albumDao = fakeAlbumDao,
            cipherService = cipherService,
            keystoreManager = keystoreManager,
            filesDir = tempFolder.root
        )
    }

    @Test
    fun primaryFiles_neverAppearInDecoyQueries() = runBlocking {
        // Save primary file
        val input = ByteArrayInputStream("PrimarySecretContent".toByteArray())
        val primaryId = repository.saveEncryptedFile(
            sourceInputStream = input,
            originalName = "financial_statement.pdf",
            mimeType = "application/pdf",
            fileType = VaultFileType.DOCUMENT,
            isDecoy = false
        )

        // Query primary vault -> must contain file
        val primaryFiles = repository.getFiles(isDecoy = false).first()
        assertEquals(1, primaryFiles.size)
        assertEquals("financial_statement.pdf", primaryFiles[0].fileName)

        // Query decoy vault -> MUST BE EMPTY
        val decoyFiles = repository.getFiles(isDecoy = true).first()
        assertTrue("Primary file leaked into Decoy vault query!", decoyFiles.isEmpty())

        // Query by ID with isDecoy = true -> MUST RETURN NULL
        val decoyQuery = repository.getFileById(primaryId, isDecoy = true).first()
        assertNull("Primary file accessible from Decoy session!", decoyQuery)

        // Query by ID with isDecoy = false -> must return file
        val primaryQuery = repository.getFileById(primaryId, isDecoy = false).first()
        assertNotNull(primaryQuery)
        assertEquals(primaryId, primaryQuery!!.id)
    }

    @Test
    fun decoyFiles_neverAppearInPrimaryQueries() = runBlocking {
        // Save decoy file
        val input = ByteArrayInputStream("HarmlessCatPhoto".toByteArray())
        val decoyId = repository.saveEncryptedFile(
            sourceInputStream = input,
            originalName = "cat.jpg",
            mimeType = "image/jpeg",
            fileType = VaultFileType.PHOTO,
            isDecoy = true
        )

        // Query decoy vault -> must contain file
        val decoyFiles = repository.getFiles(isDecoy = true).first()
        assertEquals(1, decoyFiles.size)
        assertEquals("cat.jpg", decoyFiles[0].fileName)

        // Query primary vault -> MUST BE EMPTY
        val primaryFiles = repository.getFiles(isDecoy = false).first()
        assertTrue("Decoy file leaked into Primary vault query!", primaryFiles.isEmpty())

        // Query by ID with isDecoy = false -> MUST RETURN NULL
        val primaryQuery = repository.getFileById(decoyId, isDecoy = false).first()
        assertNull("Decoy file accessible from Primary session!", primaryQuery)
    }

    @Test
    fun deleteAlbum_safelyUnlinksContainedFiles() = runBlocking {
        val albumId = repository.createAlbum("Vacation", isDecoy = false)

        val input = ByteArrayInputStream("BeachPic".toByteArray())
        val fileId = repository.saveEncryptedFile(
            sourceInputStream = input,
            originalName = "beach.jpg",
            mimeType = "image/jpeg",
            fileType = VaultFileType.PHOTO,
            albumId = albumId,
            isDecoy = false
        )

        val fileBefore = repository.getFileById(fileId, isDecoy = false).first()
        assertEquals(albumId, fileBefore?.albumId)

        // Delete album
        repository.deleteAlbum(albumId, isDecoy = false)

        // File should still exist but have albumId = null
        val fileAfter = repository.getFileById(fileId, isDecoy = false).first()
        assertNotNull("File was deleted when album was deleted!", fileAfter)
        assertNull("File albumId was not cleared after album deletion!", fileAfter?.albumId)
    }
}
