package com.pwa.offline

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.pwa.offline.dialogs.FieldEditDialogHelper
import kotlinx.coroutines.launch

class RecordCreateFragment : Fragment() {

    private enum class PendingAction {
        NONE,
        CLEARING,
        SAVING
    }

    private val viewModel: RecordCreateViewModel by viewModels {
        RecordCreateViewModelFactory(
            RecordCreateRepository(
                AppDatabaseHelper(requireContext().applicationContext)
            )
        )
    }

    private lateinit var rootView: View
    private lateinit var tableSelector: AutoCompleteTextView
    private lateinit var statusText: TextView
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var saveProgress: ProgressBar
    private lateinit var saveLoadingText: TextView
    private lateinit var fieldsContainer: android.widget.LinearLayout
    private lateinit var saveButton: Button
    private lateinit var clearButton: Button
    private lateinit var fieldsEmptyText: TextView
    private lateinit var formRenderer: RecordCreateFormRenderer
    private var renderedFormVersion: Long = -1L
    private var latestState = RecordCreateUiState()
    private var pendingAction = PendingAction.NONE
    private var lastToastKey: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_record_create, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rootView = view.findViewById(R.id.recordCreateRoot)
        tableSelector = view.findViewById(R.id.recordCreateTableSelector)
        statusText = view.findViewById(R.id.recordCreateStatusText)
        progressIndicator = view.findViewById(R.id.recordCreateProgressIndicator)
        saveProgress = view.findViewById(R.id.recordCreateSaveProgress)
        saveLoadingText = view.findViewById(R.id.recordCreateSaveLoadingText)
        fieldsContainer = view.findViewById(R.id.recordCreateFieldsContainer)
        saveButton = view.findViewById(R.id.recordCreateSaveButton)
        clearButton = view.findViewById(R.id.recordCreateClearButton)
        fieldsEmptyText = view.findViewById(R.id.recordCreateFieldsEmptyText)

        formRenderer = RecordCreateFormRenderer(
            context = requireContext(),
            container = fieldsContainer,
            emptyValueLabel = getString(R.string.record_create_empty_value),
            booleanLabel = getString(R.string.asset_boolean_label),
            onFieldFocused = ::ensureFieldVisible,
            onListFieldEditRequested = ::openListFieldEditor
        )

        tableSelector.setOnItemClickListener { _, _, position, _ ->
            val option = viewModel.uiState.value.collectionOptions.getOrNull(position) ?: return@setOnItemClickListener
            hideKeyboard()
            viewModel.selectCollection(option.id)
        }

        saveButton.setOnClickListener {
            if (!beginSaveAction()) return@setOnClickListener
            hideKeyboard()
            viewModel.save(formRenderer.buildUpdates())
        }

        clearButton.setOnClickListener {
            if (!beginClearAction()) return@setOnClickListener
            val selectedId = viewModel.uiState.value.selectedCollection?.id ?: return@setOnClickListener
            hideKeyboard()
            viewModel.selectCollection(selectedId)
        }

