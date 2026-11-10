package com.kadvii.vault.core.navigation

object NavRoutes {
    const val NOTES_HOME = "notes_home"
    const val NOTE_EDITOR = "note_editor/{noteId}"

    fun noteEditorRoute(noteId: Long = 0L): String = "note_editor/$noteId"
}
