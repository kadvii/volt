package com.kadvii.vault.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [VaultFileEntity::class, AlbumEntity::class],
    version = 2,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {

    abstract fun vaultFileDao(): VaultFileDao
    abstract fun albumDao(): AlbumDao

    companion object {
        private const val DATABASE_NAME = "vault_storage.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_files_is_decoy ON vault_files(is_decoy)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_files_album_id ON vault_files(album_id)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_vault_files_uuid ON vault_files(uuid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_albums_is_decoy ON albums(is_decoy)")
            }
        }

        @Volatile
        private var INSTANCE: VaultDatabase? = null

        fun getInstance(context: Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
