package com.kadvii.vault.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kadvii.vault.domain.model.Note

@Entity(
    tableName = "notes",
    indices = [
        Index("updated_at"),
        Index("is_pinned"),
        Index("category")
    ]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val category: String? = null,
    @ColumnInfo(name = "color_hex")
    val colorHex: String = "#F7F7F2",
    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Note = Note(
        id = id,
        title = title,
        content = content,
        category = category,
        colorHex = colorHex,
        isPinned = isPinned,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(note: Note): NoteEntity = NoteEntity(
            id = note.id,
            title = note.title,
            content = note.content,
            category = note.category,
            colorHex = note.colorHex,
            isPinned = note.isPinned,
            createdAt = note.createdAt,
            updatedAt = note.updatedAt
        )
    }
}

