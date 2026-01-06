package com.rmrbranco.galacticcom

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat

class EdgeLightingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs) {

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var animator: ValueAnimator? = null
    private var progress = 0f
    private var isSendAnimation = false

    private val leftSegmentPath = Path()
    private val rightSegmentPath = Path()
    private val leftPathMeasure = PathMeasure()
    private val rightPathMeasure = PathMeasure()
    private val fullLeftPath = Path()
    private val fullRightPath = Path()

    private var pathLength = 0f

    init {
        val neonCyan = ContextCompat.getColor(context, R.color.neon_cyan)

        // A single, wide, and very diffuse glow
        glowPaint.color = neonCyan
        glowPaint.strokeWidth = 22f
        glowPaint.style = Paint.Style.STROKE
        glowPaint.maskFilter = BlurMaskFilter(25f, BlurMaskFilter.Blur.NORMAL)
        // Use ADD blend mode to create a much stronger, more vibrant light effect
        glowPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        setLayerType(LAYER_TYPE_SOFTWARE, glowPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (animator?.isRunning != true || pathLength <= 0) return

        val snakeLength = pathLength * 0.30f // Total visible length of the snake
        val totalAnimatedDistance = pathLength + snakeLength
        val head = progress * totalAnimatedDistance

        // Calculate the start and end of the segment to draw
        val start = (head - snakeLength).coerceAtLeast(0f)
        val stop = head.coerceAtMost(pathLength)

        // Draw the single glow segment
        if (start < stop) {
            drawSegment(canvas, start, stop, glowPaint)
        }
    }

    private fun drawSegment(canvas: Canvas, start: Float, stop: Float, paint: Paint) {
        leftSegmentPath.reset()
        rightSegmentPath.reset()
        leftPathMeasure.getSegment(start, stop, leftSegmentPath, true)
        rightPathMeasure.getSegment(start, stop, rightSegmentPath, true)
        canvas.drawPath(leftSegmentPath, paint)
        canvas.drawPath(rightSegmentPath, paint)
    }

    fun startAnimation(isSend: Boolean) {
        animator?.cancel()
        this.isSendAnimation = isSend

        if (width == 0 || height == 0) {
            post { startAnimation(isSend) } // Wait until the view is measured
            return
        }

        val w = width.toFloat()
        val h = height.toFloat()

        if (isSend) {
            fullLeftPath.reset()
            fullLeftPath.moveTo(w / 2, h)
            fullLeftPath.lineTo(0f, h)
            fullLeftPath.lineTo(0f, 0f)

            fullRightPath.reset()
            fullRightPath.moveTo(w / 2, h)
            fullRightPath.lineTo(w, h)
            fullRightPath.lineTo(w, 0f)
        } else {
            fullLeftPath.reset()
            fullLeftPath.moveTo(w / 2, 0f)
            fullLeftPath.lineTo(0f, 0f)
            fullLeftPath.lineTo(0f, h)

            fullRightPath.reset()
            fullRightPath.moveTo(w / 2, 0f)
            fullRightPath.lineTo(w, 0f)
            fullRightPath.lineTo(w, h)
        }

        leftPathMeasure.setPath(fullLeftPath, false)
        rightPathMeasure.setPath(fullRightPath, false)
        pathLength = leftPathMeasure.length

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
        }
        animator?.start()
    }
}
