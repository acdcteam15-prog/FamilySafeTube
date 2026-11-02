package com.example.familysafetube.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.example.familysafetube.AudioStep
import com.example.familysafetube.ImportResult
import com.example.familysafetube.PraiseEvent
import com.example.familysafetube.PraiseSet
import com.example.familysafetube.QuizAudio
import com.example.familysafetube.QuizRecorder
import com.example.familysafetube.QuizRepository
import com.example.familysafetube.R

private fun PraiseEvent.titleRes(): Int = when (this) {
    PraiseEvent.CORRECT -> R.string.praise_correct
    PraiseEvent.TRY_AGAIN -> R.string.praise_try_again
    PraiseEvent.ROUND_DONE -> R.string.praise_round_done
}

/**
 * The parent's own voice for the quiz's reactions: up to 3 short clips for "right", "wrong" and
 * "round finished". A recorded clip plays instead of the synthesised beep; one is picked at random.
 */
@Composable
fun PraiseVoicesDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val praise: PraiseSet = remember(revision) { QuizRepository.praise() }

    val audio = remember { QuizAudio(ctx) }
    DisposableEffect(Unit) { onDispose { audio.release() } }

    var autoStopTick by remember { mutableIntStateOf(0) }          // bumped when the 8 s cap ends a recording
    val recorder = remember { QuizRecorder(ctx, onMaxReached = { autoStopTick++ }) }
    var recordingEvent by remember { mutableStateOf<PraiseEvent?>(null) }
    var recordingFile by remember { mutableStateOf<String?>(null) }
    var pendingEvent by remember { mutableStateOf<PraiseEvent?>(null) }
    var importEvent by remember { mutableStateOf<PraiseEvent?>(null) }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

    fun attach(event: PraiseEvent, fileName: String) {
        QuizRepository.updatePraise { it.add(event, fileName) }
        revision++
    }

    fun startRecording(event: PraiseEvent) {
        val name = QuizRepository.newPraiseFileName(event)
        audio.stop()
        if (recorder.start(QuizRepository.audioFile(ctx, name))) {
            recordingEvent = event
            recordingFile = name
        } else {
            Toast.makeText(ctx, ctx.getString(R.string.editor_record_failed), Toast.LENGTH_SHORT).show()
        }
    }

    fun stopRecording() {
        val event = recordingEvent
        val name = recordingFile
        recordingEvent = null
        recordingFile = null
        if (event == null || name == null) return
        if (recorder.stop()) attach(event, name)
        else Toast.makeText(ctx, ctx.getString(R.string.editor_record_failed), Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(autoStopTick) { if (autoStopTick > 0) stopRecording() }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val event = pendingEvent
        pendingEvent = null
        if (granted && event != null) startRecording(event)
        else Toast.makeText(ctx, ctx.getString(R.string.editor_mic_denied), Toast.LENGTH_SHORT).show()
    }

    fun toggleRecording(event: PraiseEvent) {
        if (recordingEvent == event) { stopRecording(); return }
        if (recordingEvent != null) return
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) startRecording(event) else { pendingEvent = event; micPermission.launch(Manifest.permission.RECORD_AUDIO) }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val event = importEvent
        importEvent = null
        if (uri != null && event != null) {
            val name = QuizRepository.newPraiseFileName(event)
            when (QuizRepository.importAudio(ctx, uri, QuizRepository.audioFile(ctx, name))) {
                ImportResult.OK -> attach(event, name)
                ImportResult.TOO_BIG -> Toast.makeText(ctx, ctx.getString(R.string.editor_import_too_big), Toast.LENGTH_SHORT).show()
                ImportResult.FAILED -> Toast.makeText(ctx, ctx.getString(R.string.editor_import_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.94f).heightIn(max = 680.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.praise_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.praise_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )

                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    for (event in PraiseEvent.values()) {
                        val clips = praise.clipsFor(event)
                        val isRecording = recordingEvent == event
                        val full = praise.isFull(event)
                        val playLabel = stringResource(R.string.editor_play)
                        val removeLabel = stringResource(R.string.editor_remove)
                        val recordLabel = stringResource(if (isRecording) R.string.editor_stop else R.string.editor_record)
                        val importLabel = stringResource(R.string.editor_import)

                        Text(
                            stringResource(event.titleRes()), fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                        if (clips.isEmpty() && !isRecording) {
                            Text(stringResource(R.string.praise_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        if (isRecording) {
                            Text(stringResource(R.string.editor_recording), color = Brand.StatusRed, fontSize = 12.sp)
                        }
                        clips.forEachIndexed { index, file ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.praise_clip, index + 1), modifier = Modifier.weight(1f))
                                TextButton(
                                    onClick = { audio.play(listOf(AudioStep.RecordedFile(file))) },
                                    enabled = !isRecording,
                                    modifier = Modifier.semantics { contentDescription = playLabel }
                                ) { Text("▶", fontSize = 20.sp) }
                                TextButton(
                                    onClick = {
                                        QuizRepository.updatePraise { it.remove(event, file) }
                                        QuizRepository.deleteAudio(ctx, file)
                                        revision++
                                    },
                                    enabled = !isRecording,
                                    modifier = Modifier.semantics { contentDescription = removeLabel }
                                ) { Text("🗑", fontSize = 20.sp) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = { toggleRecording(event) },
                                enabled = isRecording || (!full && recordingEvent == null),
                                modifier = Modifier.semantics { contentDescription = recordLabel }
                            ) { Text(if (isRecording) "⏹" else "⏺", fontSize = 20.sp) }
                            TextButton(
                                onClick = { importEvent = event; importer.launch("audio/*") },
                                enabled = !full && recordingEvent == null,
                                modifier = Modifier.semantics { contentDescription = importLabel }
                            ) { Text("📂", fontSize = 20.sp) }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.editor_done)) }
            }
        }
    }
}
