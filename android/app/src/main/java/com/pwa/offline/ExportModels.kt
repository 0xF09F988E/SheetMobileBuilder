package com.pwa.offline

data class ExportProgressState(
    val exportedRows: Int = 0,
    val metrics: ProgressState = ProgressState()
)

data class ExportExecutionResult(
    val fileName: String,
    val exportedRows: Int,
    val elapsedMs: Long,
    val bytesWritten: Long
)

data class ShareExportArtifact(
    val filePath: String,
    val fileName: String
)
