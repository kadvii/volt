package com.kadvii.vault.domain.model

enum class VaultFileType {
    PHOTO,
    VIDEO,
    DOCUMENT;

    companion object {
        fun fromMimeType(mimeType: String): VaultFileType {
            return when {
                mimeType.startsWith("image/") -> PHOTO
                mimeType.startsWith("video/") -> VIDEO
                else -> DOCUMENT
            }
        }
    }
}

data class VaultFile(
    val id: Long = 0L,
    val uuid: String,
    val fileName: String,
    val encryptedPath: String,
    val fileType: VaultFileType,
    val fileSizeBytes: Long,
    val mimeType: String,
    val thumbnailPath: String? = null,
    val albumId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isDecoy: Boolean = false
)

