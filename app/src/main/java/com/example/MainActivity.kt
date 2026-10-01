package com.example

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.nes.data.AppDatabase
import com.example.nes.data.GameEntity
import com.example.nes.data.GameRepository
import com.example.nes.emulator.ControllerManager
import com.example.nes.emulator.NesConsole
import com.example.nes.ui.EmulatorScreen
import com.example.nes.ui.ActiveTvFocusManager
import com.example.nes.ui.TvGamepadRemapScreen
import com.example.nes.ui.TvHomeScreen
import com.example.nes.ui.TvSettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NesDarkBg

sealed class AppScreen {
    object Home : AppScreen()
    data class Playing(val game: GameEntity) : AppScreen()
    object Settings : AppScreen()
    object GamepadRemap : AppScreen()
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: GameRepository

    // Hardware buttons state bitmask from Remote / Gamepad / Keyboard for Player 1 & 2
    private var hardwareButtonsMaskP1 = mutableIntStateOf(0)
    private var hardwareButtonsMaskP2 = mutableIntStateOf(0)
    private var isMenuKeyPressed = mutableStateOf(false)
    private var isGamePaused = mutableStateOf(false)
    private var currentScreenState = mutableStateOf<AppScreen>(AppScreen.Home)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hide system bars for 10-foot TV fullscreen experience
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Initialize Database & Repository
        val database = AppDatabase.getDatabase(this)
        repository = GameRepository(database.gameDao())

        setContent {
            MyApplicationTheme {
                // Ensure default games are loaded on first launch
                LaunchedEffect(Unit) {
                    repository.prepopulateDefaultGames()
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NesDarkBg)
                ) {
                    Crossfade(
                        targetState = currentScreenState.value,
                        label = "screenTransition"
                    ) { screen ->
                        when (screen) {
                            is AppScreen.Home -> {
                                TvHomeScreen(
                                    repository = repository,
                                    onLaunchGame = { game ->
                                        currentScreenState.value = AppScreen.Playing(game)
                                    },
                                    onOpenSettings = {
                                        currentScreenState.value = AppScreen.Settings
                                    }
                                )
                            }
                            is AppScreen.Playing -> {
                                EmulatorScreen(
                                    game = screen.game,
                                    repository = repository,
                                    externalButtonsMask = hardwareButtonsMaskP1.intValue,
                                    externalButtonsMaskP2 = hardwareButtonsMaskP2.intValue,
                                    isExternalMenuPressed = isMenuKeyPressed.value,
                                    onPauseChanged = { paused ->
                                        isGamePaused.value = paused
                                    },
                                    onExit = {
                                        isGamePaused.value = false
                                        currentScreenState.value = AppScreen.Home
                                    }
                                )
                            }
                            is AppScreen.Settings -> {
                                TvSettingsScreen(
                                    repository = repository,
                                    onBack = {
                                        currentScreenState.value = AppScreen.Home
                                    },
                                    onOpenGamepadRemap = {
                                        currentScreenState.value = AppScreen.GamepadRemap
                                    }
                                )
                            }
                            is AppScreen.GamepadRemap -> {
                                TvGamepadRemapScreen(
                                    onBack = {
                                        currentScreenState.value = AppScreen.Settings
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // When on Home screen, route TV Remote / Gamepad D-pad directly to ActiveTvFocusManager
        if (currentScreenState.value is AppScreen.Home) {
            val handled = ActiveTvFocusManager.instance?.handleKeyEvent(event) ?: false
            if (handled) return true
        }

        // When actively remapping on GamepadRemap screen, intercept gamepad button presses
        if (currentScreenState.value is AppScreen.GamepadRemap && ControllerManager.activeRemapTarget != null) {
            val handled = ControllerManager.handleKeyEvent(event) {}
            if (handled) return true
        }

        // When in game, intercept TV remote / Gamepad / Keyboard keys and pass into ControllerManager
        if (currentScreenState.value is AppScreen.Playing) {
            val isDown = event.action == KeyEvent.ACTION_DOWN

            // Handle in-game pause triggers
            if (event.keyCode == KeyEvent.KEYCODE_MENU || event.keyCode == KeyEvent.KEYCODE_BUTTON_L1) {
                isMenuKeyPressed.value = isDown
                return true
            }

            // CRITICAL: When the game is paused, DO NOT intercept D-Pad or Enter keys!
            // Delegate directly to Compose's focus system so the TV Remote D-Pad navigates the Pause Menu!
            if (isGamePaused.value) {
                hardwareButtonsMaskP1.intValue = 0
                hardwareButtonsMaskP2.intValue = 0
                ControllerManager.reset()
                return super.dispatchKeyEvent(event)
            }

            val handled = ControllerManager.handleKeyEvent(event) { menuDown ->
                isMenuKeyPressed.value = menuDown
            }

            if (handled) {
                hardwareButtonsMaskP1.intValue = ControllerManager.buttonsP1
                hardwareButtonsMaskP2.intValue = ControllerManager.buttonsP2
                return true
            }
        }

        return super.dispatchKeyEvent(event)
    }
}