        applyKeyboardInsets()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::renderState)
            }
        }

        viewModel.loadInitial()
    }

    private fun renderState(state: RecordCreateUiState) {
        latestState = state
        if (!state.isBusy) {
            pendingAction = PendingAction.NONE
        }
        renderValidationToast(state)
        val selectorAdapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            state.collectionOptions
        )
        tableSelector.setAdapter(selectorAdapter)
        tableSelector.setText(state.selectedCollection?.displayName.orEmpty(), false)

        if (state.formVersion != renderedFormVersion) {
            formRenderer.render(state.formFields)
            renderedFormVersion = state.formVersion
        }
        fieldsEmptyText.isVisible = state.selectedCollection != null && state.formFields.isEmpty()

        val busy = state.isBusy || pendingAction != PendingAction.NONE
        val saveLoading = pendingAction == PendingAction.SAVING || state.status == RecordCreateStatus.SAVING
        saveButton.isEnabled = state.selectedCollection != null && state.formFields.isNotEmpty() && !busy
        clearButton.isEnabled = state.selectedCollection != null && !busy
        tableSelector.isEnabled = !busy
        progressIndicator.isVisible = busy
        saveProgress.isVisible = saveLoading
        saveLoadingText.isVisible = saveLoading
        saveButton.text = if (saveLoading) "" else getString(R.string.record_create_save_button)

        statusText.text = when (state.status) {
            RecordCreateStatus.EMPTY -> getString(R.string.record_create_status_no_tables)
            RecordCreateStatus.READY -> {
                if (state.formFields.isEmpty()) {
                    getString(R.string.record_create_status_no_fields)
                } else {
                    getString(R.string.record_create_status_ready, state.formFields.size)
                }
            }
            RecordCreateStatus.LOADING_FIELDS -> getString(R.string.record_create_status_loading_fields)
            RecordCreateStatus.SAVING -> getString(R.string.record_create_status_saving)
            RecordCreateStatus.SAVED -> state.savedRecordLabel?.let {
                getString(R.string.record_create_status_saved, it)
            } ?: getString(R.string.record_create_status_saved_generic)
            RecordCreateStatus.ERROR -> state.errorMessage ?: getString(R.string.record_create_status_error)
        }
    }

    private fun renderValidationToast(state: RecordCreateUiState) {
        val message = when (state.status) {
            RecordCreateStatus.ERROR -> state.errorMessage?.trim()?.takeIf { it.isNotEmpty() }
            else -> null
        } ?: run {
            lastToastKey = null
            return
        }

        if (state.isBusy) return

        val toastKey = "${state.status}|$message"
        if (toastKey == lastToastKey) return
        lastToastKey = toastKey

        Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show()
    }

    private fun beginSaveAction(): Boolean {
        val state = latestState
        if (pendingAction != PendingAction.NONE || state.isBusy || state.selectedCollection == null || state.formFields.isEmpty()) {
            return false
        }
        pendingAction = PendingAction.SAVING
        progressIndicator.isVisible = true
        saveProgress.isVisible = true
        saveLoadingText.isVisible = true
        saveButton.isEnabled = false
        clearButton.isEnabled = false
        tableSelector.isEnabled = false
        saveButton.text = ""
        statusText.text = getString(R.string.record_create_status_saving)
        return true
    }

    private fun beginClearAction(): Boolean {
        val state = latestState
        if (pendingAction != PendingAction.NONE || state.isBusy || state.selectedCollection == null) {
            return false
        }
        pendingAction = PendingAction.CLEARING
        progressIndicator.isVisible = true
        saveProgress.isVisible = false
        saveLoadingText.isVisible = false
        saveButton.isEnabled = false
        clearButton.isEnabled = false
        tableSelector.isEnabled = false
        saveButton.text = getString(R.string.record_create_save_button)
        statusText.text = getString(R.string.record_create_status_loading_fields)
        return true
    }

    private fun hideKeyboard() {
        val inputMethodManager =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(rootView.windowToken, 0)
    }

    private fun applyKeyboardInsets() {
        val baseRootBottom = rootView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, windowInsets ->
            val imeBottom = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBottom = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val extraBottom = (imeBottom - systemBottom).coerceAtLeast(0)
            rootView.updatePadding(bottom = baseRootBottom + extraBottom)
            windowInsets
        }
        ViewCompat.requestApplyInsets(rootView)
    }

    private fun ensureFieldVisible(focusedView: View) {
        fieldsContainer.postDelayed({
            focusedView.requestRectangleOnScreen(
                android.graphics.Rect(0, 0, focusedView.width, focusedView.height),
                true
            )
        }, 150)
    }

    private fun loadOptionSuggestions(
        field: AssetFieldValue,
        query: String,
        onResult: (List<OptionSuggestion>) -> Unit
    ) {
        val sourceCollectionId = field.optionSourceCollectionId ?: run {
            onResult(emptyList())
            return
        }
        viewModel.requestOptionSuggestions(sourceCollectionId, query, onResult)
    }

    private fun openListFieldEditor(
        field: AssetFieldValue,
        currentValue: String,
        onValueSelected: (String) -> Unit
    ) {
        FieldEditDialogHelper.showListSelector(
            context = requireContext(),
            inflater = layoutInflater,
            field = field,
            initialValue = currentValue,
            loadOptionSuggestions = ::loadOptionSuggestions,
            onValueSelected = onValueSelected
        )
    }

}
