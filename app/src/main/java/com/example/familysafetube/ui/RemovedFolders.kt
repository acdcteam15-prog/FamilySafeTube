package com.example.familysafetube.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familysafetube.ImageCategoryStore
import com.example.familysafetube.R

/**
 * Settings: picture folders that were removed from Drive but whose pictures are still on the phone. The sync never deletes
 * them by itself; the parent chooses for each one: remove its pictures from the phone, or keep them. Shows nothing when there is none.
 */
@Composable
fun RemovedFoldersSection() {
    val ctx = LocalContext.current
    var book by remember { mutableStateOf(ImageCategoryStore.load()) }
    if (book.pending.isEmpty()) return

    Text(
        stringResource(R.string.removed_folders_title),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
    )
    Text(stringResource(R.string.removed_folders_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    for (id in book.pending.sorted()) {
        val name = ImageCategoryStore.displayName(ctx, id, book)
        Text(
            stringResource(R.string.removed_folder_row, name, ImageCategoryStore.count(ctx, id)),
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                ImageCategoryStore.resolveRemoved(ctx, id, removeFromPhone = true)
                book = ImageCategoryStore.load()
            }) { Text(stringResource(R.string.removed_folder_remove), fontSize = 13.sp) }
            TextButton(onClick = {
                ImageCategoryStore.resolveRemoved(ctx, id, removeFromPhone = false)
                book = ImageCategoryStore.load()
            }) { Text(stringResource(R.string.removed_folder_keep), fontSize = 13.sp) }
        }
    }
}
