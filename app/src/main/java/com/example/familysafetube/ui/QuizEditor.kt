package com.example.familysafetube.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.familysafetube.AudioSlot
import com.example.familysafetube.AudioStep
import com.example.familysafetube.CardOverride
import com.example.familysafetube.ImportResult
import com.example.familysafetube.QuizAudio
import com.example.familysafetube.QuizCard
import com.example.familysafetube.QuizCatalog
import com.example.familysafetube.QuizLang
import com.example.familysafetube.QuizRecorder
import com.example.familysafetube.QuizRepository
import com.example.familysafetube.R
import com.example.familysafetube.withNames

/** List of every quiz card (built from the synced pictures). Tap one to edit it. */
@Composable
fun QuizEditorDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val cards = remember(version) { QuizRepository.loadCards(ctx) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var showPraise by remember { mutableStateOf(false) }
    var showFamily by remember { mutableStateOf(false) }

    val audio = remember { QuizAudio(ctx) }
    DisposableEffect(Unit) { onDispose { audio.release() } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.94f).fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.editor_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.editor_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showPraise = true }) { Text(stringResource(R.string.editor_praise_button)) }
                    OutlinedButton(onClick = { showFamily = true }) { Text(stringResource(R.string.editor_family_button)) }
                    TextButton(onClick = { QuizRepository.clearAllFocus(); version++ }) { Text(stringResource(R.string.editor_clear_focus)) }
                }
                Spacer(Modifier.height(4.dp))
                if (cards.isEmpty()) {
                    Text(stringResource(R.string.editor_empty), modifier = Modifier.weight(1f).padding(16.dp))
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(cards, key = { it.id }) { card ->
                            CardRow(
                                card = card,
                                onClick = { editingId = card.id },
                                onFocusToggle = { QuizRepository.setFocus(card.id, !card.focus); version++ },
                                onEnabledChange = { on ->
                                    QuizRepository.updateOverride(card.id) { it.copy(enabled = on) }
                                    version++
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.editor_done)) }
            }
        }
    }

    editingId?.let { id ->
        cards.firstOrNull { it.id == id }?.let { card ->
            CardEditDialog(
                card = card, audio = audio,
                onChanged = { version++ },
                onDismiss = { editingId = null },
                onDeleted = { editingId = null; version++ }
            )
        }
    }
    if (showPraise) PraiseVoicesDialog(onDismiss = { showPraise = false })
    if (showFamily) AddFamilyDialog(onDismiss = { showFamily = false }, onSaved = { version++ })
}

