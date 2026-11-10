package com.kadvii.vault.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kadvii.vault.domain.model.Note
import com.kadvii.vault.domain.repository.NotesRepository
import com.kadvii.vault.domain.usecase.DeleteNoteUseCase
import com.kadvii.vault.domain.usecase.GetNotesUseCase
import com.kadvii.vault.domain.usecase.SaveNoteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotesViewModel(
    private val getNotesUseCase: GetNotesUseCase,
    private val saveNoteUseCase: SaveNoteUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase,
    private val notesRepository: NotesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotesUiState(isLoading = true))
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    init {
        loadNotes()
    }

    private fun loadNotes() {
        viewModelScope.launch {
            combine(
                getNotesUseCase(),
                notesRepository.getCategories()
            ) { notesList, dbCategories ->
                val defaultCategories = listOf("Personal", "Work", "Ideas")
                val combinedCategories = (defaultCategories + dbCategories).distinct()
                notesList to combinedCategories
            }.collect { (notesList, categories) ->
                _uiState.update { currentState ->
                    currentState.copy(
                        notes = notesList,
                        categories = categories,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onCategorySelected(category: String?) {
        _uiState.update {
            it.copy(selectedCategory = if (it.selectedCategory == category) null else category)
        }
    }

    fun onDeleteNote(id: Long) {
        viewModelScope.launch {
            deleteNoteUseCase(id)
        }
    }

    fun onTogglePin(note: Note) {
        viewModelScope.launch {
            saveNoteUseCase(note.copy(isPinned = !note.isPinned))
        }
    }

    companion object {
        fun provideFactory(
            getNotesUseCase: GetNotesUseCase,
            saveNoteUseCase: SaveNoteUseCase,
            deleteNoteUseCase: DeleteNoteUseCase,
            notesRepository: NotesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NotesViewModel(
                    getNotesUseCase = getNotesUseCase,
                    saveNoteUseCase = saveNoteUseCase,
                    deleteNoteUseCase = deleteNoteUseCase,
                    notesRepository = notesRepository
                ) as T
            }
        }
    }
}
