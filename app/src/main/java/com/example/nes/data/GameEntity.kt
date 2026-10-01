package com.example.nes.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String, // Action, Arcade, Platformer, Puzzle, Space, Custom
    val description: String,
    val releaseYear: Int = 1989,
    val developer: String = "Nintendo / Homebrew",
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedTime: Long = 0L,
    val highScore: Int = 0,
    val isCustomRom: Boolean = false,
    val customRomUri: String? = null,
    val romFileName: String? = null,
    val bannerColor: Long = 0xFF1E293BL,
    val coverIconName: String = "sports_esports"
)
