package com.kadvii.vault.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class VaultDatabaseMigrationTest {

    @Test
    fun migration_1_2_hasCorrectStartAndEndVersions() {
        assertEquals(1, VaultDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, VaultDatabase.MIGRATION_1_2.endVersion)
    }

    @Test
    fun migration_1_2_executesExpectedIndexSqlStatements() {
        val executedStatements = mutableListOf<String>()

        val fakeDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL") {
                executedStatements.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        VaultDatabase.MIGRATION_1_2.migrate(fakeDb)

        assertEquals(4, executedStatements.size)
        assertTrue(executedStatements.any { it.contains("index_vault_files_is_decoy") })
        assertTrue(executedStatements.any { it.contains("index_vault_files_album_id") })
        assertTrue(executedStatements.any { it.contains("index_vault_files_uuid") })
        assertTrue(executedStatements.any { it.contains("index_albums_is_decoy") })
    }
}
