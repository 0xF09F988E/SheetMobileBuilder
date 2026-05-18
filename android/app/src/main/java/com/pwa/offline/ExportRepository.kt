package com.pwa.offline

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class ExportRepository(
    private val appContext: Context,
    private val databaseHelper: AppDatabaseHelper,
    private val exportService: ExportService
) {

    fun delimiterRequiredMessage(): String {
        return appContext.getString(R.string.export_error_delimiter_required)
    }

    suspend fun loadMasterCollection(): CollectionOption? = withContext(Dispatchers.IO) {
        databaseHelper.listMasterCollectionOptions().firstOrNull()
    }

    suspend fun loadFieldDefinitions(collectionId: Long): List<FieldDefinition> = withContext(Dispatchers.IO) {
        databaseHelper.listFieldDefinitions(collectionId)
    }

    suspend fun loadCriterionOptions(): List<ExportCriterionOption> = withContext(Dispatchers.Default) {
        ExportCriterion.values().map { criterion ->
            ExportCriterionOption(
                criterion = criterion,
                title = appContext.getString(criterion.titleRes),
                summary = appContext.getString(criterion.summaryRes)
            )
        }
    }

    suspend fun loadDelimiterOptions(): List<ExportDelimiterOption> = withContext(Dispatchers.Default) {
        ExportDelimiter.values().map { delimiter ->
            val titleRes = when (delimiter) {
                ExportDelimiter.COMMA -> R.string.export_delimiter_comma_title
                ExportDelimiter.SEMICOLON -> R.string.export_delimiter_semicolon_title
                ExportDelimiter.PIPE -> R.string.export_delimiter_pipe_title
                ExportDelimiter.CUSTOM -> R.string.export_delimiter_custom_title
            }
            val summaryRes = when (delimiter) {
                ExportDelimiter.COMMA -> R.string.export_delimiter_comma_summary
                ExportDelimiter.SEMICOLON -> R.string.export_delimiter_semicolon_summary
                ExportDelimiter.PIPE -> R.string.export_delimiter_pipe_summary
                ExportDelimiter.CUSTOM -> R.string.export_delimiter_custom_summary
            }
            ExportDelimiterOption(
                delimiter = delimiter,
                title = appContext.getString(titleRes),
                summary = appContext.getString(summaryRes)
            )
        }
    }

    suspend fun countRecords(collectionId: Long, criterion: ExportCriterion): Int = withContext(Dispatchers.IO) {
        databaseHelper.countRecordsForExport(collectionId, criterion)
    }

    suspend fun exportToUri(
        uri: Uri,
        collection: CollectionOption,
        fields: List<FieldDefinition>,
        criterion: ExportCriterion,
        delimiter: String,
        onProgress: (processedRows: Int, totalRows: Int, elapsedMs: Long) -> Unit
    ): ExportExecutionResult = withContext(Dispatchers.IO) {
        val context = currentCoroutineContext()
        appContext.contentResolver.openOutputStream(uri, "wt").use { output ->
            requireNotNull(output) { appContext.getString(R.string.export_error_open_target) }
            exportService.exportToCsv(
                outputStream = output,
                collection = collection,
                fields = fields,
                criterion = criterion,
                delimiter = delimiter,
                onProgress = onProgress,
                onCancellationCheck = { context.ensureActive() }
            )
        }
    }

    suspend fun exportToShareFile(
        collection: CollectionOption,
        fields: List<FieldDefinition>,
        criterion: ExportCriterion,
        delimiter: String,
        onProgress: (processedRows: Int, totalRows: Int, elapsedMs: Long) -> Unit
    ): Pair<ExportExecutionResult, ShareExportArtifact> = withContext(Dispatchers.IO) {
        val context = currentCoroutineContext()
        val shareDir = File(appContext.cacheDir, ExportConfig.shareDirectory).apply { mkdirs() }
        shareDir.listFiles()?.forEach(File::delete)

        val fileName = ExportConfig.buildFileName(collection, criterion)
        val outputFile = File(shareDir, fileName)
        FileOutputStream(outputFile).use { output ->
            val result = exportService.exportToCsv(
                outputStream = output,
                collection = collection,
                fields = fields,
                criterion = criterion,
                delimiter = delimiter,
                onProgress = onProgress,
                onCancellationCheck = { context.ensureActive() }
            )
            result to ShareExportArtifact(
                filePath = outputFile.absolutePath,
                fileName = fileName
            )
        }
    }

    fun close() {
        databaseHelper.close()
    }
}
