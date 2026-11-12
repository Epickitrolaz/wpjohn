package io.wpreborn.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.net.Uri
import java.io.File

data class MapSummary(
    val id: Long,
    val name: String,
    val type: Int,
    val dti: Long,
    val round: Int,
    val status: Int,
)

data class CountryState(
    val mcId: Long,
    val coId: Long,
    val code: String,
    val name: String,
    val color: Int,
    val flagName: String,
    val flagBytes: ByteArray?,
    val capitalProvince: String,
    val capitalName: String,
    val continent: Int,
    val isCustom: Boolean,
    val provinceCount: Int,
)

class MapRuntime(
    val map: MapSummary,
    val countries: List<CountryState>,
    val countryByMc: Map<Long, CountryState>,
    val countryByCo: Map<Long, CountryState>,
    val owners: LongArray,
    val prIds: LongArray,
    val prNames: Array<String?>,
    val prCoIds: LongArray,
    val colors: IntArray,
) {
    fun countryOfProvince(index: Int): CountryState? = countryByMc[owners.getOrElse(index) { 0L }]
    fun nameOfProvince(index: Int): String {
        val province = WorldContent.provinces.getOrNull(index)
        if (province != null && !province.isLand) {
            countryByCo[prCoIds.getOrElse(index) { 0L }]?.let { return it.name }
            WorldContent.countryByCode[province.countryCode]?.let { return it.name }
        }
        return prNames.getOrNull(index)?.takeIf { it.isNotBlank() }
            ?: province?.name
            ?: "Province"
    }
}

sealed class DbResult {
    data class Ok(val message: String) : DbResult()
    data class Failure(val message: String) : DbResult()
}

class WorldRepository(private val context: Context) {

    private var helper: WorldDb? = null

    val dbFile: File get() = context.getDatabasePath(WorldDb.DB_NAME)

    @Synchronized
    fun helper(): WorldDb {
        helper?.let { return it }
        AppContextHolder.context = context.applicationContext
        WorldContent.load(context)
        Geo.ensure()
        ensureBootstrapDatabase()
        val h = WorldDb(context)
        helper = h
        return h
    }

    /** First launch: ship a ready-made database instead of inserting 10k rows. */
    private fun ensureBootstrapDatabase() {
        val file = dbFile
        if (file.exists() && file.length() > 4096) return
        try {
            file.parentFile?.mkdirs()
            context.assets.open("content/bootstrap.db").use { input ->
                file.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
            }
        } catch (_: Exception) {
            // fall back to WorldDb.onCreate population
            runCatching { file.delete() }
        }
    }

    fun db(): SQLiteDatabase = helper().writableDatabase

    @Synchronized
    fun close() {
        runCatching { helper?.close() }
        helper = null
    }

    // ---------------------------------------------------------------- maps

    fun listMaps(): List<MapSummary> {
        val out = ArrayList<MapSummary>()
        db().rawQuery(
            "select _id, ma_name, ma_type, ma_dti, ma_identyf, ma_mode, ma_round, ma_status " +
                "from ma order by ma_dti desc, _id desc", null,
        ).use { c ->
            while (c.moveToNext()) {
                out += MapSummary(
                    id = c.getLong(0), name = c.getStringOr(1), type = c.getIntOr(2),
                    dti = c.getLongOr(3), round = c.getIntOr(6), status = c.getIntOr(7),
                )
            }
        }
        return out
    }

