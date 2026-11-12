package io.wpreborn.app.data

import android.content.Context
import android.graphics.RectF
import java.io.DataInputStream
import java.io.InputStream

data class CountryInfo(
    val code: String,
    val mode: Int,
    val baseColor: Int,
    val lightColor: Int,
    val darkColor: Int,
    val name: String,
    val flag: String,
    val capitalProvince: String,
    val capitalName: String,
    val continent: Int,
    val neighbors: List<String>,
    val central: String,
)

data class ProvinceInfo(
    val code: String,
    val name: String,
    val mode: Int,
    val countryCode: String,
    val continent: Int,
    val neighbors: List<String>,
    /** Polygons of this province: each array is x0,y0,x1,y1,... in world coordinates. */
    val polys: Array<FloatArray>,
    val bbox: RectF,
) {
    val isLand: Boolean get() = mode == 3
    val area: Float get() = (bbox.width() * bbox.height())
}

/**
 * Immutable, in-memory copy of the bundled map data extracted from the original
 * application resources. Everything here is factual geographic data (province
 * outlines, names, adjacency).
 */
object WorldContent {

    @Volatile
    var loaded = false
        private set

    lateinit var countries: List<CountryInfo>
        private set
    lateinit var provinces: List<ProvinceInfo>
        private set
    lateinit var countryByCode: Map<String, CountryInfo>
        private set
    lateinit var provinceIndexByCode: Map<String, Int>
        private set

    var worldWidth = 0f
        private set
    var worldHeight = 0f
        private set

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val countries = ArrayList<CountryInfo>(256)
            val provinces = ArrayList<ProvinceInfo>(4800)
            context.assets.open("content/content.bin").buffered(1 shl 16).use { raw ->
                val input = DataInputStream(raw)
                val magic = ByteArray(4)
                input.readFully(magic)
                require(String(magic) == "WPC2") { "Unsupported content pack" }

                val countryCount = input.readInt()
                repeat(countryCount) {
                    val code = input.readUtf8()
                    val mode = input.readUnsignedByte()
                    val base = input.readInt()
                    val light = input.readInt()
                    val dark = input.readInt()
                    val name = input.readUtf8()
                    val flag = input.readUtf8()
                    val capitalProv = input.readUtf8()
                    val capitalName = input.readUtf8()
                    val continent = input.readInt()
                    val neighbors = input.readUtf8()
                    val central = input.readUtf8()
                    countries += CountryInfo(
                        code = code, mode = mode, baseColor = base, lightColor = light,
                        darkColor = dark, name = name, flag = flag,
                        capitalProvince = capitalProv, capitalName = capitalName,
                        continent = continent,
                        neighbors = if (neighbors.isEmpty()) emptyList() else neighbors.split(';'),
                        central = central,
                    )
                }

                val provinceCount = input.readInt()
                var maxX = 0f
                var maxY = 0f
                repeat(provinceCount) {
                    val code = input.readUtf8()
                    val name = input.readUtf8()
                    val mode = input.readUnsignedByte()
                    val country = input.readUtf8()
                    val continent = input.readInt()
                    val neighbors = input.readUtf8()
                    val subCount = input.readInt()
                    val polys = arrayOfNulls<FloatArray>(subCount)
                    val bbox = RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
                    for (s in 0 until subCount) {
                        val n = input.readInt()
                        val arr = FloatArray(n * 2)
                        var i = 0
                        while (i < arr.size) {
                            arr[i] = input.readFloat()
                            arr[i + 1] = input.readFloat()
                            i += 2
                        }
                        polys[s] = arr
                        var k = 0
                        while (k < arr.size) {
                            val x = arr[k]
                            val y = arr[k + 1]
                            if (x < bbox.left) bbox.left = x
                            if (x > bbox.right) bbox.right = x
                            if (y < bbox.top) bbox.top = y
                            if (y > bbox.bottom) bbox.bottom = y
                            k += 2
                        }
                    }
                    if (bbox.left != Float.MAX_VALUE) {
                        if (bbox.right > maxX) maxX = bbox.right
                        if (bbox.bottom > maxY) maxY = bbox.bottom
                    }
                    @Suppress("UNCHECKED_CAST")
                    provinces += ProvinceInfo(
                        code, name, mode, country, continent,
                        if (neighbors.isEmpty()) emptyList() else neighbors.split(';'),
                        polys as Array<FloatArray>, bbox,
                    )
                }
                worldWidth = maxX
                worldHeight = maxY
            }
            WorldContent.countries = countries
            WorldContent.provinces = provinces
            WorldContent.countryByCode = countries.associateBy { it.code }
            WorldContent.provinceIndexByCode = provinces.withIndex().associate { it.value.code to it.index }
            loaded = true
        }
    }

    private fun DataInputStream.readUtf8(): String {
        val n = readInt()
        if (n <= 0) return ""
        val bytes = ByteArray(n)
        readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }
}

fun continentName(id: Int): String = when (id) {
    1 -> "Europe"
    2 -> "Africa"
    3 -> "Europe / Asia"
    4 -> "North America"
    8 -> "Asia"
    16 -> "South America"
    32 -> "Oceania"
    128 -> "Antarctica"
    else -> "Other"
}
