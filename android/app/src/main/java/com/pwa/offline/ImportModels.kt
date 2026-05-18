package com.pwa.offline

enum class ImportValidationStatus {
    VALID,
    MISSING_REQUIRED_COLUMNS
}

data class ImportValidationResult(
    val headers: List<String>,
    val expectedFieldNames: List<String>,
    val detectedHeaderNames: List<String>,
    val matchedFieldNames: List<String>,
    val missingFieldNames: List<String>,
    val optionalMissingFieldNames: List<String>,
    val extraHeaderNames: List<String>,
    val hasExtraColumns: Boolean,
    val status: ImportValidationStatus,
    val validationState: ImportValidationState
)

data class ImportValidationState(
    val isValid: Boolean,
    val normalizedHeaders: List<String>,
    val sheet: WorkbookSheet? = null,
    val headerRowIndex: Int = ImportConfig.defaultHeaderRowIndex
)

data class ImportColumnBinding(
    val sourceColumnIndex: Int,
    val field: FieldDefinition
)

data class ImportRowValue(
    val field: FieldDefinition,
    val rawValue: String
)

data class ImportRowData(
    val values: List<ImportRowValue>
)

data class ProgressState(
    val processedUnits: Int = 0,
    val totalUnits: Int = 0,
    val elapsedMs: Long = 0L
) {
    val percent: Int
        get() = if (totalUnits > 0) {
            ((processedUnits.coerceAtMost(totalUnits) * 100L) / totalUnits).toInt()
        } else {
            0
        }

    val rowsPerSecond: Double
        get() = if (elapsedMs > 0L) {
            processedUnits * 1000.0 / elapsedMs
        } else {
            0.0
        }

    val estimatedRemainingMs: Long?
        get() = if (processedUnits > 0 && totalUnits > processedUnits && elapsedMs > 0L) {
            val remainingUnits = totalUnits - processedUnits
            ((remainingUnits.toDouble() * elapsedMs) / processedUnits).toLong()
        } else {
            null
        }
}

data class ImportProgressState(
    val processedRows: Int = 0,
    val importedRows: Int = 0,
    val skippedRows: Int = 0,
    val metrics: ProgressState = ProgressState()
)

data class ImportExecutionResult(
    val processedRows: Int,
    val importedRows: Int,
    val skippedRows: Int,
    val elapsedMs: Long,
    val conflictCounts: Map<ImportConflictReason, Int>,
    val conflictSamples: List<ImportConflict>
)