@Composable
private fun CardRow(card: QuizCard, onClick: () -> Unit, onFocusToggle: () -> Unit, onEnabledChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CardArtView(card, Modifier.size(56.dp).clip(MaterialTheme.shapes.medium))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(card.nameAr, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(card.nameEn, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            val badges = buildString {
                if (card.hasSound) append("🔊 ")
                if (card.voiceArFile != null || card.voiceEnFile != null) append("🎙 ")
            }
            if (badges.isNotEmpty()) Text(badges, fontSize = 12.sp)
            if (card.isFamily) Text("👪 " + stringResource(R.string.family_badge), fontSize = 12.sp)
            if (card.needsArabicName) {
                Text("⚠ " + stringResource(R.string.editor_needs_arabic), color = Brand.StatusAmber, fontSize = 12.sp)
            }
        }
        val focusLabel = stringResource(if (card.focus) R.string.editor_focus_on else R.string.editor_focus_off)
        TextButton(onClick = onFocusToggle, modifier = Modifier.semantics { contentDescription = focusLabel }) {
            Text(
                if (card.focus) "★" else "☆", fontSize = 22.sp,
                color = if (card.focus) Brand.StatusAmber else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val label = stringResource(R.string.editor_enabled)
        Switch(
            checked = card.enabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.semantics { contentDescription = label }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// One card
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun CardEditDialog(card: QuizCard, audio: QuizAudio, onChanged: () -> Unit, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val entry = remember(card.id) { QuizCatalog.lookup(card.labelEn) }
    val catalogHasSound = entry?.soundKey != null

    var revision by remember { mutableIntStateOf(0) }
    val current = remember(revision) { QuizRepository.overrides()[card.id] ?: CardOverride() }

    var nameAr by remember { mutableStateOf(if (card.needsArabicName) "" else card.nameAr) }
    var nameEn by remember { mutableStateOf(card.nameEn) }
    var useBuiltIn by remember { mutableStateOf(current.useBuiltInSound) }

    // Recording state: which slot is recording, and which slot is waiting for the permission dialog
    var autoStopTick by remember { mutableIntStateOf(0) }      // bumped when the 8 s cap ends a recording
    val recorder = remember { QuizRecorder(ctx, onMaxReached = { autoStopTick++ }) }
    var recordingSlot by remember { mutableStateOf<AudioSlot?>(null) }
    var recordingFile by remember { mutableStateOf<String?>(null) }
    var pendingSlot by remember { mutableStateOf<AudioSlot?>(null) }
    var importSlot by remember { mutableStateOf<AudioSlot?>(null) }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

    /** Points [slot] at [fileName] (deleting whatever it pointed at before). */
    fun attach(slot: AudioSlot, fileName: String?) {
        val old = slot.fileOf(QuizRepository.overrides()[card.id] ?: CardOverride())
        QuizRepository.updateOverride(card.id) { slot.with(it, fileName) }
        if (old != fileName) QuizRepository.deleteAudio(ctx, old)
        revision++
        onChanged()
    }

    fun startRecording(slot: AudioSlot) {
        val name = QuizRepository.newAudioFileName(card.id, slot)
        audio.stop()
        if (recorder.start(QuizRepository.audioFile(ctx, name))) {
            recordingSlot = slot
            recordingFile = name
        } else {
            Toast.makeText(ctx, ctx.getString(R.string.editor_record_failed), Toast.LENGTH_SHORT).show()
        }
    }

    fun stopRecording() {
        val slot = recordingSlot
        val name = recordingFile
        recordingSlot = null
        recordingFile = null
        if (slot == null || name == null) return
        if (recorder.stop()) attach(slot, name)
        else Toast.makeText(ctx, ctx.getString(R.string.editor_record_failed), Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(autoStopTick) { if (autoStopTick > 0) stopRecording() }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val slot = pendingSlot
        pendingSlot = null
        if (granted && slot != null) startRecording(slot)
        else Toast.makeText(ctx, ctx.getString(R.string.editor_mic_denied), Toast.LENGTH_SHORT).show()
    }

    fun toggleRecording(slot: AudioSlot) {
        if (recordingSlot == slot) { stopRecording(); return }
        if (recordingSlot != null) return                      // one recording at a time
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) startRecording(slot) else { pendingSlot = slot; micPermission.launch(Manifest.permission.RECORD_AUDIO) }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val slot = importSlot
        importSlot = null
        if (uri != null && slot != null) {
            val name = QuizRepository.newAudioFileName(card.id, slot)
            when (QuizRepository.importAudio(ctx, uri, QuizRepository.audioFile(ctx, name))) {
                ImportResult.OK -> attach(slot, name)
                ImportResult.TOO_BIG -> Toast.makeText(ctx, ctx.getString(R.string.editor_import_too_big), Toast.LENGTH_SHORT).show()
                ImportResult.FAILED -> Toast.makeText(ctx, ctx.getString(R.string.editor_import_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveAndClose() {
        QuizRepository.updateOverride(card.id) {
            it.withNames(nameAr, nameEn, defaultEnglish = card.labelEn, defaultArabic = entry?.ar)
                .copy(useBuiltInSound = useBuiltIn)
        }
        onChanged()
        onDismiss()
    }

    Dialog(onDismissRequest = ::saveAndClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.94f).heightIn(max = 680.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(16.dp)) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CardArtView(card, Modifier.size(72.dp).clip(MaterialTheme.shapes.medium))
                        Spacer(Modifier.width(12.dp))
                        Text(card.labelEn, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))

                    NameField(nameAr, { nameAr = it }, R.string.editor_name_ar) {
                        audio.play(listOf(AudioStep.Tts(nameAr.ifBlank { card.nameAr }, QuizLang.AR)))
                    }
                    Spacer(Modifier.height(8.dp))
                    NameField(nameEn, { nameEn = it }, R.string.editor_name_en) {
                        audio.play(listOf(AudioStep.Tts(nameEn.ifBlank { card.labelEn }, QuizLang.EN)))
                    }

                    // ── animal sound ──
                    Spacer(Modifier.height(16.dp))
                    if (catalogHasSound && current.soundFile == null) {
                        Row(
                            Modifier.fillMaxWidth().clickable { useBuiltIn = !useBuiltIn },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = useBuiltIn, onCheckedChange = { useBuiltIn = it })
                            Text(stringResource(R.string.editor_use_builtin), modifier = Modifier.weight(1f))
                        }
                    }
                    val builtInKey = if (catalogHasSound && useBuiltIn && current.soundFile == null) entry?.soundKey else null
                    SlotRow(
                        title = R.string.editor_sound,
                        fileName = current.soundFile,
                        builtInKey = builtInKey,
                        isRecording = recordingSlot == AudioSlot.SOUND,
                        busy = recordingSlot != null && recordingSlot != AudioSlot.SOUND,
                        onPlay = {
                            val step = current.soundFile?.let { AudioStep.RecordedFile(it) } ?: builtInKey?.let { AudioStep.BuiltIn(it) }
                            if (step != null) audio.play(listOf(step))
                        },
                        onRecord = { toggleRecording(AudioSlot.SOUND) },
                        onImport = { importSlot = AudioSlot.SOUND; importer.launch("audio/*") },
                        onRemove = { attach(AudioSlot.SOUND, null) }
                    )

                    // ── the parent's own voice saying the names ──
                    for ((slot, title) in listOf(
                        AudioSlot.VOICE_AR to R.string.editor_voice_ar,
                        AudioSlot.VOICE_EN to R.string.editor_voice_en
                    )) {
                        SlotRow(
                            title = title,
                            fileName = slot.fileOf(current),
                            builtInKey = null,
                            isRecording = recordingSlot == slot,
                            busy = recordingSlot != null && recordingSlot != slot,
                            onPlay = { slot.fileOf(current)?.let { audio.play(listOf(AudioStep.RecordedFile(it))) } },
                            onRecord = { toggleRecording(slot) },
                            onImport = { importSlot = slot; importer.launch("audio/*") },
                            onRemove = { attach(slot, null) }
                        )
                    }
                }
                if (card.isFamily) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.family_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = ::saveAndClose) { Text(stringResource(R.string.editor_done)) }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.family_delete)) },
            text = { Text(stringResource(R.string.family_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    QuizRepository.deleteFamilyCard(ctx, card.id)
                    onChanged()
                    onDeleted()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit, labelRes: Int, onPlay: () -> Unit) {
    val playLabel = stringResource(R.string.editor_play)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(stringResource(labelRes)) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onPlay, modifier = Modifier.semantics { contentDescription = playLabel }) { Text("▶", fontSize = 20.sp) }
    }
}

/** One audio attachment: shows where the sound comes from, with play / record / import / remove. */
@Composable
private fun SlotRow(
    title: Int,
    fileName: String?,
    builtInKey: String?,
    isRecording: Boolean,
    busy: Boolean,
    onPlay: () -> Unit,
    onRecord: () -> Unit,
    onImport: () -> Unit,
    onRemove: () -> Unit
) {
    val hasAudio = fileName != null || builtInKey != null
    val source = when {
        fileName != null -> R.string.editor_source_mine
        builtInKey != null -> R.string.editor_source_builtin
        else -> R.string.editor_source_none
    }
    val playLabel = stringResource(R.string.editor_play)
    val recordLabel = stringResource(if (isRecording) R.string.editor_stop else R.string.editor_record)
    val importLabel = stringResource(R.string.editor_import)
    val removeLabel = stringResource(R.string.editor_remove)

    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(stringResource(title), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(
            stringResource(if (isRecording) R.string.editor_recording else source),
            color = if (isRecording) Brand.StatusRed else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = onPlay, enabled = hasAudio && !isRecording, modifier = Modifier.semantics { contentDescription = playLabel }) { Text("▶", fontSize = 20.sp) }
            TextButton(onClick = onRecord, enabled = !busy, modifier = Modifier.semantics { contentDescription = recordLabel }) { Text(if (isRecording) "⏹" else "⏺", fontSize = 20.sp) }
            TextButton(onClick = onImport, enabled = !busy && !isRecording, modifier = Modifier.semantics { contentDescription = importLabel }) { Text("📂", fontSize = 20.sp) }
            TextButton(onClick = onRemove, enabled = fileName != null && !isRecording, modifier = Modifier.semantics { contentDescription = removeLabel }) { Text("🗑", fontSize = 20.sp) }
        }
    }
}
