package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val commandText: String,
    val source: String, // "voice" or "text"
    val actionType: String,
    val success: Boolean,
    val resultMessage: String,
    val timestamp: Long = System.currentTimeMillis()
)
