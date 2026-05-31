package com.pwa.offline

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CloneDataRepository(
    private val appContext: Context,
    private val databaseHelper: AppDatabaseHelper,
    private val cloneDataService: CloneDataService
) {

    suspend fun loadLocalSummary(): CloneSnapshotSummary = withContext(Dispatchers.IO) {
        val summary = databaseHelper.fetchDashboardSummary()
        CloneSnapshotSummary(
            totalTables = summary.totalTables,
            totalRecords = summary.totalRecords
        )
    }

    suspend fun exportBackup(targetUri: Uri): CloneExportResult {
        return cloneDataService.exportDatabase(
            databaseHelper = databaseHelper,
            targetUri = targetUri
        )
    }

    suspend fun importBackup(sourceUri: Uri): CloneImportResult {
        return cloneDataService.importDatabase(
            databaseHelper = databaseHelper,
            sourceUri = sourceUri
        )
    }

    fun close() {
        databaseHelper.close()
    }
}
