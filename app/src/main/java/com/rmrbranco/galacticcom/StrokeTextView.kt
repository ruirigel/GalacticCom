package com.rmrbranco.galacticcom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class StrokeTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var strokeWidth = 5f
    private var strokeColor = Color.BLACK
    private var isDrawing = false

    override fun onDraw(canvas: Canvas) {
        if (isDrawing) {
            super.onDraw(canvas)
            return
        }

        isDrawing = true
        
        // Guarda as cores e configurações originais
        val originalTextColor = currentTextColor
        
        // As propriedades de sombra não são diretamente acessíveis de forma fácil para restauração via getters simples do paint em todas as APIs de forma consistente,
        // mas o TextView gerencia isso. Se limparmos a layer de sombra no paint, precisamos garantir que ela volte.
        // Uma forma mais segura sem mexer na sombra do paint manualmente é desenhar o stroke e depois o fill.
        // O stroke geralmente não precisa de sombra.
        
        // Configura o Paint para Stroke
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        setTextColor(strokeColor)
        
        // Desenha o contorno
        super.onDraw(canvas)
        
        // Restaura para o texto normal (Fill)
        paint.style = Paint.Style.FILL
        setTextColor(originalTextColor)
        
        // Desenha o texto normal por cima
        super.onDraw(canvas)
        
        isDrawing = false
    }
}
