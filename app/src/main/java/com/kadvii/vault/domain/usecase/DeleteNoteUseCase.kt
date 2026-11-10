package com.kadvii.vault.domain.usecase

import com.kadvii.vault.domain.repository.NotesRepository

class DeleteNoteUseCase(
    private val notesRepository: NotesRepository
) {
    suspend operator fun invoke(id: Long) {
        notesRepository.deleteNote(id)
    }
}

