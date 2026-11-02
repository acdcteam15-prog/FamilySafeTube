package com.example.familysafetube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.familysafetube.CardArt
import com.example.familysafetube.QuizCard

private val TILE_COLORS = listOf(
    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047),
    Color(0xFFF9A825), Color(0xFF8E24AA), Color(0xFF00897B)
)

/**
 * Draws a card's picture: a photo from a file, or (for the built-in letter and number decks) a big
 * letter / digit / group of stars on a coloured tile. The text scales with the tile, so it works at
 * any size from a small list thumbnail to a full-screen answer.
 */
@Composable
fun CardArtView(card: QuizCard, modifier: Modifier, targetPx: Int = 512) {
    when (val art = card.art) {
        is CardArt.Picture -> PathImage(art.path, modifier, targetPx)
        is CardArt.Glyph -> Tile(modifier, art.colorIndex) { side ->
            Text(art.text, color = Color.White, fontSize = (side * 0.62f).sp, fontWeight = FontWeight.Bold)
        }
        is CardArt.Dots -> Tile(modifier, art.colorIndex) { side ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                (1..art.count).chunked(3).forEach { row ->
                    Row { Text("⭐".repeat(row.size), fontSize = (side * 0.2f).sp) }
                }
            }
        }
    }
}

@Composable
private fun Tile(modifier: Modifier, colorIndex: Int, content: @Composable (sideDp: Float) -> Unit) {
    BoxWithConstraints(
        modifier.background(TILE_COLORS[colorIndex.mod(TILE_COLORS.size)]),
        contentAlignment = Alignment.Center
    ) {
        content(minOf(maxWidth.value, maxHeight.value))
    }
}
