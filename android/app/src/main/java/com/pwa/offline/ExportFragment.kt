package com.pwa.offline

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class ExportFragment : Fragment() {

    private val viewModel: ExportViewModel by viewModels {
        val databaseHelper = AppDatabaseHelper(requireContext().applicationContext)
        ExportViewModelFactory(
            ExportRepository(
                appContext = requireContext().applicationContext,
                databaseHelper = databaseHelper,
                exportService = ExportService(
                    databaseHelper
                )
            )
        )
    }

    private lateinit var mainTableText: TextView
    private lateinit var formatText: TextView
    private lateinit var delimiterSelector: AutoCompleteTextView
    private lateinit var delimiterSummaryText: TextView
    private lateinit var customDelimiterLayout: TextInputLayout
    private lateinit var customDelimiterInput: TextInputEditText
    private lateinit var criterionSelector: AutoCompleteTextView
    private lateinit var criterionSummaryText: TextView
    private lateinit var estimatedRecordsText: TextView
    private lateinit var exportStatusText: TextView
    private lateinit var exportProgressBar: android.widget.ProgressBar
    private lateinit var exportProgressSummaryText: TextView
    private lateinit var exportProgressMetricsText: TextView
    private lateinit var saveButton: Button
    private lateinit var shareButton: Button
    private lateinit var overlay: View
    private lateinit var overlayText: TextView
    private lateinit var overlayCancelButton: Button
    private lateinit var resultFileText: TextView
    private lateinit var resultSummaryText: TextView

    private val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(ExportConfig.mimeTypeCsv)) { uri ->
            if (uri == null) return@registerForActivityResult
            viewModel.exportToUri(uri)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_export, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainTableText = view.findViewById(R.id.exportMainTableText)
        formatText = view.findViewById(R.id.exportFormatText)
        delimiterSelector = view.findViewById(R.id.exportDelimiterSelector)
        delimiterSummaryText = view.findViewById(R.id.exportDelimiterSummaryText)
        customDelimiterLayout = view.findViewById(R.id.exportCustomDelimiterLayout)
        customDelimiterInput = view.findViewById(R.id.exportCustomDelimiterInput)
        criterionSelector = view.findViewById(R.id.exportCriterionSelector)
        criterionSummaryText = view.findViewById(R.id.exportCriterionSummaryText)
        estimatedRecordsText = view.findViewById(R.id.exportEstimatedRecordsText)
        exportStatusText = view.findViewById(R.id.exportStatusText)
        exportProgressBar = view.findViewById(R.id.exportProgressBar)
        exportProgressSummaryText = view.findViewById(R.id.exportProgressSummaryText)
        exportProgressMetricsText = view.findViewById(R.id.exportProgressMetricsText)
        saveButton = view.findViewById(R.id.exportSaveButton)
        shareButton = view.findViewById(R.id.exportShareButton)
        overlay = view.findViewById(R.id.exportLoadingOverlay)
        overlayText = view.findViewById(R.id.exportLoadingText)
        overlayCancelButton = view.findViewById(R.id.cancelExportOverlayButton)
        resultFileText = view.findViewById(R.id.exportResultFileText)
        resultSummaryText = view.findViewById(R.id.exportResultSummaryText)

        bindActions()
        collectUiState()
        viewModel.loadInitialState()
    }

    override fun onStop() {
        viewModel.cancelExport(cancelledByExit = true)
        super.onStop()
    }

    private fun bindActions() {
        saveButton.setOnClickListener {
            createDocumentLauncher.launch(viewModel.uiState.value.suggestedFileName)
        }
        shareButton.setOnClickListener {
            viewModel.exportToShareFile()
        }
        overlayCancelButton.setOnClickListener {
            viewModel.cancelExport(cancelledByExit = false)
        }
        criterionSelector.setOnItemClickListener { _, _, position, _ ->
            val option = viewModel.uiState.value.criterionOptions.getOrNull(position)
            viewModel.selectCriterion(option)
        }
        delimiterSelector.setOnItemClickListener { _, _, position, _ ->
            val option = viewModel.uiState.value.delimiterOptions.getOrNull(position)
            viewModel.selectDelimiter(option)
        }
        customDelimiterInput.doAfterTextChanged { editable ->
            viewModel.updateCustomDelimiter(editable?.toString().orEmpty())
        }
    }

    private fun collectUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::renderState)
            }
        }
    }

    private fun renderState(state: ExportUiState) {
        criterionSelector.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                state.criterionOptions
            )
        )
        criterionSelector.setText(state.selectedCriterionOption?.title.orEmpty(), false)
        delimiterSelector.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                state.delimiterOptions
            )
        )
        delimiterSelector.setText(state.selectedDelimiterOption?.title.orEmpty(), false)

        mainTableText.text = state.masterCollection?.displayName
            ?: getString(R.string.export_no_master_table)
        formatText.text = getString(
            R.string.export_format_value_with_delimiter,
            state.resolvedDelimiter ?: getString(R.string.export_delimiter_pending)
        )
        delimiterSummaryText.text = state.selectedDelimiterOption?.summary
            ?: getString(R.string.export_delimiter_empty_summary)
        customDelimiterLayout.visibility = if (state.requiresCustomDelimiter) View.VISIBLE else View.GONE
        if (customDelimiterInput.text?.toString() != state.customDelimiterValue) {
            customDelimiterInput.setText(state.customDelimiterValue)
            customDelimiterInput.setSelection(customDelimiterInput.text?.length ?: 0)
        }
        customDelimiterLayout.error = if (state.requiresCustomDelimiter && state.resolvedDelimiter == null) {
            getString(R.string.export_error_delimiter_required)
        } else {
            null
        }
        criterionSummaryText.text = state.selectedCriterionOption?.summary
            ?: getString(R.string.export_criterion_empty_summary)
        estimatedRecordsText.text = getString(
            R.string.export_estimated_records_value,
            state.exportableRecordCount
        )
        exportStatusText.text = buildStatusText(state)
        renderProgress(state)
        renderResult(state.lastResult)

        overlay.visibility = if (state.phase == ExportUiPhase.EXPORTING) View.VISIBLE else View.GONE
        overlayText.text = getString(R.string.export_loading_exporting)
        overlayCancelButton.visibility = if (state.phase == ExportUiPhase.EXPORTING) View.VISIBLE else View.GONE

        val actionsEnabled = state.isReady && !state.isBusy && !state.isCounting
        criterionSelector.isEnabled = !state.isBusy
        delimiterSelector.isEnabled = !state.isBusy
        customDelimiterInput.isEnabled = !state.isBusy
        saveButton.isEnabled = actionsEnabled
        shareButton.isEnabled = actionsEnabled

        state.pendingShareArtifact?.let(::shareArtifact)
    }

    private fun renderProgress(state: ExportUiState) {
        val metrics = state.progress.metrics
        val hasProgress = state.phase == ExportUiPhase.EXPORTING ||
            state.lastResult != null ||
            metrics.processedUnits > 0

        exportProgressBar.visibility = if (hasProgress) View.VISIBLE else View.GONE
        exportProgressSummaryText.visibility = if (hasProgress) View.VISIBLE else View.GONE
        exportProgressMetricsText.visibility = if (hasProgress) View.VISIBLE else View.GONE

        if (!hasProgress) {
            exportProgressBar.isIndeterminate = false
            exportProgressBar.progress = 0
            exportProgressSummaryText.text = ""
            exportProgressMetricsText.text = ""
            return
        }

        val totalRows = metrics.totalUnits
        exportProgressBar.max = 100
        exportProgressBar.isIndeterminate = totalRows <= 0
        exportProgressBar.progress = if (totalRows > 0) metrics.percent else 0

        exportProgressSummaryText.text = if (totalRows > 0) {
            getString(
                R.string.export_progress_summary,
                metrics.percent,
                metrics.processedUnits,
                totalRows
            )
        } else {
            getString(
                R.string.export_progress_summary_unknown_total,
                metrics.processedUnits
            )
        }

        val eta = when {
            state.lastResult != null -> getString(R.string.export_progress_eta_done)
            totalRows > metrics.processedUnits && metrics.processedUnits >= 10 ->
                metrics.estimatedRemainingMs?.let(::formatDurationMs)
                    ?: getString(R.string.export_progress_eta_pending)
            else -> getString(R.string.export_progress_eta_pending)
        }
        exportProgressMetricsText.text = getString(
            R.string.export_progress_metrics,
            metrics.elapsedMs,
            metrics.rowsPerSecond.roundToInt(),
            state.progress.exportedRows,
            eta
        )
    }

    private fun renderResult(result: ExportExecutionResult?) {
        if (result == null) {
            resultFileText.text = getString(R.string.export_result_file_empty)
            resultSummaryText.text = getString(R.string.export_result_summary_empty)
            return
        }

        resultFileText.text = result.fileName
        resultSummaryText.text = getString(
            R.string.export_result_summary_value,
            result.exportedRows,
            result.elapsedMs,
            formatBytes(result.bytesWritten)
        )
    }

    private fun buildStatusText(state: ExportUiState): String {
        return when {
            state.cancelledByExit -> getString(R.string.export_status_cancelled_on_exit)
            state.phase == ExportUiPhase.COUNTING -> getString(R.string.export_status_counting)
            state.phase == ExportUiPhase.EXPORTING -> getString(R.string.export_status_exporting)
            state.errorMessage != null -> state.errorMessage
            !state.isReady -> getString(R.string.export_status_not_ready)
            state.lastResult != null -> getString(
                R.string.export_status_done,
                state.lastResult.exportedRows
            )
            else -> getString(R.string.export_status_ready)
        }
    }

    private fun shareArtifact(artifact: ShareExportArtifact) {
        val file = File(artifact.filePath)
        if (!file.exists()) {
            viewModel.clearPendingShareArtifact()
            return
        }

        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = ExportConfig.mimeTypeCsv
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, artifact.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.export_share_chooser_title)))
        viewModel.clearPendingShareArtifact()
    }

    private fun formatDurationMs(milliseconds: Long): String {
        return when {
            milliseconds < 1000L -> getString(R.string.import_duration_ms, milliseconds)
            else -> String.format("%1$.1fs", milliseconds / 1000.0)
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024L -> getString(R.string.export_bytes_value, bytes)
            bytes < 1024L * 1024L -> String.format("%1$.1f KB", bytes / 1024.0)
            else -> String.format("%1$.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
