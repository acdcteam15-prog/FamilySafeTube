package com.example.familysafetube.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familysafetube.AppGraph
import com.example.familysafetube.AudioStep
import com.example.familysafetube.CardAspect
import com.example.familysafetube.Phonics
import com.example.familysafetube.PhonicsEngine
import com.example.familysafetube.StickerAlbum
import com.example.familysafetube.CardStats
import com.example.familysafetube.ImageArrangement
import com.example.familysafetube.ImagePlan
import com.example.familysafetube.Keys
import com.example.familysafetube.QuizAudio
import com.example.familysafetube.PraiseEvent
import com.example.familysafetube.Policy
import com.example.familysafetube.PraisePicker
import com.example.familysafetube.QuizAudioPlan
import com.example.familysafetube.QuizCard
import com.example.familysafetube.QuizEngine
import com.example.familysafetube.QuizImageLayout
import com.example.familysafetube.QuizLang
import com.example.familysafetube.QuizMode
import com.example.familysafetube.QuizQuestion
import com.example.familysafetube.QuizRepository
import com.example.familysafetube.QuizRules
import com.example.familysafetube.R
import com.example.familysafetube.RoundTracker
import com.example.familysafetube.getOr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.random.Random

/** Height taken by everything that is not the pictures (top bar, speaker/name row, message, gaps). */
private const val CHROME_HEIGHT_DP = 200f

/** Pictures are shown big now, so decode them sharper than the small thumbnails in the editor. */
private const val QUIZ_IMAGE_PX = 1280

/** Share of questions that are letter <-> word questions, when they are switched on in Settings. */
private const val PHONICS_CHANCE = 0.25

/** Size of the object picture shown above the options in a "which letter?" question. */
private const val STIMULUS_DP = 120f

/** A string in the QUIZ language (not necessarily the phone's), used for spoken phrases like "Well done!". */
internal fun spoken(ctx: Context, lang: QuizLang, resId: Int): String {
    val cfg = Configuration(ctx.resources.configuration)
    cfg.setLocale(Locale.forLanguageTag(lang.localeTag))
    return ctx.createConfigurationContext(cfg).getString(resId)
}

/**
 * "Hear it, then pick it": the child hears the name (and the animal's sound), or only the sound, then
 * taps one of 2-3 pictures. Built for a baby: big targets, no reading needed, no timer, nothing
 * negative (a wrong tap just fades that picture and repeats what was asked). The audio repeats by
 * itself when idle. After 5 right answers a calm "well done" screen offers Again or Bye-bye.
 */
