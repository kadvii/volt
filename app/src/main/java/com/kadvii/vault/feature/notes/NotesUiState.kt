package com.kadvii.vault.feature.notes

import com.kadvii.vault.domain.model.Note

data class NotesUiState(
    val notes: List<Note> = emptyList(),
    val categories: List<String> = listOf("Personal", "Work", "Ideas"),
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val isLoading: Boolean = false
) {
    val isEmpty: Boolean
        get() = notes.isEmpty() && searchQuery.isBlank()

    val filteredNotes: List<Note>
        get() = notes.filter { note ->
            val matchesCategory = selectedCategory == null || note.category.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                note.title.contains(searchQuery, ignoreCase = true) ||
                note.content.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
}
