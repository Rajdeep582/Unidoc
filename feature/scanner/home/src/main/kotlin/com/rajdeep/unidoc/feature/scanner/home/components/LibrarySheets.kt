package com.rajdeep.unidoc.feature.scanner.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rajdeep.unidoc.core.model.Folder
import com.rajdeep.unidoc.core.ui.components.ConfirmDialog
import com.rajdeep.unidoc.core.ui.components.GradientButton
import com.rajdeep.unidoc.core.ui.components.NavigationRow
import com.rajdeep.unidoc.core.ui.components.ProgressRing
import com.rajdeep.unidoc.core.ui.components.UnidocBottomSheet
import com.rajdeep.unidoc.core.ui.theme.Dimens

@Composable
fun MoveToFolderSheet(folders: List<Folder>, onMove: (Long?) -> Unit, onNewFolder: (String) -> Unit, onDismiss: () -> Unit) {
    var newName by rememberSaveable { mutableStateOf<String?>(null) }
    UnidocBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 32.dp)) {
            Text(
                "Move to",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = Dimens.Gutter, vertical = Dimens.SpaceS)
            )
            NavigationRow("No folder", { onMove(null) }, icon = Icons.Rounded.FolderOff)
            folders.forEach { f -> NavigationRow(f.name, { onMove(f.id) }, icon = Icons.Rounded.Folder, value = "${f.documentCount}") }
            if (newName == null) {
                NavigationRow("New folder", { newName = "" }, icon = Icons.Rounded.CreateNewFolder)
            } else {
                Row(
                    Modifier.padding(horizontal = Dimens.Gutter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
                ) {
                    OutlinedTextField(
                        value = newName.orEmpty(),
                        onValueChange = { newName = it },
                        label = { Text("Folder name") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    )
                    GradientButton("Create", {
                        newName?.trim()?.takeIf { it.isNotEmpty() }?.let(onNewFolder)
                    }, enabled = !newName.isNullOrBlank())
                }
            }
        }
    }
}

@Composable
fun FolderOptionsSheet(folder: Folder, onRename: (String) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(folder.name) }
    var confirm by remember { mutableStateOf(false) }
    UnidocBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 32.dp)) {
            Text(
                folder.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = Dimens.Gutter, vertical = Dimens.SpaceS)
            )
            if (renaming) {
                Row(
                    Modifier.padding(horizontal = Dimens.Gutter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
                ) {
                    OutlinedTextField(name, {
                        name = it
                    }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f))
                    GradientButton("Save", {
                        onRename(name)
                        onDismiss()
                    }, enabled = name.isNotBlank())
                }
            } else {
                NavigationRow("Rename", { renaming = true }, icon = Icons.Rounded.DriveFileRenameOutline)
                NavigationRow("Delete folder", {
                    confirm = true
                }, icon = Icons.Rounded.DeleteOutline, subtitle = "Documents move out, nothing is deleted")
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = "Delete “${folder.name}”?",
            message = "Its ${folder.documentCount} documents stay in your library.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                onDelete()
                onDismiss()
            },
            onDismiss = { confirm = false }
        )
    }
}

@Composable
fun BusyOverlay(label: String?) {
    AnimatedVisibility(label != null, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ProgressRing(progress = null, size = 88.dp, strokeWidth = 8.dp)
                Spacer(Modifier.height(Dimens.SpaceL))
                Text(label.orEmpty(), style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }
    }
}
