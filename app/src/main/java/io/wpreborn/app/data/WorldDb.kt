package io.wpreborn.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class WorldDb(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        runCatching { db.setForeignKeyConstraintsEnabled(true) }
        runCatching { db.disableWriteAheadLogging() }
    }

    override fun onCreate(db: SQLiteDatabase) {
        createSchema(db)
        DbBootstrap.populate(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion <= 4) runCatching {
            db.execSQL("ALTER TABLE co ADD COLUMN co_capital TEXT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE co ADD COLUMN co_name_capital TEXT NULL DEFAULT ''")
        }
        if (oldVersion <= 7) runCatching {
            db.execSQL("ALTER TABLE co ADD COLUMN ma_id INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE co ADD COLUMN co_status INTEGER NOT NULL DEFAULT 0")
        }
        createSchema(db)
        if (tableCount(db, "co") == 0) DbBootstrap.populate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        createSchema(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (tableCount(db, "pr") == 0 && tableCount(db, "co") > 0) {
            // partially initialised database, never expected in practice
        }
    }

    companion object {
        const val DB_NAME = "worldprovincesdb"
        const val DB_VERSION = 13

        fun tableCount(db: SQLiteDatabase, table: String): Int = try {
            db.rawQuery("select count(*) from $table", null).use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
        } catch (_: Exception) {
            0
        }

        fun createSchema(db: SQLiteDatabase) {
            execAll(
                db,
                "CREATE TABLE IF NOT EXISTS co (_id INTEGER PRIMARY KEY, co_code TEXT UNIQUE NOT NULL,co_mode INTEGER NOT NULL,co_res_name TEXT NOT NULL,co_type INTEGER NOT NULL DEFAULT 0,co_res_flag INTEGER NOT NULL DEFAULT 0,co_custom_name TEXT NOT NULL DEFAULT '',co_custom_flag TEXT NOT NULL DEFAULT '',co_custom_color TEXT NOT NULL DEFAULT '',co_custom_code TEXT NOT NULL DEFAULT '',co_neighbors TEXT NOT NULL DEFAULT '',co_province_central TEXT NOT NULL DEFAULT '',co_continent INTEGER NOT NULL DEFAULT 0,co_flag_file BLOB NULL DEFAULT NULL,co_res_draw_flag_name TEXT NOT NULL DEFAULT '',co_capital TEXT NOT NULL DEFAULT '',co_name_capital TEXT NOT NULL DEFAULT '',ma_id INTEGER NOT NULL DEFAULT 0,co_status INTEGER NOT NULL DEFAULT 0);",
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_co__co_code ON co (co_code ASC)",
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_co__co_custom_name ON co (co_custom_name ASC)",
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_co__co_custom_code ON co (co_custom_code ASC)",
                "CREATE TABLE IF NOT EXISTS pr (_id INTEGER PRIMARY KEY, pr_code TEXT UNIQUE NOT NULL,co_id INTEGER NOT NULL, pr_mode INTEGER NOT NULL, pr_name TEXT NOT NULL DEFAULT '',pr_continent INTEGER NOT NULL DEFAULT 0,pr_custom_name TEXT NOT NULL DEFAULT '', FOREIGN KEY(co_id) REFERENCES co(_id) );",
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_pr__pr_code ON pr (pr_code ASC)",
                "CREATE TABLE IF NOT EXISTS ma (_id INTEGER PRIMARY KEY, ma_name TEXT NOT NULL,ma_type INTEGER NOT NULL,ma_dti DATETIME NOT NULL,ma_identyf INTEGER NOT NULL,ma_mode INTEGER NOT NULL,ma_round INTEGER NOT NULL,ma_status INTEGER NOT NULL);",
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_ma__ma_name ON ma (ma_name ASC, ma_status ASC)",
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_ma__ma_identyf ON ma (ma_identyf ASC)",
                "CREATE TABLE IF NOT EXISTS mc (_id INTEGER PRIMARY KEY, ma_id INTEGER NOT NULL, co_id INTEGER NOT NULL, FOREIGN KEY(ma_id) REFERENCES ma(_id), FOREIGN KEY(co_id) REFERENCES co(_id) );",
                "CREATE INDEX IF NOT EXISTS idx_mc__ma_id ON mc (ma_id ASC)",
                "CREATE INDEX IF NOT EXISTS idx_mc__co_id ON mc (co_id ASC)",
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_mc__unique ON mc (ma_id, co_id ASC)",
                "CREATE TABLE IF NOT EXISTS pc (_id INTEGER PRIMARY KEY, mc_id INTEGER NOT NULL,pr_id INTEGER NOT NULL,pc_save_mc_id INTEGER NOT NULL,FOREIGN KEY(mc_id) REFERENCES mc(_id), FOREIGN KEY(pr_id) REFERENCES pr(_id) );",
                "CREATE INDEX IF NOT EXISTS idx_pc__mc_id ON pc (mc_id ASC)",
                "CREATE INDEX IF NOT EXISTS idx_pc__pr_id ON pc (pr_id ASC)",
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_pc__unique ON pc (mc_id, pr_id ASC)",
                "CREATE TABLE IF NOT EXISTS re (_id INTEGER PRIMARY KEY, re_msg TEXT NOT NULL,re_dti DATETIME NOT NULL);",
                "CREATE TABLE IF NOT EXISTS cf (_id INTEGER PRIMARY KEY, cf_name TEXT NOT NULL,cf_dti DATETIME NOT NULL,cf_dtu DATETIME NOT NULL,cf_value DATETIME NOT NULL);",
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_cf__cf_name ON cf (cf_name ASC)",
                "CREATE TABLE IF NOT EXISTS hi (_id INTEGER PRIMARY KEY, pc_id INTEGER NOT NULL,mc_id_old INTEGER NOT NULL,mc_id_new INTEGER NOT NULL,cf_dti DATETIME NOT NULL,hi_round INTEGER NOT NULL,FOREIGN KEY(pc_id) REFERENCES pc(_id), FOREIGN KEY(mc_id_old) REFERENCES mc(_id), FOREIGN KEY(mc_id_new) REFERENCES mc(_id) );",
                "CREATE INDEX IF NOT EXISTS idx_hi__pc_id ON hi (pc_id ASC)",
                "CREATE INDEX IF NOT EXISTS idx_hi__hi_round ON hi (hi_round ASC)",
            )
        }

        private fun execAll(db: SQLiteDatabase, vararg sql: String) {
            for (s in sql) runCatching { db.execSQL(s) }
        }
    }
}