    fun createMap(rawName: String): Long {
        val name = uniqueMapName(rawName.ifBlank { "New world" })
        val db = db()
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            val identyf = db.rawQuery("select ifnull(max(ma_identyf),0)+1 from ma", null)
                .use { if (it.moveToFirst()) it.getLong(0) else 1L }
            val mapId = db.insert("ma", null, ContentValues().apply {
                put("ma_name", name)
                put("ma_type", 1)
                put("ma_dti", now)
                put("ma_identyf", identyf)
                put("ma_mode", 0)
                put("ma_round", 0)
                put("ma_status", 0)
            })
            db.execSQL(
                "insert into mc (ma_id, co_id) select ?, _id from co where ma_id = 0", arrayOf(mapId),
            )
            db.execSQL(
                "insert into pc (mc_id, pr_id, pc_save_mc_id) " +
                    "select mc._id, pr._id, mc._id from mc join pr on pr.co_id = mc.co_id " +
                    "where mc.ma_id = ? and pr.pr_mode = 3", arrayOf(mapId),
            )
            db.setTransactionSuccessful()
            return mapId
        } finally {
            db.endTransaction()
        }
    }

    fun renameMap(mapId: Long, newName: String) {
        db().update("ma", ContentValues().apply { put("ma_name", newName) }, "_id = ?", arrayOf(mapId.toString()))
    }

    fun deleteMap(mapId: Long) {
        val db = db()
        db.beginTransaction()
        try {
            db.execSQL("delete from hi where pc_id in (select _id from pc where mc_id in (select _id from mc where ma_id = ?))", arrayOf(mapId))
            db.execSQL("delete from pc where mc_id in (select _id from mc where ma_id = ?)", arrayOf(mapId))
            db.execSQL("delete from mc where ma_id = ?", arrayOf(mapId))
            db.execSQL("delete from co where ma_id = ?", arrayOf(mapId))
            db.delete("ma", "_id = ?", arrayOf(mapId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun uniqueMapName(base: String): String {
        val existing = HashSet<String>()
        db().rawQuery("select ma_name from ma", null).use { c ->
            while (c.moveToNext()) existing += c.getString(0)
        }
        if (base !in existing) return base
        var i = 2
        while ("$base $i" in existing) i++
        return "$base $i"
    }

    // --------------------------------------------------------------- runtime

    fun loadRuntime(mapId: Long): MapRuntime? {
        val map = listMaps().firstOrNull { it.id == mapId } ?: return null
        val db = db()
        val content = WorldContent.provinces
        val provinces = content
        val n = provinces.size

        val prIds = LongArray(n) { -1L }
        val prNames = arrayOfNulls<String>(n)
        val prCoIds = LongArray(n)
        val indexByPrId = HashMap<Long, Int>(n * 2)
        db.rawQuery("select _id, pr_code, pr_name, pr_custom_name, co_id, pr_mode from pr", null).use { c ->
            while (c.moveToNext()) {
                val idx = WorldContent.provinceIndexByCode[c.getStringOr(1)]
                if (idx == null) continue
                prIds[idx] = c.getLong(0)
                indexByPrId[c.getLong(0)] = idx
                prCoIds[idx] = c.getLongOr(4)
                val custom = c.getStringOr(3)
                prNames[idx] = if (custom.isNotBlank()) custom else c.getStringOr(2)
            }
        }

        val owners = LongArray(n)
        db.rawQuery(
            "select pc.pr_id, pc.mc_id from pc join mc on mc._id = pc.mc_id where mc.ma_id = ?",
            arrayOf(mapId.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                val idx = indexByPrId[c.getLong(0)] ?: continue
                owners[idx] = c.getLong(1)
            }
        }

        val countries = ArrayList<CountryState>(280)
        db.rawQuery(
            "select mc._id, co._id, co.co_code, co.co_custom_name, co.co_custom_color, " +
                "co.co_res_draw_flag_name, co.co_flag_file, co.co_capital, co.co_name_capital, " +
                "co.co_continent, co.ma_id from mc join co on co._id = mc.co_id where mc.ma_id = ?",
            arrayOf(mapId.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                val code = c.getStringOr(2)
                val builtin = WorldContent.countryByCode[code]
                val customName = c.getStringOr(3)
                val name = when {
                    customName.startsWith(NAME_MARKER) -> builtin?.name ?: code
                    customName.isBlank() -> builtin?.name ?: code
                    else -> customName
                }
                val colorValue = c.getStringOr(4)
                val color = Colors.parse(colorValue, builtin?.baseColor ?: DEFAULT_NEUTRAL)
                val flagName = c.getStringOr(5).ifBlank { builtin?.flag ?: "" }
                val flagBytes = if (c.isNull(6)) null else c.getBlob(6)
                countries += CountryState(
                    mcId = c.getLong(0),
                    coId = c.getLong(1),
                    code = code,
                    name = name,
                    color = color,
                    flagName = flagName,
                    flagBytes = flagBytes,
                    capitalProvince = c.getStringOr(7),
                    capitalName = c.getStringOr(8),
                    continent = c.getIntOr(9),
                    isCustom = c.getLongOr(10) > 0,
                    provinceCount = 0,
                )
            }
        }

        val counts = HashMap<Long, Int>(countries.size * 2)
        for (i in 0 until n) {
            val mc = owners[i]
            if (mc != 0L) counts[mc] = (counts[mc] ?: 0) + 1
        }
        val withCounts = countries.map { it.copy(provinceCount = counts[it.mcId] ?: 0) }
        val countryByMc = withCounts.associateBy { it.mcId }
        val colorByCoId = withCounts.associate { it.coId to it.color }

        val colors = IntArray(n)
        for (i in 0 until n) {
            val p = provinces[i]
            colors[i] = when {
                p.mode == 1 || p.mode == 6 -> OCEAN
                p.mode == 2 -> {
                    val base = colorByCoId[prCoIds[i]]
                        ?: WorldContent.countryByCode[p.countryCode]?.baseColor
                        ?: DEFAULT_SEA
                    blend(base, OCEAN, 0.30f)
                }
                p.isLand -> countryByMc[owners[i]]?.color ?: DEFAULT_NEUTRAL
                else -> colorByCoId[prCoIds[i]]
                    ?: WorldContent.countryByCode[p.countryCode]?.baseColor
                    ?: DEFAULT_NEUTRAL
            }
        }

        return MapRuntime(
            map, withCounts, countryByMc,
            withCounts.associateBy { it.coId },
            owners, prIds, prNames, prCoIds, colors,
        )
    }

    fun thumbnail(runtime: MapRuntime, width: Int, height: Int): Bitmap =
        Thumbnail.render(runtime.colors, width, height, OCEAN)

    // --------------------------------------------------------------- painting

    data class PaintResult(val pcId: Long, val oldMcId: Long, val inserted: Boolean)

    fun paint(mapId: Long, provinceIndex: Int, newMcId: Long, round: Int): PaintResult? {
        val runtime = currentPrId(mapId, provinceIndex) ?: return null
        val db = db()
        val (prId, pcId, oldMc) = runtime
        val now = System.currentTimeMillis()
        return if (pcId > 0) {
            db.beginTransaction()
            try {
                db.execSQL(
                    "update pc set pc_save_mc_id = mc_id, mc_id = ? where _id = ?",
                    arrayOf(newMcId, pcId),
                )
                db.insert("hi", null, ContentValues().apply {
                    put("pc_id", pcId)
                    put("mc_id_old", oldMc)
                    put("mc_id_new", newMcId)
                    put("cf_dti", now)
                    put("hi_round", round)
                })
                db.setTransactionSuccessful()
                PaintResult(pcId, oldMc, false)
            } finally {
                db.endTransaction()
            }
        } else {
            val id = db.insert("pc", null, ContentValues().apply {
                put("mc_id", newMcId)
                put("pr_id", prId)
                put("pc_save_mc_id", newMcId)
            })
            PaintResult(id, oldMc, true)
        }
    }

    fun undoPaint(mapId: Long, op: PaintResult, round: Int) {
        val db = db()
        if (op.inserted) {
            db.delete("pc", "_id = ?", arrayOf(op.pcId.toString()))
        } else {
            db.execSQL("update pc set mc_id = ? where _id = ?", arrayOf(op.oldMcId, op.pcId))
            db.insert("hi", null, ContentValues().apply {
                put("pc_id", op.pcId)
                put("mc_id_old", op.oldMcId)
                put("mc_id_new", op.oldMcId)
                put("cf_dti", System.currentTimeMillis())
                put("hi_round", round)
            })
        }
    }

    private data class PcInfo(val prId: Long, val pcId: Long, val oldMc: Long)

    private fun currentPrId(mapId: Long, provinceIndex: Int): PcInfo? {
        val code = WorldContent.provinces.getOrNull(provinceIndex)?.code ?: return null
        val db = db()
        val prId = db.rawQuery("select _id from pr where pr_code = ?", arrayOf(code)).use {
            if (it.moveToFirst()) it.getLong(0) else -1L
        }
        if (prId <= 0) return null
        db.rawQuery(
            "select pc._id, pc.mc_id from pc join mc on mc._id = pc.mc_id " +
                "where pc.pr_id = ? and mc.ma_id = ? limit 1",
            arrayOf(prId.toString(), mapId.toString()),
        ).use { c ->
            return if (c.moveToFirst()) PcInfo(prId, c.getLong(0), c.getLong(1))
            else PcInfo(prId, -1, 0)
        }
    }

    // --------------------------------------------------------------- countries

    fun setProvinceName(prId: Long, name: String) {
        db().update("pr", ContentValues().apply { put("pr_custom_name", name.trim()) },
            "_id = ?", arrayOf(prId.toString()))
    }

    fun setCountryColor(coId: Long, color: Int) {
        db().update("co", ContentValues().apply {
            put("co_custom_color", Colors.toHex(color))
        }, "_id = ?", arrayOf(coId.toString()))
    }

    fun setCountryName(coId: Long, name: String): Boolean {
        return try {
            db().update("co", ContentValues().apply { put("co_custom_name", name.trim()) },
                "_id = ?", arrayOf(coId.toString())) > 0
        } catch (_: Exception) {
            false
        }
    }

    fun setCountryFlag(coId: Long, bytes: ByteArray?) {
        db().update("co", ContentValues().apply {
            if (bytes == null) {
                putNull("co_flag_file")
                put("co_res_draw_flag_name", "")
            } else {
                put("co_flag_file", bytes)
                put("co_res_draw_flag_name", "")
            }
        }, "_id = ?", arrayOf(coId.toString()))
    }

    fun setCapital(coId: Long, provinceCode: String, capitalName: String) {
        db().update("co", ContentValues().apply {
            put("co_capital", provinceCode)
            put("co_name_capital", capitalName)
        }, "_id = ?", arrayOf(coId.toString()))
    }

    fun createCountry(mapId: Long, name: String, color: Int, flag: ByteArray?): Long? {
        val db = db()
        val code = "U_" + System.currentTimeMillis().toString(36).uppercase()
        return try {
            db.beginTransaction()
            try {
                val coId = db.insertOrThrow("co", null, ContentValues().apply {
                    put("co_code", code)
                    put("co_mode", 3)
                    put("co_res_name", "")
                    put("co_type", 1)
                    put("co_res_flag", 0)
                    put("co_custom_name", name.trim())
                    put("co_custom_flag", "")
                    put("co_custom_color", Colors.toHex(color))
                    put("co_custom_code", code)
                    put("co_neighbors", "")
                    put("co_province_central", "")
                    put("co_continent", 0)
                    if (flag == null) putNull("co_flag_file") else put("co_flag_file", flag)
                    put("co_res_draw_flag_name", "")
                    put("co_capital", "")
                    put("co_name_capital", "")
                    put("ma_id", mapId)
                    put("co_status", 0)
                })
                db.insert("mc", null, ContentValues().apply {
                    put("ma_id", mapId)
                    put("co_id", coId)
                })
                db.setTransactionSuccessful()
                coId
            } finally {
                db.endTransaction()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun deleteCountry(mapId: Long, coId: Long) {
        val db = db()
        db.beginTransaction()
        try {
            db.execSQL(
                "delete from pc where mc_id in (select _id from mc where ma_id = ? and co_id = ?)",
                arrayOf(mapId, coId),
            )
            db.execSQL("delete from mc where ma_id = ? and co_id = ?", arrayOf(mapId, coId))
            db.delete("co", "_id = ? and ma_id = ?", arrayOf(coId.toString(), mapId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // --------------------------------------------------------------- storage

    fun exportTo(uri: Uri): DbResult {
        return try {
            close()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                dbFile.inputStream().use { it.copyTo(out, 1 shl 16) }
            } ?: return DbResult.Failure("Could not open the destination")
            DbResult.Ok("Database exported")
        } catch (e: Exception) {
            DbResult.Failure(e.message ?: "Export failed")
        }
    }

    fun importFrom(uri: Uri): DbResult {
        val tmp = File(context.cacheDir, "import_probe.db")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it, 1 shl 16) }
            } ?: return DbResult.Failure("Could not read the selected file")

            val validation = validateDatabase(tmp)
            if (validation != null) return DbResult.Failure(validation)

            close()
            backupCurrent()
            val wal = File(dbFile.path + "-wal")
            val shm = File(dbFile.path + "-shm")
            val journal = File(dbFile.path + "-journal")
            wal.delete(); shm.delete(); journal.delete()
            dbFile.parentFile?.mkdirs()
            tmp.copyTo(dbFile, overwrite = true)
            tmp.delete()

            // touch the database so a broken file fails here rather than mid-session
            val test = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
            test.rawQuery("select count(*) from co", null).use { it.moveToFirst() }
            test.close()
            DbResult.Ok("Database imported")
        } catch (e: Exception) {
            DbResult.Failure(e.message ?: "Import failed")
        } finally {
            runCatching { tmp.delete() }
        }
    }

    private fun validateDatabase(file: File): String? {
        if (!file.exists() || file.length() < 1024) return "This file is not a database"
        return try {
            val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
            try {
                val tables = HashSet<String>()
                db.rawQuery("select name from sqlite_master where type = 'table'", null).use { c ->
                    while (c.moveToNext()) tables += c.getString(0)
                }
                val missing = listOf("co", "pr", "ma", "mc", "pc").filter { it !in tables }
                if (missing.isNotEmpty()) return "Not a World Provinces database (missing ${missing.joinToString()})"
                val columns = HashSet<String>()
                db.rawQuery("pragma table_info(co)", null).use { c ->
                    while (c.moveToNext()) columns += c.getString(1)
                }
                if ("co_code" !in columns || "co_custom_color" !in columns) {
                    return "Unsupported database layout"
                }
                null
            } finally {
                db.close()
            }
        } catch (e: Exception) {
            "This file is not a valid database: ${e.message}"
        }
    }

    private fun backupCurrent() {
        if (!dbFile.exists()) return
        val dir = File(context.filesDir, "database_backups")
        dir.mkdirs()
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        dbFile.copyTo(File(dir, "worldprovinces-$stamp.db"), overwrite = true)
        val backups = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        backups.drop(5).forEach { it.delete() }
    }

    fun lastBackup(): File? = File(context.filesDir, "database_backups")
        .listFiles()?.maxByOrNull { it.lastModified() }

    fun restoreLastBackup(): DbResult {
        val backup = lastBackup() ?: return DbResult.Failure("No backup available")
        return try {
            close()
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            backup.copyTo(dbFile, overwrite = true)
            db()
            DbResult.Ok("Previous data restored")
        } catch (e: Exception) {
            DbResult.Failure(e.message ?: "Restore failed")
        }
    }

    companion object {
        const val NAME_MARKER = "<@$%*%$@>"
        const val OCEAN = 0xFF0B1526.toInt()
        const val DEFAULT_NEUTRAL = 0xFF515A6E.toInt()
        const val DEFAULT_SEA = 0xFF16305A.toInt()

        fun blend(a: Int, b: Int, t: Float): Int {
            val r = (android.graphics.Color.red(a) * (1 - t) + android.graphics.Color.red(b) * t).toInt()
            val g = (android.graphics.Color.green(a) * (1 - t) + android.graphics.Color.green(b) * t).toInt()
            val bl = (android.graphics.Color.blue(a) * (1 - t) + android.graphics.Color.blue(b) * t).toInt()
            return android.graphics.Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), bl.coerceIn(0, 255))
        }
    }
}
