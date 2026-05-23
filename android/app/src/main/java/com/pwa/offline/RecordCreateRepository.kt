package com.pwa.offline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecordCreateRepository(
    private val databaseHelper: AppDatabaseHelper
) {
    suspend fun loadCollections(): List<CollectionOption> = withContext(Dispatchers.IO) {
        databaseHelper.listCollectionOptions()
    }

    suspend fun loadFieldDefinitions(collectionId: Long): List<FieldDefinition> = withContext(Dispatchers.IO) {
        databaseHelper.listFieldDefinitions(collectionId)
    }

    suspend fun createRecord(
        collectionId: Long,
        fields: List<FieldDefinition>,
        updates: Map<Long, String>
    ): Long = withContext(Dispatchers.IO) {
        databaseHelper.createRecordValues(collectionId, fields, updates)
    }

    suspend fun searchOptionSuggestions(
        collectionId: Long,
        query: String,
        limit: Int = 20
    ): List<OptionSuggestion> = withContext(Dispatchers.IO) {
        databaseHelper.searchOptionSuggestions(collectionId, query, limit)
    }

    fun close() {
        databaseHelper.close()
    }
}
