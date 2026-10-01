package com.example.nes.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import com.example.nes.emulator.SoundFilterType
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nes.data.GameEntity
import com.example.nes.data.GameRepository
import com.example.nes.emulator.Apu2A03
import com.example.nes.emulator.ControllerManager
import com.example.nes.emulator.NesConsole
import com.example.nes.emulator.NesRom
import com.example.nes.emulator.RetroGameEngine
import com.example.ui.theme.NesAccentGold
import com.example.ui.theme.NesDarkBg
import com.example.ui.theme.NesEmerald
import com.example.ui.theme.NesPrimaryCyan
import com.example.ui.theme.NesSecondaryRuby
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicInteger
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun EmulatorScreen(
    game: GameEntity,
    repository: GameRepository,
    onExit: () -> Unit,
    externalButtonsMask: Int = 0,
    externalButtonsMaskP2: Int = 0,
    isExternalMenuPressed: Boolean = false,
    onPauseChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apu = remember { Apu2A03() }
    val engine = remember { RetroGameEngine(apu) }

    var isPaused by remember { mutableStateOf(false) }
    var crtFilterEnabled by remember { mutableStateOf(true) }
    var aspectRatioMode by remember { mutableIntStateOf(0) } // 0: 4:3, 1: 8:7, 2: 16:9
    var speedMultiplier by remember { mutableIntStateOf(1) } // 1, 2, 4
    var showTouchControls by remember { mutableStateOf(false) }
    var soundEnabled by remember { mutableStateOf(true) }
    var apuChannelsEnabled by remember { mutableStateOf(true) }
    var multiplayerEnabled by remember { mutableStateOf(ControllerManager.config.isMultiplayerEnabled) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var activeSlot by remember { mutableIntStateOf(1) }

    // Audio sound filter setting profile (Authentic NES, CRT TV, Crisp, Bass Boost, Famicom RF)
    val prefs = remember { context.getSharedPreferences("nes_settings", Context.MODE_PRIVATE) }
    var soundFilter by remember {
        mutableStateOf(
            SoundFilterType.fromId(prefs.getString("sound_filter", SoundFilterType.AUTHENTIC_NES.id))
        )
    }

    LaunchedEffect(soundFilter) {
        apu.soundFilterType = soundFilter
        prefs.edit().putString("sound_filter", soundFilter.id).apply()
    }

    // Notify activity when pause state changes to allow TV remote D-Pad navigation
    LaunchedEffect(isPaused) {
        onPauseChanged(isPaused)
    }

    // Toggle 8-bit APU channels (Pulse, Triangle, Noise)
    LaunchedEffect(apuChannelsEnabled) {
        apu.chiptuneEnabled = apuChannelsEnabled
    }

    // Update ControllerManager multiplayer setting
    LaunchedEffect(multiplayerEnabled) {
        ControllerManager.config.isMultiplayerEnabled = multiplayerEnabled
    }

    // Combined controller buttons: thread-safe dynamic input reference for Player 1 & Player 2
    var virtualButtons by remember { mutableIntStateOf(0) }
    val currentButtonsRefP1 = remember { AtomicInteger(0) }
    val currentButtonsRefP2 = remember { AtomicInteger(0) }
    var renderFrameTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(externalButtonsMask, virtualButtons) {
        currentButtonsRefP1.set(externalButtonsMask or virtualButtons)
    }
    LaunchedEffect(externalButtonsMaskP2) {
        currentButtonsRefP2.set(externalButtonsMaskP2)
    }

    val bitmap = remember {
        Bitmap.createBitmap(engine.width, engine.height, Bitmap.Config.ARGB_8888)
    }

    // Initialize game and sound (with real .nes ROM loading if custom ROM)
    LaunchedEffect(game.id) {
        if (game.isCustomRom && game.customRomUri != null) {
            try {
                val inputStream = if (game.customRomUri.startsWith("content://")) {
                    context.contentResolver.openInputStream(Uri.parse(game.customRomUri))
                } else {
                    FileInputStream(File(game.customRomUri))
                }
                if (inputStream != null) {
                    val rom = NesRom.parse(inputStream)
                    engine.loadRom(rom)
                    statusMessage = "Loaded ${game.title} (Mapper ${rom.mapperId})"
                } else {
                    engine.loadGame(game.id)
                }
            } catch (e: Exception) {
                engine.loadGame(game.id)
                statusMessage = "ROM loaded with fallback: ${e.message}"
            }
        } else {
            engine.loadGame(game.id)
        }
        apu.start()
        repository.recordPlaySession(game.id)
    }

    // Handle in-game pause from remote menu key
    LaunchedEffect(isExternalMenuPressed) {
        if (isExternalMenuPressed) {
            isPaused = !isPaused
        }
    }

    // Sync audio mute status with settings
    LaunchedEffect(soundEnabled) {
        apu.isMuted = !soundEnabled
    }

    // Intercept hardware Back key -> opens pause menu or exits
    BackHandler {
        if (!isPaused) {
            isPaused = true
        } else {
            isPaused = false
        }
    }

    // High performance 60 FPS Game Loop with zero freezing
    LaunchedEffect(isPaused, speedMultiplier) {
        var lastTime = System.nanoTime()
        val targetFrameNanos = 1_000_000_000L / (60 * speedMultiplier)

        while (isActive) {
            if (!isPaused) {
                val currentBtnsP1 = currentButtonsRefP1.get()
                val currentBtnsP2 = currentButtonsRefP2.get()
                repeat(speedMultiplier) {
                    engine.update(currentBtnsP1, currentBtnsP2)
                }
                bitmap.setPixels(
                    engine.frameBuffer, 0, engine.width, 0, 0, engine.width, engine.height
                )
                renderFrameTick++
            }

            val now = System.nanoTime()
            val elapsed = now - lastTime
            val sleepNanos = targetFrameNanos - elapsed
            if (sleepNanos > 2_000_000L) {
                delay(sleepNanos / 1_000_000L)
            } else {
                delay(2)
            }
            lastTime = System.nanoTime()
        }
    }

    // Cleanup audio when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            apu.stop()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NesDarkBg),
        contentAlignment = Alignment.Center
    ) {
        // Video Screen Aspect Ratio calculation
        val screenRatio = when (aspectRatioMode) {
            0 -> 4f / 3f
            1 -> 8f / 7f
            else -> 16f / 9f
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(screenRatio)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // Rendered NES Frame directly via hardware accelerated Canvas
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                // Reading renderFrameTick guarantees Compose redraws every 60fps frame!
                val tick = renderFrameTick
                drawIntoCanvas { canvas ->
                    val srcRect = Rect(0, 0, engine.width, engine.height)
                    val dstRect = Rect(0, 0, size.width.toInt(), size.height.toInt())
                    val paint = Paint().apply {
                        isFilterBitmap = false // Crisp retro pixel art
                        isDither = false
                    }
                    canvas.nativeCanvas.drawBitmap(bitmap, srcRect, dstRect, paint)
                }
            }

            // CRT Scanline Shader Filter Overlay
            if (crtFilterEnabled) {
                CrtScanlinesOverlay()
            }
        }

        // Virtual Touch Controls Overlay
        if (showTouchControls && !isPaused) {
            VirtualControllerOverlay(
                onButtonPress = { buttonBit, pressed ->
                    virtualButtons = if (pressed) {
                        virtualButtons or buttonBit
                    } else {
                        virtualButtons and buttonBit.inv()
                    }
                },
                onPauseClick = { isPaused = true }
            )
        }

        // Quick Top Status Message / Toast
        statusMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .background(Color(0xCC0F172A), RoundedCornerShape(20.dp))
                    .border(1.5.dp, NesPrimaryCyan, RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = msg,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // IN-GAME PAUSE MENU (TV 10-foot optimized)
        AnimatedVisibility(
            visible = isPaused,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            InGamePauseMenu(
                gameTitle = game.title,
                score = engine.score,
                activeSlot = activeSlot,
                crtFilterEnabled = crtFilterEnabled,
                aspectRatioMode = aspectRatioMode,
                speedMultiplier = speedMultiplier,
                showTouchControls = showTouchControls,
                soundEnabled = soundEnabled,
                apuChannelsEnabled = apuChannelsEnabled,
                onResume = { isPaused = false },
                onReset = {
                    engine.loadGame(game.id)
                    isPaused = false
                    statusMessage = "Game Reset"
                },
                onSelectSlot = { activeSlot = it },
                onSaveState = { slot ->
                    coroutineScope.launch {
                        val state = engine.serializeState()
                        repository.saveState(game.id, slot, state, engine.score)
                        statusMessage = "State Saved to Slot $slot"
                        delay(2000)
                        statusMessage = null
                    }
                },
                onLoadState = { slot ->
                    coroutineScope.launch {
                        val state = repository.getSaveState(game.id, slot)
                        if (state != null) {
                            engine.deserializeState(state.stateJson)
                            statusMessage = "Loaded Slot $slot"
                            isPaused = false
                        } else {
                            statusMessage = "Slot $slot is Empty"
                        }
                        delay(2000)
                        statusMessage = null
                    }
                },
                onToggleCrt = { crtFilterEnabled = !crtFilterEnabled },
                onCycleAspectRatio = { aspectRatioMode = (aspectRatioMode + 1) % 3 },
                onCycleSpeed = {
                    speedMultiplier = when (speedMultiplier) {
                        1 -> 2
                        2 -> 4
                        else -> 1
                    }
                },
                onToggleTouch = { showTouchControls = !showTouchControls },
                onToggleSound = {
                    soundEnabled = !soundEnabled
                    apu.isMuted = !soundEnabled
                },
                onToggleApuChannels = {
                    apuChannelsEnabled = !apuChannelsEnabled
                },
                soundFilter = soundFilter,
                onCycleSoundFilter = {
                    val nextIdx = (soundFilter.ordinal + 1) % SoundFilterType.entries.size
                    soundFilter = SoundFilterType.entries[nextIdx]
                },
                onExitToHub = onExit
            )
        }
    }
}

