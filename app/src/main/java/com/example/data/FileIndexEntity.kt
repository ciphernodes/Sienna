package com.example.data

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Fts4
@Entity(tableName = "file_index_fts")
data class FileIndexEntity(
    @PrimaryKey val rowid: Int = 0,
    val relativePath: String,
    val content: String
)
