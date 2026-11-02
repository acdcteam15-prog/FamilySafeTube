package com.example.familysafetube.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familysafetube.AppGraph
import com.example.familysafetube.Keys
import com.example.familysafetube.R
import com.example.familysafetube.getOr
import com.example.familysafetube.update.ReleaseInfo
import com.example.familysafetube.update.UpdateInstallResult
import com.example.familysafetube.update.UpdateParser
import com.example.familysafetube.update.UpdateState

/**
 * Settings -> App updates: check GitHub for a newer build, read what's new, download it and install it.
 * Only reachable behind the parental PIN (from Settings, the home banner or the notification).
 */
@Composable
fun UpdatesDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val updates = AppGraph.updates
    val state by updates.state.collectAsStateWithLifecycle()
    val installFailure by UpdateInstallResult.failure.collectAsStateWithLifecycle()

    var askAllow by remember { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var auto by remember { mutableStateOf(updates.autoCheck) }
    var autoDownload by remember { mutableStateOf(updates.autoDownload) }
    var tokenText by rememberSaveable { mutableStateOf(updates.token) }

    // Android 13+: ask once, here, to be allowed to show the "update available" notification.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Opening the screen counts as asking: look right away (unless a result is already on screen).
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !AppGraph.settings.getOr(Keys.UPDATE_ASKED_NOTIFICATIONS, false)
        ) {
            AppGraph.settings.edit { it[Keys.UPDATE_ASKED_NOTIFICATIONS] = true }
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        updates.restoreReady()
        if (updates.state.value is UpdateState.Idle) updates.check()
    }
    // The download finished: hand it to Android's installer (it shows its own confirmation).
    LaunchedEffect(state) {
        val s = state
        if (s is UpdateState.Ready) {
            if (updates.canInstall()) updates.install(s.file) else askAllow = true
        }
    }

    fun startDownload(info: ReleaseInfo) {
        if (updates.canInstall()) updates.downloadAsync(info) else askAllow = true
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.94f).heightIn(max = 680.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.updates_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppLogo(Modifier.size(56.dp))
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    stringResource(R.string.updates_current, stringResource(R.string.app_name), updates.currentVersion),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(stringResource(R.string.updates_build, updates.currentBuild), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                Text(stringResource(R.string.updates_source, updates.repo), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                    }

                    when (val s = state) {
                        UpdateState.Idle -> Unit
                        UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.padding(end = 12.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.updates_checking))
                        }
                        is UpdateState.UpToDate -> Text(
                            stringResource(R.string.updates_up_to_date, stringResource(R.string.app_name), s.versionName)
                        )
                        is UpdateState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(s.message, color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = { updates.checkAsync() }) { Text(stringResource(R.string.updates_try_again)) }
                        }
                        is UpdateState.Available -> AvailableCard(
                            info = s.info,
                            onInstall = { startDownload(s.info) },
                            onSkip = if (!s.info.required) ({ updates.skip(s.info) }) else null
                        )
                        is UpdateState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.updates_downloading, s.info.versionName), fontWeight = FontWeight.SemiBold)
                            if (s.fraction >= 0f) {
                                LinearProgressIndicator(progress = { s.fraction }, modifier = Modifier.fillMaxWidth())
                                Text("${(s.fraction * 100).toInt()}%", fontSize = 12.sp)
                            } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        is UpdateState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.updates_ready, s.info.versionName), fontWeight = FontWeight.SemiBold)
                            Button(
                                onClick = { if (updates.canInstall()) updates.install(s.file) else askAllow = true },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.updates_install)) }
                        }
                    }

                    installFailure?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    if (state !is UpdateState.Checking && state !is UpdateState.Downloading) {
                        OutlinedButton(onClick = { updates.checkAsync() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.updates_check_now))
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.updates_auto))
                            Text(stringResource(R.string.updates_auto_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Switch(checked = auto, onCheckedChange = { auto = it; updates.autoCheck = it }, modifier = Modifier.padding(start = 12.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.updates_wifi))
                            Text(stringResource(R.string.updates_wifi_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Switch(checked = autoDownload, onCheckedChange = { autoDownload = it; updates.autoDownload = it }, modifier = Modifier.padding(start = 12.dp))
                    }

                    TextButton(onClick = { advanced = !advanced }) {
                        Text(stringResource(if (advanced) R.string.updates_hide_advanced else R.string.updates_advanced))
                    }
                    if (advanced) {
                        OutlinedTextField(
                            value = tokenText,
                            onValueChange = { tokenText = it; updates.token = it },
                            label = { Text(stringResource(R.string.updates_token_label)) },
                            supportingText = { Text(stringResource(R.string.updates_token_help)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(12.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_close)) }
            }
        }
    }

    if (askAllow) {
        AlertDialog(
            onDismissRequest = { askAllow = false },
            title = { Text(stringResource(R.string.updates_allow_title, stringResource(R.string.app_name))) },
            text = { Text(stringResource(R.string.updates_allow_text)) },
            confirmButton = {
                TextButton(onClick = { askAllow = false; ctx.startActivity(updates.allowInstallIntent()) }) {
                    Text(stringResource(R.string.updates_open_settings))
                }
            },
            dismissButton = { TextButton(onClick = { askAllow = false; updates.reset() }) { Text(stringResource(R.string.action_not_now)) } }
        )
    }
}

@Composable
private fun AvailableCard(info: ReleaseInfo, onInstall: () -> Unit, onSkip: (() -> Unit)?) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.update_banner_available_title, info.versionName, info.build),
                color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold
            )
            val size = UpdateParser.humanSize(info.sizeBytes)
            if (size.isNotEmpty()) Text(size, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 12.sp)
            if (info.required) Text(stringResource(R.string.updates_required), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            if (info.notes.isNotBlank()) {
                Text(stringResource(R.string.updates_whats_new), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                Text(info.notes, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 14.sp)
            }
            Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.updates_download_install)) }
            if (onSkip != null) {
                OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.updates_skip)) }
            }
        }
    }
}
