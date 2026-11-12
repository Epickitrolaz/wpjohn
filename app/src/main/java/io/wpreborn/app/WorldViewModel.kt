package io.wpreborn.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.wpreborn.app.data.CountryState
import io.wpreborn.app.data.DbResult
import io.wpreborn.app.data.Geo
import io.wpreborn.app.data.MapRuntime
import io.wpreborn.app.data.MapSummary
import io.wpreborn.app.data.WorldContent
import io.wpreborn.app.data.WorldRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.ArrayDeque

sealed class BootState {
    data object Loading : BootState()
    data object Ready : BootState()
    data class Failed(val message: String) : BootState()
}

class WorldViewModel(app: Application) : AndroidViewModel(app) {

    val repo = WorldRepository(app)

    var boot by mutableStateOf<BootState>(BootState.Loading)
        private set
    var maps by mutableStateOf<List<MapSummary>>(emptyList())
        private set
    var runtime by mutableStateOf<MapRuntime?>(null)
        private set
    var currentMapId by mutableStateOf<Long?>(null)
        private set
    var selectedCountryMc by mutableStateOf<Long?>(null)
        private set
    var selectedProvince by mutableStateOf(-1)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var busyLabel by mutableStateOf("Working…")
        private set

    private val undo = HashMap<Long, ArrayDeque<WorldRepository.PaintResult>>()

    init {
        load()
    }

