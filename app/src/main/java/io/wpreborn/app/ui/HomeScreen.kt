package io.wpreborn.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.wpreborn.app.WorldViewModel
import io.wpreborn.app.data.MapSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(vm: WorldViewModel) {
    var showNewDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<MapSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<MapSummary?>(null) }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var showImportInfo by remember { mutableStateOf(false) }
    var showRestore by remember { mutableStateOf(false) }
    var hasBackup by remember { mutableStateOf(false) }

    LaunchedEffect(vm.maps) {
        hasBackup = withContext(Dispatchers.IO) { vm.repo.lastBackup() != null }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingImport = uri }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) vm.exportDatabase(uri) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 0.dp, bottom = 28.dp,
        ),
    ) {
        item {
            Column(Modifier.statusBarsPadding()) {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(Periwinkle, Color(0xFF4C6BE0)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("WP", color = Color(0xFF0A1230), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "WORLD PROVINCES",
                            style = MaterialTheme.typography.labelMedium,
                            color = Gold,
                            letterSpacing = 2.sp,
                        )
                        Text(
                            "Reborn",
                            style = MaterialTheme.typography.labelMedium,
                            color = Muted,
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text("Your worlds", style = MaterialTheme.typography.displaySmall, color = Mist)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Paint provinces, build nations, rewrite history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { showNewDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Periwinkle,
                            contentColor = Color(0xFF08122E),
                        ),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New world", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { showImportInfo = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 10.dp,
                        ),
                    ) {
                        Text("Import", fontWeight = FontWeight.Medium)
                    }
                }
                if (hasBackup) {
                    TextButton(
                        onClick = { showRestore = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Text("Restore data from the last import") }
                }
                Spacer(Modifier.height(18.dp))
            }
        }

        if (vm.maps.isEmpty()) {
            item {
                EmptyWorldsCard(onCreate = { showNewDialog = true })
            }
        } else {
            items(vm.maps, key = { it.id }) { map ->
                MapCard(
                    map = map,
                    thumbnail = rememberMapThumbnail(vm, map),
                    onOpen = { vm.openMap(map.id) },
                    onRename = { renameTarget = map },
                    onDelete = { deleteTarget = map },
                    onExport = {
                        exportLauncher.launch("worldprovinces-${stamp()}.db")
                    },
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        item {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("No ads", fontSize = 12.sp) })
                AssistChip(onClick = {}, label = { Text("Offline", fontSize = 12.sp) })
                AssistChip(onClick = {}, label = { Text("Fast on old phones", fontSize = 12.sp) })
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Your progress stays on this device. Nothing is uploaded, and there are no trackers.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
        }
    }

    if (showNewDialog) {
        NameDialog(
            title = "New world",
            label = "World name",
            initial = "World ${vm.maps.size + 1}",
            confirm = "Create",
            onDismiss = { showNewDialog = false },
            onConfirm = {
                showNewDialog = false
                vm.createMap(it)
            },
        )
    }

    renameTarget?.let { map ->
        NameDialog(
            title = "Rename world",
            label = "World name",
            initial = map.name,
            confirm = "Save",
            onDismiss = { renameTarget = null },
            onConfirm = {
                vm.renameMap(map.id, it)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { map ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${map.name}?") },
            text = { Text("The world and all of its borders will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMap(map.id)
                    deleteTarget = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }

    if (showImportInfo) {
        AlertDialog(
            onDismissRequest = { showImportInfo = false },
            title = { Text("Import a database") },
            text = {
                Text(
                    "In the original app choose Input / Backup Database to File, then " +
                        "select that file here (usually named worldprovincesdb).\n\n" +
                        "Everything is supported: worlds, countries, custom flags, province " +
                        "names, colours and borders. Your current data is backed up first."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportInfo = false
                    importLauncher.launch(arrayOf("*/*"))
                }) { Text("Choose file") }
            },
            dismissButton = { TextButton(onClick = { showImportInfo = false }) { Text("Cancel") } },
        )
    }

    if (showRestore) {
        AlertDialog(
            onDismissRequest = { showRestore = false },
            title = { Text("Restore previous data?") },
            text = { Text("The data saved automatically before your last import will be restored.") },
            confirmButton = {
                TextButton(onClick = {
                    showRestore = false
                    vm.restoreBackup()
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { showRestore = false }) { Text("Cancel") } },
        )
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Replace current data?") },
            text = { Text("The selected database will replace everything currently stored in the app.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = uri
                    pendingImport = null
                    vm.importDatabase(target) { }
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
}

/** Renders at most the thumbnails that are actually on screen. */
private val thumbnailCache = object : android.util.LruCache<String, Bitmap>(24) {}

@Composable
private fun rememberMapThumbnail(vm: WorldViewModel, map: MapSummary): Bitmap? {
    val key = "${map.id}:${map.dti}"
    val state = androidx.compose.runtime.produceState<Bitmap?>(
        initialValue = thumbnailCache.get(key),
        key1 = key,
    ) {
        if (value != null) return@produceState
        val bitmap = withContext(Dispatchers.IO) { vm.loadThumbnail(map.id, 420, 280) }
        if (bitmap != null) thumbnailCache.put(key, bitmap)
        value = bitmap
    }
    return state.value
}

@Composable
private fun MapCard(
    map: MapSummary,
    thumbnail: Bitmap?,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceNight,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 104.dp, height = 74.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0E1729)),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text("◍", color = Muted, fontSize = 26.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    map.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Mist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Edited ${shortDate(map.dti)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Open", style = MaterialTheme.typography.labelLarge, color = Periwinkle)
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = Periwinkle,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Muted)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = {
                        menu = false; onRename()
                    })
                    DropdownMenuItem(text = { Text("Export database") }, onClick = {
                        menu = false; onExport()
                    })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = {
                        menu = false; onDelete()
                    })
                }
            }
        }
    }
}

@Composable
private fun EmptyWorldsCard(onCreate: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = SurfaceNight,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp)) {
            Text("No worlds yet", style = MaterialTheme.typography.titleLarge, color = Mist)
            Spacer(Modifier.height(6.dp))
            Text(
                "Create a fresh world, or import the database from the original app to continue " +
                    "exactly where you left off.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
            Spacer(Modifier.height(14.dp))
            TextButton(onClick = onCreate) { Text("Create the first world") }
        }
    }
}

@Composable
fun NameDialog(
    title: String,
    label: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember {
        mutableStateOf(
            androidx.compose.ui.text.input.TextFieldValue(
                initial,
                androidx.compose.ui.text.TextRange(initial.length),
            )
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (value.text.isNotBlank()) onConfirm(value.text.trim())
            }) {
                Text(confirm)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun shortDate(millis: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))

private fun stamp(): String =
    SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

@Composable
private fun Modifier.statusBarsPadding(): Modifier =
    this.then(Modifier.padding(top = 0.dp))
