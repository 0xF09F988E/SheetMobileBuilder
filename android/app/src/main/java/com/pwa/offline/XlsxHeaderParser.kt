package com.pwa.offline

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipFile

class XlsxHeaderParser : ImportWorkbookReader {

    override fun inspect(file: File): WorkbookInspection {
        ZipFile(file).use { zipFile ->
            return WorkbookInspection(sheets = parseSheets(zipFile))
        }
    }

    override fun parseHeaders(
        file: File,
        sheet: WorkbookSheet,
        headerRowIndex: Int
    ): SheetHeaderPreview {
        ZipFile(file).use { zipFile ->
            val sharedStrings = parseSharedStrings(zipFile)
            val cellStyles = parseCellStyles(zipFile)
            val headers = parseHeaderRow(
                zipFile = zipFile,
                sheetPath = sheet.path,
                sharedStrings = sharedStrings,
                cellStyles = cellStyles,
                headerRowIndex = headerRowIndex
            )
            return SheetHeaderPreview(sheet = sheet, headers = headers)
        }
    }

    override fun streamRows(
        file: File,
        sheet: WorkbookSheet,
        headerRowIndex: Int,
        onHeader: (SheetHeaderPreview) -> Unit,
        onRow: (List<String>) -> Unit
    ) {
        ZipFile(file).use { zipFile ->
            val sharedStrings = parseSharedStrings(zipFile)
            val cellStyles = parseCellStyles(zipFile)
            val entry = zipFile.getEntry(sheet.path)
                ?: error("No se encontro la hoja ${sheet.path} en el archivo.")

            zipFile.getInputStream(entry).use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, null)

                var headerSize = 0
                var headerParsed = false
                var currentRowIndex = -1
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "row") {
                        currentRowIndex += 1
                        val row = parseIndexedRowCells(parser, sharedStrings, cellStyles)
                        if (!headerParsed && currentRowIndex == headerRowIndex) {
                            val headers = row.map { it.trim() }.trimTrailingEmptyCells()
                            headerSize = headers.size
                            headerParsed = true
                            onHeader(SheetHeaderPreview(sheet = sheet, headers = headers))
                        } else if (headerParsed && currentRowIndex > headerRowIndex) {
                            val normalizedRow = row.padToSize(headerSize)
                            if (normalizedRow.any { it.trim().isNotEmpty() }) {
                                onRow(normalizedRow.take(headerSize))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun parseSharedStrings(zipFile: ZipFile): List<String> {
        val entry = zipFile.getEntry("xl/sharedStrings.xml") ?: return emptyList()
        zipFile.getInputStream(entry).use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, null)

            val strings = mutableListOf<String>()
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "si") {
                    strings += parseSharedStringItem(parser)
                }
            }
            return strings
        }
    }

    private fun parseSharedStringItem(parser: XmlPullParser): String {
        val builder = StringBuilder()
        val startDepth = parser.depth
        while (!(parser.eventType == XmlPullParser.END_TAG && parser.depth == startDepth && parser.name == "si")) {
            if (parser.next() == XmlPullParser.START_TAG && parser.name == "t") {
                builder.append(parser.nextText())
            }
        }
        return builder.toString()
    }

    private fun parseSheets(zipFile: ZipFile): List<WorkbookSheet> {
        val rels = parseWorkbookRelationships(zipFile)
        val workbookEntry = zipFile.getEntry("xl/workbook.xml")
            ?: error("No se encontro xl/workbook.xml en el archivo.")

        zipFile.getInputStream(workbookEntry).use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, null)
            val sheets = mutableListOf<WorkbookSheet>()

            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                    val sheetName = parser.getAttributeValue(null, "name").orEmpty()
                    val relationId = parser.getAttributeValue(
                        "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
                        "id"
                    ).orEmpty()

                    val target = rels[relationId]
                        ?: error("No se encontro la relacion de la hoja $sheetName.")

                    val path = resolveWorkbookPath(target)
                    sheets += WorkbookSheet(
                        name = sheetName,
                        path = path,
                        rowCount = parseSheetRowCount(zipFile, path)
                    )
                }
            }

            if (sheets.isNotEmpty()) {
                return sheets
            }
        }

        error("No se encontro ninguna hoja en el workbook.")
    }

    private fun parseSheetRowCount(zipFile: ZipFile, sheetPath: String): Int {
        val entry = zipFile.getEntry(sheetPath) ?: return 0
        zipFile.getInputStream(entry).use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, null)
            var countedRows = 0

            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "row") {
                    countedRows += 1
                }
            }

            return countedRows
        }
    }

    private fun parseWorkbookRelationships(zipFile: ZipFile): Map<String, String> {
        val entry = zipFile.getEntry("xl/_rels/workbook.xml.rels")
            ?: error("No se encontro xl/_rels/workbook.xml.rels en el archivo.")

        zipFile.getInputStream(entry).use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, null)

            val relationships = mutableMapOf<String, String>()
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                    val id = parser.getAttributeValue(null, "Id").orEmpty()
                    val target = parser.getAttributeValue(null, "Target").orEmpty()
                    relationships[id] = target
                }
            }
            return relationships
        }
    }

    private fun parseHeaderRow(
        zipFile: ZipFile,
        sheetPath: String,
        sharedStrings: List<String>,
        cellStyles: CellStyles,
        headerRowIndex: Int
    ): List<String> {
        val entry = zipFile.getEntry(sheetPath)
            ?: error("No se encontro la hoja $sheetPath en el archivo.")

        zipFile.getInputStream(entry).use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, null)
            var currentRowIndex = -1

            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "row") {
                    currentRowIndex += 1
                    val row = parseIndexedRowCells(parser, sharedStrings, cellStyles)
                    if (currentRowIndex == headerRowIndex) {
                        return row.map { it.trim() }.trimTrailingEmptyCells()
                    }
                }
            }
        }

        return emptyList()
    }

    private fun parseIndexedRowCells(
        parser: XmlPullParser,
        sharedStrings: List<String>,
        cellStyles: CellStyles
    ): List<String> {
        val cells = mutableMapOf<Int, String>()
        val rowDepth = parser.depth

        while (!(parser.eventType == XmlPullParser.END_TAG && parser.depth == rowDepth && parser.name == "row")) {
            if (parser.next() == XmlPullParser.START_TAG && parser.name == "c") {
                val cellReference = parser.getAttributeValue(null, "r").orEmpty()
                val columnIndex = columnIndexFromReference(cellReference)
                cells[columnIndex] = parseCellValue(parser, sharedStrings, cellStyles)
            }
        }

        if (cells.isEmpty()) {
            return emptyList()
        }

        val maxIndex = cells.keys.maxOrNull() ?: -1
        return List(maxIndex + 1) { index -> cells[index].orEmpty() }
    }

    private fun parseCellValue(
        parser: XmlPullParser,
        sharedStrings: List<String>,
        cellStyles: CellStyles
    ): String {
        val cellType = parser.getAttributeValue(null, "t").orEmpty()
        val styleIndex = parser.getAttributeValue(null, "s")?.toIntOrNull()
        val cellDepth = parser.depth
        var value = ""

        while (!(parser.eventType == XmlPullParser.END_TAG && parser.depth == cellDepth && parser.name == "c")) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "v" -> {
                            val raw = parser.nextText()
                            value = if (cellType == "s") {
                                sharedStrings.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                            } else if (cellType == "b") {
                                if (raw == "1") "TRUE" else "FALSE"
                            } else {
                                formatCellDisplayValue(cellType, raw, styleIndex, cellStyles)
                            }
                        }

                        "t" -> {
                            if (cellType == "inlineStr") {
                                value = parser.nextText()
                            }
                        }
                    }
                }
            }
        }

        return value
    }

    private fun formatCellDisplayValue(
        cellType: String,
        raw: String,
        styleIndex: Int?,
        cellStyles: CellStyles
    ): String {
        if (cellType == "str") {
            return raw
        }

        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return ""
        }

        val style = styleIndex?.let(cellStyles::styleAt)
        if (style != null) {
            if (style.isTextFormat) {
                return normalizePlainNumberString(trimmed)
            }

            if (style.isDateFormat) {
                formatExcelDate(trimmed, style.formatCode)?.let { return it }
            }

            formatNumericWithPattern(trimmed, style.formatCode)?.let { return it }
        }

        return normalizePlainNumberString(trimmed)
    }

    private fun parseCellStyles(zipFile: ZipFile): CellStyles {
        val entry = zipFile.getEntry("xl/styles.xml") ?: return CellStyles.EMPTY
        zipFile.getInputStream(entry).use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, null)

            val customFormats = mutableMapOf<Int, String>()
            val styles = mutableListOf<CellStyle>()

            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue

                when (parser.name) {
                    "numFmt" -> {
                        val id = parser.getAttributeValue(null, "numFmtId")?.toIntOrNull() ?: continue
                        val code = parser.getAttributeValue(null, "formatCode").orEmpty()
                        customFormats[id] = code
                    }

                    "xf" -> {
                        val isCellXf = parser.depth >= 3 && parser.getAttributeValue(null, "numFmtId") != null
                        if (!isCellXf) continue
                        val numFmtId = parser.getAttributeValue(null, "numFmtId")?.toIntOrNull() ?: 0
                        val formatCode = customFormats[numFmtId] ?: BUILT_IN_FORMATS[numFmtId]
                        styles += CellStyle(numFmtId = numFmtId, formatCode = formatCode)
                    }
                }
            }

            return CellStyles(styles)
        }
    }

    private fun normalizePlainNumberString(raw: String): String {
        return raw.toBigDecimalOrNull()
            ?.stripTrailingZeros()
            ?.toPlainString()
            ?.let { if (it == "-0") "0" else it }
            ?: raw
    }

    private fun formatNumericWithPattern(raw: String, formatCode: String?): String? {
        val numericValue = raw.toBigDecimalOrNull() ?: return null
        val sanitizedPattern = sanitizeExcelNumberPattern(formatCode).takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val formatter = DecimalFormat(sanitizedPattern, DecimalFormatSymbols(Locale.US))
            formatter.format(numericValue)
        }.getOrNull()
    }

    private fun sanitizeExcelNumberPattern(formatCode: String?): String {
        val base = formatCode
            ?.substringBefore(';')
            ?.replace("\\[[^]]+]".toRegex(), "")
            ?.replace("\"[^\"]*\"".toRegex(), "")
            ?.replace("\\\\(.)".toRegex(), "$1")
            ?.replace("_(.)".toRegex(), "")
            ?.replace("\\*(.)".toRegex(), "")
            ?.replace("?", "#")
            ?.trim()
            .orEmpty()

        return when {
            base.isBlank() -> ""
            base.equals("general", ignoreCase = true) -> ""
            base == "@" -> ""
            else -> base
        }
    }

    private fun formatExcelDate(raw: String, formatCode: String?): String? {
        val serial = raw.toDoubleOrNull() ?: return null
        val formatterPattern = excelDatePatternToJava(formatCode).takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val dateTime = excelSerialToDateTime(serial)
            dateTime.format(DateTimeFormatter.ofPattern(formatterPattern, Locale.US))
        }.getOrNull()
    }

    private fun excelSerialToDateTime(serial: Double): LocalDateTime {
        val wholeDays = serial.toLong()
        val fraction = serial - wholeDays
        val nanos = (fraction * 86_400_000_000_000L).toLong()
        return LocalDateTime.of(1899, 12, 30, 0, 0)
            .plusDays(wholeDays)
            .plusNanos(nanos)
    }

    private fun excelDatePatternToJava(formatCode: String?): String {
        val raw = formatCode
            ?.substringBefore(';')
            ?.replace("\\[[^]]+]".toRegex(), "")
            ?.replace("\"[^\"]*\"".toRegex(), "")
            ?.replace("\\\\(.)".toRegex(), "$1")
            ?.trim()
            .orEmpty()
        if (raw.isBlank()) return ""

        val lower = raw.lowercase(Locale.US)
        return when {
            lower.contains("h") && lower.contains("s") && lower.contains("y") -> "yyyy-MM-dd HH:mm:ss"
            lower.contains("h") && lower.contains("y") -> "yyyy-MM-dd HH:mm"
            lower.contains("h") && lower.contains("s") -> "HH:mm:ss"
            lower.contains("h") -> "HH:mm"
            else -> "yyyy-MM-dd"
        }
    }

    private fun resolveWorkbookPath(target: String): String {
        return when {
            target.startsWith("xl/") -> target
            target.startsWith("/") -> target.removePrefix("/")
            else -> "xl/$target"
        }
    }

    private fun columnIndexFromReference(cellReference: String): Int {
        val letters = cellReference.takeWhile { it.isLetter() }
        var result = 0
        letters.forEach { char ->
            result = result * 26 + (char.uppercaseChar() - 'A' + 1)
        }
        return (result - 1).coerceAtLeast(0)
    }

    private data class CellStyle(
        val numFmtId: Int,
        val formatCode: String?
    ) {
        val isTextFormat: Boolean
            get() = numFmtId == 49 || formatCode?.contains("@") == true

        val isDateFormat: Boolean
            get() {
                if (numFmtId in DATE_FORMAT_IDS) return true
                val code = formatCode?.lowercase(Locale.US).orEmpty()
                if (code.isBlank()) return false
                val stripped = code
                    .replace("\\[[^]]+]".toRegex(), "")
                    .replace("\"[^\"]*\"".toRegex(), "")
                return ('y' in stripped || 'd' in stripped) &&
                    ('m' in stripped || 'h' in stripped || 's' in stripped)
            }
    }

    private class CellStyles(
        private val styles: List<CellStyle>
    ) {
        fun styleAt(index: Int): CellStyle? = styles.getOrNull(index)

        companion object {
            val EMPTY = CellStyles(emptyList())
        }
    }

    companion object {
        private val BUILT_IN_FORMATS = mapOf(
            1 to "0",
            2 to "0.00",
            3 to "#,##0",
            4 to "#,##0.00",
            9 to "0%",
            10 to "0.00%",
            11 to "0.00E+00",
            12 to "# ?/?",
            13 to "# ??/??",
            14 to "mm-dd-yy",
            15 to "d-mmm-yy",
            16 to "d-mmm",
            17 to "mmm-yy",
            18 to "h:mm AM/PM",
            19 to "h:mm:ss AM/PM",
            20 to "h:mm",
            21 to "h:mm:ss",
            22 to "m/d/yy h:mm",
            37 to "#,##0 ;(#,##0)",
            38 to "#,##0 ;[Red](#,##0)",
            39 to "#,##0.00;(#,##0.00)",
            40 to "#,##0.00;[Red](#,##0.00)",
            45 to "mm:ss",
            46 to "[h]:mm:ss",
            47 to "mmss.0",
            48 to "##0.0E+0",
            49 to "@"
        )

        private val DATE_FORMAT_IDS = setOf(14, 15, 16, 17, 18, 19, 20, 21, 22, 45, 46, 47)
    }
}

private fun List<String>.trimTrailingEmptyCells(): List<String> {
    var end = size
    while (end > 0 && this[end - 1].trim().isEmpty()) {
        end -= 1
    }
    return take(end)
}

private fun List<String>.padToSize(size: Int): List<String> {
    return if (this.size >= size) this else this + List(size - this.size) { "" }
}
