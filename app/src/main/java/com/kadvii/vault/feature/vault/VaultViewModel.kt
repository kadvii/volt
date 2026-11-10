package com.kadvii.vault.feature.vault

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kadvii.vault.domain.model.Album
import com.kadvii.vault.domain.model.VaultFile
import com.kadvii.vault.domain.model.VaultFileType
import com.kadvii.vault.domain.repository.VaultRepository
import com.kadvii.vault.domain.usecase.DeleteVaultFileUseCase
import com.kadvii.vault.domain.usecase.EncryptAndStoreFileUseCase
import com.kadvii.vault.domain.usecase.GetVaultFilesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

data class VaultUiState(
    val selectedTab: VaultTab = VaultTab.ALL,
    val selectedAlbumId: Long? = null,
    val selectedFileIds: Set<Long> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    val isImporting: Boolean = false,
    val importProgressMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModel(
    private val isDecoy: Boolean,
    private val getVaultFilesUseCase: GetVaultFilesUseCase,
    private val encryptAndStoreFileUseCase: EncryptAndStoreFileUseCase,
    private val deleteVaultFileUseCase: DeleteVaultFileUseCase,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

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

    fun importUris(context: Context, uris: List<Uri>, defaultType: VaultFileType? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importProgressMessage = "Encrypting files...") }
            try {
                withContext(Dispatchers.IO) {
                    uris.forEach { uri ->
                        try {
                            val (fileName, mimeType) = queryUriMetadata(context, uri)
                            val resolvedType = defaultType ?: VaultFileType.fromMimeType(mimeType)

                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                encryptAndStoreFileUseCase(
                                    sourceInputStream = stream,
                                    originalName = fileName,
                                    mimeType = mimeType,
                                    fileType = resolvedType,
                                    albumId = _uiState.value.selectedAlbumId,
                                    isDecoy = isDecoy
                                )
                            }
                        } catch (fileEx: Exception) {
                            fileEx.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uiState.update { it.copy(isImporting = false, importProgressMessage = null) }
            }
        }
    }

    private fun queryUriMetadata(context: Context, uri: Uri): Pair<String, String> {
        var name = "file_${System.currentTimeMillis()}"
        val mimeType = try {
            context.contentResolver.getType(uri) ?: "application/octet-stream"
        } catch (_: Exception) {
            "application/octet-stream"
        }

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val queried = cursor.getString(nameIndex)
                    if (!queried.isNullOrBlank()) {
                        name = queried
                    }
                }
            }
        } catch (_: Exception) { }

        return Pair(name, mimeType)
    }

    companion object {
        fun provideFactory(
            isDecoy: Boolean,
            getVaultFilesUseCase: GetVaultFilesUseCase,
            encryptAndStoreFileUseCase: EncryptAndStoreFileUseCase,
            deleteVaultFileUseCase: DeleteVaultFileUseCase,
            vaultRepository: VaultRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VaultViewModel(
                    isDecoy = isDecoy,
                    getVaultFilesUseCase = getVaultFilesUseCase,
                    encryptAndStoreFileUseCase = encryptAndStoreFileUseCase,
                    deleteVaultFileUseCase = deleteVaultFileUseCase,
                    vaultRepository = vaultRepository
                ) as T
            }
        }
    }
}
