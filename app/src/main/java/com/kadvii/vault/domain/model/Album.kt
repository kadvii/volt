package com.kadvii.vault.domain.model

data class Album(
    val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isDecoy: Boolean = false,
    val fileCount: Int = 0
)

