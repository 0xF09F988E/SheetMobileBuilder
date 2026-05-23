package com.pwa.offline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DashboardRepository(
    private val databaseHelper: AppDatabaseHelper
) {
    suspend fun loadCollections(): List<CollectionOption> = withContext(Dispatchers.IO) {
        databaseHelper.listCollectionOptions().filter { !it.isOptions }
    }

    suspend fun loadMasterCollection(): CollectionOption? = withContext(Dispatchers.IO) {
        databaseHelper.listMasterCollectionOptions().firstOrNull()
    }

    suspend fun loadFields(collectionId: Long): List<FieldDefinition> = withContext(Dispatchers.IO) {
        databaseHelper.listFieldDefinitions(collectionId)
    }

    suspend fun loadReviewSummary(collectionId: Long): ReviewStatusDashboardSummary =
        withContext(Dispatchers.IO) {
            databaseHelper.fetchReviewStatusDashboard(collectionId)
        }

    suspend fun loadGroupedCards(
        collectionId: Long,
        fieldId: Long,
        reviewStatus: String?
    ): List<GroupedDashboardCard> = withContext(Dispatchers.IO) {
        databaseHelper.fetchGroupedDashboardCards(collectionId, fieldId, reviewStatus)
    }

    fun close() {
        databaseHelper.close()
    }
}
