package com.example.familysafetube.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.familysafetube.AppGraph
import com.example.familysafetube.FormError
import com.example.familysafetube.FormResult
import com.example.familysafetube.GateGuard
import com.example.familysafetube.GateRules
import com.example.familysafetube.Keys
import com.example.familysafetube.PinStore
import com.example.familysafetube.DeckId
import com.example.familysafetube.QuizDecks
import com.example.familysafetube.QuizLang
import com.example.familysafetube.QuizLevel
import com.example.familysafetube.Policy
import com.example.familysafetube.R
import com.example.familysafetube.Schedule
import com.example.familysafetube.SettingsForm
import com.example.familysafetube.SettingsInput
import com.example.familysafetube.SyncWorker
import com.example.familysafetube.UsageTracker
import com.example.familysafetube.get
import com.example.familysafetube.getOr

private fun waitText(ctx: android.content.Context, seconds: Long): String =
    if (seconds < 60) ctx.getString(R.string.wait_seconds, seconds.toInt())
    else ctx.getString(R.string.wait_minutes, ((seconds + 59) / 60).toInt())

// ═════════════════════════════════════════════════════════════════════════════
// Parental gate
// ═════════════════════════════════════════════════════════════════════════════
private enum class GateStage { CHOICE, PIN, MATH }

/**
 * PIN or maths verification. The maths fallback stays available even when a PIN is set
 * (it is the PIN-recovery path). A wrong answer is counted by [GateGuard]; after three, the gate
 * locks with an escalating, persistent delay.
 */
