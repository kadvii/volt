package com.kadvii.vault.domain.model

data class Note(
    val id: Long = 0,
    val title: String,
    val content: String,
    val category: String? = null,
    val colorHex: String = "#F7F7F2",
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
