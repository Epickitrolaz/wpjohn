package io.wpreborn.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.wpreborn.app.WorldViewModel
import io.wpreborn.app.data.Colors
import io.wpreborn.app.data.CountryState
import io.wpreborn.app.data.MapRuntime
import io.wpreborn.app.map.MapView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(vm: WorldViewModel) {
    val runtime = vm.runtime ?: return
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showCountries by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var editCountry by remember { mutableStateOf<CountryState?>(null) }
    var showNewCountry by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var renameProvince by remember { mutableStateOf<Int?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) vm.exportDatabase(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingImport = uri }

    Box(Modifier.fillMaxSize().background(Color(0xFF070D1A))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).also { view ->
                    view.onProvinceTap = { index -> vm.tapProvince(index) }
                    view.onLongPress = { index -> vm.selectProvince(index) }
                    mapView = view
                }
            },
            update = { view ->
                view.updateState(runtime.colors, vm.selectedProvince)
            },
        )

        // top bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xE60A1120), Color(0x990A1120), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.closeMap() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Mist)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        runtime.map.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = Mist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${runtime.countries.count { it.provinceCount > 0 }} countries · " +
                            "${runtime.owners.count { it != 0L }} provinces",
                        style = MaterialTheme.typography.labelMedium,
                        color = Muted,
                    )
                }
                IconButton(
                    onClick = { vm.undoPaint() },
                    enabled = vm.canUndo,
                ) {
                    Text(
                        "↶",
                        fontSize = 24.sp,
                        color = if (vm.canUndo) Mist else Color(0x40FFFFFF),
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Mist)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Countries") }, onClick = {
                            showMenu = false; showCountries = true
                        })
                        DropdownMenuItem(text = { Text("Rename world") }, onClick = {
                            showMenu = false; showRename = true
                        })
                        DropdownMenuItem(text = { Text("Export database") }, onClick = {
                            showMenu = false
                            exportLauncher.launch("worldprovinces-${fileStamp()}.db")
                        })
                        DropdownMenuItem(text = { Text("Import database") }, onClick = {
                            showMenu = false
                            importLauncher.launch(arrayOf("*/*"))
                        })
                        DropdownMenuItem(text = { Text("Delete world") }, onClick = {
                            showMenu = false; showDelete = true
                        })
                    }
                }
            }
            ContinentBar { rect ->
                if (rect == null) mapView?.fitWorld() else mapView?.zoomTo(rect)
            }
        }

        // zoom controls
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ZoomButton("+") { mapView?.zoomBy(1.35f) }
            ZoomButton("−") { mapView?.zoomBy(1f / 1.35f) }
            ZoomButton("◎") {
                mapView?.fitWorld()
            }
        }

        // bottom panel
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            val sel = vm.selectedProvince
            if (sel >= 0 && sel < runtime.colors.size) {
                ProvinceCard(vm, runtime, sel, onRename = { renameProvince = sel }) { vm.selectProvince(-1) }
            }
            PaintPanel(
                runtime = runtime,
                selectedMc = vm.selectedCountryMc,
                onSelect = { vm.selectCountry(it) },
                onCountries = { showCountries = true },
            )
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Replace current data?") },
            text = { Text("The selected database will replace everything currently stored in the app. A backup of the current data is kept.") },
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

    renameProvince?.let { index ->
        NameDialog(
            title = "Rename province",
            label = "Province name",
            initial = runtime.nameOfProvince(index),
            confirm = "Save",
            onDismiss = { renameProvince = null },
            onConfirm = { name ->
                vm.renameProvince(index, name)
                renameProvince = null
            },
        )
    }

    if (showRename) {
        NameDialog(
            title = "Rename world",
            label = "World name",
            initial = runtime.map.name,
            confirm = "Save",
            onDismiss = { showRename = false },
            onConfirm = {
                vm.renameMap(runtime.map.id, it)
                showRename = false
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete ${runtime.map.name}?") },
            text = { Text("This world and all of its borders will be removed permanently.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    vm.deleteMap(runtime.map.id)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
        )
    }

    if (showCountries) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCountries = false },
            sheetState = sheetState,
            containerColor = SurfaceNight,
        ) {
            CountriesSheet(
                runtime = runtime,
                onPick = {
                    vm.selectCountry(it)
                    showCountries = false
                },
                onEdit = { editCountry = it },
                onNew = { showNewCountry = true },
            )
        }
    }

    editCountry?.let { country ->
        EditCountryDialog(
            country = country,
            onDismiss = { editCountry = null },
            onSaveName = { name -> vm.renameCountry(country, name) },
            onSaveColor = { color -> vm.setColor(country, color) },
            onPickFlag = { uri -> vm.setFlag(country, uri) },
            onDelete = {
                vm.deleteCountry(country)
                editCountry = null
            },
        )
    }

    if (showNewCountry) {
        NewCountryDialog(
            onDismiss = { showNewCountry = false },
            onCreate = { name, color, uri ->
                vm.createCountry(name, color, uri)
                showNewCountry = false
                showCountries = false
            },
        )
    }
}

