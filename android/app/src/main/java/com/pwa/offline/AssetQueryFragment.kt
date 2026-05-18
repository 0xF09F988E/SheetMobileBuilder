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
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class AssetQueryFragment : Fragment() {

    private sealed interface PendingAction {
        data class Save(val updates: Map<Long, String>) : PendingAction
        data object Confirm : PendingAction
    }

    private val viewModel: AssetQueryViewModel by viewModels {
        AssetQueryViewModelFactory(
            AssetQueryRepository(
                AppDatabaseHelper(requireContext().applicationContext)
            )
        )
    }

    private lateinit var searchInput: EditText
    private lateinit var recordMetaText: TextView
    private lateinit var reviewStateText: TextView
    private lateinit var statusText: TextView
    private lateinit var additionalFieldsBar: LinearLayout
    private lateinit var additionalFieldsText: TextView
    private lateinit var toggleAdditionalFieldsButton: Button
    private lateinit var searchButton: Button
    private lateinit var clearSearchButton: Button
    private lateinit var confirmButton: Button
    private lateinit var editButton: Button
    private lateinit var cancelEditButton: Button
    private lateinit var saveButton: Button
    private lateinit var readOnlyActionsContainer: LinearLayout
    private lateinit var editActionsContainer: LinearLayout
    private lateinit var fieldsRecyclerView: RecyclerView
    private lateinit var rootView: View
    private lateinit var fieldAdapter: AssetFieldAdapter
    private var lastToastStatus: AssetQueryStatus? = null
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
        return inflater.inflate(R.layout.fragment_asset_query, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rootView = view.findViewById(R.id.assetQueryRoot)
        searchInput = view.findViewById(R.id.assetSearchInput)
        recordMetaText = view.findViewById(R.id.recordMetaText)
        reviewStateText = view.findViewById(R.id.assetReviewStateText)
        statusText = view.findViewById(R.id.assetStatusText)
        additionalFieldsBar = view.findViewById(R.id.assetAdditionalFieldsBar)
        additionalFieldsText = view.findViewById(R.id.assetAdditionalFieldsText)
        toggleAdditionalFieldsButton = view.findViewById(R.id.assetToggleAdditionalFieldsButton)
        searchButton = view.findViewById(R.id.searchAssetButton)
        clearSearchButton = view.findViewById(R.id.clearSearchButton)
        confirmButton = view.findViewById(R.id.confirmAssetButton)
        editButton = view.findViewById(R.id.editAssetButton)
        cancelEditButton = view.findViewById(R.id.cancelAssetEditButton)
        saveButton = view.findViewById(R.id.saveAssetChangesButton)
        readOnlyActionsContainer = view.findViewById(R.id.readOnlyActionsContainer)
        editActionsContainer = view.findViewById(R.id.editActionsContainer)
        fieldsRecyclerView = view.findViewById(R.id.assetFieldsRecyclerView)

        fieldAdapter = AssetFieldAdapter(
            emptyValueLabel = getString(R.string.asset_empty_value),
            booleanLabel = getString(R.string.asset_boolean_label),
            labelFormatter = ::buildFieldLabel,
            onFieldFocused = ::ensureFieldVisible,
            optionSuggestionsProvider = ::loadOptionSuggestions
        )
        fieldsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        fieldsRecyclerView.adapter = fieldAdapter

        searchButton.setOnClickListener {
            hideKeyboard()
            viewModel.search(searchInput.text.toString().trim())
        }
        clearSearchButton.setOnClickListener {
            searchInput.text?.clear()
            viewModel.clearResult()
        }
        toggleAdditionalFieldsButton.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.showAdditionalFields) {
                viewModel.hideAdditionalFields()
            } else {
                viewModel.showAdditionalFields()
            }
        }
        confirmButton.setOnClickListener {
            requestActionWithLocation(PendingAction.Confirm)
        }
        editButton.setOnClickListener { viewModel.enterEditMode() }
        cancelEditButton.setOnClickListener { viewModel.exitEditMode() }
        saveButton.setOnClickListener {
            requestActionWithLocation(PendingAction.Save(fieldAdapter.buildUpdates()))
        }

        applyKeyboardInsets()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::renderState)
            }
        }

        viewModel.loadInitial()
    }

    private fun renderState(state: AssetQueryUiState) {
        renderStatusToast(state.status)
        val detail = state.recordDetail

        if (detail == null) {
            recordMetaText.text = getString(R.string.asset_record_meta_empty)
            reviewStateText.text = getString(R.string.asset_review_state_empty)
            additionalFieldsBar.visibility = View.GONE
            fieldAdapter.clear()
        } else {
            recordMetaText.text = buildLastActionText(detail)
            reviewStateText.text = buildReviewState(detail)
            val visibleFields = buildVisibleFields(detail.fields, state.isEditing, state.showAdditionalFields)
            fieldAdapter.submitFields(visibleFields, state.isEditing)
            renderAdditionalFieldsBar(detail.fields, state)
        }

        readOnlyActionsContainer.visibility = if (!state.isEditing) View.VISIBLE else View.GONE
        editActionsContainer.visibility = if (state.isEditing) View.VISIBLE else View.GONE

        confirmButton.isEnabled = detail != null && !state.isBusy
        editButton.isEnabled = detail != null && !state.isBusy
        saveButton.isEnabled = detail != null && state.isEditing && !state.isBusy
        cancelEditButton.isEnabled = !state.isBusy
        searchButton.isEnabled = !state.isBusy
        clearSearchButton.isEnabled = !state.isBusy

        statusText.text = when (state.status) {
            AssetQueryStatus.EMPTY -> getString(R.string.asset_status_empty)
            AssetQueryStatus.LOOKUP_REQUIRED -> getString(R.string.asset_status_lookup_required)
            AssetQueryStatus.READY -> getString(R.string.asset_status_ready_search)
            AssetQueryStatus.SEARCHING -> getString(R.string.asset_status_searching)
            AssetQueryStatus.NOT_FOUND -> getString(R.string.asset_status_not_found)
            AssetQueryStatus.READ_ONLY -> getString(R.string.asset_status_read_only)
            AssetQueryStatus.EDITING -> getString(R.string.asset_status_editing)
            AssetQueryStatus.CONFIRMING -> getString(R.string.asset_status_confirming)
            AssetQueryStatus.CONFIRMED -> getString(R.string.asset_status_confirmed)
            AssetQueryStatus.SAVING -> getString(R.string.asset_status_saving)
            AssetQueryStatus.SAVED -> getString(R.string.asset_status_saved)
            AssetQueryStatus.ERROR -> state.errorMessage ?: getString(R.string.asset_status_save_error)
        }
    }

    private fun renderStatusToast(status: AssetQueryStatus) {
        if (status == lastToastStatus) return
        lastToastStatus = status
        val messageRes = when (status) {
            AssetQueryStatus.CONFIRMED -> R.string.asset_status_confirmed
            AssetQueryStatus.SAVED -> R.string.asset_status_saved
            else -> null
        } ?: return
        Toast.makeText(requireContext(), getString(messageRes), Toast.LENGTH_SHORT).show()
    }

    private fun hideKeyboard() {
        searchInput.clearFocus()
        val inputMethodManager =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(searchInput.windowToken, 0)
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
            PendingAction.Confirm -> viewModel.markConforme(locationMeta)
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

    private fun applyKeyboardInsets() {
        val baseListBottom = fieldsRecyclerView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, windowInsets ->
            val imeBottom = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBottom = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val extraBottom = (imeBottom - systemBottom).coerceAtLeast(0)
            fieldsRecyclerView.updatePadding(bottom = baseListBottom + extraBottom)
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

    private fun buildFieldLabel(field: AssetFieldValue): String {
        return field.fieldDisplayName
    }

    private fun buildReviewState(detail: AssetRecordDetail): String {
        val base = getString(
            R.string.asset_review_state_value,
            resolveReviewStatusLabel(detail.reviewStatus)
        )
        return if (detail.changedFieldsText.isBlank()) {
            base
        } else {
            "$base\n${getString(R.string.asset_review_changed_fields, detail.changedFieldsText)}"
        }
    }

    private fun buildLastActionText(detail: AssetRecordDetail): String {
        val actionLabel = when (detail.reviewAction) {
            ReviewActionCodes.CREATED_MANUAL -> getString(R.string.asset_last_action_created)

            ReviewActionCodes.EDIT_SAVED -> getString(R.string.asset_last_action_edited)

            ReviewActionCodes.CONFIRMED_MANUAL -> getString(R.string.asset_last_action_confirmed)

            ReviewActionCodes.IMPORTED -> getString(R.string.asset_last_action_imported)

            else -> getString(R.string.asset_last_action_unknown)
        }
        val rawTimestamp = when (detail.reviewAction) {
            ReviewActionCodes.CONFIRMED_MANUAL -> detail.reviewedAt
            ReviewActionCodes.CREATED_MANUAL,
            ReviewActionCodes.IMPORTED -> detail.createdAt
            else -> detail.updatedAt
        }
        val formattedTimestamp = TimestampFormatters.sqliteUtcToDeviceMx(rawTimestamp)
        return if (formattedTimestamp.isBlank()) {
            getString(R.string.asset_last_action_value, actionLabel)
        } else {
            getString(R.string.asset_last_action_value_with_time, actionLabel, formattedTimestamp)
        }
    }

    private fun resolveReviewStatusLabel(statusCode: String): String {
        return when (statusCode) {
            ReviewStatusCodes.PENDING -> getString(R.string.asset_review_status_pending)

            ReviewStatusCodes.CONFIRMED -> getString(R.string.asset_review_status_confirmed)

            ReviewStatusCodes.UPDATED -> getString(R.string.asset_review_status_updated)

            else -> statusCode.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    private fun buildVisibleFields(
        fields: List<AssetFieldValue>,
        isEditing: Boolean,
        showAdditionalFields: Boolean
    ): List<AssetFieldValue> {
        if (isEditing || showAdditionalFields) {
            return fields
        }
        return fields.filter(::hasUsefulValue)
    }

    private fun renderAdditionalFieldsBar(
        fields: List<AssetFieldValue>,
        state: AssetQueryUiState
    ) {
        val additionalCount = fields.count { !hasUsefulValue(it) }
        val shouldShowBar = additionalCount > 0 && !state.isEditing
        additionalFieldsBar.visibility = if (shouldShowBar) View.VISIBLE else View.GONE
        if (!shouldShowBar) return

        if (state.showAdditionalFields) {
            additionalFieldsText.text = getString(
                R.string.asset_additional_fields_visible,
                additionalCount
            )
            toggleAdditionalFieldsButton.text = getString(R.string.asset_hide_additional_fields)
        } else {
            additionalFieldsText.text = getString(
                R.string.asset_additional_fields_hidden_count,
                additionalCount
            )
            toggleAdditionalFieldsButton.text = getString(R.string.asset_show_additional_fields)
        }
    }

    private fun hasUsefulValue(field: AssetFieldValue): Boolean {
        return field.value.trim().isNotEmpty()
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
}
