package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FileIndexDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFileIndex(entity: FileIndexEntity)

    @Query("DELETE FROM file_index_fts")
    suspend fun clearIndex()

    @Query("SELECT rowid, relativePath, content FROM file_index_fts WHERE file_index_fts MATCH :query LIMIT 50")
    suspend fun searchIndex(query: String): List<FileIndexEntity>

    @Query("SELECT COUNT(*) FROM file_index_fts")
    suspend fun getIndexedCount(): Int
}
