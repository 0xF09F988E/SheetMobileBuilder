package com.pwa.offline

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class RecordCreateFragment : Fragment() {

    private sealed interface PendingAction {
        data class Save(val updates: Map<Long, String>) : PendingAction
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
    private lateinit var fieldsRecyclerView: RecyclerView
    private lateinit var saveButton: Button
    private lateinit var clearButton: Button
    private lateinit var fieldsEmptyText: TextView
    private lateinit var fieldAdapter: AssetFieldAdapter
    private var renderedFormVersion: Long = -1L
    private var pendingAction: PendingAction? = null
    private val locationPermissions = arrayOf(
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val action = pendingAction ?: return@registerForActivityResult
            pendingAction = null
            if (grants.values.any { it }) {
                performPendingActionWithLocation(action)
            } else {
                performPendingAction(action, null)
            }
        }

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
        fieldsRecyclerView = view.findViewById(R.id.recordCreateFieldsRecyclerView)
        saveButton = view.findViewById(R.id.recordCreateSaveButton)
        clearButton = view.findViewById(R.id.recordCreateClearButton)
        fieldsEmptyText = view.findViewById(R.id.recordCreateFieldsEmptyText)

        fieldAdapter = AssetFieldAdapter(
            emptyValueLabel = getString(R.string.record_create_empty_value),
            booleanLabel = getString(R.string.asset_boolean_label),
            labelFormatter = { it.fieldDisplayName },
            onFieldFocused = ::ensureFieldVisible,
            optionSuggestionsProvider = ::loadOptionSuggestions
        )
        fieldsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        fieldsRecyclerView.adapter = fieldAdapter

        tableSelector.setOnItemClickListener { _, _, position, _ ->
            val option = viewModel.uiState.value.collectionOptions.getOrNull(position) ?: return@setOnItemClickListener
            hideKeyboard()
            viewModel.selectCollection(option.id)
        }

        saveButton.setOnClickListener {
            hideKeyboard()
            requestActionWithLocation(PendingAction.Save(fieldAdapter.buildUpdates()))
        }

        clearButton.setOnClickListener {
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
        val selectorAdapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            state.collectionOptions
        )
        tableSelector.setAdapter(selectorAdapter)
        tableSelector.setText(state.selectedCollection?.displayName.orEmpty(), false)

        if (state.formVersion != renderedFormVersion) {
            fieldAdapter.submitFields(state.formFields, true)
            renderedFormVersion = state.formVersion
        }
        fieldsEmptyText.isVisible = state.selectedCollection != null && state.formFields.isEmpty()

        saveButton.isEnabled = state.selectedCollection != null && state.formFields.isNotEmpty() && !state.isBusy
        clearButton.isEnabled = state.selectedCollection != null && !state.isBusy
        tableSelector.isEnabled = !state.isBusy

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
        fieldsRecyclerView.postDelayed({
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

    private fun requestActionWithLocation(action: PendingAction) {
        if (hasAnyLocationPermission()) {
            if (!ActionLocationCapture.isLocationServiceEnabled(requireContext().applicationContext)) {
                showLocationServicePrompt(action)
                return
            }
            performPendingActionWithLocation(action)
            return
        }
        pendingAction = action
        showLocationPermissionPrompt()
    }

    private fun performPendingActionWithLocation(action: PendingAction) {
        viewLifecycleOwner.lifecycleScope.launch {
            val locationMeta = runCatching {
                ActionLocationCapture.captureBestEffort(requireContext().applicationContext)
            }.getOrNull()
            performPendingAction(action, locationMeta)
        }
    }

    private fun performPendingAction(action: PendingAction, locationMeta: ActionLocationMeta?) {
        when (action) {
            is PendingAction.Save -> viewModel.save(action.updates, locationMeta)
        }
    }

    private fun hasAnyLocationPermission(): Boolean {
        return locationPermissions.any { permission ->
            ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showLocationPermissionPrompt() {
        val action = pendingAction ?: return
        val shouldExplain = locationPermissions.any(::shouldShowRequestPermissionRationale)
        val messageRes = if (shouldExplain) {
            R.string.location_permission_rationale
        } else {
            R.string.location_permission_request_message
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.location_permission_request_title)
            .setMessage(messageRes)
            .setNegativeButton(R.string.location_permission_skip) { _, _ ->
                pendingAction = null
                performPendingAction(action, null)
            }
            .setPositiveButton(R.string.location_permission_continue) { _, _ ->
                locationPermissionLauncher.launch(locationPermissions)
            }
            .show()
    }

    private fun showLocationServicePrompt(action: PendingAction) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.location_service_request_title)
            .setMessage(R.string.location_service_request_message)
            .setNegativeButton(R.string.location_permission_skip) { _, _ ->
                pendingAction = null
                performPendingAction(action, null)
            }
            .setPositiveButton(R.string.location_service_open_settings) { _, _ ->
                pendingAction = null
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .show()
    }
}
