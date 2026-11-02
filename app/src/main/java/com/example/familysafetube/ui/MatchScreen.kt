package com.example.familysafetube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familysafetube.AudioStep
import com.example.familysafetube.MatchBoard
import com.example.familysafetube.MatchBuilder
import com.example.familysafetube.MatchEvent
import com.example.familysafetube.MatchKind
import com.example.familysafetube.MatchPair
import com.example.familysafetube.MatchSide
import com.example.familysafetube.MatchState
import com.example.familysafetube.Phonics
import com.example.familysafetube.PraiseEvent
import com.example.familysafetube.PraisePicker
import com.example.familysafetube.QuizAudio
import com.example.familysafetube.QuizAudioPlan
import com.example.familysafetube.QuizCard
import com.example.familysafetube.QuizRepository
import com.example.familysafetube.R
import com.example.familysafetube.StickerAlbum
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

private const val MATCH_IMAGE_PX = 1024

/** Height taken by the top bar and spacing (everything that is not the tiles). */
private const val MATCH_CHROME_DP = 110f

/**
 * "Tap two that go together": a letter and the thing that starts with it, a digit and that many stars, or
 * an animal's sound and its picture. Tap one tile, then its partner. A match cheers and says both names; a
 * miss just clears the choice (nothing is lost, nobody is scolded). Finishing a board can win a sticker.
 */
