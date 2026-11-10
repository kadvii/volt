package com.kadvii.vault.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kadvii.vault.domain.model.Album

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_decoy")
    val isDecoy: Boolean = false
) {
    fun toDomain(fileCount: Int = 0): Album {
        return Album(
            id = id,
            name = name,
            createdAt = createdAt,
            isDecoy = isDecoy,
            fileCount = fileCount
        )
    }

    companion object {
        fun fromDomain(album: Album): AlbumEntity {
            return AlbumEntity(
                id = album.id,
                name = album.name,
                createdAt = album.createdAt,
                isDecoy = album.isDecoy
            )
        }
    }
}