/**
 * Authentic Retro CRT Scanlines and phosphor shadow overlay
 */
@Composable
private fun CrtScanlinesOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scanlineHeight = 2.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawRect(
                color = Color(0x33000000),
                topLeft = Offset(0f, y),
                size = Size(size.width, scanlineHeight)
            )
            y += scanlineHeight * 2
        }
    }
}

@Composable
private fun InGamePauseMenu(
    gameTitle: String,
    score: Int,
    activeSlot: Int,
    crtFilterEnabled: Boolean,
    aspectRatioMode: Int,
    speedMultiplier: Int,
    showTouchControls: Boolean,
    soundEnabled: Boolean,
    apuChannelsEnabled: Boolean,
    soundFilter: SoundFilterType,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onSelectSlot: (Int) -> Unit,
    onSaveState: (Int) -> Unit,
    onLoadState: (Int) -> Unit,
    onToggleCrt: () -> Unit,
    onCycleAspectRatio: () -> Unit,
    onCycleSpeed: () -> Unit,
    onToggleTouch: () -> Unit,
    onToggleSound: () -> Unit,
    onToggleApuChannels: () -> Unit,
    onCycleSoundFilter: () -> Unit,
    onExitToHub: () -> Unit
) {
    val resumeFocusRequester = remember { FocusRequester() }

    // Automatically focus Resume button when Pause Menu opens so TV D-Pad is active immediately
    LaunchedEffect(Unit) {
        delay(100)
        try {
            resumeFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD0B0F19)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(540.dp)
                .wrapContentHeight()
                .border(2.dp, NesPrimaryCyan, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Header
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PAUSE MENU",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = NesAccentGold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "$gameTitle  •  Score: $score",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                // Slot Selector
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Slot: ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    for (slot in 1..3) {
                        val isSelected = activeSlot == slot
                        Surface(
                            modifier = Modifier
                                .size(28.dp)
                                .tvFocusable(
                                    shape = RoundedCornerShape(6.dp),
                                    focusBorderColor = NesPrimaryCyan,
                                    onClick = { onSelectSlot(slot) }
                                ),
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) NesPrimaryCyan else Color(0xFF1E293B)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$slot",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }

                // Action Buttons Grid (TV Focusable)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PauseMenuButton(
                            label = "Resume",
                            icon = Icons.Default.PlayArrow,
                            height = 36.dp,
                            modifier = Modifier.weight(1f),
                            focusColor = NesPrimaryCyan,
                            focusRequester = resumeFocusRequester,
                            onClick = onResume
                        )
                        PauseMenuButton(
                            label = "Save (Slot $activeSlot)",
                            icon = Icons.Default.Save,
                            height = 36.dp,
                            modifier = Modifier.weight(1f),
                            focusColor = NesAccentGold,
                            onClick = { onSaveState(activeSlot) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PauseMenuButton(
                            label = "Load (Slot $activeSlot)",
                            icon = Icons.Default.Refresh,
                            height = 36.dp,
                            modifier = Modifier.weight(1f),
                            focusColor = NesAccentGold,
                            onClick = { onLoadState(activeSlot) }
                        )
                        PauseMenuButton(
                            label = "Reset Game",
                            icon = Icons.Default.Refresh,
                            height = 36.dp,
                            modifier = Modifier.weight(1f),
                            focusColor = NesSecondaryRuby,
                            onClick = onReset
                        )
                    }

                    // Row 3: Audio APU Synthesizer • 8-Bit Chiptune Sound (Pulse, Triangle & Noise toggle & Sound Filter Setting)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusable(
                                shape = RoundedCornerShape(8.dp),
                                focusBorderColor = NesAccentGold,
                                onClick = onCycleSoundFilter
                            ),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E293B)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = if (apuChannelsEnabled) NesAccentGold else Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Column {
                                    Text(
                                        text = "Audio APU Synthesizer • 8-Bit Chiptune Sound",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${soundFilter.displayName} (${soundFilter.shortName})",
                                        fontSize = 9.sp,
                                        color = NesAccentGold,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.clickable { onCycleSoundFilter() },
                                    shape = RoundedCornerShape(6.dp),
                                    color = NesPrimaryCyan
                                ) {
                                    Text(
                                        text = soundFilter.badgeLabel,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Surface(
                                    modifier = Modifier.clickable { onToggleApuChannels() },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (apuChannelsEnabled) NesEmerald else Color(0xFF334155)
                                ) {
                                    Text(
                                        text = if (apuChannelsEnabled) "CHIPTUNE: ON" else "CHIPTUNE: OFF",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (apuChannelsEnabled) Color.Black else Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Settings Toggles Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PauseMenuButton(
                            label = if (crtFilterEnabled) "CRT: ON" else "CRT: OFF",
                            icon = Icons.Default.Tv,
                            height = 34.dp,
                            modifier = Modifier.weight(1f),
                            onClick = onToggleCrt
                        )
                        PauseMenuButton(
                            label = when (aspectRatioMode) {
                                0 -> "4:3"
                                1 -> "8:7"
                                else -> "16:9"
                            },
                            icon = Icons.Default.Tv,
                            height = 34.dp,
                            modifier = Modifier.weight(1f),
                            onClick = onCycleAspectRatio
                        )
                        PauseMenuButton(
                            label = "${speedMultiplier}x Speed",
                            icon = Icons.Default.FastForward,
                            height = 34.dp,
                            modifier = Modifier.weight(1f),
                            onClick = onCycleSpeed
                        )
                        PauseMenuButton(
                            label = if (soundEnabled) "Audio: ON" else "Audio: OFF",
                            icon = Icons.Default.VolumeUp,
                            height = 34.dp,
                            modifier = Modifier.weight(1f),
                            onClick = onToggleSound
                        )
                    }

                    // Row 5: Touch Pad & Exit to TV Hub (Fully visible simultaneously!)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PauseMenuButton(
                            label = if (showTouchControls) "Touch Pad: ON" else "Touch Pad: OFF",
                            icon = Icons.Default.TouchApp,
                            height = 36.dp,
                            modifier = Modifier.weight(1f),
                            focusColor = NesPrimaryCyan,
                            onClick = onToggleTouch
                        )
                        PauseMenuButton(
                            label = "Exit to TV Hub",
                            icon = Icons.Default.Home,
                            height = 36.dp,
                            modifier = Modifier.weight(1f),
                            focusColor = NesSecondaryRuby,
                            onClick = onExitToHub
                        )
                    }
                }

                // Footer: Controls hint for TV
                Text(
                    text = "TV Remote: [D-Pad] Navigate • [Enter] Select • [Back] Resume / Exit",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

@Composable
private fun PauseMenuButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    focusColor: Color = NesPrimaryCyan,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val reqMod = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    Surface(
        modifier = modifier
            .height(height)
            .then(reqMod)
            .tvFocusable(
                shape = RoundedCornerShape(8.dp),
                focusBorderColor = focusColor,
                onClick = onClick
            ),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1E293B)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = focusColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
