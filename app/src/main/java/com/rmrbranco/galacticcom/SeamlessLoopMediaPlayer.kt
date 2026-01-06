package com.rmrbranco.galacticcom

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.animation.LinearInterpolator

class SeamlessLoopMediaPlayer(private val context: Context, private val resId: Int) {
    private var currentPlayer: MediaPlayer? = null
    private var nextPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private val defaultCrossfadeDuration = 3000L // 3 segundos de crossfade
    private var crossfadeRunnable: Runnable? = null
    private var currentFadeOut: ValueAnimator? = null
    private var currentFadeIn: ValueAnimator? = null

    init {
        currentPlayer = createPlayer()
        nextPlayer = createPlayer()
    }

    private fun createPlayer(): MediaPlayer? {
        val mp = MediaPlayer.create(context, resId)
        mp?.setVolume(1f, 1f)
        return mp
    }

    fun start() {
        if (isPlaying) return
        isPlaying = true

        if (currentPlayer == null) currentPlayer = createPlayer()
        if (nextPlayer == null) nextPlayer = createPlayer()

        // Se o player atual não estiver tocando, inicie-o
        if (currentPlayer?.isPlaying == false) {
            currentPlayer?.setVolume(1f, 1f)
            currentPlayer?.start()
        }
        
        scheduleNextCrossfade()
    }

    private fun scheduleNextCrossfade() {
        val duration = currentPlayer?.duration ?: return
        
        // Ajusta duração do crossfade se o áudio for curto
        val crossfadeDuration = if (duration < defaultCrossfadeDuration * 2) {
            (duration / 3).toLong()
        } else {
            defaultCrossfadeDuration
        }

        // Calcula quando iniciar o crossfade
        // currentPosition pode não ser 0 se foi resumido, então calculamos o tempo restante
        val timeRemaining = duration - (currentPlayer?.currentPosition ?: 0)
        val delay = timeRemaining - crossfadeDuration
        
        val safeDelay = if (delay < 0) 0 else delay.toLong()

        crossfadeRunnable = Runnable {
            performCrossfade(crossfadeDuration)
        }
        handler.postDelayed(crossfadeRunnable!!, safeDelay)
    }

    private fun performCrossfade(duration: Long) {
        if (!isPlaying) return
        if (nextPlayer == null) nextPlayer = createPlayer()
        
        // Prepara o próximo player
        try {
            nextPlayer?.seekTo(0)
            nextPlayer?.setVolume(0f, 0f)
            nextPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        // Cancela animações anteriores se houver
        currentFadeIn?.cancel()
        currentFadeOut?.cancel()

        // Fade In Next
        currentFadeIn = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            addUpdateListener { 
                val v = it.animatedValue as Float
                try { nextPlayer?.setVolume(v, v) } catch (e: Exception) {}
            }
            start()
        }

        // Fade Out Current
        currentFadeOut = ValueAnimator.ofFloat(1f, 0f).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            addUpdateListener { 
                val v = it.animatedValue as Float
                try { currentPlayer?.setVolume(v, v) } catch (e: Exception) {}
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Troca os players
                    val temp = currentPlayer
                    currentPlayer = nextPlayer
                    nextPlayer = temp
                    
                    // Reseta o antigo player (agora nextPlayer)
                    try {
                        if (nextPlayer?.isPlaying == true) {
                            nextPlayer?.pause()
                        }
                        nextPlayer?.seekTo(0)
                        nextPlayer?.setVolume(1f, 1f)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Agenda o próximo ciclo
                    scheduleNextCrossfade()
                }
            })
            start()
        }
    }

    fun pause() {
        isPlaying = false
        handler.removeCallbacksAndMessages(null)
        currentFadeIn?.cancel()
        currentFadeOut?.cancel()
        try {
            if (currentPlayer?.isPlaying == true) currentPlayer?.pause()
            if (nextPlayer?.isPlaying == true) nextPlayer?.pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun isPlaying(): Boolean {
        return isPlaying
    }

    fun release() {
        pause()
        currentPlayer?.release()
        nextPlayer?.release()
        currentPlayer = null
        nextPlayer = null
    }
}
