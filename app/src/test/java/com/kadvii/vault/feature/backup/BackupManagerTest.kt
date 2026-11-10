package com.kadvii.vault.feature.backup

import com.kadvii.vault.core.security.AesGcmCipherService
import com.kadvii.vault.data.database.VaultFileDao
import com.kadvii.vault.data.database.VaultFileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class FakeVaultFileDao : VaultFileDao {
    val filesList = mutableListOf<VaultFileEntity>()
    val flow = MutableStateFlow<List<VaultFileEntity>>(emptyList())

    private fun updateFlow() {
        flow.value = filesList.toList()
    }

    override fun getAllFiles(isDecoy: Boolean): Flow<List<VaultFileEntity>> = flow
    override fun getFilesByType(fileType: String, isDecoy: Boolean): Flow<List<VaultFileEntity>> = flow
    override fun getFilesByAlbum(albumId: Long, isDecoy: Boolean): Flow<List<VaultFileEntity>> = flow
    override fun getFavoriteFiles(isDecoy: Boolean): Flow<List<VaultFileEntity>> = flow
    override fun getFileById(id: Long): Flow<VaultFileEntity?> = MutableStateFlow(filesList.firstOrNull { it.id == id })
    override suspend fun getFileByIdSync(id: Long): VaultFileEntity? = filesList.firstOrNull { it.id == id }

    override suspend fun insertFile(file: VaultFileEntity): Long {
        filesList.add(file)
        updateFlow()
        return filesList.size.toLong()
    }

    override suspend fun updateFile(file: VaultFileEntity) {
        val index = filesList.indexOfFirst { it.id == file.id }
        if (index != -1) filesList[index] = file
        updateFlow()
    }

    override suspend fun deleteFileById(id: Long) {
        filesList.removeAll { it.id == id }
        updateFlow()
    }

    override suspend fun setFavorite(id: Long, isFavorite: Boolean, timestamp: Long) {}
    override suspend fun moveToAlbum(id: Long, albumId: Long?, timestamp: Long) {}
    override suspend fun countFilesInAlbum(albumId: Long, isDecoy: Boolean): Int = 0
}

class BackupManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fakeDao: FakeVaultFileDao
    private lateinit var cipherService: AesGcmCipherService
    private lateinit var backupManager: BackupManager
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        filesDir = tempFolder.root
        fakeDao = FakeVaultFileDao()
        cipherService = AesGcmCipherService()
        backupManager = BackupManager(
            vaultFileDao = fakeDao,
            cipherService = cipherService,
            filesDir = filesDir
        )
    }

    @Test
    fun createBackup_writesVltMagicHeader() = runBlocking {
        val backupOut = ByteArrayOutputStream()
        backupManager.createBackup(
            passphrase = "SecurePassphrase123!",
            outputStream = backupOut,
            isDecoy = false
        )

        val bytes = backupOut.toByteArray()
        assertTrue(bytes.size >= 36) // 4 magic + 32 salt + encrypted payload
        assertEquals('V'.code.toByte(), bytes[0])
        assertEquals('L'.code.toByte(), bytes[1])
        assertEquals('T'.code.toByte(), bytes[2])
        assertEquals('1'.code.toByte(), bytes[3])
    }

    @Test
    fun roundtrip_createAndRestoreBackup_restoresFilesAndDatabase() = runBlocking {
        // Prepare dummy disk file
        val dummyFile = File(filesDir, "test_file.enc")
        dummyFile.writeBytes("EncryptedContentBytes_12345".toByteArray())

        val entity = VaultFileEntity(
            id = 1L,
            uuid = "test-uuid-1",
            fileName = "photo.jpg",
            encryptedPath = dummyFile.absolutePath,
            fileType = "PHOTO",
            fileSizeBytes = dummyFile.length(),
            mimeType = "image/jpeg",
            isDecoy = false
        )
        fakeDao.insertFile(entity)

        val backupOut = ByteArrayOutputStream()
        val result = backupManager.createBackup(
            passphrase = "MySecretBackupKey99!",
            outputStream = backupOut,
            isDecoy = false
        )
        assertEquals(1, result.fileCount)

        // Clear DAO for restore test
        fakeDao.filesList.clear()

        // Restore
        val backupIn = ByteArrayInputStream(backupOut.toByteArray())
        val restoreResult = backupManager.restoreBackup(
            passphrase = "MySecretBackupKey99!",
            inputStream = backupIn,
            isDecoy = false
        )

        assertEquals(1, restoreResult.restoredFilesCount)
        assertEquals(1, fakeDao.filesList.size)
        assertEquals("photo.jpg", fakeDao.filesList[0].fileName)
    }

    @Test
    fun restoreBackup_withIncorrectPassphrase_throwsSecurityException() = runBlocking {
        val backupOut = ByteArrayOutputStream()
        backupManager.createBackup(
            passphrase = "CorrectPassphrase!",
            outputStream = backupOut,
            isDecoy = false
        )

        val backupIn = ByteArrayInputStream(backupOut.toByteArray())
        try {
            backupManager.restoreBackup(
                passphrase = "WrongPassphraseX",
                inputStream = backupIn,
                isDecoy = false
            )
            fail("Expected SecurityException when passphrase is wrong")
        } catch (e: SecurityException) {
            // Expected
            assertTrue(e.message!!.contains("verification failed", ignoreCase = true) || e.message!!.contains("tampered", ignoreCase = true))
        }
    }
}

