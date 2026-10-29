package com.appman.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.appman.app.R

internal val Ink = Color(0xFF17202A)
internal val Teal = Color(0xFF218DAE)
internal val Cyan = Color(0xFF8ACFF8)
internal val Blue = Color(0xFF3A86FF)
internal val ActionButtonBlue = Color(0xFFAAC4F5)
internal val Cream = Color(0xFFFCE59A)
internal val Yellow = Color(0xFFFFD758)
internal val Sand = Color(0xFFE9DFC3)
internal val White = Color(0xFFFFF8F8)
internal val Black = Color(0xFF000000)

// app/src/main/res/fonts/ndot77jp_extended.ttf — rename the .ttf to this
// (Android font resource names must be lowercase letters/digits/underscores
// only) before building.
//internal val NdotFont = FontFamily(Font(R.font.ndot77jp_extended))

@Composable internal fun AppManTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Blue, secondary = Blue, background = White, surface = White, onBackground = Ink, onSurface = Ink), content = content)
}