@Composable
fun ParentGate(onSuccess: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current

    // Locked out? Tell the parent and close straight away.
    val lockMs = remember { GateGuard.remainingLockMs(ctx) }
    LaunchedEffect(Unit) {
        if (lockMs > 0) {
            Toast.makeText(ctx, ctx.getString(R.string.gate_still_locked, waitText(ctx, lockMs / 1000)), Toast.LENGTH_LONG).show()
            UsageTracker.recordLockAttempt(ctx)
            onDismiss()
        }
    }
    if (lockMs > 0) return

    var stage by remember { mutableStateOf(if (PinStore.hasPin(ctx)) GateStage.CHOICE else GateStage.MATH) }

    fun failed() {
        UsageTracker.recordLockAttempt(ctx)
        val lockSecs = GateGuard.recordFailure(ctx)
        val msg = if (lockSecs > 0) ctx.getString(R.string.gate_locked, waitText(ctx, lockSecs.toLong()))
                  else ctx.getString(R.string.gate_wrong, GateGuard.attemptsLeft(ctx))
        Toast.makeText(ctx, msg, if (lockSecs > 0) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        onDismiss()
    }
    fun passed() {
        GateGuard.recordSuccess(ctx)
        onSuccess()
    }

    when (stage) {
        GateStage.CHOICE -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { AppLogo(Modifier.size(56.dp)) },
            title = { Text(stringResource(R.string.gate_title)) },
            text = {
                Column {
                    TextButton(onClick = { stage = GateStage.PIN }) { Text(stringResource(R.string.gate_choose_pin)) }
                    TextButton(onClick = { stage = GateStage.MATH }) { Text(stringResource(R.string.gate_choose_math)) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
        )

        GateStage.PIN -> {
            var pin by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.gate_pin_title)) },
                text = {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
                        label = { Text(stringResource(R.string.gate_pin_hint)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { if (PinStore.verify(ctx, pin.trim())) passed() else failed() }) {
                        Text(stringResource(R.string.action_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { stage = GateStage.MATH }) { Text(stringResource(R.string.gate_use_math)) }
                }
            )
        }

        GateStage.MATH -> {
            val challenge = remember { GateRules.mathChallenge() }
            var answer by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.gate_math_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.gate_math_question, challenge.first))
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = answer,
                            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) answer = it },
                            label = { Text(stringResource(R.string.gate_answer_hint)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { if (answer.trim() == challenge.second.toString()) passed() else failed() }) {
                        Text(stringResource(R.string.action_confirm))
                    }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Settings
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onPin: () -> Unit,
    onClearStorage: () -> Unit,
    onDashboard: () -> Unit,
    onQuizEditor: () -> Unit,
    onUpdates: () -> Unit
) {
    val ctx = LocalContext.current
    val st = AppGraph.settings

    fun limitText(v: Int) = if (v == 0) "" else v.toString()

    var limit by remember { mutableStateOf(st.getOr(Keys.TIME_LIMIT_MINS, 30).toString()) }
    var bedStart by remember { mutableStateOf(st.getOr(Keys.BEDTIME_START, "20:00")) }
    var bedEnd by remember { mutableStateOf(st.getOr(Keys.BEDTIME_END, "07:00")) }
    var yt by remember { mutableStateOf(limitText(st.getOr(Keys.catLimit(Policy.CAT_YOUTUBE), 0))) }
    var vid by remember { mutableStateOf(limitText(st.getOr(Keys.catLimit(Policy.CAT_VIDEOS), 0))) }
    var img by remember { mutableStateOf(limitText(st.getOr(Keys.catLimit(Policy.CAT_IMAGES), 0))) }
    var syncTime by remember { mutableStateOf(st.getOr(Keys.SYNC_TIME, "06:00")) }
    var wifiOnly by remember { mutableStateOf(st.getOr(Keys.SYNC_WIFI_ONLY, false)) }
    var voice by remember { mutableStateOf(st.getOr(Keys.VOICE_OVER, true)) }
    var autoplay by remember { mutableStateOf(st.getOr(Keys.AUTOPLAY_NEXT, false)) }

    var weekendOn by remember { mutableStateOf(st.getOr(Keys.WEEKEND_ENABLED, false)) }
    var wLimit by remember { mutableStateOf(st.getOr(Keys.WEEKEND_LIMIT, 60).toString()) }
    var wBedStart by remember { mutableStateOf(st.getOr(Keys.WEEKEND_BED_START, "22:00")) }
    var wBedEnd by remember { mutableStateOf(st.getOr(Keys.WEEKEND_BED_END, "09:00")) }
    var wDays by remember { mutableStateOf(Schedule.parseDays(st.getOr(Keys.WEEKEND_DAYS, ""))) }
    var remoteOn by remember { mutableStateOf(st.getOr(Keys.REMOTE_ENABLED, false)) }
    var quizOn by remember { mutableStateOf(st.getOr(Keys.QUIZ_ENABLED, true)) }
    var quizLang by remember { mutableStateOf(QuizLang.fromCode(st[Keys.QUIZ_LANG])) }
    var quizSoundOnly by remember { mutableStateOf(st.getOr(Keys.QUIZ_SOUND_ONLY, true)) }
    var quizLevel by remember { mutableStateOf(QuizLevel.fromCode(st[Keys.QUIZ_LEVEL])) }
    var quizPhonics by remember { mutableStateOf(st.getOr(Keys.QUIZ_PHONICS, false)) }
    var matchOn by remember { mutableStateOf(st.getOr(Keys.MATCH_ENABLED, true)) }
    var quizDecks by remember { mutableStateOf(QuizDecks.parse(st[Keys.QUIZ_DECKS])) }

    fun save() {
        val result = SettingsForm.validate(SettingsInput(
            limitMins = limit, bedtimeStart = bedStart, bedtimeEnd = bedEnd,
            youtubeLimit = yt, videosLimit = vid, imagesLimit = img, syncTime = syncTime,
            weekendEnabled = weekendOn, weekendLimit = wLimit, weekendBedStart = wBedStart,
            weekendBedEnd = wBedEnd, weekendDays = wDays
        ))
        when (result) {
            is FormResult.Error -> {
                val msg = when (result.error) {
                    FormError.LIMIT     -> R.string.err_limit
                    FormError.SYNC_TIME -> R.string.err_sync_time
                    FormError.BEDTIME   -> R.string.err_bedtime
                    FormError.WEEKEND_LIMIT   -> R.string.err_weekend_limit
                    FormError.WEEKEND_BEDTIME -> R.string.err_weekend_bedtime
                    FormError.WEEKEND_DAYS    -> R.string.err_weekend_days
                }
                Toast.makeText(ctx, ctx.getString(msg), Toast.LENGTH_SHORT).show()
            }
            is FormResult.Ok -> {
                val v = result.settings
                st.edit { p ->
                    p[Keys.TIME_LIMIT_MINS] = v.limitMins
                    p[Keys.SYNC_WIFI_ONLY] = wifiOnly
                    p[Keys.VOICE_OVER] = voice
                    p[Keys.BEDTIME_START] = v.bedtimeStart
                    p[Keys.BEDTIME_END] = v.bedtimeEnd
                    p[Keys.catLimit(Policy.CAT_YOUTUBE)] = v.youtubeLimit
                    p[Keys.catLimit(Policy.CAT_VIDEOS)] = v.videosLimit
                    p[Keys.catLimit(Policy.CAT_IMAGES)] = v.imagesLimit
                    p[Keys.SYNC_TIME] = v.syncTime
                    p[Keys.AUTOPLAY_NEXT] = autoplay
                    p[Keys.WEEKEND_ENABLED] = v.weekendEnabled
                    p[Keys.WEEKEND_LIMIT] = v.weekendLimit
                    p[Keys.WEEKEND_BED_START] = v.weekendBedStart
                    p[Keys.WEEKEND_BED_END] = v.weekendBedEnd
                    p[Keys.WEEKEND_DAYS] = Schedule.formatDays(v.weekendDays)
                    p[Keys.REMOTE_ENABLED] = remoteOn
                    p[Keys.QUIZ_ENABLED] = quizOn
                    p[Keys.QUIZ_LANG] = quizLang.code
                    p[Keys.QUIZ_SOUND_ONLY] = quizSoundOnly
                    p[Keys.QUIZ_LEVEL] = quizLevel.code
                    p[Keys.QUIZ_PHONICS] = quizPhonics
                    p[Keys.MATCH_ENABLED] = matchOn
                    p[Keys.QUIZ_DECKS] = QuizDecks.format(quizDecks)
                }
                SyncWorker.scheduleDaily(ctx, replace = true)   // re-anchor the schedule to the new time
                Toast.makeText(ctx, ctx.getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
                onSaved()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.94f).heightIn(max = 640.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppLogo(Modifier.size(36.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.settings_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))

                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    SectionTitle(R.string.settings_limit_label)
                    Field(limit, { limit = it }, R.string.settings_limit_hint, numeric = true)

                    SectionTitle(R.string.settings_bed_start)
                    Field(bedStart, { bedStart = it }, R.string.settings_time_hint)
                    SectionTitle(R.string.settings_bed_end)
                    Field(bedEnd, { bedEnd = it }, R.string.settings_time_hint)

                    SectionTitle(R.string.settings_cat_label)
                    Field(yt, { yt = it }, R.string.settings_limit_youtube, numeric = true)
                    Spacer(Modifier.height(8.dp))
                    Field(vid, { vid = it }, R.string.settings_limit_videos, numeric = true)
                    Spacer(Modifier.height(8.dp))
                    Field(img, { img = it }, R.string.settings_limit_images, numeric = true)

                    SectionTitle(R.string.settings_sync_time)
                    Field(syncTime, { syncTime = it }, R.string.settings_time_hint)

                    SectionTitle(R.string.settings_options)
                    CheckRow(wifiOnly, { wifiOnly = it }, R.string.settings_wifi_only)
                    CheckRow(voice, { voice = it }, R.string.settings_voice)
                    CheckRow(autoplay, { autoplay = it }, R.string.settings_autoplay)

                    SectionTitle(R.string.settings_weekend_enable)
                    CheckRow(weekendOn, { weekendOn = it }, R.string.settings_weekend_enable)
                    if (weekendOn) {
                        SectionTitle(R.string.settings_weekend_days)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            java.time.DayOfWeek.values().forEach { day ->
                                FilterChip(
                                    selected = day in wDays,
                                    onClick = { wDays = if (day in wDays) wDays - day else wDays + day },
                                    label = { Text(day.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Field(wLimit, { wLimit = it }, R.string.settings_weekend_limit, numeric = true)
                        Spacer(Modifier.height(8.dp))
                        Field(wBedStart, { wBedStart = it }, R.string.settings_weekend_bed_start)
                        Spacer(Modifier.height(8.dp))
                        Field(wBedEnd, { wBedEnd = it }, R.string.settings_weekend_bed_end)
                    }

                    SectionTitle(R.string.settings_remote_label)
                    CheckRow(remoteOn, { remoteOn = it }, R.string.settings_remote_enable)
                    val remoteTs = st.getOr(Keys.REMOTE_LAST_TS, 0L)
                    val remoteText = when {
                        remoteTs == 0L -> stringResource(R.string.settings_remote_none)
                        else -> {
                            val whenText = java.text.DateFormat.getDateTimeInstance(
                                java.text.DateFormat.SHORT, java.text.DateFormat.SHORT
                            ).format(java.util.Date(remoteTs))
                            if (st.getOr(Keys.REMOTE_LAST_OK, false)) stringResource(R.string.settings_remote_ok, whenText)
                            else stringResource(R.string.settings_remote_bad, whenText)
                        }
                    }
                    Text(remoteText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

                    SectionTitle(R.string.settings_quiz_label)
                    CheckRow(quizOn, { quizOn = it }, R.string.settings_quiz_enable)
                    CheckRow(quizSoundOnly, { quizSoundOnly = it }, R.string.settings_quiz_sound_only)
                    CheckRow(quizPhonics, { quizPhonics = it }, R.string.settings_quiz_phonics)
                    CheckRow(matchOn, { matchOn = it }, R.string.settings_match_enable)
                    SectionTitle(R.string.settings_quiz_language)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = quizLang == QuizLang.AR,
                            onClick = { quizLang = QuizLang.AR },
                            label = { Text(stringResource(R.string.lang_arabic)) }
                        )
                        FilterChip(
                            selected = quizLang == QuizLang.EN,
                            onClick = { quizLang = QuizLang.EN },
                            label = { Text(stringResource(R.string.lang_english)) }
                        )
                    }
                    SectionTitle(R.string.settings_quiz_level)
                    for ((level, label) in listOf(
                        QuizLevel.BABY to R.string.level_baby,
                        QuizLevel.TODDLER to R.string.level_toddler,
                        QuizLevel.OLDER to R.string.level_older
                    )) {
                        Row(
                            Modifier.fillMaxWidth().clickable { quizLevel = level },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = quizLevel == level, onClick = { quizLevel = level })
                            Text(stringResource(label), modifier = Modifier.weight(1f))
                        }
                    }
                    SectionTitle(R.string.settings_quiz_decks)
                    for ((deck, label) in listOf(
                        DeckId.AR_LETTERS to R.string.deck_arletters,
                        DeckId.EN_LETTERS to R.string.deck_enletters,
                        DeckId.NUMBERS to R.string.deck_numbers,
                        DeckId.COUNTING to R.string.deck_counting
                    )) {
                        CheckRow(
                            checked = deck in quizDecks,
                            onChange = { on -> quizDecks = if (on) quizDecks + deck else quizDecks - deck },
                            labelRes = label
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onQuizEditor, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_quiz_edit))
                    }

                    SectionTitle(R.string.settings_pin_label)
                    OutlinedButton(onClick = onPin, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (PinStore.hasPin(ctx)) R.string.settings_pin_change else R.string.settings_pin_set))
                    }

                    SectionTitle(R.string.settings_actions)
                    OutlinedButton(onClick = onClearStorage, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_clear_storage))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onDashboard, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_view_dashboard))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onUpdates, modifier = Modifier.fillMaxWidth()) {
                        Text("⬇️  " + stringResource(R.string.updates_title))
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    Button(onClick = ::save) { Text(stringResource(R.string.action_save)) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(res: Int) {
    Text(
        stringResource(res),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, labelRes: Int, numeric: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text)
    )
}

@Composable
private fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, labelRes: Int) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(stringResource(labelRes), modifier = Modifier.weight(1f))
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// PIN management
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun PinMenuDialog(onDismiss: () -> Unit, onCreate: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_menu_title)) },
        text = {
            Column {
                TextButton(onClick = onCreate) { Text(stringResource(R.string.pin_set_new)) }
                if (PinStore.hasPin(ctx)) {
                    TextButton(onClick = {
                        PinStore.removePin(ctx)
                        Toast.makeText(ctx, ctx.getString(R.string.pin_removed), Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }) { Text(stringResource(R.string.pin_remove)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
fun PinCreateDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_create_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin1, onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin1 = it },
                    label = { Text(stringResource(R.string.pin_new_hint)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin2, onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin2 = it },
                    label = { Text(stringResource(R.string.pin_confirm_hint)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pin1.length != 4 || !pin1.all(Char::isDigit) ->
                        Toast.makeText(ctx, ctx.getString(R.string.pin_err_digits), Toast.LENGTH_SHORT).show()
                    pin1 != pin2 ->
                        Toast.makeText(ctx, ctx.getString(R.string.pin_err_match), Toast.LENGTH_SHORT).show()
                    else -> {
                        PinStore.setPin(ctx, pin1)
                        Toast.makeText(ctx, ctx.getString(R.string.pin_saved), Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
fun ClearStorageDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_title)) },
        text = { Text(stringResource(R.string.clear_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_clear)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

// ═════════════════════════════════════════════════════════════════════════════
// Images: category picker
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun CategoryPickerDialog(onPick: (displayName: String, syncCategory: String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val options = listOf(
        R.string.cat_animals to "animals",
        R.string.cat_letters to "letters",
        R.string.cat_veg to "veg&fruits",
        R.string.cat_other to "other"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.picker_title)) },
        text = {
            Column {
                options.forEach { (labelRes, category) ->
                    TextButton(onClick = { onPick(ctx.getString(labelRes), category) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(labelRes), fontSize = 17.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
