package com.rmrbranco.galacticcom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import java.security.MessageDigest

object AlienAvatarGenerator {

    fun generate(seed: String, width: Int, height: Int): Bitmap {
        val hash = sha256(seed)
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        // Background color from hash
        val bgColor = Color.rgb(hash[0].toInt() and 0xFF, hash[1].toInt() and 0xFF, hash[2].toInt() and 0xFF)
        canvas.drawColor(bgColor)

        // Foreground color from hash
        val fgColor = Color.rgb(hash[3].toInt() and 0xFF, hash[4].toInt() and 0xFF, hash[5].toInt() and 0xFF)
        paint.color = fgColor

        val gridSize = 5 // Create a 5x5 grid for the avatar
        val pixelSize = width / (gridSize + 2).toFloat() // with padding

        for (i in 0 until gridSize) {
            for (j in 0 until gridSize / 2 + 1) {
                // Use hash bytes to decide whether to draw a pixel
                val byteIndex = (i * (gridSize / 2 + 1) + j) % hash.size
                if (hash[byteIndex].toInt() and 0b1 == 1) {
                    val x = (j + 1) * pixelSize
                    val y = (i + 1) * pixelSize
                    canvas.drawRect(x, y, x + pixelSize, y + pixelSize, paint)

                    // Mirror for symmetry
                    val mirroredX = (gridSize - j) * pixelSize
                    canvas.drawRect(mirroredX, y, mirroredX + pixelSize, y + pixelSize, paint)
                }
            }
        }

        return bitmap
    }

    private fun sha256(base: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(base.toByteArray())
    }
}