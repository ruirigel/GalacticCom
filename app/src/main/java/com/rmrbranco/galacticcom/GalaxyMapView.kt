package com.rmrbranco.galacticcom

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withSave
import kotlin.math.*
import kotlin.random.Random

class GalaxyMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onGalaxyClickListener: ((GalaxyInfo) -> Unit)? = null

    private var galaxies: List<GalaxyInfo> = emptyList()
    private val galaxyPositions = mutableMapOf<String, PointF>()
    private val galaxyScales = mutableMapOf<String, Float>()
    private val galaxyRotations = mutableMapOf<String, Float>()
    private val galaxyFlips = mutableMapOf<String, Boolean>()

    private val galaxyBitmap: Bitmap? by lazy {
        val drawable = ContextCompat.getDrawable(context, R.drawable.logotipo)
        if (drawable != null) {
            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } else {
            null
        }
    }

    private val paint: Paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val neonCyanColor = ContextCompat.getColor(context, R.color.neon_cyan)
            colorFilter = PorterDuffColorFilter(neonCyanColor, PorterDuff.Mode.SRC_IN)
        }
    }

    private val textPaint: Paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.neon_cyan)
            textSize = 75f
            textAlign = Paint.Align.CENTER
            try {
                typeface = ResourcesCompat.getFont(context, R.font.orbitron)
            } catch (e: Exception) {
                // Fallback if font loading fails
                typeface = Typeface.MONOSPACE
            }
        }
    }

    private var scaleFactor = 1.0f
    private var offsetX = 0.0f
    private var offsetY = 0.0f

    private var isInitialPanComplete = false

    // Scroller for implementing fling behavior
    private val scroller = OverScroller(context, DecelerateInterpolator())

    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scroller.forceFinished(true) // Stop any fling animation
            val oldScaleFactor = scaleFactor
            scaleFactor *= detector.scaleFactor
            scaleFactor = max(0.05f, min(scaleFactor, 3.0f))
            offsetX = detector.focusX - (detector.focusX - offsetX) * (scaleFactor / oldScaleFactor)
            offsetY = detector.focusY - (detector.focusY - offsetY) * (scaleFactor / oldScaleFactor)
            invalidate()
            return true
        }
    }

    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            scroller.forceFinished(true) // Stop any fling animation when a new gesture starts
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            offsetX -= distanceX
            offsetY -= distanceY
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            scroller.fling(
                offsetX.toInt(), offsetY.toInt(),
                velocityX.toInt() / 2, velocityY.toInt() / 2, // Reduced velocity for a less aggressive fling
                Int.MIN_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MAX_VALUE
            )
            postInvalidateOnAnimation() // Start the animation
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val currentBitmap = galaxyBitmap ?: return false
            val worldX = (e.x - offsetX) / scaleFactor
            val worldY = (e.y - offsetY) / scaleFactor

            val halfW = currentBitmap.width / 2f
            val halfH = currentBitmap.height / 2f

            for (galaxy in galaxies.reversed()) {
                val pos = galaxyPositions[galaxy.name] ?: continue
                val scale = galaxyScales[galaxy.name] ?: continue
                val rotation = galaxyRotations[galaxy.name] ?: 0f
                val isFlipped = galaxyFlips[galaxy.name] ?: false

                val relX = worldX - pos.x
                val relY = worldY - pos.y

                val angleRad = -rotation * (Math.PI / 180f).toFloat()
                val cosA = cos(angleRad)
                val sinA = sin(angleRad)

                var localX = (relX * cosA - relY * sinA) / scale
                val localY = (relX * sinA + relY * cosA) / scale
                if (isFlipped) localX = -localX

                if (abs(localX) <= halfW && abs(localY) <= halfH) {
                    onGalaxyClickListener?.invoke(galaxy)
                    return true
                }
            }
            return super.onSingleTapUp(e)
        }
    }

    private val scaleDetector = ScaleGestureDetector(context, scaleListener)
    private val gestureDetector = GestureDetector(context, gestureListener)

    override fun computeScroll() {
        super.computeScroll()
        if (scroller.computeScrollOffset()) {
            offsetX = scroller.currX.toFloat()
            offsetY = scroller.currY.toFloat()
            postInvalidateOnAnimation() // Keep the animation going until it's finished
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        tryCenterInitialGalaxy()
    }

    fun setGalaxies(newGalaxies: List<GalaxyInfo>) {
        val galaxySetChanged = galaxies.map { it.name }.toSet() != newGalaxies.map { it.name }.toSet()
        galaxies = newGalaxies

        if (galaxySetChanged) {
            val newKeys = galaxies.map { it.name }.toSet()
            galaxyPositions.keys.retainAll(newKeys)
            galaxyScales.keys.retainAll(newKeys)
            galaxyRotations.keys.retainAll(newKeys)
            galaxyFlips.keys.retainAll(newKeys)

            galaxies.forEach { galaxy ->
                if (!galaxyPositions.containsKey(galaxy.name)) {
                    val random = Random(galaxy.name.hashCode().toLong())
                    val radius = parseDimensionToDistance(galaxy.dimension, random)
                    val angle = random.nextFloat() * 2 * Math.PI.toFloat()
                    galaxyPositions[galaxy.name] = PointF(radius * cos(angle), radius * sin(angle))
                    galaxyScales[galaxy.name] = parseDimensionToScale(galaxy.dimension, random)
                    galaxyRotations[galaxy.name] = random.nextFloat() * 360f
                    galaxyFlips[galaxy.name] = random.nextBoolean()
                }
            }
        }
        tryCenterInitialGalaxy()
        invalidate()
    }

    private fun tryCenterInitialGalaxy() {
        if (isInitialPanComplete || galaxies.isEmpty() || width == 0 || height == 0) {
            return
        }

        val galaxyToCenterOn = galaxies.firstOrNull { it.isCurrentGalaxy } ?: galaxies.first()
        val centerPos = galaxyPositions[galaxyToCenterOn.name] ?: return

        scaleFactor = 0.8f
        offsetX = (width / 2f) - (centerPos.x * scaleFactor)
        offsetY = (height / 2f) - (centerPos.y * scaleFactor)
        isInitialPanComplete = true
    }

    private fun parseDimensionToDistance(dimension: String, random: Random): Float {
        return try {
            dimension.replace(",", "").split(" ")[0].toFloat() * 0.08f
        } catch (e: Exception) { random.nextFloat() * 4000f }
    }

    private fun parseDimensionToScale(dimension: String, random: Random): Float {
        val minDimension = 5000f
        val maxDimension = 250000f
        val minScale = 0.5f
        val maxScale = 3.0f
        return try {
            val lightYears = dimension.replace(",", "").split(" ")[0].toFloat().coerceIn(minDimension, maxDimension)
            minScale + ((lightYears - minDimension) / (maxDimension - minDimension)) * (maxScale - minScale)
        } catch (e: Exception) { random.nextFloat() * 2.5f + 0.5f }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return false
        var handled = scaleDetector.onTouchEvent(event)
        handled = gestureDetector.onTouchEvent(event) || handled
        return handled || super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentBitmap = galaxyBitmap ?: return
        if (!isInitialPanComplete) return

        val halfW = currentBitmap.width / 2f
        val halfH = currentBitmap.height / 2f

        canvas.withSave {
            translate(offsetX, offsetY)
            scale(scaleFactor, scaleFactor)

            // 1. Draw all galaxies
            galaxies.forEach { galaxy ->
                val pos = galaxyPositions[galaxy.name] ?: return@forEach
                val scale = galaxyScales[galaxy.name] ?: 1f
                val rotation = galaxyRotations[galaxy.name] ?: 0f
                val isFlipped = galaxyFlips[galaxy.name] == true

                withSave {
                    translate(pos.x, pos.y)
                    rotate(rotation)
                    if (isFlipped) scale(-1f, 1f)
                    scale(scale, scale)
                    drawBitmap(currentBitmap, -halfW, -halfH, paint)
                }
            }

            // 2. Draw all names, only if zoomed in enough
            if (scaleFactor > 0.4f) {
                galaxies.forEach { galaxy ->
                    val pos = galaxyPositions[galaxy.name] ?: return@forEach
                    val scale = galaxyScales[galaxy.name] ?: 1f
                    val textYOffset = (halfH * scale) + textPaint.textSize / scaleFactor
                    drawText(galaxy.name, pos.x, pos.y + textYOffset, textPaint)
                }
            }
        }
    }
}
