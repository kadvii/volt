package com.kadvii.vault.feature.notes

data class NoteEditorUiState(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val category: String? = null,
    val colorHex: String = "#F7F7F2",
    val isPinned: Boolean = false,
    val isNewNote: Boolean = true,
    val isSaved: Boolean = false,
    val availableCategories: List<String> = listOf("Personal", "Work", "Ideas")
)

