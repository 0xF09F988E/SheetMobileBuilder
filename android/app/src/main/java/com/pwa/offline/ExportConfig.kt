package com.pwa.offline

import androidx.annotation.StringRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportCriterion(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int
) {
    FULL(
        key = "full",
        titleRes = R.string.export_criterion_full_title,
        summaryRes = R.string.export_criterion_full_summary
    ),
    UPDATED_LAST_24_HOURS(
        key = "updated_last_24_hours",
        titleRes = R.string.export_criterion_updated_24h_title,
        summaryRes = R.string.export_criterion_updated_24h_summary
    ),
    REVIEWED_LAST_24_HOURS(
        key = "reviewed_last_24_hours",
        titleRes = R.string.export_criterion_reviewed_24h_title,
        summaryRes = R.string.export_criterion_reviewed_24h_summary
    );

    companion object {
        fun default(): ExportCriterion = FULL
    }
}

object ExportConfig {
    const val rowChunkSize = 400
    const val progressStep = 100
    const val mimeTypeCsv = "text/csv"
    const val shareDirectory = "shared_exports"

    fun buildFileName(
        collection: CollectionOption,
        criterion: ExportCriterion,
        now: Date = Date()
    ): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)
        return "${collection.slug}_${criterion.key}_$timestamp.csv"
    }
}

data class ExportCriterionOption(
    val criterion: ExportCriterion,
    val title: String,
    val summary: String
) {
    override fun toString(): String = title
}
