package com.example.nes.ui

import android.view.KeyEvent
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.nes.data.GameEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class TvHomeSection {
    TOP_BAR,       // Category tabs + Load ROM + Settings
    HERO_ACTIONS,  // Play Game & Toggle Favorite buttons in Hero Showcase
    GAME_GRID      // Main game library grid / carousel
}

object ActiveTvFocusManager {
    var instance: TvFocusManager? = null
}

/**
 * Custom Focus Manager for Jetpack Compose that enables rock-solid
 * TV remote D-pad navigation for the main game library grid and all TV screens.
 */
class TvFocusManager(
    val coroutineScope: CoroutineScope,
    val listState: LazyListState
) {
    var currentSection by mutableStateOf(TvHomeSection.GAME_GRID)
    var selectedGameIndex by mutableIntStateOf(0)
    var selectedTabIndex by mutableIntStateOf(0)
    var selectedHeroActionIndex by mutableIntStateOf(1) // 0: Favorite, 1: Play Game
    var selectedTopButton by mutableIntStateOf(0) // 0..tabs.size-1: Tab, 100: Load ROM, 101: Settings

    var isDialogOpen by mutableStateOf(false)
    var onDialogKeyEvent: ((KeyEvent) -> Boolean)? = null

    var gamesList: List<GameEntity> = emptyList()
    var tabsList: List<String> = emptyList()

    var onLaunchGame: ((GameEntity) -> Unit)? = null
    var onToggleFavorite: (() -> Unit)? = null
    var onOpenSettings: (() -> Unit)? = null
    var onOpenRomPicker: (() -> Unit)? = null
    var onSelectTab: ((Int) -> Unit)? = null
    var onGameFocused: ((GameEntity) -> Unit)? = null

    fun syncGames(games: List<GameEntity>) {
        gamesList = games
        if (games.isNotEmpty()) {
            if (selectedGameIndex >= games.size) {
                selectedGameIndex = games.size - 1
            }
            onGameFocused?.invoke(games[selectedGameIndex])
        }
    }

    fun syncTabs(tabs: List<String>) {
        tabsList = tabs
    }

    /**
     * Intercepts and processes TV remote D-Pad KeyEvents.
     * Returns true if the key was consumed.
     */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (isDialogOpen && onDialogKeyEvent != null) {
            return onDialogKeyEvent?.invoke(event) ?: false
        }

        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }

        val keyCode = event.keyCode

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_J -> {
                onDpadLeft()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_L -> {
                onDpadRight()
                true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_I -> {
                onDpadUp()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_K -> {
                onDpadDown()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_SPACE -> {
                onDpadCenter()
                true
            }
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_BUTTON_B -> {
                onBack()
            }
            else -> false
        }
    }

    fun onDpadLeft() {
        when (currentSection) {
            TvHomeSection.GAME_GRID -> {
                if (gamesList.isNotEmpty()) {
                    if (selectedGameIndex > 0) {
                        selectedGameIndex--
                    } else {
                        selectedGameIndex = gamesList.size - 1 // Wrap around to end
                    }
                    scrollToSelectedGame()
                    onGameFocused?.invoke(gamesList[selectedGameIndex])
                }
            }
            TvHomeSection.HERO_ACTIONS -> {
                // Toggle between Play (1) and Favorite (0)
                selectedHeroActionIndex = 0
            }
            TvHomeSection.TOP_BAR -> {
                if (selectedTopButton == 101) {
                    selectedTopButton = 100 // From Settings to Load ROM
                } else if (selectedTopButton == 100) {
                    selectedTopButton = (tabsList.size - 1).coerceAtLeast(0)
                } else if (selectedTopButton > 0) {
                    selectedTopButton--
                    selectedTabIndex = selectedTopButton
                    onSelectTab?.invoke(selectedTabIndex)
                }
            }
        }
    }

    fun onDpadRight() {
        when (currentSection) {
            TvHomeSection.GAME_GRID -> {
                if (gamesList.isNotEmpty()) {
                    if (selectedGameIndex < gamesList.size - 1) {
                        selectedGameIndex++
                    } else {
                        selectedGameIndex = 0 // Wrap around to start
                    }
                    scrollToSelectedGame()
                    onGameFocused?.invoke(gamesList[selectedGameIndex])
                }
            }
            TvHomeSection.HERO_ACTIONS -> {
                // Toggle to Play (1)
                selectedHeroActionIndex = 1
            }
            TvHomeSection.TOP_BAR -> {
                if (selectedTopButton < tabsList.size - 1) {
                    selectedTopButton++
                    selectedTabIndex = selectedTopButton
                    onSelectTab?.invoke(selectedTabIndex)
                } else if (selectedTopButton == tabsList.size - 1) {
                    selectedTopButton = 100 // Move to Load ROM button
                } else if (selectedTopButton == 100) {
                    selectedTopButton = 101 // Move to Settings button
                }
            }
        }
    }

    fun onDpadUp() {
        when (currentSection) {
            TvHomeSection.GAME_GRID -> {
                // Move focus up to Hero Actions (defaulting to the Play Button)
                currentSection = TvHomeSection.HERO_ACTIONS
                selectedHeroActionIndex = 1
            }
            TvHomeSection.HERO_ACTIONS -> {
                // Move focus up to the Top Bar
                currentSection = TvHomeSection.TOP_BAR
                selectedTopButton = selectedTabIndex
            }
            TvHomeSection.TOP_BAR -> {
                // Already at the very top, keep focus
            }
        }
    }

    fun onDpadDown() {
        when (currentSection) {
            TvHomeSection.TOP_BAR -> {
                // Move focus down to Hero Actions
                currentSection = TvHomeSection.HERO_ACTIONS
                selectedHeroActionIndex = 1
            }
            TvHomeSection.HERO_ACTIONS -> {
                // Move focus down into the Game Library Grid!
                currentSection = TvHomeSection.GAME_GRID
                scrollToSelectedGame()
            }
            TvHomeSection.GAME_GRID -> {
                // Already in main game grid
            }
        }
    }

    fun onDpadCenter() {
        when (currentSection) {
            TvHomeSection.GAME_GRID -> {
                if (gamesList.isNotEmpty() && selectedGameIndex in gamesList.indices) {
                    onLaunchGame?.invoke(gamesList[selectedGameIndex])
                }
            }
            TvHomeSection.HERO_ACTIONS -> {
                if (selectedHeroActionIndex == 1) {
                    if (gamesList.isNotEmpty() && selectedGameIndex in gamesList.indices) {
                        onLaunchGame?.invoke(gamesList[selectedGameIndex])
                    }
                } else {
                    onToggleFavorite?.invoke()
                }
            }
            TvHomeSection.TOP_BAR -> {
                when (selectedTopButton) {
                    100 -> onOpenRomPicker?.invoke()
                    101 -> onOpenSettings?.invoke()
                    else -> {
                        selectedTabIndex = selectedTopButton
                        onSelectTab?.invoke(selectedTabIndex)
                    }
                }
            }
        }
    }

    fun onBack(): Boolean {
        return if (currentSection != TvHomeSection.GAME_GRID) {
            currentSection = TvHomeSection.GAME_GRID
            true
        } else {
            false
        }
    }

    private fun scrollToSelectedGame() {
        coroutineScope.launch {
            try {
                // Scroll with an offset so the card is prominently centered
                val targetIndex = (selectedGameIndex - 1).coerceAtLeast(0)
                listState.animateScrollToItem(targetIndex)
            } catch (_: Exception) {}
        }
    }
}
