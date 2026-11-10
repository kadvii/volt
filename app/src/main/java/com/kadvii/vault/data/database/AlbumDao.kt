package com.kadvii.vault.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums WHERE is_decoy = :isDecoy ORDER BY name ASC")
    fun getAlbums(isDecoy: Boolean): Flow<List<AlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: AlbumEntity): Long

    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun deleteAlbumById(id: Long)

    @Query("UPDATE albums SET name = :newName WHERE id = :id")
    suspend fun renameAlbum(id: Long, newName: String)
}

