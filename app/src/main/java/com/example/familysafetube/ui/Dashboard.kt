package com.example.familysafetube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.familysafetube.AppGraph
import com.example.familysafetube.DayUsage
import com.example.familysafetube.Policy
import com.example.familysafetube.LevelAdvisor
import com.example.familysafetube.ProgressSummary
import com.example.familysafetube.QuizLang
import com.example.familysafetube.QuizLevel
import com.example.familysafetube.QuizProgress
import com.example.familysafetube.QuizRepository
import com.example.familysafetube.R
import com.example.familysafetube.StickerAlbum
import com.example.familysafetube.WordProgress
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private data class DashboardData(
    val week: List<DayUsage>,
    val today: Map<String, Float>,
    val lockAttempts: Int,
    val bonusMinutes: Int,
    val fileCount: Int,
    val storageMb: Long,
    val fileNames: List<String>,
    val quiz: ProgressSummary,
    val quizLang: QuizLang,
    val level: QuizLevel,
    val levelAdvice: QuizLevel?,
    val stickers: StickerAlbum.Progress
)

@Composable
fun DashboardDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val data = remember {
        val files = ctx.filesDir.listFiles()
        val summary = QuizProgress.summarize(QuizRepository.loadCards(ctx), QuizRepository.stats())
        DashboardData(
            week = AppGraph.usage.history(7),
            today = AppGraph.usage.categoriesToday(),
            lockAttempts = AppGraph.usage.lockAttemptsToday(),
            bonusMinutes = AppGraph.usage.bonusToday(),
            fileCount = files?.size ?: 0,
            storageMb = (files?.sumOf { it.length() } ?: 0L) / (1024 * 1024),
            fileNames = ctx.filesDir.list()?.sorted() ?: emptyList(),
            quiz = summary,
            quizLang = QuizRepository.language(),
            level = QuizRepository.level(),
            levelAdvice = LevelAdvisor.suggest(QuizRepository.level(), summary),
            stickers = StickerAlbum.progress(
                QuizRepository.stickers(),
                QuizRepository.loadCards(ctx).filter { it.enabled }.map { it.id }.toSet()
            )
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.94f).heightIn(max = 680.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.dash_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    val fav = data.today.maxByOrNull { it.value }?.key
                    Text(
                        stringResource(R.string.dash_favorite,
                            fav?.let { Policy.categoryLabel(ctx, it) } ?: stringResource(R.string.dash_none)),
                        fontSize = 15.sp
                    )
                    Text(stringResource(R.string.dash_storage, data.fileCount, data.storageMb.toInt()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text(stringResource(R.string.dash_locks, data.lockAttempts),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text(stringResource(R.string.dash_bonus, data.bonusMinutes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.dash_week), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    WeekChart(data.week)
                    Legend(data.week)

                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.dash_today), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    data.today.forEach { (cat, mins) ->
                        val limit = Policy.categoryLimit(ctx, cat)
                        val limitText = if (limit > 0) stringResource(R.string.dash_limit_suffix, limit) else ""
                        Text(
                            stringResource(R.string.dash_category_line, Policy.categoryLabel(ctx, cat), oneDecimal(mins)) + limitText,
                            fontSize = 13.sp
                        )
                    }
                    Text(stringResource(R.string.dash_total, oneDecimal(data.today.values.sum())),
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))

                    Spacer(Modifier.height(16.dp))
                    QuizProgressSection(data.quiz, data.quizLang, data.level, data.levelAdvice)
                    Text(
                        stringResource(R.string.dash_stickers, data.stickers.collected, data.stickers.total),
                        fontSize = 13.sp, color = Brand.StatusAmber, modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.dash_files), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        if (data.fileNames.isEmpty()) stringResource(R.string.dash_no_files) else data.fileNames.joinToString("\n"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(12.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_close))
                }
            }
        }
    }
}