@Composable
fun QuizScreen(cards: List<QuizCard>, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = remember { QuizRepository.language() }
    val level = remember { QuizRepository.level() }
    val soundOnlyChance = remember {
        if (AppGraph.settings.getOr(Keys.QUIZ_SOUND_ONLY, true)) level.soundOnlyChance else 0.0
    }
    val praise = remember { QuizRepository.praise() }                 // the parent's own recorded reactions
    val lastClip = remember { HashMap<PraiseEvent, String>() }        // so the same clip isn't played twice in a row
    fun praiseClip(event: PraiseEvent): String? =
        PraisePicker.pick(praise.clipsFor(event), lastClip[event])?.also { lastClip[event] = it }
    val audio = remember { QuizAudio(ctx) }
    DisposableEffect(Unit) { onDispose { audio.release() } }

    val stats = remember { QuizRepository.stats().toMutableMap() }
    val recent = remember { mutableListOf<String>() }
    val round = remember { RoundTracker() }
    val phonicsOn = remember { AppGraph.settings.getOr(Keys.QUIZ_PHONICS, false) }
    val objectCards = remember(cards) { cards.filter { Phonics.isObject(it) } }
    val letterCards = remember { QuizRepository.letterCards() }
    val eligibleStickers = remember(cards) { cards.filter { it.enabled }.map { it.id }.toSet() }
    val roundCorrect = remember { mutableListOf<String>() }            // what was learned this round, for the sticker
    var newSticker by remember { mutableStateOf<QuizCard?>(null) }
    var showAlbum by remember { mutableStateOf(false) }
    var lastRoundOfDay by remember { mutableStateOf(false) }      // this round used up the parent's daily limit
    var misses by remember { mutableIntStateOf(0) }            // wrong answers in a row (makes questions easier)
    var question by remember { mutableStateOf<QuizQuestion?>(null) }
    var stars by remember { mutableIntStateOf(0) }
    var roundOver by remember { mutableStateOf(false) }
    var wrongIds by remember { mutableStateOf(setOf<String>()) }
    var solvedId by remember { mutableStateOf<String?>(null) }
    var locked by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var interaction by remember { mutableIntStateOf(0) }       // bumps on every tap, restarting the idle timer

    fun nextQuestion() {
        val optionCount = QuizEngine.optionCount(misses, level)
        // Sometimes a letter <-> word question (if switched on and there are enough pictures with a first letter)
        val phonics = if (phonicsOn && Random.nextDouble() < PHONICS_CHANCE)
            PhonicsEngine.pick(objectCards, letterCards, lang, optionCount, recent) else null
        val q = phonics ?: QuizEngine.pick(cards, stats, recent, optionCount, soundOnlyChance = soundOnlyChance)
        question = q
        wrongIds = emptySet()
        solvedId = null
        locked = false
        message = null
        if (q != null) {
            recent.add(q.learnedCard.id)
            while (recent.size > QuizEngine.RECENT_SIZE) recent.removeAt(0)
        }
    }

    LaunchedEffect(Unit) { nextQuestion() }

    // Ask the question shortly after it appears...
    LaunchedEffect(question) {
        val q = question ?: return@LaunchedEffect
        delay(450)
        audio.play(QuizAudioPlan.promptFor(q, lang))
    }
    // ...and repeat it if the child does nothing for a while.
    LaunchedEffect(question, interaction) {
        val q = question ?: return@LaunchedEffect
        while (true) {
            delay(level.idleRepeatMs)
            if (!locked && !roundOver && !showAlbum) audio.play(QuizAudioPlan.promptFor(q, lang))
        }
    }

    fun finishRound() {
        roundOver = true
        stars = QuizRules.SET_SIZE
        AppGraph.usage.recordQuizRound()                              // counts towards the optional daily limit
        lastRoundOfDay = Policy.quizRoundsReached()
        // Visual reward: a sticker of something just got right (never extra screen time)
        val awarded = StickerAlbum.award(roundCorrect.toList(), QuizRepository.stickers(), eligibleStickers)
        roundCorrect.clear()
        newSticker = awarded?.let { id -> cards.firstOrNull { it.id == id } }
        if (awarded != null) QuizRepository.addSticker(awarded)
        val clip = praiseClip(PraiseEvent.ROUND_DONE)
        if (clip != null) {
            audio.play(listOf(AudioStep.RecordedFile(clip)))              // the parent's voice
        } else {
            audio.cheer()
            scope.launch {
                delay(900)
                audio.play(listOf(AudioStep.Tts(spoken(ctx, lang, R.string.quiz_well_done), lang)))
            }
        }
    }

    fun pick(card: QuizCard) {
        val q = question ?: return
        if (locked || roundOver || card.id in wrongIds) return
        interaction++
        val correct = card.id == q.target.id
        stats[q.target.id] = (stats[q.target.id] ?: CardStats()).record(correct)
        QuizRepository.recordAnswer(q.target.id, correct)

        if (correct) {
            misses = 0
            locked = true
            solvedId = card.id
            roundCorrect.add(q.learnedCard.id)
            val roundDone = round.correctAnswer()
            stars = round.correctInRound
            message = ctx.getString(R.string.quiz_correct)
            val reveal = {
                // now the name is revealed (sound-only questions too)
                audio.play(QuizAudioPlan.revealFor(q, lang)) {
                    scope.launch {
                        delay(700)
                        if (roundDone) finishRound() else nextQuestion()
                    }
                }
            }
            val clip = praiseClip(PraiseEvent.CORRECT)
            if (clip != null) {
                audio.play(listOf(AudioStep.RecordedFile(clip))) { scope.launch { delay(250); reveal() } }
            } else {
                audio.cheer()
                scope.launch { delay(1_100); reveal() }                  // let the cheer finish first
            }
        } else {
            misses++
            wrongIds = wrongIds + card.id
            message = ctx.getString(R.string.quiz_try_again)
            val hint = { audio.play(QuizAudioPlan.hintFor(q, lang)) }   // gently repeat what was asked
            val clip = praiseClip(PraiseEvent.TRY_AGAIN)
            if (clip != null) {
                audio.play(listOf(AudioStep.RecordedFile(clip))) { scope.launch { delay(250); hint() } }
            } else {
                audio.oops()
                scope.launch { delay(700); hint() }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // BoxWithConstraints gives the real free space (after system bars and padding), so the
        // pictures can be made as big as that space allows.
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            val q = question
            val chromeDp = CHROME_HEIGHT_DP + (if (q?.stimulus != null) STIMULUS_DP + 12f else 0f)
            val imageHeightDp = (maxHeight.value - chromeDp).coerceAtLeast(QuizImageLayout.MIN_SIDE)

            // Measure the real shape of this question's pictures (off the main thread) and size the frames to match,
            // so tall letter cards are shown whole instead of being cropped in a wide frame.
            val optionKey = q?.options?.joinToString("|") { it.id }
            var measured by remember { mutableStateOf<Pair<String?, Float>>(null to 0f) }
            LaunchedEffect(optionKey) {
                measured = if (q == null) (null to 0f)
                else optionKey to QuizImageLayout.meanAspect(withContext(Dispatchers.IO) { q.options.map { CardAspect.of(it) } })
            }
            val aspect = if (measured.first == optionKey) measured.second else 0f
            // The pictures are always stacked, one above the other (never side by side).
            val plan = QuizImageLayout.plan(q?.options?.size ?: 3, maxWidth.value, imageHeightDp, aspect = aspect, only = ImageArrangement.COLUMN)

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),   // safety net for huge font sizes only
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onExit) { Text(stringResource(R.string.quiz_back)) }
                    val albumLabel = stringResource(R.string.album_button)
                    TextButton(onClick = { showAlbum = true }, modifier = Modifier.semantics { contentDescription = albumLabel }) {
                        Text("🏆", fontSize = 24.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    // Stars for the current round (language-neutral, no reading needed)
                    Row(Modifier.clearAndSetSemantics { }) {
                        repeat(QuizRules.SET_SIZE) { i -> Text(if (i < stars) "⭐" else "☆", fontSize = 22.sp) }
                    }
                }

                if (q != null) {
                    // Compact header: the speaker button beside the name, so the pictures get the room.
                    val replayLabel = stringResource(R.string.quiz_replay_desc)
                    val hideName = q.mode == QuizMode.HEAR_SOUND && solvedId == null
                    // The card whose name is shown/spoken: the letter (letter -> word), the object (word -> letter) or the answer itself
                    val shown = q.promptCard ?: q.stimulus ?: q.target
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Brand.AccentBlue)
                                .semantics { contentDescription = replayLabel }
                                .clickable(role = Role.Button) {
                                    interaction++
                                    audio.play(QuizAudioPlan.promptFor(q, lang))
                                },
                            contentAlignment = Alignment.Center
                        ) { Text("🔊", fontSize = 34.sp, modifier = Modifier.clearAndSetSemantics { }) }

                        Spacer(Modifier.width(16.dp))
                        if (q.promptCard != null) {
                            // "Which picture starts with this letter?": show the letter
                            CardArtView(q.promptCard!!, Modifier.size(72.dp).clip(MaterialTheme.shapes.medium))
                            Spacer(Modifier.width(16.dp))
                        }
                        // The name, big. In a sound-only question it stays hidden ("?") until the child answers.
                        Text(
                            if (hideName) "❓" else if (lang == QuizLang.AR) shown.nameAr else shown.nameEn,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 36.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    val recordedVoice = if (lang == QuizLang.AR) shown.voiceArFile else shown.voiceEnFile
                    if (q.mode != QuizMode.HEAR_SOUND && recordedVoice == null && !audio.isTtsAvailable(lang)) {
                        Text(
                            stringResource(
                                R.string.quiz_tts_missing,
                                stringResource(if (lang == QuizLang.AR) R.string.lang_arabic else R.string.lang_english)
                            ),
                            color = Brand.StatusAmber, fontSize = 12.sp, textAlign = TextAlign.Center
                        )
                    }

                    q.stimulus?.let { object0 ->
                        // "Which letter does this start with?": show the object
                        Spacer(Modifier.height(6.dp))
                        CardArtView(object0, Modifier.size(STIMULUS_DP.dp).clip(MaterialTheme.shapes.large), QUIZ_IMAGE_PX)
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    OptionsLayout(q.options, plan) { option, index, sizeModifier ->
                        val isWrong = option.id in wrongIds
                        val isSolved = option.id == solvedId
                        val scale by animateFloatAsState(if (isSolved) 1.06f else 1f, tween(250), label = "pick")
                        val optionLabel = stringResource(R.string.quiz_option, index + 1)   // "Picture 2": never the answer
                        CardArtView(
                            card = option,
                            targetPx = QUIZ_IMAGE_PX,
                            modifier = sizeModifier
                                .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (isWrong) 0.3f else 1f }
                                .clip(MaterialTheme.shapes.large)
                                .border(
                                    width = if (isSolved) 6.dp else 1.dp,
                                    color = if (isSolved) Brand.StatusGreen else MaterialTheme.colorScheme.outline,
                                    shape = MaterialTheme.shapes.large
                                )
                                .semantics { contentDescription = optionLabel }
                                .clickable(role = Role.Button) { pick(option) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                val failed = message == stringResource(R.string.quiz_try_again)
                Text(
                    message ?: "",
                    color = if (failed) Brand.StatusAmber else Brand.StatusGreen,
                    fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                )
            }
        }

        if (showAlbum) StickerAlbumDialog(cards = cards, lang = lang, onDismiss = { showAlbum = false })

        if (roundOver) {
            RoundComplete(
                newSticker = newSticker,
                showAgain = !lastRoundOfDay,
                footer = if (lastRoundOfDay) stringResource(R.string.quiz_last_round_today) else null,
                onAlbum = { showAlbum = true },
                onAgain = {
                    stars = 0
                    roundOver = false
                    nextQuestion()
                },
                onBye = {
                    // Say goodbye, then leave (with a safety timer in case the voice never answers).
                    audio.play(listOf(AudioStep.Tts(spoken(ctx, lang, R.string.quiz_bye), lang))) { onExit() }
                    scope.launch { delay(3_000); onExit() }
                }
            )
        }
    }
}

/** Lays the answer pictures out as [plan] says (row / stacked / two-per-row), each picture at the planned size. */
@Composable
private fun OptionsLayout(
    options: List<QuizCard>,
    plan: ImagePlan,
    item: @Composable (card: QuizCard, index: Int, size: Modifier) -> Unit
) {
    val size = Modifier.size(plan.widthDp.dp, plan.heightDp.dp)
    val gap = QuizImageLayout.GAP_DP.dp
    when (plan.arrangement) {
        ImageArrangement.ROW -> Row(horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
            options.forEachIndexed { i, o -> item(o, i, size) }
        }
        ImageArrangement.COLUMN -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            options.forEachIndexed { i, o -> item(o, i, size) }
        }
        ImageArrangement.GRID -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            options.withIndex().chunked(2).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                    rowItems.forEach { (i, o) -> item(o, i, size) }
                }
            }
        }
    }
}

