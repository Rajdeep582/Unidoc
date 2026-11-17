package com.rajdeep.unidoc.core.ui.format

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ln
import kotlin.math.pow

/** Human file size: 0 B, 812 KB, 3.4 MB. Uses 1024 units, one decimal above KB. */
fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
    val value = bytes / 1024.0.pow(exp)
    return if (exp == 1) {
        String.format(Locale.US, "%.0f %s", value, units[exp - 1])
    } else {
        String.format(Locale.US, "%.1f %s", value, units[exp - 1])
    }
}

/** "Just now", "12 min ago", "3 h ago", "Yesterday", else a medium date. */
fun formatRelativeTime(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val diff = (nowMillis - epochMillis).coerceAtLeast(0)
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)} min ago"
        diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)} h ago"
        diff < TimeUnit.DAYS.toMillis(2) -> "Yesterday"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))
    }
}
