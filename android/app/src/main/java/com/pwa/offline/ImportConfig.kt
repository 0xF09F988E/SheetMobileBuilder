package com.pwa.offline

object ImportConfig {
    const val batchSize = 500
    const val maxConflictPreview = 20
    const val defaultHeaderRowIndex = 0
    val headerRowOptions: List<Int> = (1..10).toList()
}
