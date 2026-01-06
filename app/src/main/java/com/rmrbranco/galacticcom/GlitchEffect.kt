package com.rmrbranco.galacticcom

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import java.util.Random

object GlitchEffect {
    private val random = Random()
    private val handler = Handler(Looper.getMainLooper())

    fun apply(view: View, intensity: Float = 1.0f) {
        val duration = (50 + random.nextInt(100)).toLong() // Very short, snappy duration for lines
        
        // Generate random jitter values focused on Horizontal tearing
        val numJitters = 4
        val xValues = FloatArray(numJitters + 2)
        
        // Start state
        xValues[0] = 0f
        
        for (i in 1..numJitters) {
            // Horizontal Line Glitch: Sharp left/right shifts
            // Intensity determines how far the "line" shifts
            xValues[i] = (random.nextFloat() - 0.5f) * 40f * intensity 
        }
        
        // End state (reset)
        xValues[numJitters + 1] = 0f

        val pvhX = PropertyValuesHolder.ofFloat(View.TRANSLATION_X, *xValues)

        ObjectAnimator.ofPropertyValuesHolder(view, pvhX).apply {
            this.duration = duration
            interpolator = null // Linear/Sharp movement
            start()
        }
        
        // Occasional flicker only for text views or specific elements, 
        // to avoid blinking the whole background if not desired.
        // But for VHS style, slight brightness flicker is okay.
        if (random.nextFloat() > 0.7f) {
            val originalAlpha = view.alpha
            view.alpha = originalAlpha * (0.8f + random.nextFloat() * 0.2f)
            handler.postDelayed({ view.alpha = originalAlpha }, duration)
        }
    }
    
    fun applyTextGlitch(textView: TextView, originalText: String? = null) {
        val currentText = originalText ?: textView.text.toString()
        val originalColor = textView.textColors
        
        // Simple character replacement glitch
        val corruptedText = StringBuilder(currentText)
        val glitchChars = "#@$%&!?"
        
        val glitchCount = random.nextInt(2) + 1
        for (i in 0 until glitchCount) {
            if (corruptedText.isNotEmpty()) {
                val index = random.nextInt(corruptedText.length)
                corruptedText.setCharAt(index, glitchChars[random.nextInt(glitchChars.length)])
            }
        }
        
        textView.text = corruptedText.toString()
        textView.setTextColor(Color.parseColor("#00FFFF")) 
        apply(textView)
        
        handler.postDelayed({
            textView.text = currentText
            textView.setTextColor(originalColor)
        }, 100)
    }
}