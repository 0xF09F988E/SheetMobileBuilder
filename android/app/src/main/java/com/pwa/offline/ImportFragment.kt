package com.pwa.offline

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class ImportFragment : Fragment() {

    private val viewModel: ImportViewModel by viewModels {
        ImportViewModelFactory(
            ImportRepository(
                databaseHelper = AppDatabaseHelper(requireContext().applicationContext),
                importService = ImportService(
                    AppDatabaseHelper(requireContext().applicationContext),
                    XlsxHeaderParser()
                )
            )
        )
    }

    private lateinit var tableSelector: AutoCompleteTextView
    private lateinit var sheetSelector: AutoCompleteTextView
    private lateinit var headerRowSelector: AutoCompleteTextView
    private lateinit var selectFileButton: Button
    private lateinit var cancelImportOverlayButton: Button
    private lateinit var selectedFileText: TextView
    private lateinit var expectedColumnsText: TextView
    private lateinit var headersPreviewText: TextView
    private lateinit var validationPreviewText: TextView
    private lateinit var statusText: TextView
    private lateinit var startImportButton: Button
    private lateinit var clearTableDataButton: Button
    private lateinit var resultPanel: LinearLayout
    private lateinit var readCountText: TextView
    private lateinit var insertedCountText: TextView
    private lateinit var skippedCountText: TextView
    private lateinit var observationsText: TextView
    private lateinit var loadingOverlay: View
    private lateinit var loadingText: TextView
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var progressSummaryText: TextView
    private lateinit var progressMetricsText: TextView

    private var stagedWorkbookFile: File? = null

    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            stageWorkbook(uri)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_import, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tableSelector = view.findViewById(R.id.importTableSelector)
        sheetSelector = view.findViewById(R.id.importSheetSelector)
        headerRowSelector = view.findViewById(R.id.importHeaderRowSelector)
        selectFileButton = view.findViewById(R.id.selectFileButton)
        cancelImportOverlayButton = view.findViewById(R.id.cancelImportOverlayButton)
        selectedFileText = view.findViewById(R.id.selectedFileText)
        expectedColumnsText = view.findViewById(R.id.expectedColumnsText)
        headersPreviewText = view.findViewById(R.id.headersPreviewText)
        validationPreviewText = view.findViewById(R.id.validationPreviewText)
        statusText = view.findViewById(R.id.importStatusText)
        startImportButton = view.findViewById(R.id.startImportButton)
        clearTableDataButton = view.findViewById(R.id.clearTableDataButton)
        resultPanel = view.findViewById(R.id.importResultPanel)
        readCountText = view.findViewById(R.id.importReadCountText)
        insertedCountText = view.findViewById(R.id.importInsertedCountText)
        skippedCountText = view.findViewById(R.id.importSkippedCountText)
        observationsText = view.findViewById(R.id.importObservationsText)
        loadingOverlay = view.findViewById(R.id.importLoadingOverlay)
        loadingText = view.findViewById(R.id.importLoadingText)
        progressBar = view.findViewById(R.id.importProgressBar)
        progressSummaryText = view.findViewById(R.id.importProgressSummaryText)
        progressMetricsText = view.findViewById(R.id.importProgressMetricsText)

        bindActions()
        collectUiState()
        viewModel.loadInitialState()
    }

    override fun onStop() {
        viewModel.cancelImport(cancelledByExit = true)
        super.onStop()
    }

    override fun onDestroy() {
        stagedWorkbookFile?.delete()
        stagedWorkbookFile = null
        super.onDestroy()
    }

    private fun bindActions() {
        selectFileButton.setOnClickListener {
            openDocumentLauncher.launch(
                arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            )
        }
        startImportButton.setOnClickListener {
            stagedWorkbookFile?.let(viewModel::importFile)
        }
        cancelImportOverlayButton.setOnClickListener {
            viewModel.cancelImport(cancelledByExit = false)
        }
        clearTableDataButton.setOnClickListener {
            confirmClearSelectedTableData()
        }

        tableSelector.setOnItemClickListener { _, _, position, _ ->
            val option = viewModel.uiState.value.tableOptions.getOrNull(position)
            viewModel.selectTable(option)
            stagedWorkbookFile?.let { viewModel.validateSelection(it) }
        }
        sheetSelector.setOnItemClickListener { _, _, position, _ ->
            val sheet = viewModel.uiState.value.workbookSheets.getOrNull(position)
            viewModel.selectSheet(sheet)
            stagedWorkbookFile?.let { viewModel.validateSelection(it) }
        }
        headerRowSelector.setOnItemClickListener { _, _, position, _ ->
            val index = ImportConfig.headerRowOptions.getOrNull(position)?.minus(1)
                ?: ImportConfig.defaultHeaderRowIndex
            viewModel.selectHeaderRow(index)
            stagedWorkbookFile?.let { viewModel.validateSelection(it) }
        }
    }

    private fun collectUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: ImportUiState) {
        tableSelector.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                state.tableOptions.ifEmpty { listOf(CollectionOption.EMPTY) }
            )
        )
        tableSelector.setText(state.selectedTableOption?.displayName.orEmpty(), false)

        val sheetOptions = state.workbookSheets.ifEmpty {
            listOf(WorkbookSheet(getString(R.string.import_sheet_selector_empty), ""))
        }
        sheetSelector.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                sheetOptions
            )
        )
        sheetSelector.setText(state.selectedSheet?.name ?: sheetOptions.first().name, false)

        headerRowSelector.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                ImportConfig.headerRowOptions
            )
        )
        headerRowSelector.setText((state.selectedHeaderRowIndex + 1).toString(), false)

        selectedFileText.text = state.selectedFileLabel.ifBlank { getString(R.string.import_no_file) }
        expectedColumnsText.text = state.expectedFieldNames.takeIf { it.isNotEmpty() }
            ?.joinToString("\n") { "- $it" }
            ?: getString(R.string.preview_empty)
        headersPreviewText.text = buildHeadersPreview(state.validationResult)
        validationPreviewText.text = buildValidationPreview(state)
        renderStatus(state)
        renderProgress(state)
        renderResult(state.importResult)

        val isBusy = state.isBusy
        loadingOverlay.visibility = if (isBusy) View.VISIBLE else View.GONE
        loadingText.text = when (state.phase) {
            ImportUiPhase.INSPECTING -> getString(R.string.import_loading_inspecting)
            ImportUiPhase.VALIDATING -> getString(R.string.import_loading_validating)
            ImportUiPhase.IMPORTING -> getString(R.string.import_loading_importing)
            ImportUiPhase.CLEARING -> getString(R.string.import_loading_clearing)
            ImportUiPhase.IDLE -> getString(R.string.import_loading_default)
        }

        cancelImportOverlayButton.visibility = if (state.phase == ImportUiPhase.IMPORTING) View.VISIBLE else View.GONE
        selectFileButton.isEnabled = !isBusy
        tableSelector.isEnabled = !isBusy && state.tableOptions.isNotEmpty()
        sheetSelector.isEnabled = !isBusy && state.workbookSheets.isNotEmpty()
        headerRowSelector.isEnabled = !isBusy && state.selectedFileLabel.isNotBlank()
        clearTableDataButton.isEnabled = !isBusy && (state.selectedTableOption?.id ?: -1L) >= 0L
        startImportButton.isEnabled = !isBusy && state.validationResult?.validationState?.isValid == true
    }

    private fun renderStatus(state: ImportUiState) {
        statusText.text = when {
            state.cancelledByExit -> getString(R.string.import_status_cancelled_on_exit)
            state.phase == ImportUiPhase.INSPECTING -> getString(R.string.import_status_reading)
            state.phase == ImportUiPhase.VALIDATING -> getString(R.string.import_status_reading)
            state.phase == ImportUiPhase.IMPORTING -> getString(R.string.import_status_importing)
            state.phase == ImportUiPhase.CLEARING -> getString(R.string.import_status_clearing)
            state.errorMessage != null -> state.errorMessage
            state.importResult != null -> getString(
                R.string.import_status_done,
                state.importResult.processedRows,
                state.importResult.importedRows,
                state.importResult.skippedRows
            )
            state.validationResult != null -> when (state.validationResult.status) {
                ImportValidationStatus.VALID -> getString(R.string.import_status_valid)
                ImportValidationStatus.MISSING_REQUIRED_COLUMNS -> getString(R.string.import_status_invalid)
            }
            else -> getString(R.string.import_status_idle)
        }
    }

    private fun renderProgress(state: ImportUiState) {
        val metrics = state.progress.metrics
        val hasImportActivity = state.phase == ImportUiPhase.IMPORTING ||
            state.importResult != null ||
            metrics.processedUnits > 0

        progressBar.visibility = if (hasImportActivity) View.VISIBLE else View.GONE
        progressSummaryText.visibility = if (hasImportActivity) View.VISIBLE else View.GONE
        progressMetricsText.visibility = if (hasImportActivity) View.VISIBLE else View.GONE

        if (!hasImportActivity) {
            progressBar.isIndeterminate = false
            progressBar.progress = 0
            progressSummaryText.text = ""
            progressMetricsText.text = ""
            return
        }

        val totalRows = metrics.totalUnits
        val processedRows = metrics.processedUnits
        progressBar.max = 100
        progressBar.isIndeterminate = totalRows <= 0
        progressBar.progress = if (totalRows > 0) metrics.percent else 0

        progressSummaryText.text = if (totalRows > 0) {
            getString(
                R.string.import_progress_summary,
                metrics.percent,
                processedRows,
                totalRows
            )
        } else {
            getString(
                R.string.import_progress_summary_unknown_total,
                processedRows
            )
        }

        val rowsPerSecond = metrics.rowsPerSecond.roundToInt()
        val eta = when {
            state.importResult != null -> getString(R.string.import_progress_eta_done)
            totalRows > processedRows && processedRows >= 10 -> {
                metrics.estimatedRemainingMs?.let(::formatDurationMs)
                    ?: getString(R.string.import_progress_eta_pending)
            }
            else -> getString(R.string.import_progress_eta_pending)
        }
        progressMetricsText.text = getString(
            R.string.import_progress_metrics,
            metrics.elapsedMs,
            rowsPerSecond,
            state.progress.importedRows,
            state.progress.skippedRows,
            eta
        )
    }

    private fun renderResult(result: ImportExecutionResult?) {
        if (result == null) {
            resultPanel.visibility = View.GONE
            readCountText.text = "0"
            insertedCountText.text = "0"
            skippedCountText.text = "0"
            observationsText.text = getString(R.string.import_observations_empty)
            return
        }

        resultPanel.visibility = View.VISIBLE
        readCountText.text = result.processedRows.toString()
        insertedCountText.text = result.importedRows.toString()
        skippedCountText.text = result.skippedRows.toString()
        observationsText.text = buildObservationsText(result)
    }

    private fun buildHeadersPreview(result: ImportValidationResult?): String {
        return if (result == null || result.headers.isEmpty()) {
            getString(R.string.preview_empty)
        } else {
            result.headers.joinToString(separator = "\n") { "- $it" }
        }
    }

    private fun buildValidationPreview(state: ImportUiState): CharSequence {
        val result = state.validationResult ?: return state.errorMessage ?: getString(R.string.import_preview_human)
        val successColor = ContextCompat.getColor(requireContext(), R.color.success)
        val errorColor = ContextCompat.getColor(requireContext(), R.color.error)
        val builder = SpannableStringBuilder()

        fun appendLine(text: String, color: Int? = null, bold: Boolean = false) {
            val start = builder.length
            builder.append(text)
            val end = builder.length
            if (color != null) {
                builder.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (bold) {
                builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            builder.append('\n')
        }

        appendLine(
            when (result.status) {
                ImportValidationStatus.VALID -> getString(R.string.import_validation_ok)
                ImportValidationStatus.MISSING_REQUIRED_COLUMNS -> getString(R.string.import_validation_missing)
            },
            color = if (result.status == ImportValidationStatus.VALID) successColor else errorColor,
            bold = true
        )
        appendLine(
            getString(R.string.import_validation_expected, result.expectedFieldNames.joinToString(", "))
        )
        appendLine(
            getString(R.string.import_validation_headers, result.detectedHeaderNames.joinToString(", "))
        )
        if (result.matchedFieldNames.isNotEmpty()) {
            appendLine(
                getString(R.string.import_validation_present_list, result.matchedFieldNames.joinToString(", ")),
                color = successColor
            )
        }
        if (result.missingFieldNames.isNotEmpty()) {
            appendLine(
                getString(R.string.import_validation_missing_list, result.missingFieldNames.joinToString(", ")),
                color = errorColor
            )
        }
        if (result.optionalMissingFieldNames.isNotEmpty()) {
            appendLine(
                getString(
                    R.string.import_validation_optional_missing_list,
                    result.optionalMissingFieldNames.joinToString(", ")
                ),
                color = successColor
            )
        }
        if (result.hasExtraColumns) {
            appendLine(
                getString(
                    R.string.import_validation_extra_list,
                    result.extraHeaderNames.joinToString(", ")
                ),
                color = errorColor
            )
        }
        if (builder.isNotEmpty() && builder.last() == '\n') {
            builder.delete(builder.length - 1, builder.length)
        }
        return builder
    }

    private fun buildObservationsText(result: ImportExecutionResult): String {
        if (result.skippedRows == 0) {
            return getString(R.string.import_observations_empty)
        }

        val lines = mutableListOf<String>()
        result.conflictCounts.forEach { (reason, count) ->
            lines += getString(
                R.string.import_observation_conflict_count,
                resolveConflictReason(reason),
                count
            )
        }
        if (result.conflictSamples.isNotEmpty()) {
            lines += ""
            lines += getString(R.string.import_observation_sample_limit, result.conflictSamples.size)
            result.conflictSamples.forEach { conflict ->
                lines += getString(
                    R.string.import_observation_sample_row,
                    conflict.rowNumber,
                    conflict.fieldName,
                    conflict.value
                )
            }
        }
        return lines.joinToString("\n")
    }

    private fun resolveConflictReason(reason: ImportConflictReason): String {
        return when (reason) {
            ImportConflictReason.DUPLICATE -> getString(R.string.import_conflict_reason_duplicate)
            ImportConflictReason.INVALID_VALUE -> getString(R.string.import_conflict_reason_invalid_type)
            ImportConflictReason.OTHER -> getString(R.string.import_conflict_reason_other)
        }
    }

    private fun formatDurationMs(milliseconds: Long): String {
        return when {
            milliseconds < 1000L -> getString(R.string.import_duration_ms, milliseconds)
            else -> String.format(
                "%1$.1fs",
                milliseconds / 1000.0
            )
        }
    }

    private fun confirmClearSelectedTableData() {
        val collection = viewModel.uiState.value.selectedTableOption ?: return
        if (collection.id < 0) return

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_clear_table_data_title)
            .setMessage(getString(R.string.dialog_clear_table_data_message, collection.displayName))
            .setNegativeButton(R.string.dialog_delete_cancel, null)
            .setPositiveButton(R.string.dialog_delete_confirm) { _, _ ->
                viewModel.clearCollectionData()
            }
            .show()
    }

    private fun stageWorkbook(uri: Uri) {
        stagedWorkbookFile?.delete()
        stagedWorkbookFile = copyUriToTempFile(uri)
        val label = uri.lastPathSegment ?: uri.toString()
        viewModel.setSelectedFileLabel(label)
        stagedWorkbookFile?.let(viewModel::inspectWorkbook)
    }

    private fun copyUriToTempFile(uri: Uri): File {
        val tempFile = File.createTempFile("import-screen-", ".xlsx", requireContext().cacheDir)
        requireContext().contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { getString(R.string.import_error_open_file) }
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }
}
