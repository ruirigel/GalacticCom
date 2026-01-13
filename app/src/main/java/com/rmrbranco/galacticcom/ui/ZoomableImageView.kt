package com.rmrbranco.galacticcom.ui

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr), View.OnTouchListener,
    ScaleGestureDetector.OnScaleGestureListener {

    private var mode = NONE
    private val matrix = Matrix()
    private val last = PointF()
    private val start = PointF()
    private val m = FloatArray(9)

    private var viewWidth = 0
    private var viewHeight = 0
    private var saveScale = 1f
    private var origWidth = 0f
    private var origHeight = 0f
    private var oldMeasuredWidth = 0
    private var oldMeasuredHeight = 0

    private var scaleDetector: ScaleGestureDetector? = null
    private var gestureDetector: GestureDetector? = null

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }

    init {
        super.setClickable(true)
        scaleDetector = ScaleGestureDetector(context, this)
        matrix.setTranslate(1f, 1f)
        imageMatrix = matrix
        scaleType = ScaleType.MATRIX
        setOnTouchListener(this)
        
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val origScale = saveScale
                val targetScale: Float

                if (saveScale == 1f) {
                    targetScale = 3f
                    matrix.postScale(targetScale, targetScale, e.x, e.y)
                    saveScale = targetScale
                } else {
                    targetScale = 1f
                    val scaleFactor = targetScale / origScale
                    matrix.postScale(scaleFactor, scaleFactor, e.x, e.y)
                    saveScale = targetScale
                }
                fixTrans()
                imageMatrix = matrix
                invalidate()
                return true
            }
        })
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        scaleDetector?.onTouchEvent(event)
        gestureDetector?.onTouchEvent(event)

        val curr = PointF(event.x, event.y)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                last.set(curr)
                start.set(last)
                mode = DRAG
            }

            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG) {
                    val deltaX = curr.x - last.x
                    val deltaY = curr.y - last.y
                    val fixTransX = getFixDragTrans(deltaX, viewWidth.toFloat(), origWidth * saveScale)
                    val fixTransY = getFixDragTrans(deltaY, viewHeight.toFloat(), origHeight * saveScale)
                    matrix.postTranslate(fixTransX, fixTransY)
                    fixTrans()
                    last.set(curr.x, curr.y)
                }
            }

            MotionEvent.ACTION_UP -> {
                mode = NONE
                val xDiff = abs(curr.x - start.x).toInt()
                val yDiff = abs(curr.y - start.y).toInt()
                if (xDiff < 3 && yDiff < 3) performClick()
            }

            MotionEvent.ACTION_POINTER_UP -> mode = NONE
        }

        imageMatrix = matrix
        invalidate()
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        mode = ZOOM
        return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        var mScaleFactor = detector.scaleFactor
        val prevScale = saveScale
        saveScale *= mScaleFactor

        if (saveScale > 5f) {
            saveScale = 5f
            mScaleFactor = 5f / prevScale
        } else if (saveScale < 1f) {
            saveScale = 1f
            mScaleFactor = 1f / prevScale
        }

        if (origWidth * saveScale <= viewWidth || origHeight * saveScale <= viewHeight) {
            matrix.postScale(mScaleFactor, mScaleFactor, viewWidth / 2f, viewHeight / 2f)
        } else {
            matrix.postScale(mScaleFactor, mScaleFactor, detector.focusX, detector.focusY)
        }

        fixTrans()
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {}

    private fun fixTrans() {
        matrix.getValues(m)
        val transX = m[Matrix.MTRANS_X]
        val transY = m[Matrix.MTRANS_Y]

        val fixTransX = getFixTrans(transX, viewWidth.toFloat(), origWidth * saveScale)
        val fixTransY = getFixTrans(transY, viewHeight.toFloat(), origHeight * saveScale)

        if (fixTransX != 0f || fixTransY != 0f)
            matrix.postTranslate(fixTransX, fixTransY)
    }

    private fun getFixTrans(trans: Float, viewSize: Float, contentSize: Float): Float {
        val minTrans: Float
        val maxTrans: Float

        if (contentSize <= viewSize) {
            minTrans = 0f
            maxTrans = viewSize - contentSize
        } else {
            minTrans = viewSize - contentSize
            maxTrans = 0f
        }

        if (trans < minTrans)
            return -trans + minTrans
        if (trans > maxTrans)
            return -trans + maxTrans
        return 0f
    }

    private fun getFixDragTrans(delta: Float, viewSize: Float, contentSize: Float): Float {
        if (contentSize <= viewSize) {
            return 0f
        }
        return delta
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        viewWidth = MeasureSpec.getSize(widthMeasureSpec)
        viewHeight = MeasureSpec.getSize(heightMeasureSpec)

        if (oldMeasuredHeight == viewWidth && oldMeasuredHeight == viewHeight || viewWidth == 0 || viewHeight == 0)
            return

        oldMeasuredHeight = viewHeight
        oldMeasuredWidth = viewWidth

        if (saveScale == 1f) {
            // Fit Center
            val drawable = drawable
            if (drawable == null || drawable.intrinsicWidth == 0 || drawable.intrinsicHeight == 0)
                return
            
            val bmWidth = drawable.intrinsicWidth
            val bmHeight = drawable.intrinsicHeight

            val scaleX = viewWidth.toFloat() / bmWidth
            val scaleY = viewHeight.toFloat() / bmHeight
            val scale = scaleX.coerceAtMost(scaleY)
            
            matrix.setScale(scale, scale)

            // Center the image
            var redundantYSpace = viewHeight.toFloat() - (scale * bmHeight)
            var redundantXSpace = viewWidth.toFloat() - (scale * bmWidth)
            redundantYSpace /= 2f
            redundantXSpace /= 2f

            matrix.postTranslate(redundantXSpace, redundantYSpace)

            origWidth = viewWidth - 2 * redundantXSpace
            origHeight = viewHeight - 2 * redundantYSpace
            
            imageMatrix = matrix
        }
        fixTrans()
    }
}