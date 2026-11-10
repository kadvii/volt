package com.kadvii.vault.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kadvii.vault.domain.model.VaultFile
import com.kadvii.vault.domain.model.VaultFileType

@Entity(
    tableName = "vault_files",
    indices = [
        Index(value = ["is_decoy"]),
        Index(value = ["album_id"]),
        Index(value = ["uuid"], unique = true)
    ]
)
data class VaultFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "uuid")
    val uuid: String,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "encrypted_path")
    val encryptedPath: String,
    @ColumnInfo(name = "file_type")
    val fileType: String, // PHOTO, VIDEO, DOCUMENT
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String? = null,
    @ColumnInfo(name = "album_id")
    val albumId: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_decoy")
    val isDecoy: Boolean = false
) {
    fun toDomain(): VaultFile {
        val parsedType = try {
            VaultFileType.valueOf(fileType)
        } catch (_: Exception) {
            VaultFileType.DOCUMENT
        }
        return VaultFile(
            id = id,
            uuid = uuid,
            fileName = fileName,
            encryptedPath = encryptedPath,
            fileType = parsedType,
            fileSizeBytes = fileSizeBytes,
            mimeType = mimeType,
            thumbnailPath = thumbnailPath,
            albumId = albumId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isFavorite = isFavorite,
            isDecoy = isDecoy
        )
    }

    companion object {
        fun fromDomain(file: VaultFile): VaultFileEntity {
            return VaultFileEntity(
                id = file.id,
                uuid = file.uuid,
                fileName = file.fileName,
                encryptedPath = file.encryptedPath,
                fileType = file.fileType.name,
                fileSizeBytes = file.fileSizeBytes,
                mimeType = file.mimeType,
                thumbnailPath = file.thumbnailPath,
                albumId = file.albumId,
                createdAt = file.createdAt,
                updatedAt = file.updatedAt,
                isFavorite = file.isFavorite,
                isDecoy = file.isDecoy
            )
        }
    }
}

