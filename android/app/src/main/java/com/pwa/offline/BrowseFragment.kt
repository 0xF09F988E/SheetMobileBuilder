package com.pwa.offline

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class BrowseFragment : Fragment() {

    private val viewModel: BrowseViewModel by viewModels {
        BrowseViewModelFactory(
            BrowseRepository(
                AppDatabaseHelper(requireContext().applicationContext)
            )
        )
    }

    private lateinit var selectedTableText: TextView
    private lateinit var pageText: TextView
    private lateinit var visibleRangeText: TextView
    private lateinit var statusText: TextView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RecordPreviewAdapter
    private var lastActionNonce: Long = -1L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_browse, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        selectedTableText = view.findViewById(R.id.browseSelectedTableText)
        pageText = view.findViewById(R.id.browsePageText)
        visibleRangeText = view.findViewById(R.id.browseVisibleRangeText)
        statusText = view.findViewById(R.id.browseStatusText)
        loadingIndicator = view.findViewById(R.id.browseLoadingIndicator)
        previousButton = view.findViewById(R.id.previousPageButton)
        nextButton = view.findViewById(R.id.nextPageButton)
        recyclerView = view.findViewById(R.id.recordsRecyclerView)

        adapter = RecordPreviewAdapter(
            onShareRecord = ::shareRecord,
            onDeleteRecord = ::confirmDeleteRecord
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        previousButton.setOnClickListener { viewModel.goPrevious() }
        nextButton.setOnClickListener { viewModel.goNext() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::renderState)
            }
        }

        viewModel.loadInitial()
    }

    private fun renderState(state: BrowseUiState) {
        loadingIndicator.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        if (!state.hasMasterTable) {
            adapter.submitList(emptyList())
            selectedTableText.text = getString(R.string.browse_selected_table_empty)
            pageText.text = getString(R.string.browse_page_empty)
            visibleRangeText.text = getString(R.string.browse_range_empty)
            statusText.visibility = View.VISIBLE
            statusText.text = getString(R.string.browse_status_empty)
            previousButton.isEnabled = false
            nextButton.isEnabled = false
            return
        }

        val totalPages = state.totalPages
        val firstVisible = if (state.records.isEmpty()) 0 else (state.currentPage * BrowseUiState.PAGE_SIZE) + 1
        val lastVisible = if (state.records.isEmpty()) 0 else firstVisible + state.records.size - 1

        selectedTableText.text = state.masterCollection?.let {
            getString(R.string.browse_selected_table_value, it.displayName, state.totalRecords)
        } ?: getString(R.string.browse_selected_table_empty)

        pageText.text = if (totalPages == 0) {
            getString(R.string.browse_page_empty)
        } else {
            getString(R.string.browse_page_value, state.currentPage + 1, totalPages)
        }

        visibleRangeText.text = if (state.records.isEmpty()) {
            getString(R.string.browse_range_empty)
        } else {
            getString(R.string.browse_range_value, firstVisible, lastVisible)
        }

        adapter.submitList(state.records)
        renderActionMessage(state)

        if (state.records.isEmpty() && !state.isLoading) {
            statusText.visibility = View.VISIBLE
            statusText.text = getString(R.string.browse_status_no_rows)
        } else {
            statusText.visibility = View.GONE
        }

        previousButton.isEnabled = !state.isLoading && state.currentPage > 0
        nextButton.isEnabled = !state.isLoading &&
            ((state.currentPage + 1) * BrowseUiState.PAGE_SIZE < state.totalRecords)
    }

    private fun renderActionMessage(state: BrowseUiState) {
        val message = state.actionMessage ?: return
        if (state.actionMessageNonce == lastActionNonce) return
        lastActionNonce = state.actionMessageNonce
        Snackbar.make(
            recyclerView,
            getString(R.string.browse_delete_done, message),
            Snackbar.LENGTH_SHORT
        ).show()
        viewModel.clearActionMessage()
    }

    private fun shareRecord(record: RecordPreview) {
        val title = record.title.ifBlank { getString(R.string.browse_record_title_fallback) }
        val body = buildString {
            append(title)
            if (record.values.isNotEmpty()) {
                appendLine()
                record.values.entries.forEachIndexed { index, entry ->
                    append(entry.key)
                    append(": ")
                    append(entry.value.ifBlank { "-" })
                    if (index < record.values.size - 1) {
                        appendLine()
                    }
                }
            }
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.browse_share_chooser_title)))
    }

    private fun confirmDeleteRecord(record: RecordPreview) {
        val title = record.title.ifBlank { getString(R.string.browse_record_title_fallback) }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.browse_delete_title)
            .setMessage(getString(R.string.browse_delete_message, title))
            .setNegativeButton(R.string.dialog_delete_cancel, null)
            .setPositiveButton(R.string.dialog_delete_confirm) { _, _ ->
                viewModel.deleteRecord(record)
            }
            .show()
    }
}
