package com.pwa.offline

import java.io.File

data class WorkbookSheet(
    val name: String,
    val path: String,
    val rowCount: Int = 0
) {
    override fun toString(): String = name
}

data class WorkbookInspection(
    val sheets: List<WorkbookSheet>
)

data class SheetHeaderPreview(
    val sheet: WorkbookSheet,
    val headers: List<String>
)

interface ImportWorkbookReader {
    fun inspect(file: File): WorkbookInspection

    fun parseHeaders(
        file: File,
        sheet: WorkbookSheet,
        headerRowIndex: Int = ImportConfig.defaultHeaderRowIndex
    ): SheetHeaderPreview

    fun streamRows(
        file: File,
        sheet: WorkbookSheet,
        headerRowIndex: Int = ImportConfig.defaultHeaderRowIndex,
        onHeader: (SheetHeaderPreview) -> Unit,
        onRow: (List<String>) -> Unit
    )
}