/**
 * The celebration after a round: fireworks, confetti and falling stars, the logo hopping and wiggling, big stars, the
 * sticker just won, then Again / My stickers / Bye-bye. [showAgain] is false after the last round the parent allows today.
 * No timer, no pressure.
 */
@Composable
internal fun RoundComplete(
    newSticker: QuizCard?,
    onAlbum: () -> Unit,
    onAgain: () -> Unit,
    onBye: () -> Unit,
    showAgain: Boolean = true,
    footer: String? = null
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }                       // starts at 0 so the stars really grow in
    val pulse by animateFloatAsState(targetValue = if (shown) 1f else 0f, animationSpec = tween(700), label = "stars")
    val clock = rememberCelebrationClock()
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f)).safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        CelebrationLayer(clock, Modifier.fillMaxSize())          // behind the content; takes no touches
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            Spacer(Modifier.height(32.dp))                       // room for the logo's hop
            DancingLogo(clock, Modifier.size(120.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                "⭐".repeat(QuizRules.SET_SIZE),
                fontSize = 40.sp,
                modifier = Modifier.graphicsLayer { scaleX = 0.6f + 0.4f * pulse; scaleY = 0.6f + 0.4f * pulse }.clearAndSetSemantics { }
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.quiz_set_done),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
            if (newSticker != null) {
                // The visual reward: the sticker just won
                Spacer(Modifier.height(16.dp))
                CardArtView(
                    newSticker,
                    Modifier.size(150.dp).clip(MaterialTheme.shapes.large).border(4.dp, Brand.StatusAmber, MaterialTheme.shapes.large),
                    QUIZ_IMAGE_PX
                )
                Text(
                    "🏆 " + stringResource(R.string.sticker_new),
                    color = Brand.StatusAmber, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (footer != null) {
                Spacer(Modifier.height(12.dp))
                Text(footer, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (showAgain) BigChoice("🔁", stringResource(R.string.quiz_again), Brand.StatusGreen, onAgain)
                BigChoice("🏆", stringResource(R.string.album_button), Brand.StatusAmber, onAlbum)
                BigChoice("👋", stringResource(R.string.quiz_bye), Brand.AccentBlue, onBye)
            }
        }
    }
}

@Composable
internal fun BigChoice(emoji: String, label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Column(
        Modifier
            .size(width = 104.dp, height = 120.dp)
            .clip(MaterialTheme.shapes.large)
            .background(color)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(emoji, fontSize = 44.sp, modifier = Modifier.clearAndSetSemantics { })
        Text(label, color = androidx.compose.ui.graphics.Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** A picture loaded off the main thread and downsampled so big photos can't exhaust memory. */
@Composable
internal fun PathImage(path: String, modifier: Modifier, targetPx: Int = 512) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, targetPx) {
        value = withContext(Dispatchers.IO) { decodeSampled(path, targetPx) }
    }
    BoxWithConstraints(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) {
            val tileAspect =
                if (maxWidth.value.isFinite() && maxHeight.value.isFinite() && maxHeight.value > 0f) maxWidth.value / maxHeight.value else 0f
            // Fill the frame by cropping only when little is lost; otherwise show the whole picture.
            val crop = QuizImageLayout.shouldCrop(b.width.toFloat() / b.height, tileAspect)
            Image(
                bitmap = b, contentDescription = null,
                contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun decodeSampled(path: String, target: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
}
