package com.pwa.offline

import android.os.SystemClock
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter

class ExportService(
    private val databaseHelper: AppDatabaseHelper
) {
    private val metadataHeaders = listOf(
        "record_id",
        "created_at",
        "updated_at",
        "review_status",
        "review_action",
        "reviewed_at",
        "changed_fields_text"
    )

    fun exportToCsv(
        outputStream: OutputStream,
        collection: CollectionOption,
        fields: List<FieldDefinition>,
        criterion: ExportCriterion,
        delimiter: String,
        onProgress: (processedRows: Int, totalRows: Int, elapsedMs: Long) -> Unit,
        onCancellationCheck: () -> Unit = {}
    ): ExportExecutionResult {
        val totalRows = databaseHelper.countRecordsForExport(collection.id, criterion)
        val fileName = ExportConfig.buildFileName(collection, criterion)
        val countingStream = CountingOutputStream(outputStream)
        val writer = BufferedWriter(OutputStreamWriter(countingStream, Charsets.UTF_8))
        var exportedRows = 0
        var elapsedMs: Long

        onProgress(0, totalRows, 0L)

        try {
            val startedAt = SystemClock.elapsedRealtime()
            writer.write('\uFEFF'.code)
            writer.write((metadataHeaders + fields.map { it.displayName }).joinToString(delimiter) { escapeCsv(it, delimiter) })
            writer.newLine()

            databaseHelper.streamExportRows(
                collectionId = collection.id,
                fields = fields,
                criterion = criterion,
                chunkSize = ExportConfig.rowChunkSize,
                onRow = { values ->
                    onCancellationCheck()
                    writer.write(values.joinToString(delimiter) { escapeCsv(it, delimiter) })
                    writer.newLine()
                    exportedRows += 1
                    if (exportedRows == totalRows || exportedRows % ExportConfig.progressStep == 0) {
                        writer.flush()
                        onProgress(
                            exportedRows,
                            totalRows,
                            SystemClock.elapsedRealtime() - startedAt
                        )
                    }
                },
                onCancellationCheck = onCancellationCheck
            )
            writer.flush()
            elapsedMs = SystemClock.elapsedRealtime() - startedAt
        } finally {
            writer.close()
        }

        onProgress(exportedRows, totalRows, elapsedMs)
        return ExportExecutionResult(
            fileName = fileName,
            exportedRows = exportedRows,
            elapsedMs = elapsedMs,
            bytesWritten = countingStream.bytesWritten
        )
    }

    private fun escapeCsv(value: String, delimiter: String): String {
        val sanitized = value.replace("\r\n", "\n").replace('\r', '\n')
        val mustQuote = sanitized.contains(delimiter) ||
            sanitized.contains('"') ||
            sanitized.contains('\n')
        if (!mustQuote) return sanitized
        return "\"${sanitized.replace("\"", "\"\"")}\""
    }
}

private class CountingOutputStream(
    private val delegate: OutputStream
) : OutputStream() {
    var bytesWritten: Long = 0L
        private set

    override fun write(b: Int) {
        delegate.write(b)
        bytesWritten += 1
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
        bytesWritten += len.toLong()
    }

    override fun flush() {
        delegate.flush()
    }

    override fun close() {
        delegate.close()
    }
}
