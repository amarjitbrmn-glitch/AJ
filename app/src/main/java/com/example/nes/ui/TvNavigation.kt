package com.example.nes.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.NesAccentGold
import com.example.ui.theme.NesPrimaryCyan

/**
 * TV Focusable Modifier:
 * Adds smooth 10-foot scale animation, glowing neon border, and D-Pad center / click listener.
 */
fun Modifier.tvFocusable(
    shape: Shape = RoundedCornerShape(12.dp),
    focusBorderColor: Color = NesPrimaryCyan,
    borderWidth: Dp = 3.dp,
    scaleAmount: Float = 1.06f,
    onClick: () -> Unit = {}
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) scaleAmount else 1.0f,
        animationSpec = tween(durationMillis = 180),
        label = "tvFocusScale"
    )

    this
        .scale(animatedScale)
        .onFocusChanged { isFocused = it.isFocused }
        .focusable()
        .then(
            if (isFocused) {
                Modifier
                    .shadow(elevation = 16.dp, shape = shape, spotColor = focusBorderColor, ambientColor = focusBorderColor)
                    .border(width = borderWidth, color = focusBorderColor, shape = shape)
            } else {
                Modifier
            }
        )
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
        .onKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown &&
                (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
            ) {
                onClick()
                true
            } else {
                false
            }
        }
}

/**
 * Android TV Standard Overscan Safe Padding (5% margins)
 */
val TvSafeHorizontalPadding = 32.dp
val TvSafeVerticalPadding = 20.dp
