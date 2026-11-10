package com.kadvii.vault.feature.vault

import com.kadvii.vault.core.security.VaultSessionManager
import com.kadvii.vault.domain.model.Album
import com.kadvii.vault.domain.model.VaultFile
import com.kadvii.vault.domain.model.VaultFileType
import com.kadvii.vault.domain.repository.VaultRepository
import com.kadvii.vault.domain.usecase.DeleteVaultFileUseCase
import com.kadvii.vault.domain.usecase.EncryptAndStoreFileUseCase
import com.kadvii.vault.domain.usecase.GetVaultFilesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.InputStream

class FakeVaultRepositoryForVm : VaultRepository {
    val files = mutableListOf<VaultFile>()
    val albums = mutableListOf<Album>()
    val deletedFiles = mutableListOf<VaultFile>()
    val filesFlow = MutableStateFlow<List<VaultFile>>(emptyList())
    val albumsFlow = MutableStateFlow<List<Album>>(emptyList())

    override fun getFiles(fileType: VaultFileType?, albumId: Long?, isDecoy: Boolean): Flow<List<VaultFile>> = filesFlow
    override fun getFavoriteFiles(isDecoy: Boolean): Flow<List<VaultFile>> = filesFlow
    override fun getFileById(id: Long, isDecoy: Boolean?): Flow<VaultFile?> =
        MutableStateFlow(files.firstOrNull { it.id == id })
    override fun getAlbums(isDecoy: Boolean): Flow<List<Album>> = albumsFlow

    override suspend fun saveEncryptedFile(
        sourceInputStream: InputStream,
        originalName: String,
        mimeType: String,
        fileType: VaultFileType,
        albumId: Long?,
        isDecoy: Boolean
    ): Long {
        val id = (files.size + 1).toLong()
        val file = VaultFile(
            id = id,
            uuid = "uuid-$id",
            fileName = originalName,
            encryptedPath = "/path/$id",
            fileType = fileType,
            fileSizeBytes = 1024L,
            mimeType = mimeType,
            isDecoy = isDecoy
        )
        files.add(file)
        filesFlow.value = files.toList()
        return id
    }

    override suspend fun deleteFile(file: VaultFile): Boolean {
        deletedFiles.add(file)
        files.remove(file)
        filesFlow.value = files.toList()
        return true
    }

    override suspend fun toggleFavorite(fileId: Long, isFavorite: Boolean) {}
    override suspend fun moveToAlbum(fileId: Long, albumId: Long?) {}

    override suspend fun createAlbum(name: String, isDecoy: Boolean): Long {
        val id = (albums.size + 1).toLong()
        albums.add(Album(id = id, name = name, isDecoy = isDecoy))
        albumsFlow.value = albums.toList()
        return id
    }

    override suspend fun deleteAlbum(albumId: Long, isDecoy: Boolean) {
        albums.removeAll { it.id == albumId }
        albumsFlow.value = albums.toList()
    }

    override fun getDecryptedBytes(file: VaultFile): ByteArray = ByteArray(0)
    override fun getDecryptedStream(file: VaultFile): InputStream = ByteArray(0).inputStream()
    override suspend fun decryptToFile(file: VaultFile, destinationFile: File) {}
    override suspend fun checkDuplicate(fileName: String, fileSizeBytes: Long, isDecoy: Boolean): Boolean = false
    override suspend fun cleanupOrphanedFiles(isDecoy: Boolean): Int = 0
}

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeVaultRepositoryForVm
    private lateinit var sessionManager: VaultSessionManager
    private lateinit var viewModel: VaultViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeVaultRepositoryForVm()
        sessionManager = VaultSessionManager()
        viewModel = VaultViewModel(
            isDecoy = false,
            getVaultFilesUseCase = GetVaultFilesUseCase(fakeRepo),
            encryptAndStoreFileUseCase = EncryptAndStoreFileUseCase(fakeRepo),
            deleteVaultFileUseCase = DeleteVaultFileUseCase(fakeRepo),
            vaultRepository = fakeRepo,
            sessionManager = sessionManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selection_toggleAndClear_managesSelectionCorrectly() {
        assertFalse(viewModel.uiState.value.isMultiSelectMode)

        viewModel.toggleFileSelection(101L)
        assertTrue(viewModel.uiState.value.isMultiSelectMode)
        assertTrue(viewModel.uiState.value.selectedFileIds.contains(101L))

        viewModel.toggleFileSelection(102L)
        assertEquals(2, viewModel.uiState.value.selectedFileIds.size)

        viewModel.toggleFileSelection(101L)
        assertEquals(1, viewModel.uiState.value.selectedFileIds.size)
        assertFalse(viewModel.uiState.value.selectedFileIds.contains(101L))

        viewModel.clearSelection()
        assertFalse(viewModel.uiState.value.isMultiSelectMode)
        assertTrue(viewModel.uiState.value.selectedFileIds.isEmpty())
    }

    @Test
    fun cancelImport_abortsOperationAndResetsFlags() {
        sessionManager.isImportInProgress = true
        viewModel.cancelImport()

        assertFalse(viewModel.uiState.value.isImporting)
        assertEquals("Import cancelled", viewModel.uiState.value.importProgressMessage)
        assertFalse(sessionManager.isImportInProgress)
    }

    @Test
    fun clearImportSummary_resetsSummaryState() {
        viewModel.clearImportSummary()
        assertNull(viewModel.uiState.value.lastImportSummary)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun deleteSelectedFiles_removesAllSelectedFiles() = runTest {
        val collectJob = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.files.collect {}
        }

        val file1 = VaultFile(1L, "u1", "f1.jpg", "/p1", VaultFileType.PHOTO, 100L, "image/jpeg")
        val file2 = VaultFile(2L, "u2", "f2.jpg", "/p2", VaultFileType.PHOTO, 200L, "image/jpeg")
        fakeRepo.files.addAll(listOf(file1, file2))
        fakeRepo.filesFlow.value = fakeRepo.files.toList()
        advanceUntilIdle()

        viewModel.toggleFileSelection(1L)
        viewModel.toggleFileSelection(2L)

        viewModel.deleteSelectedFiles()
        advanceUntilIdle()

        assertEquals(2, fakeRepo.deletedFiles.size)
        assertFalse(viewModel.uiState.value.isMultiSelectMode)
        assertTrue(viewModel.uiState.value.selectedFileIds.isEmpty())
        collectJob.cancel()
    }
}
