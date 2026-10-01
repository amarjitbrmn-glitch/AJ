package com.example.nes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nes.emulator.NesConsole
import com.example.ui.theme.NesSecondaryRuby

@Composable
fun VirtualControllerOverlay(
    onButtonPress: (Int, Boolean) -> Unit,
    onPauseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Pause Button top right
        IconButton(
            onClick = onPauseClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .testTag("pause_button")
                .background(Color(0x88000000), CircleShape)
                .border(1.dp, Color(0x66FFFFFF), CircleShape)
        ) {
            Icon(Icons.Default.Pause, contentDescription = "Pause Menu", tint = Color.White)
        }

        // Bottom Controls Container
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // LEFT: Virtual D-Pad Cross
            VirtualDpad(
                onDirectionChange = { dir, pressed ->
                    onButtonPress(dir, pressed)
                }
            )

            // CENTER: Select & Start Buttons
            Row(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .background(Color(0x991E293B), RoundedCornerShape(20.dp))
                    .border(1.5.dp, Color(0x66FFFFFF), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PillButton(
                    label = "SELECT",
                    testTag = "btn_select",
                    onPress = { onButtonPress(NesConsole.BUTTON_SELECT, it) }
                )
                PillButton(
                    label = "START",
                    testTag = "btn_start",
                    onPress = { onButtonPress(NesConsole.BUTTON_START, it) }
                )
            }

            // RIGHT: B & A Action Buttons + Turbo
            ActionButtonsGroup(
                onButtonPress = onButtonPress
            )
        }
    }
}

@Composable
private fun VirtualDpad(
    onDirectionChange: (Int, Boolean) -> Unit
) {
    var activeDir by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = Modifier
            .size(140.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val dir = getDirection(offset.x, offset.y, 140f)
                        if (dir != null && dir != activeDir) {
                            activeDir?.let { onDirectionChange(it, false) }
                            activeDir = dir
                            onDirectionChange(dir, true)
                        }
                    },
                    onDrag = { change, _ ->
                        val dir = getDirection(change.position.x, change.position.y, 140f)
                        if (dir != activeDir) {
                            activeDir?.let { onDirectionChange(it, false) }
                            activeDir = dir
                            dir?.let { onDirectionChange(it, true) }
                        }
                    },
                    onDragEnd = {
                        activeDir?.let { onDirectionChange(it, false) }
                        activeDir = null
                    },
                    onDragCancel = {
                        activeDir?.let { onDirectionChange(it, false) }
                        activeDir = null
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        val dir = getDirection(offset.x, offset.y, 140f)
                        if (dir != null) {
                            activeDir = dir
                            onDirectionChange(dir, true)
                            tryAwaitRelease()
                            onDirectionChange(dir, false)
                            activeDir = null
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Horizontal Bar
        Box(
            modifier = Modifier
                .width(130.dp)
                .height(44.dp)
                .background(Color(0xCC1F2937), RoundedCornerShape(8.dp))
                .border(2.dp, Color(0x66FFFFFF), RoundedCornerShape(8.dp))
        )
        // Vertical Bar
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(130.dp)
                .background(Color(0xCC1F2937), RoundedCornerShape(8.dp))
                .border(2.dp, Color(0x66FFFFFF), RoundedCornerShape(8.dp))
        )
        // Center Pip
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Color(0xFF374151), CircleShape)
        )
    }
}

private fun getDirection(x: Float, y: Float, size: Float): Int? {
    val cx = size / 2f
    val cy = size / 2f
    val dx = x - cx
    val dy = y - cy
    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
    if (dist < 12f) return null // Deadzone

    return if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
        if (dx > 0) NesConsole.BUTTON_RIGHT else NesConsole.BUTTON_LEFT
    } else {
        if (dy > 0) NesConsole.BUTTON_DOWN else NesConsole.BUTTON_UP
    }
}

@Composable
private fun PillButton(
    label: String,
    testTag: String,
    onPress: (Boolean) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(16.dp)
                .rotate(-24f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF4B5563))
                .border(1.dp, Color(0xFF9CA3AF), RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onPress(true)
                            tryAwaitRelease()
                            onPress(false)
                        }
                    )
                }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            color = Color(0xFFCBD5E1),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActionButtonsGroup(
    onButtonPress: (Int, Boolean) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Turbo Row (X / Y)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RoundNesButton(
                label = "TURBO B",
                subLabel = "Y",
                color = Color(0xFFBE123C),
                size = 46,
                testTag = "btn_turbo_b",
                onPress = { onButtonPress(NesConsole.BUTTON_B, it) }
            )
            RoundNesButton(
                label = "TURBO A",
                subLabel = "X",
                color = Color(0xFFBE123C),
                size = 46,
                testTag = "btn_turbo_a",
                onPress = { onButtonPress(NesConsole.BUTTON_A, it) }
            )
        }

        // Primary Buttons (B / A)
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RoundNesButton(
                label = "B",
                subLabel = "",
                color = NesSecondaryRuby,
                size = 58,
                testTag = "btn_b",
                onPress = { onButtonPress(NesConsole.BUTTON_B, it) }
            )
            RoundNesButton(
                label = "A",
                subLabel = "",
                color = NesSecondaryRuby,
                size = 58,
                testTag = "btn_a",
                onPress = { onButtonPress(NesConsole.BUTTON_A, it) }
            )
        }
    }
}

@Composable
private fun RoundNesButton(
    label: String,
    subLabel: String,
    color: Color,
    size: Int,
    testTag: String,
    onPress: (Boolean) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(color)
                .border(2.5.dp, Color(0xFFFCA5A5), CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onPress(true)
                            tryAwaitRelease()
                            onPress(false)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = if (label.length > 2) 9.sp else 18.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }
        if (subLabel.isNotEmpty()) {
            Text(
                text = subLabel,
                fontSize = 9.sp,
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
