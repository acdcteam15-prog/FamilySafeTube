package com.example.familysafetube.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.familysafetube.R

/**
 * The lock screen: it replaces the whole app (nothing of the app is even drawn behind it) and shows only a padlock
 * on a plain dark screen. Tapping the padlock asks for the parent PIN. The only words are the invisible description
 * read out by a screen reader.
 */
@Composable
fun LockOverlay(onTap: () -> Unit) {
    val description = stringResource(R.string.lock_description)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .clickable(role = Role.Button, onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_lock),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            alpha = 0.55f
        )
    }
}
