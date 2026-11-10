package com.kadvii.vault.domain.repository

import com.kadvii.vault.domain.model.Note
import kotlinx.coroutines.flow.Flow

interface NotesRepository {
    fun getNotes(): Flow<List<Note>>
    suspend fun getNoteById(id: Long): Note?
    fun searchNotes(query: String): Flow<List<Note>>
    fun getNotesByCategory(category: String): Flow<List<Note>>
    fun getCategories(): Flow<List<String>>
    suspend fun saveNote(note: Note): Long
    suspend fun deleteNote(id: Long)
}
