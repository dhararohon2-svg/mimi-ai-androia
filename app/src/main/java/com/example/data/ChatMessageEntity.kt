package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String, // "user" or "mimi"
    val message: String,
    val emotion: String = "neutral", // "happy", "sad", "angry", "shy", "neutral"
    val timestamp: Long = System.currentTimeMillis()
)
