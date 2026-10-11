package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val triggerPhrase: String,
    val title: String,
    // JSON array of actions, e.g. [{"type":"battery_status"},{"type":"open_app","args":{"app_name":"YouTube"}}]
    val actionsJson: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
