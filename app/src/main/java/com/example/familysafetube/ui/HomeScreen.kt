package com.example.familysafetube.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familysafetube.HomeStatus
import com.example.familysafetube.MainUiState
import com.example.familysafetube.QuizState
import com.example.familysafetube.R
import com.example.familysafetube.TimeRules

/** Width at which the home screen switches from one column to two (tablets, landscape phones). */
private val TWO_COLUMN_MIN_WIDTH = 720.dp
private val CONTENT_MAX_WIDTH = 1100.dp

@Composable
fun HomeScreen(
    state: MainUiState,
    onSync: () -> Unit,
    onYoutube: () -> Unit,
    onOffline: () -> Unit,
    onImages: () -> Unit,
    onQuiz: () -> Unit,
    onMatch: () -> Unit,
    onUpdates: () -> Unit,
    onSettings: () -> Unit,
    onResetTimer: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        Header(state)

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val twoColumns = maxWidth >= TWO_COLUMN_MIN_WIDTH
            if (twoColumns) {
                // Tablet / landscape: status and actions on the left, big mode cards on the right.
                Row(
                    Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxSize().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        UpdateBanner(onUpdates)
                        UsageCard(state)
                        SyncButton(state, onSync)
                        ParentButtons(onSettings, onResetTimer)
                    }
                    Column(
                        Modifier.weight(1.3f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Modes(state, onYoutube, onOffline, onImages, onQuiz, onMatch)
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UpdateBanner(onUpdates)
                    UsageCard(state)
                    SyncButton(state, onSync)
                    Modes(state, onYoutube, onOffline, onImages, onQuiz, onMatch)
                    ParentButtons(onSettings, onResetTimer)
                }
            }
        }
    }
}

@Composable
private fun Modes(
    state: MainUiState,
    onYoutube: () -> Unit,
    onOffline: () -> Unit,
    onImages: () -> Unit,
    onQuiz: () -> Unit,
    onMatch: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(stringResource(R.string.home_content_modes))
        ModeCard(
            emoji = "▶",
            title = stringResource(R.string.mode_youtube),
            subtitle = if (state.youtubeCount > 0) stringResource(R.string.youtube_ready, state.youtubeCount)
                       else stringResource(R.string.youtube_none),
            brush = Brand.YoutubeBrush,
            onClick = onYoutube
        )
        ModeCard(
            emoji = "📁",
            title = stringResource(R.string.mode_offline),
            subtitle = if (state.offlineCount > 0) stringResource(R.string.offline_ready, state.offlineCount)
                       else stringResource(R.string.offline_none),
            brush = Brand.OfflineBrush,
            onClick = onOffline
        )
        ModeCard(
            emoji = "🖼️",
            title = stringResource(R.string.mode_images),
            subtitle = stringResource(R.string.images_count, state.imagesCount),
            brush = Brand.ImagesBrush,
            onClick = onImages
        )
        if (state.quiz != QuizState.HIDDEN) {
            ModeCard(
                emoji = "🧩",
                title = stringResource(R.string.mode_quiz),
                subtitle = stringResource(
                    if (state.quiz == QuizState.READY) R.string.quiz_subtitle else R.string.quiz_none
                ),
                brush = Brand.QuizBrush,
                onClick = onQuiz
            )
        }
        if (state.matchVisible) {
            ModeCard(
                emoji = "🔗",
                title = stringResource(R.string.mode_match),
                subtitle = stringResource(R.string.match_subtitle),
                brush = Brand.MatchBrush,
                onClick = onMatch
            )
        }
    }
}

@Composable
private fun ParentButtons(onSettings: () -> Unit, onResetTimer: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        SecondaryButton(stringResource(R.string.btn_settings), Modifier.weight(1f), onSettings)
        SecondaryButton(stringResource(R.string.btn_reset_timer), Modifier.weight(1f), onResetTimer)
    }
}

// ── Header ───────────────────────────────────────────────────────────────────
@Composable
private fun Header(state: MainUiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brand.Header)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.app_name),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(4.dp))
            StatusPill(state)
        }
        val left = TimeRules.minutesLeft(state.limitMins, state.usedMins).toInt()
        Text(
            "⏱ " + stringResource(R.string.home_time_left, left),
            color = usageColor(state),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StatusPill(state: MainUiState) {
    val text = when (state.status) {
        HomeStatus.ACTIVE        -> stringResource(R.string.status_active)
        HomeStatus.BEDTIME       -> stringResource(R.string.status_bedtime)
        HomeStatus.LIMIT_REACHED -> stringResource(R.string.status_limit)
        HomeStatus.SYNC_FAILED   -> stringResource(R.string.status_sync_failed)
        HomeStatus.SYNCING       -> stringResource(R.string.status_syncing, state.syncMessage ?: "")
    }
    val color = when (state.status) {
        HomeStatus.ACTIVE        -> Brand.StatusGreen
        HomeStatus.SYNCING       -> Brand.StatusAmber
        HomeStatus.BEDTIME,
        HomeStatus.LIMIT_REACHED,
        HomeStatus.SYNC_FAILED   -> Brand.StatusRed
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF0D1830))
            .border(1.dp, Color(0xFF1E3060), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }   // TalkBack announces status changes
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private fun usageColor(state: MainUiState): Color {
    val pct = (state.usedMins / state.limitMins.coerceAtLeast(1)).coerceIn(0f, 1f)
    return when {
        pct > 0.85f -> Brand.StatusRed
        pct > 0.60f -> Brand.StatusAmber
        else        -> Brand.StatusBlue
    }
}

// ── Usage card ───────────────────────────────────────────────────────────────
@Composable
private fun UsageCard(state: MainUiState) {
    val fraction = (state.usedMins / state.limitMins.coerceAtLeast(1)).coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = fraction, animationSpec = tween(600), label = "usage")

    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            }
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_usage_title),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.home_usage_numbers, state.usedMins.toInt(), state.limitMins),
                color = usageColor(state),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brand.TrackDark)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brand.UsageBrush)
            )
        }
    }
}

// ── Buttons ──────────────────────────────────────────────────────────────────
@Composable
private fun SyncButton(state: MainUiState, onClick: () -> Unit) {
    val label = state.syncMessage?.let { "🔄  " + it.uppercase() } ?: stringResource(R.string.home_sync_default)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brand.SyncBrush)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
    )
}

@Composable
private fun ModeCard(emoji: String, title: String, subtitle: String, brush: Brush, onClick: () -> Unit) {
    val chevron = if (LocalLayoutDirection.current == LayoutDirection.Rtl) "‹" else "›"
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(brush)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 26.sp, modifier = Modifier.clearAndSetSemantics { })   // decorative
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xCCFFFFFF), fontSize = 12.sp)
        }
        Text(chevron, color = Color(0xCCFFFFFF), fontSize = 28.sp, modifier = Modifier.clearAndSetSemantics { })
    }
}

@Composable
private fun SecondaryButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(BorderStroke(1.dp, Color(0xFF2A3A60)), RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}