/** Which words the child knows and which need practice, named in the quiz language. */
@Composable
private fun QuizProgressSection(summary: ProgressSummary, lang: QuizLang, level: QuizLevel, advice: QuizLevel?) {
    fun names(words: List<WordProgress>) =
        if (words.isEmpty()) null else words.joinToString("، ".takeIf { lang == QuizLang.AR } ?: ", ") {
            if (lang == QuizLang.AR) it.card.nameAr else it.card.nameEn
        }
    val none = stringResource(R.string.dash_quiz_none)

    Text(stringResource(R.string.dash_quiz_title), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    Spacer(Modifier.height(4.dp))
    summary.accuracyPercent?.let {
        Text(stringResource(R.string.dash_quiz_totals, summary.totalAnswers, it), fontSize = 13.sp)
    }
    Text(
        stringResource(R.string.dash_quiz_learned, summary.learned.size, names(summary.learned) ?: none),
        fontSize = 13.sp, color = Brand.StatusGreen
    )
    Text(
        stringResource(R.string.dash_quiz_struggling, summary.struggling.size, names(summary.struggling) ?: none),
        fontSize = 13.sp, color = Brand.StatusAmber
    )
    Text(
        stringResource(R.string.dash_quiz_new, summary.newCount),
        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    // A gentle suggestion only: the parent decides (Settings -> Quiz level)
    if (advice != null) {
        val name = stringResource(
            when (advice) {
                QuizLevel.BABY -> R.string.level_name_baby
                QuizLevel.TODDLER -> R.string.level_name_toddler
                QuizLevel.OLDER -> R.string.level_name_older
            }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(if (advice.ordinal > level.ordinal) R.string.dash_quiz_level_up else R.string.dash_quiz_level_down, name),
            fontSize = 13.sp, color = Brand.AccentBlue
        )
    }
}

private fun oneDecimal(v: Float) = String.format(Locale.getDefault(), "%.1f", v)

private fun colorFor(category: String) = Brand.CategoryColors[category] ?: Brand.CategoryFallback

/** Stacked daily bars, oldest day first. Plain layouts only (no Canvas), so RTL mirrors automatically. */
@Composable
private fun WeekChart(days: List<DayUsage>) {
    val chartHeight = 120.dp
    val maxTotal = max(days.maxOfOrNull { it.totalMinutes } ?: 0f, 1f)
    val ctx = LocalContext.current
    // One spoken summary for the whole chart ("Mon: 12 min, Tue: 0 min, ...") instead of 7 unlabeled bars.
    val summary = days.joinToString(", ") {
        ctx.getString(
            R.string.dash_chart_day,
            it.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            it.totalMinutes.roundToInt()
        )
    }

    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = summary },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEach { day ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    if (day.totalMinutes >= 1f) day.totalMinutes.roundToInt().toString() else "",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))

                // Bar area has a fixed height so all days share one scale; the bar sits at the bottom.
                Column(Modifier.height(chartHeight).fillMaxWidth(), verticalArrangement = Arrangement.Bottom) {
                    val barHeight = chartHeight * (day.totalMinutes / maxTotal)
                    if (day.totalMinutes > 0f) {
                        Column(Modifier.height(barHeight).fillMaxWidth().clip(RoundedCornerShape(4.dp))) {
                            // Top segment first: reverse so the first category sits at the bottom.
                            day.minutesByCategory.entries.sortedByDescending { it.key }.forEach { (cat, mins) ->
                                if (mins > 0f) {
                                    Box(Modifier.weight(mins).fillMaxWidth().background(colorFor(cat)))
                                }
                            }
                        }
                    } else {
                        Box(Modifier.height(2.dp).fillMaxWidth().background(MaterialTheme.colorScheme.outline))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun Legend(days: List<DayUsage>) {
    val ctx = LocalContext.current
    val cats = days.flatMap { it.minutesByCategory.keys }.distinct().sorted()
    if (cats.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        cats.forEach { cat ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colorFor(cat)))
                Spacer(Modifier.size(4.dp))
                Text(Policy.categoryLabel(ctx, cat), fontSize = 11.sp)
            }
        }
    }
}
