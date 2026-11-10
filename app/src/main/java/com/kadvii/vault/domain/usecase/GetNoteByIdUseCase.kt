package com.kadvii.vault.domain.usecase

import com.kadvii.vault.domain.model.Note
import com.kadvii.vault.domain.repository.NotesRepository

class GetNoteByIdUseCase(
    private val notesRepository: NotesRepository
) {
    suspend operator fun invoke(id: Long): Note? {
        return notesRepository.getNoteById(id)
    }
}

