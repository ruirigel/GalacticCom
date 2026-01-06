package com.rmrbranco.galacticcom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.random.Random

class StarrySkyView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val stars = mutableListOf<Star>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random(System.currentTimeMillis())
    private val baseColor: Int

    init {
        baseColor = ContextCompat.getColor(context, R.color.star_color)
    }

    private data class Star(val x: Float, val y: Float, val radius: Float, val alpha: Int)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            createStars(w, h, 200) // Create 200 stars
        }
    }

    private fun createStars(width: Int, height: Int, count: Int) {
        stars.clear()
        for (i in 0 until count) {
            val x = random.nextFloat() * width
            val y = random.nextFloat() * height
            val radius = random.nextFloat() * 2f + 0.5f // Radius between 0.5 and 2.5
            val alpha = random.nextInt(200) + 55     // Alpha between 55 and 255
            stars.add(Star(x, y, radius, alpha))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)

        for (star in stars) {
            paint.color = Color.argb(star.alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            canvas.drawCircle(star.x, star.y, star.radius, paint)
        }
    }
}
