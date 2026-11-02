package com.example.familysafetube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.familysafetube.QuizAudio
import com.example.familysafetube.QuizAudioPlan
import com.example.familysafetube.QuizCard
import com.example.familysafetube.QuizLang
import com.example.familysafetube.QuizRepository
import com.example.familysafetube.R
import com.example.familysafetube.StickerAlbum

/**
 * The sticker album: everything the child has collected, newest first, then "❓" slots for what is still
 * to be found. Tapping a collected sticker says its name (and plays its sound). A visual reward only.
 */
@Composable
fun StickerAlbumDialog(cards: List<QuizCard>, lang: QuizLang, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val enabled = remember(cards) { cards.filter { it.enabled } }
    val collected = remember { QuizRepository.stickers() }
    val order = remember(enabled, collected) { StickerAlbum.albumOrder(enabled, collected) }
    val progress = remember(enabled, collected) { StickerAlbum.progress(collected, enabled.map { it.id }.toSet()) }

    val audio = remember { QuizAudio(ctx) }
    DisposableEffect(Unit) { onDispose { audio.release() } }
    val lockedLabel = stringResource(R.string.album_locked)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(0.96f).fillMaxHeight(0.92f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.album_title, progress.collected, progress.total),
                    fontSize = 22.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(order, key = { it.first.id }) { (card, have) ->
                        val frame = Modifier.aspectRatio(1f).clip(MaterialTheme.shapes.large)
                        if (have) {
                            CardArtView(
                                card,
                                frame.border(3.dp, Brand.StatusAmber, MaterialTheme.shapes.large)
                                    .clickable(role = Role.Button) { audio.play(QuizAudioPlan.reveal(card, lang)) }
                            )
                        } else {
                            Box(
                                frame.background(MaterialTheme.colorScheme.surfaceVariant).semantics { contentDescription = lockedLabel },
                                contentAlignment = Alignment.Center
                            ) { Text("❓", fontSize = 34.sp) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_close)) }
            }
        }
    }
}
