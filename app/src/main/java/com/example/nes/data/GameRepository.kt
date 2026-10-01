package com.example.nes.data

import kotlinx.coroutines.flow.Flow

class GameRepository(private val gameDao: GameDao) {

    val allGames: Flow<List<GameEntity>> = gameDao.getAllGames()
    val favoriteGames: Flow<List<GameEntity>> = gameDao.getFavoriteGames()
    val recentGames: Flow<List<GameEntity>> = gameDao.getRecentGames()

    fun getGamesByCategory(category: String): Flow<List<GameEntity>> =
        gameDao.getGamesByCategory(category)

    suspend fun getGameById(id: String): GameEntity? = gameDao.getGameById(id)

    suspend fun insertGame(game: GameEntity) = gameDao.insertGame(game)

    suspend fun toggleFavorite(game: GameEntity) {
        gameDao.updateGame(game.copy(isFavorite = !game.isFavorite))
    }

    suspend fun recordPlaySession(gameId: String, newScore: Int = 0) {
        val game = gameDao.getGameById(gameId) ?: return
        val updated = game.copy(
            playCount = game.playCount + 1,
            lastPlayedTime = System.currentTimeMillis(),
            highScore = maxOf(game.highScore, newScore)
        )
        gameDao.updateGame(updated)
    }

    suspend fun updateHighScore(gameId: String, score: Int) {
        val game = gameDao.getGameById(gameId) ?: return
        if (score > game.highScore) {
            gameDao.updateGame(game.copy(highScore = score))
        }
    }

    fun getSaveStates(gameId: String): Flow<List<SaveStateEntity>> =
        gameDao.getSaveStatesForGame(gameId)

    suspend fun getSaveState(gameId: String, slot: Int): SaveStateEntity? =
        gameDao.getSaveState(gameId, slot)

    suspend fun saveState(gameId: String, slot: Int, stateJson: String, score: Int = 0) {
        val entity = SaveStateEntity(
            gameId = gameId,
            slot = slot,
            timestamp = System.currentTimeMillis(),
            stateJson = stateJson,
            label = "Slot $slot",
            score = score
        )
        gameDao.insertSaveState(entity)
    }

    suspend fun deleteSaveState(gameId: String, slot: Int) =
        gameDao.deleteSaveState(gameId, slot)

    suspend fun prepopulateDefaultGames() {
        val defaultList = listOf(
            GameEntity(
                id = "retro_super_bros",
                title = "Super Retro Bros",
                category = "Platformer",
                description = "Classic 8-bit side-scrolling platformer adventure. Run, jump, collect coins, stomp goombas, and rescue the kingdom across retro worlds.",
                releaseYear = 1985,
                developer = "Retro Dev Team",
                bannerColor = 0xFFDC2626L,
                coverIconName = "sports_esports"
            ),
            GameEntity(
                id = "space_star_defender",
                title = "Star Defender 8-Bit",
                category = "Action",
                description = "Thrilling vertical space arcade shooter. Command your starship, blast waves of alien invaders, collect power-ups, and defeat flagship bosses.",
                releaseYear = 1986,
                developer = "Pixel Stars",
                bannerColor = 0xFF2563EBL,
                coverIconName = "rocket_launch"
            ),
            GameEntity(
                id = "brick_breaker_arcade",
                title = "Brick Breaker 8-Bit",
                category = "Arcade",
                description = "Fast-paced paddle and ball arcade challenge. Smash colorful bricks, catch multi-ball powerups, laser blasters, and clear all challenging levels.",
                releaseYear = 1987,
                developer = "Arcade Legends",
                bannerColor = 0xFFD97706L,
                coverIconName = "grid_view"
            ),
            GameEntity(
                id = "retro_maze_runner",
                title = "Retro Maze Runner",
                category = "Arcade",
                description = "Navigate retro labyrinth corridors, gobble glowing pellets, evade ghost chasers, and munch power cherries in this timeless 8-bit maze runner.",
                releaseYear = 1980,
                developer = "Namco Tribute",
                bannerColor = 0xFFF59E0BL,
                coverIconName = "pest_control"
            ),
            GameEntity(
                id = "cyber_puzzle_2048",
                title = "Cyber Pixel 2048",
                category = "Puzzle",
                description = "Strategic NES sliding number puzzle. Combine matching pixel tiles using the D-Pad to reach the mythical 2048 block on your TV.",
                releaseYear = 1990,
                developer = "Famicom Homebrew",
                bannerColor = 0xFF059669L,
                coverIconName = "extension"
            ),
            GameEntity(
                id = "alter_ego_retro",
                title = "Alter Ego 8-Bit",
                category = "Puzzle",
                description = "Award-winning retro puzzle platformer where you control a hero and their mirrored phantom counterpart simultaneously across intricate chambers.",
                releaseYear = 1988,
                developer = "Retro Soft",
                bannerColor = 0xFF7C3AEDL,
                coverIconName = "psychology"
            )
        )
        gameDao.insertGames(defaultList)
    }
}
