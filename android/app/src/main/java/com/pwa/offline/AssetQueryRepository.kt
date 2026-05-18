package com.pwa.offline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AssetQueryRepository(
    private val databaseHelper: AppDatabaseHelper
) {
    suspend fun loadMasterCollection(): CollectionOption? = withContext(Dispatchers.IO) {
        databaseHelper.listMasterCollectionOptions().firstOrNull()
    }

    suspend fun loadLookupFields(collectionId: Long): List<FieldDefinition> = withContext(Dispatchers.IO) {
        databaseHelper.listExactLookupFields(collectionId)
    }

    suspend fun findRecord(collectionId: Long, query: String): AssetRecordDetail? = withContext(Dispatchers.IO) {
        databaseHelper.findRecordByLookupValue(collectionId, query)
    }

    suspend fun listFieldDefinitions(collectionId: Long): List<FieldDefinition> = withContext(Dispatchers.IO) {
        databaseHelper.listFieldDefinitions(collectionId)
    }

    suspend fun searchOptionSuggestions(
        collectionId: Long,
        query: String,
        limit: Int = 20
    ): List<OptionSuggestion> = withContext(Dispatchers.IO) {
        databaseHelper.searchOptionSuggestions(collectionId, query, limit)
    }

    suspend fun updateRecord(
        recordId: Long,
        collectionId: Long,
        fields: List<FieldDefinition>,
        updates: Map<Long, String>
    ): AssetRecordDetail? = withContext(Dispatchers.IO) {
        databaseHelper.updateRecordValues(recordId, collectionId, fields, updates)
        databaseHelper.getRecordDetail(collectionId, recordId)
    }

    suspend fun markConforme(
        recordId: Long,
        collectionId: Long
    ): AssetRecordDetail? = withContext(Dispatchers.IO) {
        databaseHelper.markRecordAsConforme(recordId, collectionId)
        databaseHelper.getRecordDetail(collectionId, recordId)
    }

    fun close() {
        databaseHelper.close()
    }
}
