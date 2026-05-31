package com.pwa.offline

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CloneDataConfig {
    const val mimeType = "application/octet-stream"
    const val fileExtension = ".pwa-clone"
    private const val filePrefix = "patrigo-clone"

    fun buildSuggestedFileName(now: Date = Date()): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US)
        return "$filePrefix-${formatter.format(now)}$fileExtension"
    }
}
