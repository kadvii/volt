package com.kadvii.vault.feature.notes

import com.kadvii.vault.domain.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesUiStateTest {

    @Test
    fun defaultState_isEmpty_returnsTrue() {
        val state = NotesUiState()
        assertTrue(state.isEmpty)
        assertTrue(state.filteredNotes.isEmpty())
    }

    @Test
    fun stateWithNotes_isEmpty_returnsFalse() {
        val note = Note(id = 1L, title = "Meeting", content = "Discuss goals", category = "Work")
        val state = NotesUiState(notes = listOf(note))
        assertFalse(state.isEmpty)
        assertEquals(1, state.filteredNotes.size)
    }

    @Test
    fun stateWithSearchQuery_filtersNotesCorrectly() {
        val note1 = Note(id = 1L, title = "Shopping", content = "Apples, Milk", category = "Personal")
        val note2 = Note(id = 2L, title = "Work Review", content = "Quarterly results", category = "Work")
        val state = NotesUiState(
            notes = listOf(note1, note2),
            searchQuery = "shopping"
        )

        val filtered = state.filteredNotes
        assertEquals(1, filtered.size)
        assertEquals("Shopping", filtered.first().title)
    }

    @Test
    fun stateWithCategoryFilter_filtersNotesCorrectly() {
        val note1 = Note(id = 1L, title = "Note 1", content = "Content 1", category = "Personal")
        val note2 = Note(id = 2L, title = "Note 2", content = "Content 2", category = "Work")
        val state = NotesUiState(
            notes = listOf(note1, note2),
            selectedCategory = "Work"
        )

        val filtered = state.filteredNotes
        assertEquals(1, filtered.size)
        assertEquals("Note 2", filtered.first().title)
    }
}
