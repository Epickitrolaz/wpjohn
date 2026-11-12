package io.wpreborn.app.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import io.wpreborn.app.data.Geo
import io.wpreborn.app.data.MapRuntime
import io.wpreborn.app.data.WorldContent
import java.util.concurrent.Executors

/**
 * Hardware friendly world map. All province geometry is drawn from pre-parsed
 * paths. The current viewport is cached into a bitmap and reused while the user
 * pans or pinches, so gestures stay smooth even on low end devices.
 */
class MapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var onProvinceTap: ((Int) -> Unit)? = null
    var onLongPress: ((Int) -> Unit)? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x3D000000
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x14FFFFFF
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFFE08A.toInt()
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val bitmapMatrix = Matrix()

    private var colors: IntArray = IntArray(0)
    private var selection: Int = -1
    private var mapRuntime: MapRuntime? = null

    private var scale = 1f
    private var minScale = 1f
    private var maxScale = 20f
    private var tx = 0f
    private var ty = 0f
    private var initialised = false

    private var rendered: Bitmap? = null
    private var renderBuffer: Bitmap? = null
    private var renderedScale = 1f
    private var renderedTx = 0f
    private var renderedTy = 0f
    private var generation = 0
    private var renderScheduled = false
    private var renderInFlight = false
    private var pendingRender = false
    private var gesturing = false

    private val handler = Handler(Looper.getMainLooper())
    private val renderer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "map-renderer").apply { priority = Thread.MIN_PRIORITY }
    }

    private var borderPath: Path? = null
    private val borderLock = Any()

    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var panning = false
    private val touchSlop = 8f * resources.displayMetrics.density

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            panning = false
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val old = scale
            val next = (scale * detector.scaleFactor).coerceIn(minScale, maxScale)
            if (next == old) return true
            val fx = detector.focusX
            val fy = detector.focusY
            tx = fx - (fx - tx) * (next / old)
            ty = fy - (fy - ty) * (next / old)
            scale = next
            clampViewport()
            invalidate()
            scheduleRender()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val index = provinceAt(e.x, e.y)
            onProvinceTap?.invoke(index)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val index = provinceAt(e.x, e.y)
            if (index >= 0) onLongPress?.invoke(index)
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val old = scale
            val next = (scale * 1.8f).coerceIn(minScale, maxScale)
            tx = e.x - (e.x - tx) * (next / old)
            ty = e.y - (e.y - ty) * (next / old)
            scale = next
            clampViewport()
            invalidate()
            scheduleRender()
            return true
        }
    })

    fun bind(runtime: MapRuntime) {
        mapRuntime = runtime
        colors = runtime.colors.copyOf()
        scheduleRender()
    }

    fun updateState(newColors: IntArray, selected: Int) {
        val colorsChanged = !newColors.contentEquals(colors)
        val selectionChanged = selection != selected
        if (!colorsChanged && !selectionChanged) return
        if (colorsChanged) colors = newColors.copyOf()
        selection = selected
        scheduleRender()
    }

    fun updateColors(newColors: IntArray) {
        colors = newColors
        scheduleRender()
    }

    fun setSelection(index: Int) {
        if (selection == index) return
        selection = index
        scheduleRender()
    }

    fun fitWorld() {
        if (width == 0 || height == 0) return
        val worldW = WorldContent.worldWidth.coerceAtLeast(1f)
        val worldH = WorldContent.worldHeight.coerceAtLeast(1f)
        minScale = minOf(width / worldW, height / worldH) * 0.97f
        maxScale = minScale * 18f
        scale = minScale
        tx = (width - worldW * scale) / 2f
        ty = (height - worldH * scale) / 2f
        invalidate()
        scheduleRender()
    }

    fun zoomBy(factor: Float) {
        val cx = width / 2f
        val cy = height / 2f
        val old = scale
        val next = (scale * factor).coerceIn(minScale, maxScale)
        tx = cx - (cx - tx) * (next / old)
        ty = cy - (cy - ty) * (next / old)
        scale = next
        clampViewport()
        invalidate()
        scheduleRender()
    }

    fun zoomTo(rect: android.graphics.RectF, padding: Float = 0.88f) {
        if (width == 0 || height == 0) return
        val fit = minOf(width / rect.width().coerceAtLeast(1f), height / rect.height().coerceAtLeast(1f)) * padding
        scale = fit.coerceIn(minScale, maxScale)
        tx = width / 2f - rect.centerX() * scale
        ty = height / 2f - rect.centerY() * scale
        clampViewport()
        invalidate()
        scheduleRender()
    }

    fun centerOn(provinceIndex: Int) {
        val b = Geo.bounds.getOrNull(provinceIndex) ?: return
        val cx = b.centerX()
        val cy = b.centerY()
        tx = width / 2f - cx * scale
        ty = height / 2f - cy * scale
        clampViewport()
        invalidate()
        scheduleRender()
    }

    fun zoomLabel(): String = String.format(java.util.Locale.US, "%.1f×", scale / minScale.coerceAtLeast(0.0001f))

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!initialised) {
            initialised = true
            fitWorld()
        } else {
            clampViewport()
            invalidate()
            scheduleRender()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val bitmap = rendered
        if (bitmap != null && !bitmap.isRecycled) {
            val ds = scale / renderedScale
            bitmapMatrix.reset()
            bitmapMatrix.setScale(ds, ds)
            bitmapMatrix.postTranslate(tx - renderedTx * ds, ty - renderedTy * ds)
            canvas.drawBitmap(bitmap, bitmapMatrix, bitmapPaint)
        } else if (colors.isNotEmpty() && width > 0) {
            // First frame: only fill the background; the asynchronous renderer
            // will provide the map within a few dozen milliseconds.
            canvas.drawColor(io.wpreborn.app.data.WorldRepository.OCEAN)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                downX = event.x
                downY = event.y
                panning = false
                gesturing = true
            }
            MotionEvent.ACTION_POINTER_DOWN -> panning = false
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && event.pointerCount == 1 && !panning) {
                    if (Math.abs(event.x - downX) > touchSlop || Math.abs(event.y - downY) > touchSlop) {
                        panning = true
                    }
                }
                if (panning && !scaleDetector.isInProgress) {
                    tx += event.x - lastX
                    ty += event.y - lastY
                    clampViewport()
                    invalidate()
                    scheduleRender()
                }
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastX = event.x
                lastY = event.y
                gesturing = false
                scheduleRender()
            }
        }
        return true
    }

    private fun clampViewport() {
        val w = width.toFloat()
        val h = height.toFloat()
        val worldW = WorldContent.worldWidth * scale
        val worldH = WorldContent.worldHeight * scale
        // Keep at least a sliver of the world on screen, but never force a
        // position: continent jumps need to be able to centre anywhere.
        val marginX = 100f
        val marginY = 100f
        tx = tx.coerceIn(marginX - worldW, w - marginX)
        ty = ty.coerceIn(marginY - worldH, h - marginY)
    }

    private fun scheduleRender() {
        if (width == 0 || height == 0) return
        if (gesturing) return
        if (renderInFlight) {
            pendingRender = true
            return
        }
        if (renderScheduled) return
        renderScheduled = true
        handler.postDelayed(renderTask, 60)
    }

    private val renderTask = Runnable {
        renderScheduled = false
        renderNow()
    }

    private fun renderNow() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0 || colors.isEmpty()) return
        Geo.ensure()
        val s = scale
        val localTx = tx
        val localTy = ty
        val cols = colors
        val sel = selection
        val gen = ++generation
        renderInFlight = true
        renderer.execute {
            val reusable = renderBuffer?.takeIf { it.width == w && it.height == h && !it.isRecycled }
            renderBuffer = null
            val bitmap = try {
                reusable ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            } catch (_: OutOfMemoryError) {
                handler.post { renderInFlight = false }
                return@execute
            }
            val canvas = Canvas(bitmap)
            val started = System.nanoTime()
            drawWorld(canvas, w, h, s, localTx, localTy, cols, sel)
            android.util.Log.d(
                "WPRender",
                "rendered ${w}x$h in ${(System.nanoTime() - started) / 1_000_000} ms",
            )
            handler.post {
                renderInFlight = false
                if (gen == generation) {
                    renderBuffer = rendered
                    rendered = bitmap
                    renderedScale = s
                    renderedTx = localTx
                    renderedTy = localTy
                    invalidate()
                } else {
                    renderBuffer = bitmap
                }
                if (pendingRender) {
                    pendingRender = false
                    scheduleRender()
                }
            }
        }
    }

    private fun drawWorld(
        canvas: Canvas,
        w: Int,
        h: Int,
        s: Float,
        ox: Float,
        oy: Float,
        cols: IntArray,
        selected: Int,
    ) {
        canvas.drawColor(io.wpreborn.app.data.WorldRepository.OCEAN)
        val paths = Geo.paths
        val bounds = Geo.bounds
        val left = (0f - ox) / s
        val top = (0f - oy) / s
        val right = (w - ox) / s
        val bottom = (h - oy) / s

        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(s, s)

        val worldW = WorldContent.worldWidth
        val worldH = WorldContent.worldHeight
        gridPaint.strokeWidth = (0.6f / s).coerceAtLeast(0.03f)
        for (i in 1 until 12) {
            val gx = worldW * i / 12f
            canvas.drawLine(gx, 0f, gx, worldH, gridPaint)
        }
        for (i in 1 until 6) {
            val gy = worldH * i / 6f
            canvas.drawLine(0f, gy, worldW, gy, gridPaint)
        }

        fillPaint.style = Paint.Style.FILL
        for (i in io.wpreborn.app.data.Geo.drawOrder) {
            val b = bounds[i]
            if (b.right < left || b.left > right || b.bottom < top || b.top > bottom) continue
            fillPaint.color = cols.getOrElse(i) { Color.TRANSPARENT }
            canvas.drawPath(paths[i], fillPaint)
        }

        val border = ensureBorderPath()
        if (border != null) {
            borderPaint.strokeWidth = (0.7f / s).coerceAtLeast(0.05f)
            canvas.drawPath(border, borderPaint)
        }

        if (selected in paths.indices) {
            highlightPaint.strokeWidth = (2.4f / s).coerceAtLeast(0.2f)
            canvas.drawPath(paths[selected], highlightPaint)
        }
        canvas.restore()
    }

    private fun ensureBorderPath(): Path? {
        borderPath?.let { return it }
        synchronized(borderLock) {
            borderPath?.let { return it }
            if (Geo.paths.isEmpty()) return null
            val combined = Path()
            val provinces = WorldContent.provinces
            for (i in Geo.paths.indices) {
                if (provinces[i].isLand) combined.addPath(Geo.paths[i])
            }
            borderPath = combined
            return combined
        }
    }

    fun provinceAt(sx: Float, sy: Float): Int {
        if (scale <= 0f) return -1
        val wx = (sx - tx) / scale
        val wy = (sy - ty) / scale
        val provinces = WorldContent.provinces
        val bounds = Geo.bounds
        var bestLand = -1
        var bestLandArea = Float.MAX_VALUE
        var bestOther = -1
        var bestOtherArea = Float.MAX_VALUE
        for (i in provinces.indices) {
            val b = bounds[i]
            if (wx < b.left || wx > b.right || wy < b.top || wy > b.bottom) continue
            if (!pointInProvince(i, wx, wy)) continue
            val p = provinces[i]
            if (p.isLand) {
                if (p.area < bestLandArea) {
                    bestLandArea = p.area
                    bestLand = i
                }
            } else if (p.area < bestOtherArea) {
                bestOtherArea = p.area
                bestOther = i
            }
        }
        return bestLand
    }

    private fun pointInProvince(index: Int, x: Float, y: Float): Boolean {
        val polys = WorldContent.provinces[index].polys
        for (poly in polys) {
            if (pointInPolygon(poly, x, y)) return true
        }
        return false
    }

    private fun pointInPolygon(poly: FloatArray, x: Float, y: Float): Boolean {
        var inside = false
        var j = poly.size - 2
        var i = 0
        while (i < poly.size) {
            val xi = poly[i]
            val yi = poly[i + 1]
            val xj = poly[j]
            val yj = poly[j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                inside = !inside
            }
            j = i
            i += 2
        }
        return inside
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(renderTask)
        super.onDetachedFromWindow()
    }
}