    fun load() {
        boot = BootState.Loading
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    WorldContent.load(getApplication())
                    io.wpreborn.app.data.AppContextHolder.context = getApplication()
                    Geo.ensure()
                    repo.listMaps()
                }
                refreshMaps()
                boot = BootState.Ready
            } catch (t: Throwable) {
                boot = BootState.Failed(t.message ?: "Could not open the world data")
            }
        }
    }

    fun refreshMaps() {
        viewModelScope.launch {
            maps = withContext(Dispatchers.IO) { repo.listMaps() }
        }
    }

    fun thumbnail(mapId: Long, width: Int, height: Int): Bitmap? = null

    suspend fun loadThumbnail(mapId: Long, width: Int, height: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            val rt = repo.loadRuntime(mapId) ?: return@withContext null
            repo.thumbnail(rt, width, height)
        }

    fun openMap(mapId: Long) {
        viewModelScope.launch {
            busy = true
            busyLabel = "Opening world…"
            val rt = withContext(Dispatchers.IO) { repo.loadRuntime(mapId) }
            busy = false
            if (rt == null) {
                message = "This world could not be opened"
                return@launch
            }
            currentMapId = mapId
            runtime = rt
            selectedCountryMc = null
            selectedProvince = -1
        }
    }

    fun closeMap() {
        currentMapId = null
        runtime = null
        selectedCountryMc = null
        selectedProvince = -1
        refreshMaps()
    }

    fun selectCountry(mcId: Long?) {
        selectedCountryMc = if (selectedCountryMc == mcId) null else mcId
    }

    fun selectProvince(index: Int) {
        selectedProvince = index
    }

    fun tapProvince(index: Int) {
        val rt = runtime ?: return
        val mapId = currentMapId ?: return
        if (index < 0) {
            selectedProvince = -1
            return
        }
        val mc = selectedCountryMc
        if (mc == null) {
            selectedProvince = index
            return
        }
        val province = WorldContent.provinces.getOrNull(index) ?: return
        if (!province.isLand) {
            selectedProvince = index
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repo.paint(mapId, index, mc, rt.map.round)
            } ?: return@launch
            undo.getOrPut(mapId) { ArrayDeque() }.apply {
                if (size >= 300) removeLast()
                addFirst(result)
            }
            selectedProvince = index
            runtime = withContext(Dispatchers.IO) { repo.loadRuntime(mapId) }
        }
    }

    fun undoPaint() {
        val mapId = currentMapId ?: return
        val stack = undo[mapId] ?: return
        val op = stack.pollFirst() ?: return
        val rt = runtime ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.undoPaint(mapId, op, rt.map.round) }
            runtime = withContext(Dispatchers.IO) { repo.loadRuntime(mapId) }
        }
    }

    val canUndo: Boolean get() = currentMapId?.let { undo[it]?.isNotEmpty() == true } ?: false

    // -------------------------------------------------------------- maps

    fun createMap(name: String) {
        viewModelScope.launch {
            busy = true
            busyLabel = "Building world…"
            val id = withContext(Dispatchers.IO) { repo.createMap(name) }
            refreshMaps()
            busy = false
            openMap(id)
        }
    }

    fun renameMap(mapId: Long, name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.renameMap(mapId, name.ifBlank { "World" }) }
            refreshMaps()
            runtime?.let { rt ->
                if (rt.map.id == mapId) runtime = withContext(Dispatchers.IO) { repo.loadRuntime(mapId) }
            }
        }
    }

    fun deleteMap(mapId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteMap(mapId) }
            if (currentMapId == mapId) closeMap()
            refreshMaps()
            message = "World deleted"
        }
    }

    // -------------------------------------------------------------- countries

    fun setColor(country: CountryState, color: Int) {
        val mapId = currentMapId ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.setCountryColor(country.coId, color) }
            runtime = withContext(Dispatchers.IO) { repo.loadRuntime(mapId) }
        }
    }

    fun renameCountry(country: CountryState, name: String): Boolean {
        val mapId = currentMapId ?: return false
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repo.setCountryName(country.coId, trimmed) }
            if (!ok) message = "That name is already in use"
            runtime = withContext(Dispatchers.IO) { repo.loadRuntime(mapId) }
        }
        return true
    }

    fun setFlag(country: CountryState, uri: Uri?) {
        val mapId = currentMapId ?: return
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                uri?.let { downscaleFlag(it) }
            }
            withContext(Dispatchers.IO) { repo.setCountryFlag(country.coId, bytes) }
            runtime = withContext(Dispatchers.IO) { repo.loadRuntime(mapId) }
        }
    }

    private fun downscaleFlag(uri: Uri): ByteArray? {
        return try {
            val resolver = getApplication<Application>().contentResolver
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (opts.outWidth / sample > 512 || opts.outHeight / sample > 512) sample *= 2
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null
            val scaled = if (bmp.width > 256 || bmp.height > 160) {
                val ratio = minOf(256f / bmp.width, 160f / bmp.height)
                Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true)
            } else bmp
            ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun createCountry(name: String, color: Int, flagUri: Uri?) {
        val mapId = currentMapId ?: return
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { flagUri?.let { downscaleFlag(it) } }
            val coId = withContext(Dispatchers.IO) { repo.createCountry(mapId, name, color, bytes) }
            if (coId == null) {
                message = "Could not create the country (name must be unique)"
                return@launch
            }
            val rt = withContext(Dispatchers.IO) { repo.loadRuntime(mapId) }
            runtime = rt
            selectedCountryMc = rt?.countries?.firstOrNull { it.coId == coId }?.mcId
        }
    }

    fun renameProvince(index: Int, name: String) {
        val mapId = currentMapId ?: return
        val rt = runtime ?: return
        val prId = rt.prIds.getOrNull(index) ?: return
        if (prId <= 0) {
            message = "This province is missing from the database"
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.setProvinceName(prId, name) }
            runtime = withContext(Dispatchers.IO) { repo.loadRuntime(mapId) }
        }
    }

    fun deleteCountry(country: CountryState) {
        val mapId = currentMapId ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteCountry(mapId, country.coId) }
            runtime = withContext(Dispatchers.IO) { repo.loadRuntime(mapId) }
            if (selectedCountryMc == country.mcId) selectedCountryMc = null
            message = "Country deleted"
        }
    }

    // -------------------------------------------------------------- storage

    fun importDatabase(uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            busy = true
            busyLabel = "Importing database…"
            closeMap()
            val result = withContext(Dispatchers.IO) { repo.importFrom(uri) }
            busy = false
            message = when (result) {
                is DbResult.Ok -> "Database imported"
                is DbResult.Failure -> "Import failed: ${result.message}"
            }
            refreshMaps()
            onDone(result is DbResult.Ok)
        }
    }

    fun exportDatabase(uri: Uri) {
        viewModelScope.launch {
            busy = true
            busyLabel = "Exporting database…"
            val result = withContext(Dispatchers.IO) { repo.exportTo(uri) }
            busy = false
            message = when (result) {
                is DbResult.Ok -> "Database saved"
                is DbResult.Failure -> "Export failed: ${result.message}"
            }
        }
    }

    fun restoreBackup() {
        viewModelScope.launch {
            busy = true
            busyLabel = "Restoring backup…"
            closeMap()
            val result = withContext(Dispatchers.IO) { repo.restoreLastBackup() }
            busy = false
            message = when (result) {
                is DbResult.Ok -> "Backup restored"
                is DbResult.Failure -> "Restore failed: ${result.message}"
            }
            refreshMaps()
        }
    }

    fun clearMessage() {
        message = null
    }
}