@Composable
fun MatchScreen(cards: List<QuizCard>, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = remember { QuizRepository.language() }
    val level = remember { QuizRepository.level() }
    val audio = remember { QuizAudio(ctx) }
    DisposableEffect(Unit) { onDispose { audio.release() } }

    val praise = remember { QuizRepository.praise() }
    val lastClip = remember { HashMap<PraiseEvent, String>() }
    fun praiseClip(event: PraiseEvent): String? =
        PraisePicker.pick(praise.clipsFor(event), lastClip[event])?.also { lastClip[event] = it }

    val objects = remember(cards) { cards.filter { Phonics.isObject(it) } }
    val letters = remember { QuizRepository.letterCards() }
    val numberTiles = remember { QuizRepository.numberTiles() }
    val countTiles = remember { QuizRepository.countTiles() }
    val kinds = remember { MatchBuilder.possibleKinds(objects, letters, lang) }
    val eligibleStickers = remember(cards) { cards.filter { it.enabled }.map { it.id }.toSet() }

    var state by remember { mutableStateOf<MatchState?>(null) }
    var flash by remember { mutableStateOf(setOf<String>()) }          // tiles shown red for a moment ("LEFT:id")
    var busy by remember { mutableStateOf(false) }                      // celebrating a match: taps are ignored
    var roundOver by remember { mutableStateOf(false) }
    var newSticker by remember { mutableStateOf<QuizCard?>(null) }
    var showAlbum by remember { mutableStateOf(false) }

    fun newBoard() {
        flash = emptySet(); busy = false; roundOver = false; newSticker = null
        state = null
        for (kind in kinds.shuffled()) {
            val board = MatchBuilder.build(kind, objects, letters, numberTiles, countTiles, lang, MatchBuilder.pairCount(level))
            if (board != null) { state = MatchState(board); break }
        }
    }
    LaunchedEffect(Unit) { newBoard() }

    fun nameStep(card: QuizCard) = QuizAudioPlan.nameStep(card, lang)

    /** What is said / played when a tile is tapped. */
    fun tileSteps(board: MatchBoard, side: MatchSide, card: QuizCard): List<AudioStep> =
        if (board.kind == MatchKind.SOUND_PICTURE && side == MatchSide.LEFT) listOfNotNull(QuizAudioPlan.soundStep(card))
        else listOf(nameStep(card))

    /** What is said when a pair is matched ("ميم، موزة"). */
    fun revealSteps(kind: MatchKind, pair: MatchPair): List<AudioStep> = when (kind) {
        MatchKind.LETTER_OBJECT -> listOf(nameStep(pair.left), nameStep(pair.right))
        MatchKind.NUMBER_COUNT -> listOf(nameStep(pair.left))
        MatchKind.SOUND_PICTURE -> listOfNotNull(nameStep(pair.right), QuizAudioPlan.soundStep(pair.right))
    }

    fun finishBoard(done: MatchState) {
        val awarded = StickerAlbum.award(done.board.pairs.map { it.right.id }, QuizRepository.stickers(), eligibleStickers)
        newSticker = awarded?.let { id -> cards.firstOrNull { it.id == id } }
        if (awarded != null) QuizRepository.addSticker(awarded)
        roundOver = true
        val clip = praiseClip(PraiseEvent.ROUND_DONE)
        if (clip != null) {
            audio.play(listOf(AudioStep.RecordedFile(clip)))
        } else {
            audio.cheer()
            scope.launch {
                delay(900)
                audio.play(listOf(AudioStep.Tts(spoken(ctx, lang, R.string.quiz_well_done), lang)))
            }
        }
    }

    fun onTap(side: MatchSide, pairId: String) {
        val before = state ?: return
        if (roundOver || busy) return
        val (after, event) = before.tap(side, pairId)
        state = after
        val pair = before.board.pair(pairId)
        val tapped = if (side == MatchSide.LEFT) pair.left else pair.right
        when (event) {
            MatchEvent.IGNORED -> Unit
            MatchEvent.SELECTED -> audio.play(tileSteps(before.board, side, tapped))
            MatchEvent.WRONG -> {
                val first = before.selected
                flash = setOfNotNull(first?.let { "${it.first.name}:${it.second}" }, "${side.name}:$pairId")
                val clip = praiseClip(PraiseEvent.TRY_AGAIN)
                if (clip != null) audio.play(listOf(AudioStep.RecordedFile(clip))) else audio.oops()
                scope.launch { delay(700); flash = emptySet() }
            }
            MatchEvent.MATCHED, MatchEvent.DONE -> {
                busy = true
                val reveal = {
                    audio.play(revealSteps(before.board.kind, pair)) {
                        scope.launch {
                            delay(500)
                            busy = false
                            if (event == MatchEvent.DONE) finishBoard(after)
                        }
                    }
                }
                val clip = praiseClip(PraiseEvent.CORRECT)
                if (clip != null) {
                    audio.play(listOf(AudioStep.RecordedFile(clip))) { scope.launch { delay(250); reveal() } }
                } else {
                    audio.cheer()
                    scope.launch { delay(1_000); reveal() }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            // Read the available space here: inside the Column/Row below these members are out of scope.
            val availableWidthDp = maxWidth.value
            val availableHeightDp = maxHeight.value
            val current = state
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onExit) { Text(stringResource(R.string.quiz_back)) }
                    val albumLabel = stringResource(R.string.album_button)
                    TextButton(onClick = { showAlbum = true }, modifier = Modifier.semantics { contentDescription = albumLabel }) {
                        Text("🏆", fontSize = 24.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    if (current != null) {
                        Row(Modifier.clearAndSetSemantics { }) {
                            repeat(current.board.pairs.size) { i -> Text(if (i < current.solved.size) "⭐" else "☆", fontSize = 22.sp) }
                        }
                    }
                }

                if (current != null) {
                    val n = current.board.pairs.size
                    val gap = 12f
                    val side = min((availableWidthDp - gap) / 2, (availableHeightDp - MATCH_CHROME_DP - gap * (n - 1)) / n).coerceIn(96f, 300f)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap.dp, Alignment.CenterHorizontally)
                    ) {
                        for (columnSide in MatchSide.values()) {
                            val order = if (columnSide == MatchSide.LEFT) current.board.leftOrder else current.board.rightOrder
                            Column(verticalArrangement = Arrangement.spacedBy(gap.dp)) {
                                order.forEachIndexed { index, id ->
                                    val pair = current.board.pair(id)
                                    val card = if (columnSide == MatchSide.LEFT) pair.left else pair.right
                                    val solved = id in current.solved
                                    val selected = current.selected == (columnSide to id)
                                    val flashing = "${columnSide.name}:$id" in flash
                                    val label = stringResource(R.string.quiz_option, index + 1)
                                    CardArtView(
                                        card = card,
                                        targetPx = MATCH_IMAGE_PX,
                                        modifier = Modifier
                                            .size(side.dp)
                                            .graphicsLayer { alpha = if (solved) 0.3f else 1f }
                                            .clip(MaterialTheme.shapes.large)
                                            .border(
                                                width = when { selected -> 6.dp; solved -> 4.dp; flashing -> 5.dp; else -> 1.dp },
                                                color = when {
                                                    selected -> Brand.AccentBlue
                                                    solved -> Brand.StatusGreen
                                                    flashing -> Brand.StatusRed
                                                    else -> MaterialTheme.colorScheme.outline
                                                },
                                                shape = MaterialTheme.shapes.large
                                            )
                                            .semantics { contentDescription = label }
                                            .clickable(enabled = !solved, role = Role.Button) { onTap(columnSide, id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAlbum) StickerAlbumDialog(cards = cards, lang = lang, onDismiss = { showAlbum = false })

        if (roundOver) {
            RoundComplete(
                newSticker = newSticker,
                onAlbum = { showAlbum = true },
                onAgain = { newBoard() },
                onBye = {
                    audio.play(listOf(AudioStep.Tts(spoken(ctx, lang, R.string.quiz_bye), lang))) { onExit() }
                    scope.launch { delay(3_000); onExit() }
                }
            )
        }
    }
}
