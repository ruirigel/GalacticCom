package com.rmrbranco.galacticcom

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeUtils {

    private const val SECOND_MILLIS = 1000
    private const val MINUTE_MILLIS = 60 * SECOND_MILLIS
    private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    private const val DAY_MILLIS = 24 * HOUR_MILLIS

    fun getRelativeTimeSpanString(time: Long): String {
        val now = System.currentTimeMillis()
        if (time > now || time <= 0) {
            return "in the future"
        }

        val diff = now - time
        return when {
            diff < MINUTE_MILLIS -> "Just now"
            diff < 2 * MINUTE_MILLIS -> "1m ago"
            diff < HOUR_MILLIS -> "${diff / MINUTE_MILLIS}m ago"
            diff < 2 * HOUR_MILLIS -> "1h ago"
            diff < DAY_MILLIS -> "${diff / HOUR_MILLIS}h ago"
            diff < 2 * DAY_MILLIS -> "Yesterday"
            else -> {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
                sdf.format(time)
            }
        }
    }

    fun formatDuration(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(hours)
        return String.format("%02d:%02d", hours, minutes)
    }
}