package com.pwa.offline

import java.io.File
import kotlin.math.max

class ImportService(
    private val databaseHelper: AppDatabaseHelper,
    private val workbookReader: ImportWorkbookReader
) {
    fun inspectWorkbook(file: File): WorkbookInspection {
        return workbookReader.inspect(file)
    }

    fun validateSelection(
        file: File,
        collectionId: Long,
        sheet: WorkbookSheet,
        headerRowIndex: Int = ImportConfig.defaultHeaderRowIndex
    ): ImportValidationResult {
        val preview = workbookReader.parseHeaders(file, sheet, headerRowIndex)
        return buildValidationResult(collectionId, preview, headerRowIndex)
    }

    fun importFile(
        file: File,
        collectionId: Long,
        validationState: ImportValidationState,
        onProgress: (processedRows: Int, importedRows: Int, skippedRows: Int, totalRows: Int, elapsedMs: Long) -> Unit,
        onCancellationCheck: () -> Unit
    ): ImportExecutionResult {
        onCancellationCheck()
        val sheet = requireNotNull(validationState.sheet) { "No sheet selected for import." }
        val headerRowIndex = validationState.headerRowIndex
        val firstDataRowNumber = headerRowIndex + 2
        val totalRows = (sheet.rowCount - firstDataRowNumber + 1).coerceAtLeast(0)
        val fields = databaseHelper.listFieldDefinitions(collectionId)
        val fieldBySlug = fields.associateBy { it.slug }
        val importPlan = validationState.normalizedHeaders.mapIndexedNotNull { index, slug ->
            fieldBySlug[slug]?.let { field ->
                ImportColumnBinding(
                    sourceColumnIndex = index,
                    field = field
                )
            }
        }
        val uniqueValueCache = databaseHelper.loadExistingUniqueValueCache(
            collectionId = collectionId,
            fieldIds = importPlan.asSequence()
                .map { it.field }
                .filter { it.isUniqueValue }
                .map { it.id }
                .distinct()
                .toList()
        )

        val batch = mutableListOf<ImportRowData>()
        var importedCount = 0
        var processedRows = 0
        var skippedRows = 0
        val conflictSamples = mutableListOf<ImportConflict>()
        val conflictCounts = linkedMapOf<ImportConflictReason, Int>()
        val startedAtNs = System.nanoTime()

        workbookReader.streamRows(
            file = file,
            sheet = sheet,
            headerRowIndex = headerRowIndex,
            onHeader = { },
            onRow = { row ->
                onCancellationCheck()
                processedRows += 1
                val rowValues = ArrayList<ImportRowValue>(importPlan.size)
                var hasMeaningfulValue = false
                importPlan.forEach { binding ->
                    val rawValue = row.getOrNull(binding.sourceColumnIndex).orEmpty()
                    if (!hasMeaningfulValue && rawValue.trim().isNotEmpty()) {
                        hasMeaningfulValue = true
                    }
                    rowValues += ImportRowValue(
                        field = binding.field,
                        rawValue = rawValue
                    )
                }

                if (hasMeaningfulValue) {
                    batch += ImportRowData(values = rowValues)
                }

                if (batch.size >= ImportConfig.batchSize) {
                    onCancellationCheck()
                    val rowStartNumber = max(firstDataRowNumber, firstDataRowNumber + processedRows - batch.size)
                    val batchResult = databaseHelper.insertImportedRows(
                        collectionId = collectionId,
                        rows = batch,
                        startingRowNumber = rowStartNumber,
                        uniqueValueCache = uniqueValueCache
                    )
                    importedCount += batchResult.insertedRecords
                    skippedRows += batchResult.conflicts.size
                    absorbConflicts(batchResult.conflicts, conflictCounts, conflictSamples)
                    batch.clear()
                    onProgress(
                        processedRows,
                        importedCount,
                        skippedRows,
                        totalRows,
                        elapsedMillisecondsSince(startedAtNs)
                    )
                }
            }
        )

        if (batch.isNotEmpty()) {
            onCancellationCheck()
            val rowStartNumber = max(firstDataRowNumber, firstDataRowNumber + processedRows - batch.size)
            val batchResult = databaseHelper.insertImportedRows(
                collectionId = collectionId,
                rows = batch,
                startingRowNumber = rowStartNumber,
                uniqueValueCache = uniqueValueCache
            )
            importedCount += batchResult.insertedRecords
            skippedRows += batchResult.conflicts.size
            absorbConflicts(batchResult.conflicts, conflictCounts, conflictSamples)
        }

        onCancellationCheck()
        val elapsedMs = elapsedMillisecondsSince(startedAtNs)
        onProgress(processedRows, importedCount, skippedRows, totalRows, elapsedMs)

        return ImportExecutionResult(
            processedRows = processedRows,
            importedRows = importedCount,
            skippedRows = skippedRows,
            elapsedMs = elapsedMs,
            conflictCounts = conflictCounts,
            conflictSamples = conflictSamples
        )
    }

    private fun elapsedMillisecondsSince(startedAtNs: Long): Long {
        return ((System.nanoTime() - startedAtNs).coerceAtLeast(0L)) / 1_000_000L
    }

    private fun absorbConflicts(
        conflicts: List<ImportConflict>,
        conflictCounts: LinkedHashMap<ImportConflictReason, Int>,
        conflictSamples: MutableList<ImportConflict>
    ) {
        conflicts.forEach { conflict ->
            conflictCounts[conflict.reason] = (conflictCounts[conflict.reason] ?: 0) + 1
            if (conflictSamples.size < ImportConfig.maxConflictPreview) {
                conflictSamples += conflict
            }
        }
    }

    private fun buildValidationResult(
        collectionId: Long,
        preview: SheetHeaderPreview,
        headerRowIndex: Int
    ): ImportValidationResult {
        val fields = databaseHelper.listFieldDefinitions(collectionId)
        val fieldNameBySlug = fields.associate { it.slug to it.displayName }
        val requiredFields = fields.filter { it.isRequiredValue }
        val optionalFields = fields.filterNot { it.isRequiredValue }
        val expectedSlugs = requiredFields.map { it.slug }
        val normalizedHeaders = preview.headers.map(databaseHelper::normalizeIdentifierValue)
        val missing = expectedSlugs.filterNot(normalizedHeaders::contains)
        val matched = expectedSlugs.filter(normalizedHeaders::contains)
        val optionalMissing = optionalFields.map { it.slug }.filterNot(normalizedHeaders::contains)
        val knownSlugs = fields.map { it.slug }
        val extra = normalizedHeaders.filterNot(knownSlugs::contains)
        val expectedNames = fields.map { it.displayName }
        val detectedNames = normalizedHeaders.map { normalized ->
            fieldNameBySlug[normalized] ?: normalized
        }
        val matchedNames = matched.map { fieldNameBySlug[it] ?: it }
        val missingNames = missing.map { fieldNameBySlug[it] ?: it }
        val optionalMissingNames = optionalMissing.map { fieldNameBySlug[it] ?: it }
        val extraNames = extra.map { normalized ->
            fieldNameBySlug[normalized] ?: normalized
        }

        return ImportValidationResult(
            headers = preview.headers,
            expectedFieldNames = expectedNames,
            detectedHeaderNames = detectedNames,
            matchedFieldNames = matchedNames,
            missingFieldNames = missingNames,
            optionalMissingFieldNames = optionalMissingNames,
            extraHeaderNames = extraNames,
            hasExtraColumns = extra.isNotEmpty(),
            status = if (missing.isEmpty()) {
                ImportValidationStatus.VALID
            } else {
                ImportValidationStatus.MISSING_REQUIRED_COLUMNS
            },
            validationState = ImportValidationState(
                isValid = missing.isEmpty(),
                normalizedHeaders = normalizedHeaders,
                sheet = preview.sheet,
                headerRowIndex = headerRowIndex
            )
        )
    }
}
