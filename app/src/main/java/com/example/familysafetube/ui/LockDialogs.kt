package com.example.familysafetube.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.familysafetube.LockPolicy
import com.example.familysafetube.R

/**
 * Shown after the parent PIN on a "time is up" or bedtime lock: add extra minutes, or open Settings (to change the
 * limits), or leave it locked. A manual lock needs none of this: the PIN simply opens it.
 */
@Composable
fun GrantTimeDialog(onGrant: (minutes: Int) -> Unit, onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AppLogo(Modifier.size(56.dp)) },
        title = { Text(stringResource(R.string.lock_grant_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (minutes in LockPolicy.GRANT_CHOICES) {
                    Button(onClick = { onGrant(minutes) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.lock_grant_option, minutes))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.btn_settings)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_now)) } }
    )
}

/**
 * "Lock now": the parent (or anyone) picks how long the app stays covered by the padlock. Locking needs no check
 * (it only takes things away); opening it again always asks the parent.
 */
@Composable
fun LockChoiceDialog(onPick: (minutes: Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AppLogo(Modifier.size(56.dp)) },
        title = { Text(stringResource(R.string.lock_choice_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (minutes in LockPolicy.LOCK_CHOICES) {
                    Button(onClick = { onPick(minutes) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (minutes == 0) stringResource(R.string.lock_choice_manual)
                            else stringResource(R.string.lock_choice_minutes, minutes)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
