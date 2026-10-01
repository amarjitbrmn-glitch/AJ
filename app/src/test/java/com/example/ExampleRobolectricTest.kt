package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.nes.data.AppDatabase
import com.example.nes.data.GameRepository
import com.example.nes.emulator.Apu2A03
import com.example.nes.emulator.RetroGameEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("NES TV", appName)
    }

    @Test
    fun `test in memory room database and repository`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = GameRepository(db.gameDao())

        // Test pre-population
        repository.prepopulateDefaultGames()
        val games = repository.allGames.first()
        assertTrue(games.isNotEmpty())

        // Check specific game
        val superBros = repository.getGameById("retro_super_bros")
        assertNotNull(superBros)
        assertEquals("Super Retro Bros", superBros?.title)

        // Test toggle favorite
        repository.toggleFavorite(superBros!!)
        val updated = repository.getGameById("retro_super_bros")
        assertTrue(updated?.isFavorite == true)

        db.close()
    }

    @Test
    fun `test save state serialization with retro game engine`() {
        val apu = Apu2A03()
        val engine = RetroGameEngine(apu)
        engine.loadGame("retro_super_bros")
        engine.score = 1500
        engine.lives = 2

        val serialized = engine.serializeState()
        assertNotNull(serialized)
        assertTrue(serialized.contains("retro_super_bros"))
        assertTrue(serialized.contains("1500"))

        val engine2 = RetroGameEngine(apu)
        engine2.deserializeState(serialized)
        assertEquals("retro_super_bros", engine2.currentGameId)
        assertEquals(1500, engine2.score)
        assertEquals(2, engine2.lives)
    }
}
