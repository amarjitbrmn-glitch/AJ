package com.example.nes.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY title ASC")
    fun getAllGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE playCount > 0 ORDER BY lastPlayedTime DESC")
    fun getRecentGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE category = :category ORDER BY title ASC")
    fun getGamesByCategory(category: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id LIMIT 1")
    suspend fun getGameById(id: String): GameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: GameEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGames(games: List<GameEntity>)

    @Update
    suspend fun updateGame(game: GameEntity)

    @Delete
    suspend fun deleteGame(game: GameEntity)

    @Query("SELECT * FROM save_states WHERE gameId = :gameId ORDER BY slot ASC")
    fun getSaveStatesForGame(gameId: String): Flow<List<SaveStateEntity>>

    @Query("SELECT * FROM save_states WHERE gameId = :gameId AND slot = :slot LIMIT 1")
    suspend fun getSaveState(gameId: String, slot: Int): SaveStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaveState(saveState: SaveStateEntity)

    @Query("DELETE FROM save_states WHERE gameId = :gameId AND slot = :slot")
    suspend fun deleteSaveState(gameId: String, slot: Int)
}