/** Builds a complete, original-compatible database from the bundled content pack. */
object DbBootstrap {

    private const val NAME_MARKER = "<@$%*%$@>"

    fun populate(db: SQLiteDatabase) {
        WorldContent.load(AppContextHolder.context)
        val provinces = WorldContent.provinces
        val countries = WorldContent.countries
        db.beginTransaction()
        try {
            val coIdByCode = HashMap<String, Long>(countries.size * 2)
            for (c in countries) {
                val cv = ContentValues().apply {
                    put("co_code", c.code)
                    put("co_mode", c.mode)
                    put("co_res_name", c.flag)
                    put("co_type", 0)
                    put("co_res_flag", 0)
                    put("co_custom_name", NAME_MARKER + c.code)
                    put("co_custom_flag", "")
                    put("co_custom_color", Colors.toHex(c.baseColor))
                    put("co_custom_code", c.code.replace("L_", ""))
                    put("co_neighbors", c.neighbors.joinToString(";"))
                    put("co_province_central", c.central.ifEmpty { c.capitalProvince })
                    put("co_continent", c.continent)
                    putNull("co_flag_file")
                    put("co_res_draw_flag_name", c.flag)
                    put("co_capital", c.capitalProvince)
                    put("co_name_capital", c.capitalName)
                    put("ma_id", 0)
                    put("co_status", 0)
                }
                coIdByCode[c.code] = db.insert("co", null, cv)
            }

            val prIdByCode = HashMap<String, Long>(provinces.size * 2)
            val countryIdByProvince = LongArray(provinces.size)
            for ((index, p) in provinces.withIndex()) {
                val coId = coIdByCode[p.countryCode] ?: continue
                val cv = ContentValues().apply {
                    put("pr_code", p.code)
                    put("co_id", coId)
                    put("pr_mode", p.mode)
                    put("pr_name", p.name)
                    put("pr_continent", p.continent)
                    put("pr_custom_name", "")
                }
                val prId = db.insert("pr", null, cv)
                prIdByCode[p.code] = prId
                countryIdByProvince[index] = coId
            }

            val now = System.currentTimeMillis()
            val mapId = db.insert("ma", null, ContentValues().apply {
                put("ma_name", "World")
                put("ma_type", 1)
                put("ma_dti", now)
                put("ma_identyf", 1L)
                put("ma_mode", 0)
                put("ma_round", 0)
                put("ma_status", 0)
            })

            val mcByCoId = HashMap<Long, Long>(countries.size * 2)
            for (c in countries) {
                val coId = coIdByCode[c.code] ?: continue
                mcByCoId[coId] = db.insert("mc", null, ContentValues().apply {
                    put("ma_id", mapId)
                    put("co_id", coId)
                })
            }

            for ((index, p) in provinces.withIndex()) {
                if (p.mode != 3) continue
                val coId = countryIdByProvince[index]
                val mcId = mcByCoId[coId] ?: continue
                db.insert("pc", null, ContentValues().apply {
                    put("mc_id", mcId)
                    put("pr_id", prIdByCode[p.code])
                    put("pc_save_mc_id", mcId)
                })
            }

            db.insert("cf", null, ContentValues().apply {
                put("cf_name", "VERSION_DATA_DB")
                put("cf_dti", now)
                put("cf_dtu", now)
                put("cf_value", "13")
            })

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

/** Small holder so bootstrap can reach a Context without threading it everywhere. */
object AppContextHolder {
    @Volatile
    lateinit var context: Context
}

fun Cursor.getStringOr(index: Int, fallback: String = ""): String =
    if (isNull(index)) fallback else getString(index) ?: fallback

fun Cursor.getLongOr(index: Int, fallback: Long = 0L): Long =
    if (isNull(index)) fallback else getLong(index)

fun Cursor.getIntOr(index: Int, fallback: Int = 0): Int =
    if (isNull(index)) fallback else getInt(index)
