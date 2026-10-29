package com.appman.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Four squares in a 2x2 grid that blink one at a time, clockwise
// (top-left -> top-right -> bottom-right -> bottom-left -> repeat), as
// a lightweight loading indicator for the app list.
@Composable internal fun PixelLoader(
    modifier: Modifier = Modifier,
    squareSize: androidx.compose.ui.unit.Dp = 16.dp,
    gap: androidx.compose.ui.unit.Dp = 5.dp
) {
    val colors = remember {
        listOf(
            Color(0xFF601D49), // top-left
            Color(0xFFBD5579), // top-right
            Color(0xFFEA9D9D), // bottom-right
            Color(0xFFFFEBB8)  // bottom-left
        )
    }

    var active by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(220)
            active = (active + 1) % 4
        }
    }

    val alphas = (0..3).map { i ->
        animateFloatAsState(
            targetValue = if (i == active) 1f else 0.28f,
            animationSpec = tween(180),
            label = "pixel$i"
        ).value
    }

    @Composable
    fun pixel(index: Int) = Box(
        Modifier
            .size(squareSize)
            .background(colors[index].copy(alpha = alphas[index]), RoundedCornerShape(3.dp))
    )

    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) { pixel(0); pixel(1) }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) { pixel(3); pixel(2) }
    }
}
