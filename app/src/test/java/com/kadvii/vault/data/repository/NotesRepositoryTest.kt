package com.kadvii.vault.data.repository

import com.kadvii.vault.data.database.NoteDao
import com.kadvii.vault.data.database.NoteEntity
import com.kadvii.vault.domain.model.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class NotesRepositoryTest {

    private lateinit var fakeDao: FakeNoteDao
    private lateinit var repository: NotesRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeNoteDao()
        repository = NotesRepositoryImpl(fakeDao)
    }

    @Test
    fun saveNote_persistsAndRetrievesNote() = runBlocking {
        val note = Note(title = "Idea", content = "Build a privacy app", category = "Ideas")
        val savedId = repository.saveNote(note)

        val retrieved = repository.getNoteById(savedId)
        assertNotNull(retrieved)
        assertEquals("Idea", retrieved?.title)
        assertEquals("Build a privacy app", retrieved?.content)
        assertEquals("Ideas", retrieved?.category)
    }

    @Test
    fun deleteNote_removesNoteFromRepository() = runBlocking {
        val note = Note(id = 100L, title = "To Delete", content = "Temporary")
        repository.saveNote(note)

        repository.deleteNote(100L)
        val retrieved = repository.getNoteById(100L)
        assertNull(retrieved)

        val notesList = repository.getNotes().first()
        assertEquals(0, notesList.size)
    }

    @Test
    fun getNotes_sortsPinnedNotesFirst() = runBlocking {
        val regularNote = Note(id = 1L, title = "Regular", content = "Normal note", isPinned = false, updatedAt = 1000L)
        val pinnedNote = Note(id = 2L, title = "Pinned", content = "Important note", isPinned = true, updatedAt = 500L)

        repository.saveNote(regularNote)
        repository.saveNote(pinnedNote)

        val notes = repository.getNotes().first()
        assertEquals(2, notes.size)
        assertEquals("Pinned", notes.first().title)
    }

    @Test
    fun searchNotes_returnsMatchingResults() = runBlocking {
        repository.saveNote(Note(id = 1L, title = "Grocery Shopping", content = "Apples"))
        repository.saveNote(Note(id = 2L, title = "Project Meeting", content = "Architecture"))

        val searchResults = repository.searchNotes("Shopping").first()
        assertEquals(1, searchResults.size)
        assertEquals("Grocery Shopping", searchResults.first().title)
    }

    @Test
    fun getNotesByCategory_returnsFilteredResults() = runBlocking {
        repository.saveNote(Note(id = 1L, title = "Personal Task", content = "Workout", category = "Personal"))
        repository.saveNote(Note(id = 2L, title = "Work Sprint", content = "Review PR", category = "Work"))

        val personalNotes = repository.getNotesByCategory("Personal").first()
        assertEquals(1, personalNotes.size)
        assertEquals("Personal Task", personalNotes.first().title)
    }

    private class FakeNoteDao : NoteDao {
        private val notes = MutableStateFlow<List<NoteEntity>>(emptyList())
        private var nextId = 1L

        override fun getAllNotes(): Flow<List<NoteEntity>> = notes.map { list ->
            list.sortedWith(
                compareByDescending<NoteEntity> { it.isPinned }
                    .thenByDescending { it.updatedAt }
            )
        }

        override suspend fun getNoteById(id: Long): NoteEntity? {
            return notes.value.find { it.id == id }
        }

        override fun searchNotes(query: String): Flow<List<NoteEntity>> = notes.map { list ->
            list.filter {
                it.title.contains(query, ignoreCase = true) || it.content.contains(query, ignoreCase = true)
            }
        }

        override fun getNotesByCategory(category: String): Flow<List<NoteEntity>> = notes.map { list ->
            list.filter { it.category.equals(category, ignoreCase = true) }
        }

        override fun getAllCategories(): Flow<List<String>> = notes.map { list ->
            list.mapNotNull { it.category }.distinct()
        }

        override suspend fun insertNote(note: NoteEntity): Long {
            val id = if (note.id == 0L) nextId++ else note.id
            val updated = note.copy(id = id)
            val current = notes.value.toMutableList()
            val existingIndex = current.indexOfFirst { it.id == id }
            if (existingIndex >= 0) {
                current[existingIndex] = updated
            } else {
                current.add(updated)
            }
            notes.value = current
            return id
        }

        override suspend fun updateNote(note: NoteEntity) {
            insertNote(note)
        }

        override suspend fun deleteNoteById(id: Long) {
            notes.value = notes.value.filterNot { it.id == id }
        }
    }
}
