package com.pwa.offline

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CloneSnapshotSummary(
    val totalTables: Int,
    val totalRecords: Int
)

data class CloneExportResult(
    val fileName: String,
    val bytesWritten: Long,
    val elapsedMs: Long
)

data class CloneImportResult(
    val fileName: String,
    val bytesRead: Long,
    val elapsedMs: Long,
    val restoredSummary: CloneSnapshotSummary
)

class CloneDataService(
    private val appContext: Context
) {

    suspend fun exportDatabase(
        databaseHelper: AppDatabaseHelper,
        targetUri: Uri
    ): CloneExportResult = withContext(Dispatchers.IO) {
        val sourceFile = appContext.getDatabasePath(AppDatabaseHelper.databaseName())
        require(sourceFile.exists()) {
            appContext.getString(R.string.clone_error_database_missing)
        }

        val startedAt = System.currentTimeMillis()
        databaseHelper.close()
        appContext.contentResolver.openOutputStream(targetUri, "w").use { output ->
            requireNotNull(output) {
                appContext.getString(R.string.clone_error_open_target)
            }
            FileInputStream(sourceFile).use { input ->
                input.copyTo(output)
            }
        }

        CloneExportResult(
            fileName = resolveDisplayName(targetUri) ?: CloneDataConfig.buildSuggestedFileName(),
            bytesWritten = sourceFile.length(),
            elapsedMs = System.currentTimeMillis() - startedAt
        )
    }

    suspend fun importDatabase(
        databaseHelper: AppDatabaseHelper,
        sourceUri: Uri
    ): CloneImportResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val sourceName = resolveDisplayName(sourceUri) ?: appContext.getString(R.string.clone_unknown_file)
        val incomingFile = File(appContext.cacheDir, "clone-import-incoming.db")
        val rollbackFile = File(appContext.cacheDir, "clone-import-rollback.db")
        val targetFile = appContext.getDatabasePath(AppDatabaseHelper.databaseName())

        incomingFile.delete()
        rollbackFile.delete()

        appContext.contentResolver.openInputStream(sourceUri).use { input ->
            requireNotNull(input) {
                appContext.getString(R.string.clone_error_open_source)
            }
            FileOutputStream(incomingFile).use { output ->
                input.copyTo(output)
            }
        }

        require(incomingFile.exists() && incomingFile.length() > 0L) {
            appContext.getString(R.string.clone_error_empty_file)
        }

        validateBackupFile(incomingFile)

        val currentVersion = databaseHelper.readableDatabase.version
        val incomingVersion = readDatabaseVersion(incomingFile)
        require(incomingVersion == currentVersion) {
            appContext.getString(
                R.string.clone_error_incompatible_version,
                incomingVersion,
                currentVersion
            )
        }

        if (targetFile.exists()) {
            copyFile(targetFile, rollbackFile)
        }

        databaseHelper.close()
        deleteDatabaseSidecars(targetFile)
        copyFile(incomingFile, targetFile)

        val restoredSummary = try {
            validateRestoredDatabase()
        } catch (error: Throwable) {
            if (rollbackFile.exists()) {
                deleteDatabaseSidecars(targetFile)
                copyFile(rollbackFile, targetFile)
            }
            throw error
        } finally {
            incomingFile.delete()
            rollbackFile.delete()
        }

        CloneImportResult(
            fileName = sourceName,
            bytesRead = targetFile.length(),
            elapsedMs = System.currentTimeMillis() - startedAt,
            restoredSummary = restoredSummary
        )
    }

    private fun validateBackupFile(file: File) {
        FileInputStream(file).use { input ->
            val header = ByteArray(16)
            val read = input.read(header)
            require(read == header.size && String(header, Charsets.US_ASCII).startsWith("SQLite format 3")) {
                appContext.getString(R.string.clone_error_invalid_format)
            }
        }

        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val tables = mutableSetOf<String>()
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table'",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    tables += cursor.getString(0)
                }
            }

            val requiredTables = setOf(
                "collections",
                "fields",
                "records",
                "record_values",
                "record_lookup_index",
                "record_unique_index",
                "record_review_log"
            )
            require(tables.containsAll(requiredTables)) {
                appContext.getString(R.string.clone_error_invalid_schema)
            }
        } finally {
            db.close()
        }
    }

    private fun readDatabaseVersion(file: File): Int {
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            db.version
        } finally {
            db.close()
        }
    }

    private fun validateRestoredDatabase(): CloneSnapshotSummary {
        val helper = AppDatabaseHelper(appContext)
        return try {
            val summary = helper.fetchDashboardSummary()
            CloneSnapshotSummary(
                totalTables = summary.totalTables,
                totalRecords = summary.totalRecords
            )
        } finally {
            helper.close()
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }

    private fun copyFile(source: File, target: File) {
        target.parentFile?.mkdirs()
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun deleteDatabaseSidecars(targetFile: File) {
        targetFile.delete()
        File("${targetFile.absolutePath}-journal").delete()
        File("${targetFile.absolutePath}-wal").delete()
        File("${targetFile.absolutePath}-shm").delete()
    }
}
