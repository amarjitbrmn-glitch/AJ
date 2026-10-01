package com.example.nes.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import com.example.nes.emulator.SoundFilterType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nes.data.GameRepository
import com.example.nes.emulator.ControllerDeviceType
import com.example.nes.emulator.ControllerManager
import com.example.ui.theme.NesAccentGold
import com.example.ui.theme.NesDarkBg
import com.example.ui.theme.NesEmerald
import com.example.ui.theme.NesPrimaryCyan
import com.example.ui.theme.NesSecondaryRuby
import com.example.ui.theme.NesSurface
import com.example.ui.theme.NesSurfaceVariant
import kotlinx.coroutines.launch

@Composable
fun TvSettingsScreen(
    repository: GameRepository,
    onBack: () -> Unit,
    onOpenGamepadRemap: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("nes_settings", Context.MODE_PRIVATE) }
    var soundFilter by remember {
        mutableStateOf(
            SoundFilterType.fromId(prefs.getString("sound_filter", SoundFilterType.AUTHENTIC_NES.id))
        )
    }

    LaunchedEffect(soundFilter) {
        prefs.edit().putString("sound_filter", soundFilter.id).apply()
    }

    var crtScanlinesDefault by remember { mutableStateOf(true) }
    var aspectRatioIndex by remember { mutableIntStateOf(0) } // 0: 4:3, 1: 8:7, 2: 16:9
    var soundEnabled by remember { mutableStateOf(true) }
    var multiplayerEnabled by remember { mutableStateOf(ControllerManager.config.isMultiplayerEnabled) }
    var player1Device by remember { mutableStateOf(ControllerManager.config.player1Type) }
    var player2Device by remember { mutableStateOf(ControllerManager.config.player2Type) }
    var resetNotice by remember { mutableStateOf<String?>(null) }

    val connectedGamepads = remember { ControllerManager.getConnectedGamepadsCount() }

    LaunchedEffect(multiplayerEnabled, player1Device, player2Device) {
        ControllerManager.config.isMultiplayerEnabled = multiplayerEnabled
        ControllerManager.config.player1Type = player1Device
        ControllerManager.config.player2Type = player2Device
    }

    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NesDarkBg)
            .padding(horizontal = TvSafeHorizontalPadding, vertical = TvSafeVerticalPadding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("settings_back_button")
                        .tvFocusable(
                            shape = RoundedCornerShape(10.dp),
                            focusBorderColor = NesPrimaryCyan,
                            onClick = onBack
                        ),
                    shape = RoundedCornerShape(10.dp),
                    color = NesSurfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = "SETTINGS & CONTROLLER CONFIG",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Settings Grid: 2 Columns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // COLUMN 1: Multiplayer Controls & Hardware Assignment
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Multiplayer Controls Card
                    SettingsCard(title = "Multiplayer Player 1 & 2 Controls", icon = Icons.Default.SportsEsports) {
                        SettingsToggleRow(
                            title = "Multiplayer Mode (2 Players)",
                            subtitle = "Enables simultaneous 2-player controls for TV Remote, Gamepads & Keyboards",
                            checked = multiplayerEnabled,
                            onCheckedChange = { multiplayerEnabled = it }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Hardware detection banner
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0F172A)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Gamepad, contentDescription = null, tint = NesEmerald, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Connected: $connectedGamepads Gamepad(s) • Keyboard Active • TV Remote Ready",
                                    fontSize = 11.sp,
                                    color = Color(0xFFE2E8F0),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Player 1 Device Assignment
                        Column {
                            Text("Player 1 Controller Assignment", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Assign primary input hardware for Player 1", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val p1Options = listOf(
                                    ControllerDeviceType.AUTO to "Auto",
                                    ControllerDeviceType.TV_REMOTE to "TV Remote",
                                    ControllerDeviceType.GAMEPAD_1 to "Gamepad 1",
                                    ControllerDeviceType.KEYBOARD_WASD to "Keyboard"
                                )
                                p1Options.forEach { (type, label) ->
                                    val isSel = player1Device == type
                                    Surface(
                                        modifier = Modifier
                                            .height(30.dp)
                                            .tvFocusable(shape = RoundedCornerShape(6.dp), focusBorderColor = NesPrimaryCyan, onClick = { player1Device = type }),
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSel) NesPrimaryCyan else NesSurfaceVariant
                                    ) {
                                        Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                                            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.Black else Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Player 2 Device Assignment
                        Column {
                            Text("Player 2 Controller Assignment", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Assign secondary input hardware for Player 2", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val p2Options = listOf(
                                    ControllerDeviceType.GAMEPAD_2 to "Gamepad 2",
                                    ControllerDeviceType.KEYBOARD_ARROWS to "Keyboard 2",
                                    ControllerDeviceType.TV_REMOTE to "TV Remote",
                                    ControllerDeviceType.GAMEPAD_1 to "Split Pad"
                                )
                                p2Options.forEach { (type, label) ->
                                    val isSel = player2Device == type
                                    Surface(
                                        modifier = Modifier
                                            .height(30.dp)
                                            .tvFocusable(shape = RoundedCornerShape(6.dp), focusBorderColor = NesAccentGold, onClick = { player2Device = type }),
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSel) NesAccentGold else NesSurfaceVariant
                                    ) {
                                        Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                                            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.Black else Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Remap Gamepad Buttons Button specifically for NES controller inputs
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .tvFocusable(
                                    shape = RoundedCornerShape(8.dp),
                                    focusBorderColor = NesAccentGold,
                                    onClick = onOpenGamepadRemap
                                ),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E293B)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.SportsEsports, contentDescription = null, tint = NesAccentGold, modifier = Modifier.size(18.dp))
                                    Column {
                                        Text("Remap Gamepad Buttons", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("Customize controller buttons specifically for NES inputs", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = NesAccentGold
                                ) {
                                    Text("REMAP", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }
                        }
                    }

                    // Video & Display
                    SettingsCard(title = "Display & CRT Video", icon = Icons.Default.Tv) {
                        SettingsToggleRow(
                            title = "CRT Scanlines Shader",
                            subtitle = "Authentic retro TV scanline raster filter",
                            checked = crtScanlinesDefault,
                            onCheckedChange = { crtScanlinesDefault = it }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Column {
                            Text("Default Aspect Ratio", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val ratios = listOf("4:3 (Original)", "8:7 (Pixel)", "16:9 (Wide)")
                                ratios.forEachIndexed { idx, label ->
                                    val isSelected = aspectRatioIndex == idx
                                    Surface(
                                        modifier = Modifier
                                            .height(30.dp)
                                            .tvFocusable(shape = RoundedCornerShape(6.dp), focusBorderColor = NesPrimaryCyan, onClick = { aspectRatioIndex = idx }),
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) NesPrimaryCyan else NesSurfaceVariant
                                    ) {
                                        Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                                            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.Black else Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Audio APU & Sound Filter Setting
                    SettingsCard(title = "Audio APU & Sound Filter", icon = Icons.Default.VolumeUp) {
                        SettingsToggleRow(
                            title = "8-Bit Chiptune Sound",
                            subtitle = "Enable Pulse, Triangle & Noise audio channels",
                            checked = soundEnabled,
                            onCheckedChange = { soundEnabled = it }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Column {
                            Text(
                                text = "Sound Filter Setting",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = soundFilter.description,
                                fontSize = 10.sp,
                                color = NesAccentGold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SoundFilterType.entries.forEach { filter ->
                                    val isSelected = soundFilter == filter
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                            .tvFocusable(
                                                shape = RoundedCornerShape(6.dp),
                                                focusBorderColor = NesAccentGold,
                                                onClick = { soundFilter = filter }
                                            ),
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) NesAccentGold else NesSurfaceVariant
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = filter.shortName,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.Black else Color.White,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Reset Library
                    SettingsCard(title = "Library & Database", icon = Icons.Default.Refresh) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Restore Built-in Games", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Re-populate catalog to original state", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                            Surface(
                                modifier = Modifier
                                    .height(32.dp)
                                    .tvFocusable(shape = RoundedCornerShape(6.dp), focusBorderColor = NesSecondaryRuby, onClick = {
                                        coroutineScope.launch {
                                            repository.prepopulateDefaultGames()
                                            resetNotice = "Games Restored!"
                                        }
                                    }),
                                shape = RoundedCornerShape(6.dp),
                                color = NesSecondaryRuby
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                    Text("Restore", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }

                        resetNotice?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(it, fontSize = 10.sp, color = NesAccentGold, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // COLUMN 2: Controller & Keyboard Mapping Reference
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Keyboard Player 1 & 2 Mapping
                    SettingsCard(title = "Keyboard Controls (Player 1 & 2)", icon = Icons.Default.Keyboard) {
                        Text("Player 1 Keyboard Mapping:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NesPrimaryCyan)
                        KeyMappingRow("W / A / S / D", "NES D-Pad (Up/Left/Down/Right)")
                        KeyMappingRow("K / J", "NES Button A / Button B")
                        KeyMappingRow("I / U", "Turbo A / Turbo B")
                        KeyMappingRow("Space / Tab", "NES Start / Select")

                        Spacer(modifier = Modifier.height(6.dp))

                        Text("Player 2 Keyboard Mapping:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NesAccentGold)
                        KeyMappingRow("Arrow Keys (Up/Down/Left/Right)", "NES D-Pad 2")
                        KeyMappingRow("M / N (or Numpad 3 / 1)", "NES Button A / B")
                        KeyMappingRow("O / P (or Numpad 5 / 4)", "Turbo A / B")
                        KeyMappingRow("Numpad Enter / Right Shift", "NES Start / Select")
                    }

                    // Android TV Remote Controls
                    SettingsCard(title = "Android TV Remote Controls", icon = Icons.Default.Tv) {
                        KeyMappingRow("D-Pad (Up/Down/Left/Right)", "Directional Pad")
                        KeyMappingRow("D-Pad Center / Enter", "NES Button A")
                        KeyMappingRow("Back Button", "NES Button B / Back")
                        KeyMappingRow("Play/Pause / Menu", "NES Start / In-Game Pause Menu")
                    }

                    // Gamepad / Bluetooth Controller Controls
                    SettingsCard(title = "Gamepad / Controller Controls (P1 & P2)", icon = Icons.Default.Gamepad) {
                        KeyMappingRow("D-Pad / Left Stick", "NES D-Pad")
                        KeyMappingRow("Button A / Button B", "NES Button A / Button B")
                        KeyMappingRow("Button X / Button Y", "Turbo A / Turbo B")
                        KeyMappingRow("Start / Select", "NES Start / Select")
                        KeyMappingRow("L1 / R1 / Menu Button", "In-Game Pause Menu")
                        Text(
                            text = "💡 Plug in 2 controllers: Gamepad 1 automatically controls Player 1, and Gamepad 2 controls Player 2!",
                            fontSize = 10.sp,
                            color = NesEmerald
                        )
                    }

                    SettingsCard(title = "About NES TV", icon = Icons.Default.Info) {
                        Text(
                            text = "NES TV Console for Android TV\nFeatures full 60 FPS emulation, 2A03 APU audio synthesis, simultaneous 2-Player multiplayer, CRT scanlines, save states, and .NES ROM loader.",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NesSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = NesPrimaryCyan, modifier = Modifier.size(16.dp))
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(subtitle, fontSize = 10.sp, color = Color(0xFF94A3B8))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = NesPrimaryCyan,
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF334155)
            )
        )
    }
}

@Composable
private fun KeyMappingRow(key: String, action: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(key, fontSize = 10.sp, color = Color(0xFFCBD5E1))
        Text(action, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NesAccentGold)
    }
}
