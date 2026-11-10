package com.kadvii.vault.data.repository

import com.kadvii.vault.data.database.NoteDao
import com.kadvii.vault.data.database.NoteEntity
import com.kadvii.vault.domain.model.Note
import com.kadvii.vault.domain.repository.NotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NotesRepositoryImpl(
    private val noteDao: NoteDao
) : NotesRepository {

    override fun getNotes(): Flow<List<Note>> {
        return noteDao.getAllNotes().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getNoteById(id: Long): Note? {
        return noteDao.getNoteById(id)?.toDomain()
    }

    override fun searchNotes(query: String): Flow<List<Note>> {
        return noteDao.searchNotes(query).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getNotesByCategory(category: String): Flow<List<Note>> {
        return noteDao.getNotesByCategory(category).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getCategories(): Flow<List<String>> {
        return noteDao.getAllCategories()
    }

    override suspend fun saveNote(note: Note): Long {
        val entity = NoteEntity.fromDomain(note).copy(
            updatedAt = System.currentTimeMillis()
        )
        return noteDao.insertNote(entity)
    }

    override suspend fun deleteNote(id: Long) {
        noteDao.deleteNoteById(id)
    }
}
