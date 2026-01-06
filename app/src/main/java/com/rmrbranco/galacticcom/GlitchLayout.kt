package com.rmrbranco.galacticcom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
import kotlin.random.Random

class GlitchLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var glitchIntensity = 0f
    private val paint = Paint()
    private val random = Random.Default
    private var bufferBitmap: Bitmap? = null
    private var bufferCanvas: Canvas? = null
    
    // Recovery mechanism
    private var nextAttemptTime = 0L
    private val RETRY_DELAY_MS = 10000L // 10 seconds cooldown after OOM

    fun setIntensity(intensity: Float) {
        glitchIntensity = intensity.coerceIn(0f, 1f)
        // Always invalidate to allow redrawing, which manages logic
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        recycleBuffer()
    }

    private fun recycleBuffer() {
        try {
            bufferBitmap?.recycle()
        } catch (e: Exception) {
            // Ignore recycling errors
        } finally {
            bufferBitmap = null
            bufferCanvas = null
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val currentTime = System.currentTimeMillis()

        // 1. Check if we are in "Cool Down" mode due to previous OutOfMemory
        if (currentTime < nextAttemptTime) {
            super.dispatchDraw(canvas)
            return
        }

        // 2. Optimization: If intensity is near zero, skip processing and draw normally
        // unless we need to clear the buffer to save memory
        if (glitchIntensity <= 0.01f) {
            super.dispatchDraw(canvas)
            // Optional: aggressive memory saving - if we are idle for too long, we could clear buffer here
            return
        }

        val w = width
        val h = height

        if (w == 0 || h == 0) return

        // 3. Initialize Buffer (Safely)
        if (bufferBitmap == null || bufferBitmap?.width != w || bufferBitmap?.height != h) {
            recycleBuffer()
            
            try {
                bufferBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bufferCanvas = Canvas(bufferBitmap!!)
            } catch (e: OutOfMemoryError) {
                Log.e("GlitchLayout", "OOM: Cannot create glitch buffer. Retrying in ${RETRY_DELAY_MS/1000}s", e)
                nextAttemptTime = currentTime + RETRY_DELAY_MS
                recycleBuffer() // Ensure cleanup
                super.dispatchDraw(canvas) // Draw normal without glitch
                return
            }
        }
        
        // 4. Capture View Content
        // Use try-catch for drawing as well, just in case
        try {
            bufferBitmap!!.eraseColor(Color.TRANSPARENT)
            super.dispatchDraw(bufferCanvas!!)
        } catch (e: Exception) {
            Log.e("GlitchLayout", "Error drawing to buffer", e)
            super.dispatchDraw(canvas)
            return
        } catch (e: OutOfMemoryError) {
            Log.e("GlitchLayout", "OOM drawing to buffer", e)
            nextAttemptTime = currentTime + RETRY_DELAY_MS
            recycleBuffer()
            super.dispatchDraw(canvas)
            return
        }

        // 5. Render Glitch Effects
        // Base Image
        if (glitchIntensity < 0.9f) {
            canvas.drawBitmap(bufferBitmap!!, 0f, 0f, null)
        } else {
            canvas.drawColor(if (random.nextBoolean()) Color.BLACK else Color.rgb(20, 0, 20))
        }

        // Slices (Tearing)
        val numSlices = (5 + 30 * glitchIntensity).toInt()
        for (i in 0 until numSlices) {
            val sliceHeight = random.nextInt(5, (60 * glitchIntensity + 20).toInt())
            val sliceY = random.nextInt(0, h - sliceHeight)
            val shift = (random.nextFloat() - 0.5f) * w * 0.3f * glitchIntensity
            
            val srcRect = Rect(0, sliceY, w, sliceY + sliceHeight)
            val dstRect = Rect(shift.toInt(), sliceY, w + shift.toInt(), sliceY + sliceHeight)
            
            canvas.drawBitmap(bufferBitmap!!, srcRect, dstRect, null)
        }

        // Scanlines
        paint.style = Paint.Style.FILL
        val numLines = (random.nextInt(5, 15) * glitchIntensity).toInt()
        for (i in 0 until numLines) {
             val lineHeight = random.nextInt(2, 10)
             val lineY = random.nextInt(0, h)
             
             val colorType = random.nextInt(5)
             paint.color = when(colorType) {
                 0 -> Color.CYAN
                 1 -> Color.MAGENTA
                 2 -> Color.WHITE
                 3 -> Color.GREEN
                 else -> Color.RED
             }
             paint.alpha = (200 * glitchIntensity).toInt()
             
             canvas.drawRect(0f, lineY.toFloat(), w.toFloat(), (lineY + lineHeight).toFloat(), paint)
        }
    }
}