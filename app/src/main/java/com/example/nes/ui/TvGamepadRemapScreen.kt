package com.example.nes.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nes.emulator.ControllerManager
import com.example.nes.emulator.NesButtonTarget
import com.example.ui.theme.NesAccentGold
import com.example.ui.theme.NesDarkBg
import com.example.ui.theme.NesEmerald
import com.example.ui.theme.NesPrimaryCyan
import com.example.ui.theme.NesSecondaryRuby
import com.example.ui.theme.NesSurface
import com.example.ui.theme.NesSurfaceVariant

@Composable
fun TvGamepadRemapScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var listeningTarget by remember { mutableStateOf<NesButtonTarget?>(null) }
    var updateTrigger by remember { mutableIntStateOf(0) }
    var lastPressedKeyCode by remember { mutableIntStateOf(0) }
    var statusNotification by remember { mutableStateOf<String?>(null) }

    // Load persisted mapping on startup
    LaunchedEffect(Unit) {
        ControllerManager.loadMapping(context)
        updateTrigger++
    }

    // Connect listener to ControllerManager
    DisposableEffect(listeningTarget) {
        ControllerManager.activeRemapTarget = listeningTarget
        ControllerManager.onRemapCaptured = { target, keyCode ->
            lastPressedKeyCode = keyCode
            ControllerManager.saveMapping(context)
            statusNotification = "Mapped ${target.label} to ${ControllerManager.getKeyName(keyCode)}"
            listeningTarget = null
            updateTrigger++
        }
        onDispose {
            ControllerManager.activeRemapTarget = null
            ControllerManager.onRemapCaptured = null
        }
    }

    BackHandler {
        if (listeningTarget != null) {
            listeningTarget = null
        } else {
            onBack()
        }
    }

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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
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

                    Column {
                        Text(
                            text = "NES GAMEPAD BUTTON REMAPPING",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Configure custom Bluetooth / USB controller buttons specifically for NES emulator inputs",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // Status toast banner
                statusNotification?.let {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NesEmerald
                    ) {
                        Text(
                            text = it,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Presets Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Presets:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                PresetChip(
                    label = "Standard (Xbox / Android)",
                    onClick = {
                        ControllerManager.applyStandardPreset(context)
                        statusNotification = "Applied Standard Xbox / Android Preset"
                        updateTrigger++
                    }
                )

                PresetChip(
                    label = "Nintendo Switch (Swap A & B)",
                    onClick = {
                        ControllerManager.applyNintendoPreset(context)
                        statusNotification = "Applied Nintendo Switch Inverted Layout"
                        updateTrigger++
                    }
                )

                PresetChip(
                    label = "PlayStation (Cross / Square)",
                    onClick = {
                        ControllerManager.applyPlayStationPreset(context)
                        statusNotification = "Applied PlayStation Controller Layout"
                        updateTrigger++
                    }
                )

                PresetChip(
                    label = "Reset Defaults",
                    color = NesSecondaryRuby,
                    onClick = {
                        ControllerManager.applyStandardPreset(context)
                        statusNotification = "Reset to Default Controls"
                        updateTrigger++
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Two-column mapping cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Column 1: Action & Turbo Buttons + System
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RemapGroupCard(title = "Primary Action Buttons", icon = Icons.Default.SportsEsports) {
                        RemapItemRow(
                            target = NesButtonTarget.BUTTON_A,
                            currentKeyCode = ControllerManager.gamepadRemap.btnA,
                            onClick = { listeningTarget = NesButtonTarget.BUTTON_A }
                        )
                        RemapItemRow(
                            target = NesButtonTarget.BUTTON_B,
                            currentKeyCode = ControllerManager.gamepadRemap.btnB,
                            onClick = { listeningTarget = NesButtonTarget.BUTTON_B }
                        )
                        RemapItemRow(
                            target = NesButtonTarget.TURBO_A,
                            currentKeyCode = ControllerManager.gamepadRemap.turboA,
                            onClick = { listeningTarget = NesButtonTarget.TURBO_A }
                        )
                        RemapItemRow(
                            target = NesButtonTarget.TURBO_B,
                            currentKeyCode = ControllerManager.gamepadRemap.turboB,
                            onClick = { listeningTarget = NesButtonTarget.TURBO_B }
                        )
                    }

                    RemapGroupCard(title = "System & Menu Triggers", icon = Icons.Default.Gamepad) {
                        RemapItemRow(
                            target = NesButtonTarget.START,
                            currentKeyCode = ControllerManager.gamepadRemap.start,
                            onClick = { listeningTarget = NesButtonTarget.START }
                        )
                        RemapItemRow(
                            target = NesButtonTarget.SELECT,
                            currentKeyCode = ControllerManager.gamepadRemap.select,
                            onClick = { listeningTarget = NesButtonTarget.SELECT }
                        )
                        RemapItemRow(
                            target = NesButtonTarget.PAUSE_MENU,
                            currentKeyCode = ControllerManager.gamepadRemap.pauseMenu,
                            onClick = { listeningTarget = NesButtonTarget.PAUSE_MENU }
                        )
                    }
                }

                // Column 2: Directional Pad Inputs & Instructions
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RemapGroupCard(title = "NES Directional Pad (D-Pad)", icon = Icons.Default.Gamepad) {
                        RemapItemRow(
                            target = NesButtonTarget.DPAD_UP,
                            currentKeyCode = ControllerManager.gamepadRemap.dpadUp,
                            onClick = { listeningTarget = NesButtonTarget.DPAD_UP }
                        )
                        RemapItemRow(
                            target = NesButtonTarget.DPAD_DOWN,
                            currentKeyCode = ControllerManager.gamepadRemap.dpadDown,
                            onClick = { listeningTarget = NesButtonTarget.DPAD_DOWN }
                        )
                        RemapItemRow(
                            target = NesButtonTarget.DPAD_LEFT,
                            currentKeyCode = ControllerManager.gamepadRemap.dpadLeft,
                            onClick = { listeningTarget = NesButtonTarget.DPAD_LEFT }
                        )
                        RemapItemRow(
                            target = NesButtonTarget.DPAD_RIGHT,
                            currentKeyCode = ControllerManager.gamepadRemap.dpadRight,
                            onClick = { listeningTarget = NesButtonTarget.DPAD_RIGHT }
                        )
                    }

                    // Instructions & Live controller helper card
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
                            Text(
                                text = "💡 How to Remap:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = NesAccentGold
                            )
                            Text(
                                text = "1. Select any NES button above using your TV remote D-Pad or Gamepad.\n" +
                                        "2. When prompted, press the desired physical button on your Gamepad.\n" +
                                        "3. The app instantly saves your custom mapping and applies it to Player 1 & 2 games.",
                                fontSize = 11.sp,
                                color = Color(0xFFCBD5E1),
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF0F172A),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "All changes are automatically saved and ready to play in the emulator.",
                                    fontSize = 10.sp,
                                    color = NesEmerald,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Active listening modal dialog
        if (listeningTarget != null) {
            val target = listeningTarget!!
            AlertDialog(
                onDismissRequest = { listeningTarget = null },
                containerColor = Color(0xFF131B2E),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.SportsEsports, contentDescription = null, tint = NesPrimaryCyan)
                        Text(
                            text = "Remapping: ${target.label}",
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                    }
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = target.description,
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E293B),
                            border = androidx.compose.foundation.BorderStroke(2.dp, NesAccentGold),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "👉 PRESS ANY BUTTON ON YOUR GAMEPAD NOW 👈",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = NesAccentGold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Current Assignment: ${ControllerManager.getKeyName(ControllerManager.gamepadRemap.getKeyCodeForTarget(target))}",
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { listeningTarget = null },
                        colors = ButtonDefaults.buttonColors(containerColor = NesSurfaceVariant)
                    ) {
                        Text("Cancel", color = Color.White)
                    }
                }
            )
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    color: Color = NesSurfaceVariant,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(30.dp)
            .tvFocusable(
                shape = RoundedCornerShape(6.dp),
                focusBorderColor = NesPrimaryCyan,
                onClick = onClick
            ),
        shape = RoundedCornerShape(6.dp),
        color = color
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun RemapGroupCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
            Spacer(modifier = Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun RemapItemRow(
    target: NesButtonTarget,
    currentKeyCode: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .tvFocusable(
                shape = RoundedCornerShape(8.dp),
                focusBorderColor = NesAccentGold,
                onClick = onClick
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
            Column {
                Text(
                    text = target.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = target.description,
                    fontSize = 9.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = NesPrimaryCyan
            ) {
                Text(
                    text = ControllerManager.getKeyName(currentKeyCode),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
