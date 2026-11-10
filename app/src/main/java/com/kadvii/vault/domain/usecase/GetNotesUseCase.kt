package com.kadvii.vault.domain.usecase

import com.kadvii.vault.domain.model.Note
import com.kadvii.vault.domain.repository.NotesRepository
import kotlinx.coroutines.flow.Flow

class GetNotesUseCase(
    private val notesRepository: NotesRepository
) {
    operator fun invoke(): Flow<List<Note>> {
        return notesRepository.getNotes()
    }
}

