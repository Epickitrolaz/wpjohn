package io.wpreborn.app.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** Parsed vector geometry for every province. Built once, shared by all views. */
object Geo {

    @Volatile
    private var built = false

    lateinit var paths: Array<Path>
        private set
    lateinit var bounds: Array<RectF>
        private set

    /** Painter's order: ocean, ocean extras, seas, then land on top. */
    lateinit var drawOrder: IntArray
        private set

    /** Bounding box of land provinces for each continent id. */
    lateinit var continentBounds: Map<Int, RectF>
        private set

    fun ensure() {
        if (built) return
        synchronized(this) {
            if (built) return
            val provinces = WorldContent.provinces
            val p = arrayOfNulls<Path>(provinces.size)
            val b = arrayOfNulls<RectF>(provinces.size)
            for (i in provinces.indices) {
                val info = provinces[i]
                val path = Path()
                path.fillType = Path.FillType.EVEN_ODD
                for (poly in info.polys) {
                    if (poly.size < 6) continue
                    path.moveTo(poly[0], poly[1])
                    var k = 2
                    while (k < poly.size) {
                        path.lineTo(poly[k], poly[k + 1])
                        k += 2
                    }
                    path.close()
                }
                p[i] = path
                b[i] = RectF(info.bbox)
            }
            @Suppress("UNCHECKED_CAST")
            paths = p as Array<Path>
            @Suppress("UNCHECKED_CAST")
            bounds = b as Array<RectF>
            val continents = HashMap<Int, RectF>()
            for (i in provinces.indices) {
                val info = provinces[i]
                if (!info.isLand) continue
                val b = b!![i]!!
                val continent = WorldContent.countryByCode[info.countryCode]?.continent ?: info.continent
                val acc = continents[continent]
                if (acc == null) continents[info.continent] = RectF(b)
                else {
                    if (b.left < acc.left) acc.left = b.left
                    if (b.top < acc.top) acc.top = b.top
                    if (b.right > acc.right) acc.right = b.right
                    if (b.bottom > acc.bottom) acc.bottom = b.bottom
                }
            }
            continentBounds = continents
            drawOrder = provinces.indices.sortedWith(
                compareBy(
                    { modeRank(provinces[it].mode) },
                    { it },
                )
            ).toIntArray()
            built = true
        }
    }
}

private fun modeRank(mode: Int): Int = when (mode) {
    1 -> 0
    6 -> 1
    2 -> 2
    else -> 3
}

object Colors {

    fun parse(value: String?, fallback: Int): Int {
        if (value.isNullOrBlank()) return fallback
        var s = value.trim().replace("\\", "")
        if (s.startsWith("#")) s = s.substring(1)
        return try {
            when (s.length) {
                6 -> 0xFF000000.toInt() or s.toLong(16).toInt()
                8 -> s.toLong(16).toInt()
                3 -> {
                    val r = s[0].digitToInt(16) * 17
                    val g = s[1].digitToInt(16) * 17
                    val b = s[2].digitToInt(16) * 17
                    Color.rgb(r, g, b)
                }
                else -> fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }

    fun toHex(color: Int): String = String.format("#%06X", color and 0xFFFFFF)

    fun darken(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    fun lighten(color: Int, factor: Float): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) + (255 - Color.green(color)) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) + (255 - Color.blue(color)) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    /** Perceived luminance, 0..1. */
    fun luminance(color: Int): Float {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    fun onColor(color: Int): Int = if (luminance(color) > 0.6f) Color.BLACK else Color.WHITE
}

/** Renders a small preview of a map state. */
object Thumbnail {

    fun render(
        colors: IntArray,
        width: Int,
        height: Int,
        ocean: Int = OceanColor,
        background: Int? = null,
    ): Bitmap {
        Geo.ensure()
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        background?.let { canvas.drawColor(it) }
        val worldW = WorldContent.worldWidth
        val worldH = WorldContent.worldHeight
        val scale = minOf(width / worldW, height / worldH)
        val dx = (width - worldW * scale) / 2f
        val dy = (height - worldH * scale) / 2f
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL
        canvas.drawColor(ocean)
        val paths = Geo.paths
        for (i in Geo.drawOrder) {
            paint.color = colors.getOrElse(i) { ocean }
            paint.style = Paint.Style.FILL
            canvas.drawPath(paths[i], paint)
        }
        return bmp
    }

    const val OceanColor = 0xFF101C33.toInt()
}