@Composable
private fun ContinentBar(onPick: (android.graphics.RectF?) -> Unit) {
    val continents = listOf(
        "World" to 0,
        "Europe" to 1,
        "Asia" to 8,
        "Africa" to 2,
        "N. America" to 4,
        "S. America" to 16,
        "Oceania" to 32,
        "Antarctica" to 128,
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(continents) { (label, id) ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xAA121A2D),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF)),
                modifier = Modifier.clickable {
                    onPick(if (id == 0) null else io.wpreborn.app.data.Geo.continentBounds[id])
                },
            ) {
                Text(
                    label,
                    color = if (id == 0) Gold else Color(0xFFC9D2E6),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun ZoomButton(label: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = Color(0xCC121A2D),
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = Mist, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ProvinceCard(
    vm: WorldViewModel,
    runtime: MapRuntime,
    index: Int,
    onRename: () -> Unit,
    onClose: () -> Unit,
) {
    val province = runtime
    val name = runtime.nameOfProvince(index)
    val country = runtime.countryOfProvince(index)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xF2121A2D),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = Mist)
                Text(
                    country?.name ?: "Unclaimed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (country != null) Color(io.wpreborn.app.data.Colors.lighten(country.color, 0.45f)) else Muted,
                )
            }
            if (runtime.prIds.getOrElse(index) { -1 } > 0) {
                TextButton(onClick = onRename) { Text("Rename") }
            }
            TextButton(onClick = onClose) { Text("Close") }
        }
    }
}

@Composable
private fun PaintPanel(
    runtime: MapRuntime,
    selectedMc: Long?,
    onSelect: (Long) -> Unit,
    onCountries: () -> Unit,
) {
    val selected = runtime.countryByMc[selectedMc]
    Surface(
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        color = Color(0xF5121A2D),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(top = 14.dp, bottom = 12.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "PAINT WITH",
                        style = MaterialTheme.typography.labelMedium,
                        color = Muted,
                        letterSpacing = 1.6.sp,
                    )
                    Text(
                        selected?.name ?: "Inspect mode",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected != null) Color(io.wpreborn.app.data.Colors.lighten(selected.color, 0.45f)) else Mist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = onCountries,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) { Text("Countries", fontSize = 13.sp) }
            }
            Spacer(Modifier.height(10.dp))
            val visible = runtime.countries
                .filter { it.provinceCount > 0 || it.isCustom }
                .sortedWith(
                    compareBy(
                        { !it.isCustom },
                        { it.provinceCount == 0 },
                        { it.name.lowercase(Locale.ROOT) },
                    )
                )
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            val selectedIndex = visible.indexOfFirst { it.mcId == selectedMc }
            androidx.compose.runtime.LaunchedEffect(selectedMc) {
                if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
            }
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    InspectChip(selected = selectedMc == null) {
                        if (selectedMc != null) onSelect(selectedMc)
                    }
                }
                items(visible, key = { it.mcId }) { country ->
                    CountryChip(
                        country = country,
                        selected = country.mcId == selectedMc,
                        onClick = { onSelect(country.mcId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CountryChip(country: CountryState, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFF243354) else Color(0xFF1A2338),
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) Periwinkle else Color(0x14FFFFFF),
        ),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CountryFlag(country, size = 24.dp)
            Spacer(Modifier.width(7.dp))
            Text(
                country.name,
                color = if (selected) Mist else Color(0xFFC3CBDE),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.width(104.dp),
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
            ColorDot(country.color, size = 10.dp)
        }
    }
}

