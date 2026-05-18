package com.pwa.offline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

class ImportRepository(
    private val databaseHelper: AppDatabaseHelper,
    private val importService: ImportService
) {
    suspend fun loadTableOptions(): List<CollectionOption> = withContext(Dispatchers.IO) {
        databaseHelper.listCollectionOptions()
    }

    suspend fun loadFieldDefinitions(collectionId: Long): List<FieldDefinition> = withContext(Dispatchers.IO) {
        databaseHelper.listFieldDefinitions(collectionId)
    }

    suspend fun inspectWorkbook(file: File): WorkbookInspection = withContext(Dispatchers.IO) {
        importService.inspectWorkbook(file)
    }

    suspend fun validateSelection(
        file: File,
        collectionId: Long,
        sheet: WorkbookSheet,
        headerRowIndex: Int
    ): ImportValidationResult = withContext(Dispatchers.IO) {
        importService.validateSelection(file, collectionId, sheet, headerRowIndex)
    }

    suspend fun importFile(
        file: File,
        collectionId: Long,
        validationState: ImportValidationState,
        onProgress: (processedRows: Int, importedRows: Int, skippedRows: Int, totalRows: Int, elapsedMs: Long) -> Unit
    ): ImportExecutionResult = withContext(Dispatchers.IO) {
        val context = currentCoroutineContext()
        importService.importFile(
            file = file,
            collectionId = collectionId,
            validationState = validationState,
            onProgress = onProgress,
            onCancellationCheck = {
                context.ensureActive()
            }
        )
    }

    suspend fun clearCollectionData(collectionId: Long): Int = withContext(Dispatchers.IO) {
        databaseHelper.clearRecordsForCollection(collectionId)
        databaseHelper.countRecordsForCollection(collectionId)
    }

    fun close() {
        databaseHelper.close()
    }
}
