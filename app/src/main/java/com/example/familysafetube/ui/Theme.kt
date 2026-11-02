package com.example.familysafetube.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Brand colours carried over from the original XML theme (res/values/colors.xml). */
object Brand {
    val BgDeep = Color(0xFF070B14)
    val Card = Color(0xFF0F1628)
    val CardBorder = Color(0xFF1E2D50)
    val Header = Color(0xFF0D1224)
    val AccentBlue = Color(0xFF4F8EF7)
    val TextPrimary = Color(0xFFF0F4FF)
    val TextSecondary = Color(0xFF8892B0)
    val TrackDark = Color(0xFF1A2540)

    val StatusGreen = Color(0xFF00E676)
    val StatusRed = Color(0xFFFF5252)
    val StatusAmber = Color(0xFFFFB300)
    val StatusBlue = Color(0xFF448AFF)

    val YoutubeBrush = Brush.linearGradient(listOf(Color(0xFFE53935), Color(0xFFFF6F00)))
    val OfflineBrush = Brush.linearGradient(listOf(Color(0xFF00897B), Color(0xFF26C6DA)))
    val ImagesBrush = Brush.linearGradient(listOf(Color(0xFF5E35B1), Color(0xFFAB47BC)))
    val MatchBrush = Brush.linearGradient(listOf(Color(0xFF00ACC1), Color(0xFF5C6BC0)))
    val QuizBrush = Brush.linearGradient(listOf(Color(0xFFEC407A), Color(0xFFFFB300)))
    val SyncBrush = Brush.linearGradient(listOf(Color(0xFFF57C00), Color(0xFFFFA726)))
    val UsageBrush = Brush.horizontalGradient(listOf(Color(0xFF4F8EF7), Color(0xFF00E676)))

    /** Colours for the per-category chart segments (keys match Policy.CAT_*). */
    val CategoryColors = mapOf(
        "youtube" to Color(0xFFE53935),
        "offline_videos" to Color(0xFF00897B),
        "images" to Color(0xFF7E57C2)
    )
    val CategoryFallback = Color(0xFF8892B0)
}

private val DarkColors = darkColorScheme(
    primary = Brand.AccentBlue,
    onPrimary = Color.White,
    secondary = Color(0xFF00897B),
    background = Brand.BgDeep,
    onBackground = Brand.TextPrimary,
    surface = Brand.Card,
    onSurface = Brand.TextPrimary,
    surfaceVariant = Color(0xFF141C32),
    onSurfaceVariant = Brand.TextSecondary,
    outline = Brand.CardBorder,
    error = Brand.StatusRed
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6FE0),
    onPrimary = Color.White,
    secondary = Color(0xFF00796B),
    background = Color(0xFFF5F7FC),
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE9EDF7),
    onSurfaceVariant = Color(0xFF4A5578),
    outline = Color(0xFFC7CFE4),
    error = Color(0xFFD32F2F)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

/**
 * The kids' home screen is designed dark (as before), so [darkTheme] defaults to true. A light scheme
 * is included and can be enabled by passing isSystemInDarkTheme() once the player screens get the
 * same treatment (Phase 5).
 */
@Composable
fun FamilySafeTubeTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        content = content
    )
}
