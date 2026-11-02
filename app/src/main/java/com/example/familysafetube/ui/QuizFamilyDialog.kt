package com.example.familysafetube.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.familysafetube.FamilyCard
import com.example.familysafetube.FamilyPhotos
import com.example.familysafetube.QuizFamilyStore
import com.example.familysafetube.QuizRepository
import com.example.familysafetube.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Makes a card from the parent's own photo (family, a pet, the family car...). Take a photo with the
 * camera app or pick one from the gallery, type the name, save. The voice is recorded afterwards by
 * tapping the card in the editor. The photo is straightened, shrunk and stored privately in the app.
 */
@Composable
fun AddFamilyDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var tempPhoto by remember { mutableStateOf<File?>(null) }       // the processed photo, not saved yet
    var nameAr by remember { mutableStateOf("") }
    var nameEn by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    // if the dialog closes without saving, the temporary photo must not be left behind
    DisposableEffect(Unit) { onDispose { if (!saved) tempPhoto?.delete() } }

    fun process(uri: Uri) {
        scope.launch {
            val target = File(ctx.cacheDir, "family_new_${System.currentTimeMillis()}.jpg")
            val ok = withContext(Dispatchers.IO) { FamilyPhotos.importPhoto(ctx, uri, target) }
            if (ok) {
                tempPhoto?.delete()
                tempPhoto = target
            } else {
                Toast.makeText(ctx, ctx.getString(R.string.family_photo_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) process(uri)
    }

    var captureUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = captureUri
        captureUri = null
        if (success && uri != null) process(uri)
    }

    fun takePhoto() {
        try {
            val dir = File(ctx.cacheDir, "quiz_capture").also { it.mkdirs() }
            val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            captureUri = uri
            camera.launch(uri)
        } catch (e: Exception) {
            captureUri = null
            Toast.makeText(ctx, ctx.getString(R.string.family_no_camera), Toast.LENGTH_SHORT).show()
        }
    }

    fun save() {
        val photo = tempPhoto ?: return
        val id = QuizFamilyStore.newId(System.currentTimeMillis())
        val fileName = "$id.jpg"
        val dest = FamilyPhotos.file(ctx, fileName)
        val copied = try { photo.copyTo(dest, overwrite = true); true } catch (e: Exception) { false }
        if (!copied) {
            Toast.makeText(ctx, ctx.getString(R.string.family_photo_failed), Toast.LENGTH_SHORT).show()
            return
        }
        QuizRepository.addFamilyCard(FamilyCard(id, fileName, nameAr.trim(), nameEn.trim()))
        photo.delete()
        saved = true
        onSaved()
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.94f).heightIn(max = 680.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.family_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.family_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val photo = tempPhoto
                        if (photo != null) {
                            PathImage(photo.absolutePath, Modifier.size(200.dp).clip(MaterialTheme.shapes.large))
                        } else {
                            Box(
                                Modifier.size(200.dp).clip(MaterialTheme.shapes.large)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) { Text("🖼️", fontSize = 56.sp) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        OutlinedButton(onClick = ::takePhoto) { Text(stringResource(R.string.family_take)) }
                        OutlinedButton(onClick = {
                            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text(stringResource(R.string.family_choose)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = nameAr, onValueChange = { nameAr = it }, singleLine = true,
                        label = { Text(stringResource(R.string.editor_name_ar)) }, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nameEn, onValueChange = { nameEn = it }, singleLine = true,
                        label = { Text(stringResource(R.string.editor_name_en)) }, modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    Button(
                        onClick = ::save,
                        enabled = tempPhoto != null && (nameAr.isNotBlank() || nameEn.isNotBlank())
                    ) { Text(stringResource(R.string.family_save)) }
                }
            }
        }
    }
}