@Composable
private fun InspectChip(selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFF243354) else Color(0xFF1A2338),
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) Periwinkle else Color(0x14FFFFFF),
        ),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("◎", color = if (selected) Mist else Muted, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Text("Inspect", color = if (selected) Mist else Color(0xFFC3CBDE), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CountriesSheet(
    runtime: MapRuntime,
    onPick: (Long) -> Unit,
    onEdit: (CountryState) -> Unit,
    onNew: () -> Unit,
) {
    val ordered = runtime.countries.sortedWith(
        compareBy({ it.provinceCount == 0 }, { it.name.lowercase(Locale.ROOT) })
    )
    Column(Modifier.fillMaxWidth().height(520.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Countries", style = MaterialTheme.typography.headlineSmall, color = Mist)
                Text(
                    "${ordered.size} in this world",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
            Button(
                onClick = onNew,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Periwinkle, contentColor = Color(0xFF08122E)),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("New")
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(ordered, key = { it.mcId }) { country ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(country.mcId) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CountryFlag(country, size = 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(country.name, color = Mist, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            buildString {
                                append("${country.provinceCount} provinces")
                                if (country.capitalName.isNotBlank()) append(" · ${country.capitalName}")
                            },
                            color = Muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    ColorDot(country.color, size = 12.dp)
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onEdit(country) }) { Text("Edit") }
                }
            }
        }
    }
}

@Composable
private fun EditCountryDialog(
    country: CountryState,
    onDismiss: () -> Unit,
    onSaveName: (String) -> Unit,
    onSaveColor: (Int) -> Unit,
    onPickFlag: (Uri?) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(country.name) }
    var color by remember { mutableStateOf(country.color) }
    var confirmDelete by remember { mutableStateOf(false) }
    val flagPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onPickFlag(uri)
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${country.name}?") },
            text = { Text("Its provinces become unclaimed. This only affects this world.") },
            confirmButton = {
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit country") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CountryFlag(country.copy(color = color), size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("Colour", style = MaterialTheme.typography.labelLarge, color = Muted)
                Spacer(Modifier.height(8.dp))
                ColorGrid(selected = color) { color = it; onSaveColor(it) }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { flagPicker.launch("image/*") }) { Text("Choose custom flag image") }
                if (country.isCustom) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete country", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSaveName(name.trim())
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NewCountryDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Int, Uri?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(PresetColors[4]) }
    var flagUri by remember { mutableStateOf<Uri?>(null) }
    val flagPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) flagUri = uri
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New country") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Country name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text("Colour", style = MaterialTheme.typography.labelLarge, color = Muted)
                Spacer(Modifier.height(8.dp))
                ColorGrid(selected = color) { color = it }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { flagPicker.launch("image/*") }) {
                    Text(if (flagUri == null) "Add a flag (optional)" else "Flag selected ✓")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onCreate(name.trim(), color, flagUri)
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ColorGrid(selected: Int, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PresetColors.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .then(
                                if (c == selected) Modifier.border(2.dp, Color.White, CircleShape)
                                else Modifier.border(1.dp, Color(0x33FFFFFF), CircleShape)
                            )
                            .clickable { onPick(c) },
                    )
                }
            }
        }
    }
}

private fun fileStamp(): String =
    SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
