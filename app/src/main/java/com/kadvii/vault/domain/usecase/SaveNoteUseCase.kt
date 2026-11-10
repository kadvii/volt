package com.kadvii.vault.domain.usecase

import com.kadvii.vault.domain.model.Note
import com.kadvii.vault.domain.repository.NotesRepository

class SaveNoteUseCase(
    private val notesRepository: NotesRepository
) {
    suspend operator fun invoke(note: Note): Long {
        return notesRepository.saveNote(note)
    }
}

