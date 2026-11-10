package com.kadvii.vault.feature.vault

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class VaultTab {
    ALL,
    PHOTOS,
    VIDEOS,
    DOCUMENTS,
    ALBUMS
}

data class ImportSummary(
    val totalCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val failedFileNames: List<String> = emptyList(),
    val skippedDuplicatesCount: Int = 0
)

data class VaultUiState(
    val selectedTab: VaultTab = VaultTab.ALL,
    val selectedAlbumId: Long? = null,
    val selectedFileIds: Set<Long> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    val isImporting: Boolean = false,
    val importProgress: Float = 0f,
    val importProgressMessage: String? = null,
    val lastImportSummary: ImportSummary? = null,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModel(
    private val isDecoy: Boolean,
    private val getVaultFilesUseCase: GetVaultFilesUseCase,
    private val encryptAndStoreFileUseCase: EncryptAndStoreFileUseCase,
    private val deleteVaultFileUseCase: DeleteVaultFileUseCase,
    private val vaultRepository: VaultRepository,
    private val sessionManager: VaultSessionManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private var importJob: Job? = null

    val albums: StateFlow<List<Album>> = vaultRepository.getAlbums(isDecoy)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val files: StateFlow<List<VaultFile>> = _uiState.flatMapLatest { state ->
        val fileType = when (state.selectedTab) {
            VaultTab.ALL -> null
            VaultTab.PHOTOS -> VaultFileType.PHOTO
            VaultTab.VIDEOS -> VaultFileType.VIDEO
            VaultTab.DOCUMENTS -> VaultFileType.DOCUMENT
            VaultTab.ALBUMS -> null
        }
        getVaultFilesUseCase(
            fileType = fileType,
            albumId = state.selectedAlbumId,
            isDecoy = isDecoy
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onTabSelected(tab: VaultTab) {
        _uiState.update { it.copy(selectedTab = tab, selectedAlbumId = null) }
    }

    fun onAlbumSelected(albumId: Long?) {
        _uiState.update { it.copy(selectedAlbumId = albumId) }
    }

    fun toggleFileSelection(fileId: Long) {
        _uiState.update { current ->
            val updated = if (current.selectedFileIds.contains(fileId)) {
                current.selectedFileIds - fileId
            } else {
                current.selectedFileIds + fileId
            }
            current.copy(
                selectedFileIds = updated,
                isMultiSelectMode = updated.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedFileIds = emptySet(), isMultiSelectMode = false) }
    }

    fun deleteSelectedFiles() {
        val selectedIds = _uiState.value.selectedFileIds
        viewModelScope.launch {
            val allFiles = files.value
            allFiles.filter { it.id in selectedIds }.forEach { file ->
                deleteVaultFileUseCase(file)
            }
            clearSelection()
        }
    }

    fun deleteSingleFile(file: VaultFile) {
        viewModelScope.launch {
            deleteVaultFileUseCase(file)
        }
    }

    fun toggleFavorite(file: VaultFile) {
        viewModelScope.launch {
            vaultRepository.toggleFavorite(file.id, !file.isFavorite)
        }
    }

    fun createAlbum(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            vaultRepository.createAlbum(name.trim(), isDecoy)
        }
    }

    fun deleteAlbum(albumId: Long) {
        viewModelScope.launch {
            vaultRepository.deleteAlbum(albumId, isDecoy)
            if (_uiState.value.selectedAlbumId == albumId) {
                _uiState.update { it.copy(selectedAlbumId = null) }
            }
        }
    }

    fun clearImportSummary() {
        _uiState.update { it.copy(lastImportSummary = null, errorMessage = null) }
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        sessionManager?.isImportInProgress = false
        _uiState.update {
            it.copy(
                isImporting = false,
                importProgressMessage = "Import cancelled"
            )
        }
    }

    fun importUris(context: Context, uris: List<Uri>, defaultType: VaultFileType? = null) {
        if (uris.isEmpty()) return
        importJob?.cancel()

        importJob = viewModelScope.launch {
            sessionManager?.isImportInProgress = true
            _uiState.update {
                it.copy(
                    isImporting = true,
                    importProgress = 0f,
                    importProgressMessage = "Preparing import of ${uris.size} file(s)...",
                    lastImportSummary = null
                )
            }

            var successCount = 0
            var failureCount = 0
            var duplicateCount = 0
            val failedNames = mutableListOf<String>()
            val total = uris.size

            try {
                withContext(Dispatchers.IO) {
                    uris.forEachIndexed { index, uri ->
                        ensureActive()

                        val (fileName, mimeType, size) = queryUriMetadata(context, uri)
                        val resolvedType = defaultType ?: VaultFileType.fromMimeType(mimeType)

                        _uiState.update {
                            it.copy(
                                importProgress = index.toFloat() / total,
                                importProgressMessage = "Encrypting ($index/$total): $fileName"
                            )
                        }

                        // Duplicate check
                        if (size > 0 && vaultRepository.checkDuplicate(fileName, size, isDecoy)) {
                            duplicateCount++
                            return@forEachIndexed
                        }

                        try {
                            val stream = context.contentResolver.openInputStream(uri)
                            if (stream == null) {
                                failureCount++
                                failedNames.add("$fileName (unable to open stream)")
                                return@forEachIndexed
                            }

                            stream.use { inputStream ->
                                encryptAndStoreFileUseCase(
                                    sourceInputStream = inputStream,
                                    originalName = fileName,
                                    mimeType = mimeType,
                                    fileType = resolvedType,
                                    albumId = _uiState.value.selectedAlbumId,
                                    isDecoy = isDecoy
                                )
                            }
                            successCount++
                        } catch (e: Exception) {
                            ensureActive()
                            failureCount++
                            failedNames.add("$fileName (${e.message ?: "encryption failed"})")
                        }
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                failureCount++
                failedNames.add("Import error: ${e.message}")
            } finally {
                sessionManager?.isImportInProgress = false
                val summary = ImportSummary(
                    totalCount = total,
                    successCount = successCount,
                    failureCount = failureCount,
                    failedFileNames = failedNames,
                    skippedDuplicatesCount = duplicateCount
                )
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importProgress = 1f,
                        importProgressMessage = null,
                        lastImportSummary = summary
                    )
                }
            }
        }
    }

    private fun queryUriMetadata(context: Context, uri: Uri): Triple<String, String, Long> {
        var name = "file_${System.currentTimeMillis()}"
        var size = -1L
        var mimeType = try {
            context.contentResolver.getType(uri) ?: "application/octet-stream"
        } catch (_: Exception) {
            "application/octet-stream"
        }

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        val queried = cursor.getString(nameIndex)
                        if (!queried.isNullOrBlank()) {
                            name = queried
                        }
                    }
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) { }

        // Fix missing extension if MIME is known
        if (!name.contains(".") && mimeType != "application/octet-stream") {
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            if (!extension.isNullOrBlank()) {
                name = "$name.$extension"
            }
        }

        // Infer MIME if generic octet-stream but extension exists
        if (mimeType == "application/octet-stream" && name.contains(".")) {
            val extension = name.substringAfterLast('.').lowercase()
            val inferred = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            if (!inferred.isNullOrBlank()) {
                mimeType = inferred
            }
        }

        return Triple(name, mimeType, size)
    }

    companion object {
        fun provideFactory(
            isDecoy: Boolean,
            getVaultFilesUseCase: GetVaultFilesUseCase,
            encryptAndStoreFileUseCase: EncryptAndStoreFileUseCase,
            deleteVaultFileUseCase: DeleteVaultFileUseCase,
            vaultRepository: VaultRepository,
            sessionManager: VaultSessionManager? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VaultViewModel(
                    isDecoy = isDecoy,
                    getVaultFilesUseCase = getVaultFilesUseCase,
                    encryptAndStoreFileUseCase = encryptAndStoreFileUseCase,
                    deleteVaultFileUseCase = deleteVaultFileUseCase,
                    vaultRepository = vaultRepository,
                    sessionManager = sessionManager
                ) as T
            }
        }
    }
}
