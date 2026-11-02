package com.example.familysafetube.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familysafetube.AppGraph
import com.example.familysafetube.R
import com.example.familysafetube.update.UpdatePolicy
import com.example.familysafetube.update.UpdateState

/**
 * "Update available: 1.3.0 - tap to see what is new." Shown at the top of the home screen while a newer build
 * exists. Tapping it asks for the parental PIN first (the child cannot start an install).
 */
@Composable
fun UpdateBanner(onOpen: () -> Unit) {
    val updates = AppGraph.updates
    val state by updates.state.collectAsStateWithLifecycle()
    val skipped by updates.skippedBuild.collectAsStateWithLifecycle()
    val available = (state as? UpdateState.Available)?.info ?: (state as? UpdateState.Ready)?.info
    val release = UpdatePolicy.bannerRelease(available, skipped) ?: return
    val ready = state is UpdateState.Ready

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⬇️", fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    stringResource(
                        if (ready) R.string.update_banner_ready_title else R.string.update_banner_available_title,
                        release.versionName, release.build
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(
                        when {
                            ready -> R.string.update_banner_ready_sub
                            release.required -> R.string.update_banner_required_sub
                            else -> R.string.update_banner_sub
                        }
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 13.sp
                )
            }
        }
    }
}
