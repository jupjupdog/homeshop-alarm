package com.homeshop.alarm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Orange = Color(0xFFFF6B2C)
private val OnAirRed = Color(0xFFE53935)

private val Light = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    secondary = Color(0xFF3D5AFE),
    error = OnAirRed,
)
private val Dark = darkColorScheme(
    primary = Color(0xFFFF8A57),
    secondary = Color(0xFF8C9EFF),
    error = Color(0xFFFF6E6E),
)

@Composable
fun HomeshopTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
