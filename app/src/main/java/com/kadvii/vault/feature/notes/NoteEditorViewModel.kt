package com.kadvii.vault.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kadvii.vault.domain.model.Note
import com.kadvii.vault.domain.usecase.DeleteNoteUseCase
import com.kadvii.vault.domain.usecase.GetNoteByIdUseCase
import com.kadvii.vault.domain.usecase.SaveNoteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NoteEditorViewModel(
    private val noteId: Long,
    private val getNoteByIdUseCase: GetNoteByIdUseCase,
    private val saveNoteUseCase: SaveNoteUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteEditorUiState(id = noteId, isNewNote = noteId <= 0))
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    init {
        if (noteId > 0) {
            loadNote(noteId)
        }
    }

    private fun loadNote(id: Long) {
        viewModelScope.launch {
            val note = getNoteByIdUseCase(id)
            if (note != null) {
                _uiState.update {
                    it.copy(
                        id = note.id,
                        title = note.title,
                        content = note.content,
                        category = note.category,
                        colorHex = note.colorHex,
                        isPinned = note.isPinned,
                        isNewNote = false
                    )
                }
            }
        }
    }

    fun onTitleChange(newTitle: String) {
        _uiState.update { it.copy(title = newTitle) }
    }

    fun onContentChange(newContent: String) {
        _uiState.update { it.copy(content = newContent) }
    }

    fun onCategoryChange(newCategory: String?) {
        _uiState.update { it.copy(category = newCategory) }
    }

    fun onColorChange(newColorHex: String) {
        _uiState.update { it.copy(colorHex = newColorHex) }
    }

    fun onTogglePin() {
        _uiState.update { it.copy(isPinned = !it.isPinned) }
    }

    fun saveNote(onSaved: () -> Unit) {
        val state = _uiState.value
        if (state.title.isBlank() && state.content.isBlank()) {
            onSaved()
            return
        }

        viewModelScope.launch {
            val note = Note(
                id = if (state.isNewNote) 0 else state.id,
                title = state.title.ifBlank { "Untitled" },
                content = state.content,
                category = state.category,
                colorHex = state.colorHex,
                isPinned = state.isPinned,
                updatedAt = System.currentTimeMillis()
            )
            saveNoteUseCase(note)
            _uiState.update { it.copy(isSaved = true) }
            onSaved()
        }
    }

    fun deleteNote(onDeleted: () -> Unit) {
        val id = _uiState.value.id
        if (id > 0) {
            viewModelScope.launch {
                deleteNoteUseCase(id)
                onDeleted()
            }
        } else {
            onDeleted()
        }
    }

    companion object {
        fun provideFactory(
            noteId: Long,
            getNoteByIdUseCase: GetNoteByIdUseCase,
            saveNoteUseCase: SaveNoteUseCase,
            deleteNoteUseCase: DeleteNoteUseCase
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NoteEditorViewModel(
                    noteId = noteId,
                    getNoteByIdUseCase = getNoteByIdUseCase,
                    saveNoteUseCase = saveNoteUseCase,
                    deleteNoteUseCase = deleteNoteUseCase
                ) as T
            }
        }
    }
}

