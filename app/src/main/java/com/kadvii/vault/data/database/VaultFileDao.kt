package com.kadvii.vault.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultFileDao {

    @Query("SELECT * FROM vault_files WHERE is_decoy = :isDecoy ORDER BY created_at DESC")
    fun getAllFiles(isDecoy: Boolean): Flow<List<VaultFileEntity>>

    @Query("SELECT * FROM vault_files WHERE is_decoy = :isDecoy AND file_type = :fileType ORDER BY created_at DESC")
    fun getFilesByType(fileType: String, isDecoy: Boolean): Flow<List<VaultFileEntity>>

    @Query("SELECT * FROM vault_files WHERE is_decoy = :isDecoy AND album_id = :albumId ORDER BY created_at DESC")
    fun getFilesByAlbum(albumId: Long, isDecoy: Boolean): Flow<List<VaultFileEntity>>

    @Query("SELECT * FROM vault_files WHERE is_decoy = :isDecoy AND is_favorite = 1 ORDER BY created_at DESC")
    fun getFavoriteFiles(isDecoy: Boolean): Flow<List<VaultFileEntity>>

    @Query("SELECT * FROM vault_files WHERE id = :id LIMIT 1")
    fun getFileById(id: Long): Flow<VaultFileEntity?>

    @Query("SELECT * FROM vault_files WHERE id = :id LIMIT 1")
    suspend fun getFileByIdSync(id: Long): VaultFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: VaultFileEntity): Long

    @Update
    suspend fun updateFile(file: VaultFileEntity)

    @Query("DELETE FROM vault_files WHERE id = :id")
    suspend fun deleteFileById(id: Long)

    @Query("UPDATE vault_files SET is_favorite = :isFavorite, updated_at = :timestamp WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE vault_files SET album_id = :albumId, updated_at = :timestamp WHERE id = :id")
    suspend fun moveToAlbum(id: Long, albumId: Long?, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM vault_files WHERE album_id = :albumId AND is_decoy = :isDecoy")
    suspend fun countFilesInAlbum(albumId: Long, isDecoy: Boolean): Int
}

