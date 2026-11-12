package io.wpreborn.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.wpreborn.app.data.CountryState
import io.wpreborn.app.data.WorldContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.LruCache

private val flagCache = object : LruCache<String, Bitmap>(48) {}

private suspend fun loadFlag(context: android.content.Context, country: CountryState): Bitmap? {
    val key = country.coId.toString() + ":" + country.flagName + ":" + (country.flagBytes?.size ?: 0)
    flagCache.get(key)?.let { return it }
    val bmp = withContext(Dispatchers.IO) {
        try {
            if (country.flagBytes != null) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeByteArray(country.flagBytes, 0, country.flagBytes.size, opts)
            } else if (country.flagName.isNotBlank()) {
                context.assets.open("content/flags/${country.flagName}.png").use { input ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeStream(input, null, opts)
                }
            } else {
                val info = WorldContent.countryByCode[country.code]
                if (info != null && info.flag.isNotBlank()) {
                    context.assets.open("content/flags/${info.flag}.png").use { input ->
                        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                        BitmapFactory.decodeStream(input, null, opts)
                    }
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
    if (bmp != null) flagCache.put(key, bmp)
    return bmp
}

@Composable
fun CountryFlag(
    country: CountryState,
    size: Dp = 32.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(6.dp),
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, country.coId, country.flagName) {
        value = loadFlag(context, country)
    }
    val bmp = bitmap
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(Color(0xFF2A3450)),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = country.name,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size * 0.55f)
                    .clip(CircleShape)
                    .background(Color(country.color)),
            )
        }
    }
}

@Composable
fun ColorDot(color: Int, size: Dp = 14.dp, selected: Boolean = false) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(color))
            .then(
                if (selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier
                    .border(1.dp, Color(0x33FFFFFF), CircleShape)
            ),
    )
}

val PresetColors = listOf(
    0xFFE45757, 0xFFE08A3C, 0xFFE3C13F, 0xFF6FBF4A, 0xFF3FA98C, 0xFF3E8FD1,
    0xFF5468D8, 0xFF8B5FD6, 0xFFD05FA8, 0xFF8A6A52, 0xFFB9C0CC, 0xFF5D6B85,
    0xFFA33B3B, 0xFF2F6E4F, 0xFF1F4F8F, 0xFF6B4C9A, 0xFFB78B2A, 0xFF39404D,
).map { it.toInt() }
