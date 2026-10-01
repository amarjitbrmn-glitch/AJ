package com.example.nes.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "save_states")
data class SaveStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: String,
    val slot: Int, // 1, 2, 3
    val timestamp: Long = System.currentTimeMillis(),
    val stateJson: String,
    val label: String = "Slot $slot",
    val score: Int = 0
)
