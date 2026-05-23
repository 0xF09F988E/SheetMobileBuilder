package com.pwa.offline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BrowseRepository(
    private val databaseHelper: AppDatabaseHelper
) {
    suspend fun loadMasterCollection(): CollectionOption? = withContext(Dispatchers.IO) {
        databaseHelper.listMasterCollectionOptions().firstOrNull()
    }

    suspend fun countRecords(collectionId: Long): Int = withContext(Dispatchers.IO) {
        databaseHelper.countRecordsForCollection(collectionId)
    }

    suspend fun loadPage(
        collectionId: Long,
        page: Int,
        pageSize: Int
    ): List<RecordPreview> = withContext(Dispatchers.IO) {
        databaseHelper.listRecordPage(collectionId, page, pageSize)
    }

    suspend fun deleteRecord(recordId: Long): Boolean = withContext(Dispatchers.IO) {
        databaseHelper.deleteRecord(recordId)
    }

    fun close() {
        databaseHelper.close()
    }
}
