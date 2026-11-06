package com.example.familysafetube.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familysafetube.CelebrationConfig
import com.example.familysafetube.CelebrationEffect
import com.example.familysafetube.CelebrationEvent
import com.example.familysafetube.CelebrationPhrases
import com.example.familysafetube.CelebrationSettings
import com.example.familysafetube.PhraseId
import com.example.familysafetube.PraiseSet
import com.example.familysafetube.QuizLang
import com.example.familysafetube.R
import com.example.familysafetube.VoiceSource
import com.example.familysafetube.VoiceStyle
import java.util.Locale

/**
 * Settings > Celebration: when it plays (after a right answer / at the end of a round), the sound effect, whose voice
 * (the parent's recordings or the phone, with the child's name if wanted), which phrases, the voice style (pitch and
 * speed), which recording to use, and Test buttons. It only edits a [CelebrationConfig]; the Settings dialog saves it.
 */
@Composable
fun CelebrationSection(
    config: CelebrationConfig,
    onChange: (CelebrationConfig) -> Unit,
    lang: QuizLang,
    praise: PraiseSet,
    onTest: (CelebrationEvent) -> Unit,
    /** Say one phrase now, exactly as the form shows it (to check the pronunciation). */
    onHear: (PhraseId) -> Unit,
    /** False when the phone has no text-to-speech voice for [lang]. */
    ttsAvailable: Boolean,
    onOpenVoiceSettings: () -> Unit
) {
    Heading(R.string.celeb_title)
    CheckLine(config.onAnswer, { onChange(config.copy(onAnswer = it)) }, stringResource(R.string.celeb_on_answer))
    CheckLine(config.onRound, { onChange(config.copy(onRound = it)) }, stringResource(R.string.celeb_on_round))

    Heading(R.string.celeb_sound)
    ChipRow {
        for (effect in CelebrationEffect.values()) {
            FilterChip(
                selected = config.effect == effect,
                onClick = { onChange(config.copy(effect = effect)) },
                label = { Text(stringResource(effectLabel(effect))) }
            )
        }
    }

    Heading(R.string.celeb_voice)
    ChipRow {
        for (source in VoiceSource.values()) {
            FilterChip(
                selected = config.voice == source,
                onClick = { onChange(config.copy(voice = source)) },
                label = { Text(stringResource(voiceLabel(source))) }
            )
        }
    }

    if (config.voice != VoiceSource.OFF && !ttsAvailable) {
        Text(
            stringResource(R.string.celeb_no_voice),
            color = MaterialTheme.colorScheme.error, fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        TextButton(onClick = onOpenVoiceSettings) { Text(stringResource(R.string.celeb_open_voice_settings)) }
    }

    if (config.voice != VoiceSource.OFF) {
        CheckLine(config.useName, { onChange(config.copy(useName = it)) }, stringResource(R.string.celeb_use_name))
        if (config.useName) {
            OutlinedTextField(
                value = config.name,
                onValueChange = { onChange(config.copy(name = it.take(CelebrationSettings.NAME_MAX))) },
                singleLine = true,
                label = { Text(stringResource(R.string.celeb_name_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            if (lang == QuizLang.AR) {
                Text(
                    stringResource(R.string.celeb_name_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        if (lang == QuizLang.AR) {
            CheckLine(config.diacritics, { onChange(config.copy(diacritics = it)) }, stringResource(R.string.celeb_diacritics))
            Text(
                stringResource(R.string.celeb_diacritics_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
            )
        }

        Heading(R.string.celeb_phrases)
        val shownName = if (config.useName) CelebrationSettings.cleanName(config.name) else ""
        for (id in PhraseId.values()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clickable { onChange(config.copy(phrases = CelebrationSettings.withPhrase(config.phrases, id, id !in config.phrases))) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = id in config.phrases,
                        onCheckedChange = { on -> onChange(config.copy(phrases = CelebrationSettings.withPhrase(config.phrases, id, on))) }
                    )
                    // exactly what the phone will say
                    Text(CelebrationPhrases.text(lang, id, shownName, config.diacritics), modifier = Modifier.weight(1f))
                }
                TextButton(onClick = { onHear(id) }) { Text("▶ " + stringResource(R.string.celeb_hear), fontSize = 13.sp) }
            }
        }

        Heading(R.string.celeb_style)
        ChipRow {
            for (preset in VoiceStyle.PRESETS) {
                FilterChip(
                    selected = VoiceStyle.presetOf(config.style) == preset,
                    onClick = { onChange(config.copy(style = preset)) },
                    label = { Text(stringResource(styleLabel(preset))) }
                )
            }
        }
        SliderRow(R.string.celeb_pitch, config.style.pitch, VoiceStyle.PITCH_MIN..VoiceStyle.PITCH_MAX) {
            onChange(config.copy(style = config.style.copy(pitch = it)))
        }
        SliderRow(R.string.celeb_speed, config.style.rate, VoiceStyle.RATE_MIN..VoiceStyle.RATE_MAX) {
            onChange(config.copy(style = config.style.copy(rate = it)))
        }
    }

    if (config.voice == VoiceSource.RECORDINGS) {
        val hasAny = CelebrationEvent.values().any { praise.clipsFor(it.praiseEvent).isNotEmpty() }
        if (!hasAny) {
            Text(
                stringResource(R.string.celeb_recordings_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            for (event in CelebrationEvent.values()) {
                val clips = praise.clipsFor(event.praiseEvent)
                if (clips.isEmpty()) continue
                Heading(if (event == CelebrationEvent.ANSWER) R.string.celeb_recording_answer else R.string.celeb_recording_round)
                val chosen = config.clipFor(event)
                ChipRow {
                    FilterChip(
                        selected = chosen !in clips,                                   // none picked (or the picked one was deleted)
                        onClick = { onChange(config.withClip(event, "")) },
                        label = { Text(stringResource(R.string.celeb_clip_random)) }
                    )
                    clips.forEachIndexed { index, file ->
                        FilterChip(
                            selected = chosen == file,
                            onClick = { onChange(config.withClip(event, file)) },
                            label = { Text(stringResource(R.string.praise_clip, index + 1)) }
                        )
                    }
                }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        OutlinedButton(onClick = { onTest(CelebrationEvent.ANSWER) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.celeb_test_answer), fontSize = 13.sp)
        }
        OutlinedButton(onClick = { onTest(CelebrationEvent.ROUND) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.celeb_test_round), fontSize = 13.sp)
        }
    }
}

private fun effectLabel(e: CelebrationEffect): Int = when (e) {
    CelebrationEffect.CHEER -> R.string.celeb_effect_cheer
    CelebrationEffect.FANFARE -> R.string.celeb_effect_fanfare
    CelebrationEffect.APPLAUSE -> R.string.celeb_effect_applause
    CelebrationEffect.SPARKLE -> R.string.celeb_effect_sparkle
    CelebrationEffect.NONE -> R.string.celeb_effect_none
}

private fun voiceLabel(v: VoiceSource): Int = when (v) {
    VoiceSource.RECORDINGS -> R.string.celeb_voice_recordings
    VoiceSource.PHONE -> R.string.celeb_voice_phone
    VoiceSource.OFF -> R.string.celeb_voice_off
}

private fun styleLabel(s: VoiceStyle): Int = when (s) {
    VoiceStyle.PLAYFUL -> R.string.celeb_style_playful
    VoiceStyle.GENTLE -> R.string.celeb_style_gentle
    VoiceStyle.EXCITED -> R.string.celeb_style_excited
    else -> R.string.celeb_style_normal
}

@Composable
private fun Heading(res: Int) {
    Text(
        stringResource(res),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun CheckLine(checked: Boolean, onChange: (Boolean) -> Unit, label: String) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, modifier = Modifier.weight(1f))
    }
}

/** A row of chips that scrolls sideways when the screen is narrow. */
@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun SliderRow(labelRes: Int, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Text(stringResource(labelRes) + ": " + String.format(Locale.ROOT, "%.2f", value), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
}
