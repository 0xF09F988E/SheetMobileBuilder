package com.pwa.offline

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class CloneDataFragment : Fragment() {

    private val viewModel: CloneDataViewModel by viewModels {
        val appContext = requireContext().applicationContext
        val databaseHelper = AppDatabaseHelper(appContext)
        CloneDataViewModelFactory(
            CloneDataRepository(
                appContext = appContext,
                databaseHelper = databaseHelper,
                cloneDataService = CloneDataService(appContext)
            )
        )
    }

    private lateinit var totalTablesText: TextView
    private lateinit var totalRecordsText: TextView
    private lateinit var statusText: TextView
    private lateinit var resultFileText: TextView
    private lateinit var resultSummaryText: TextView
    private lateinit var exportButton: Button
    private lateinit var importButton: Button
    private lateinit var overlay: View
    private lateinit var overlayText: TextView

    private val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(CloneDataConfig.mimeType)) { uri ->
            if (uri == null) return@registerForActivityResult
            viewModel.exportBackup(uri)
        }

    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            confirmImport(uri)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_clone_data, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        totalTablesText = view.findViewById(R.id.cloneTotalTablesText)
        totalRecordsText = view.findViewById(R.id.cloneTotalRecordsText)
        statusText = view.findViewById(R.id.cloneStatusText)
        resultFileText = view.findViewById(R.id.cloneResultFileText)
        resultSummaryText = view.findViewById(R.id.cloneResultSummaryText)
        exportButton = view.findViewById(R.id.cloneExportButton)
        importButton = view.findViewById(R.id.cloneImportButton)
        overlay = view.findViewById(R.id.cloneLoadingOverlay)
        overlayText = view.findViewById(R.id.cloneLoadingText)

        exportButton.setOnClickListener {
            createDocumentLauncher.launch(CloneDataConfig.buildSuggestedFileName())
        }
        importButton.setOnClickListener {
            openDocumentLauncher.launch(arrayOf(CloneDataConfig.mimeType, "*/*"))
        }

        collectUiState()
        viewModel.loadInitialState()
    }

    private fun collectUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::renderState)
            }
        }
    }

    private fun renderState(state: CloneDataUiState) {
        totalTablesText.text = state.localSummary?.totalTables?.toString()
            ?: getString(R.string.clone_summary_placeholder)
        totalRecordsText.text = state.localSummary?.totalRecords?.toString()
            ?: getString(R.string.clone_summary_placeholder)

        statusText.text = when {
            state.errorMessage != null -> state.errorMessage
            state.phase == CloneDataPhase.LOADING -> getString(R.string.clone_status_loading)
            state.phase == CloneDataPhase.EXPORTING -> getString(R.string.clone_status_exporting)
            state.phase == CloneDataPhase.IMPORTING -> getString(R.string.clone_status_importing)
            state.lastImportResult != null -> getString(
                R.string.clone_status_import_done,
                state.lastImportResult.restoredSummary.totalRecords
            )
            state.lastExportResult != null -> getString(
                R.string.clone_status_export_done,
                state.lastExportResult.fileName
            )
            else -> getString(R.string.clone_status_ready)
        }

        renderResult(state)

        overlay.visibility = if (state.isBusy) View.VISIBLE else View.GONE
        overlayText.text = when (state.phase) {
            CloneDataPhase.EXPORTING -> getString(R.string.clone_loading_export)
            CloneDataPhase.IMPORTING -> getString(R.string.clone_loading_import)
            else -> getString(R.string.clone_loading_default)
        }

        exportButton.isEnabled = !state.isBusy
        importButton.isEnabled = !state.isBusy

        if (state.restartRequired) {
            showRestartDialog()
            viewModel.clearRestartRequired()
        }
    }

    private fun renderResult(state: CloneDataUiState) {
        val importResult = state.lastImportResult
        val exportResult = state.lastExportResult
        when {
            importResult != null -> {
                resultFileText.text = importResult.fileName
                resultSummaryText.text = getString(
                    R.string.clone_result_import_summary,
                    importResult.restoredSummary.totalTables,
                    importResult.restoredSummary.totalRecords,
                    formatBytes(importResult.bytesRead),
                    importResult.elapsedMs
                )
            }
            exportResult != null -> {
                resultFileText.text = exportResult.fileName
                resultSummaryText.text = getString(
                    R.string.clone_result_export_summary,
                    formatBytes(exportResult.bytesWritten),
                    exportResult.elapsedMs
                )
            }
            else -> {
                resultFileText.text = getString(R.string.clone_result_file_empty)
                resultSummaryText.text = getString(R.string.clone_result_summary_empty)
            }
        }
    }

    private fun confirmImport(uri: Uri) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.clone_import_confirm_title)
            .setMessage(R.string.clone_import_confirm_message)
            .setNegativeButton(R.string.dialog_delete_cancel, null)
            .setPositiveButton(R.string.clone_import_confirm_button) { _, _ ->
                viewModel.importBackup(uri)
            }
            .show()
    }

    private fun showRestartDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.clone_restart_title)
            .setMessage(R.string.clone_restart_message)
            .setCancelable(false)
            .setPositiveButton(R.string.clone_restart_button) { _, _ ->
                restartApplication()
            }
            .show()
    }

    private fun restartApplication() {
        val context = requireContext()
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            ?: return
        startActivity(launchIntent)
        requireActivity().finishAffinity()
        Runtime.getRuntime().exit(0)
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024L -> getString(R.string.export_bytes_value, bytes)
            bytes < 1024L * 1024L -> String.format("%1$.1f KB", bytes / 1024.0)
            else -> String.format("%1$.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
