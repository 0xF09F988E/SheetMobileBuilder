package com.pwa.offline

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SchemaFragment : Fragment() {

    private lateinit var databaseHelper: AppDatabaseHelper
    private lateinit var fieldTypeOptions: List<SchemaFieldTypeOption>
    private lateinit var searchModeOptions: List<SchemaSearchModeOption>
    private lateinit var valueRuleOptions: List<SchemaValueRuleOption>
    private lateinit var optionDisplayRoleOptions: List<SchemaOptionDisplayRoleOption>

    private lateinit var collectionsCountText: TextView
    private lateinit var selectedTableHintText: TextView
    private lateinit var statusText: TextView
    private lateinit var schemaLoadingOverlay: View
    private lateinit var schemaLoadingText: TextView
    private lateinit var schemaScrollView: ScrollView
    private lateinit var schemaContentContainer: LinearLayout
    private lateinit var schemaTabLayout: TabLayout
    private lateinit var schemaTablesTabContent: LinearLayout
    private lateinit var schemaFieldsTabContent: LinearLayout
    private lateinit var collectionsRecyclerView: RecyclerView
    private lateinit var fieldsRecyclerView: RecyclerView
    private lateinit var collectionsEmptyText: TextView
    private lateinit var fieldsEmptyText: TextView
    private lateinit var collectionNameInput: EditText
    private lateinit var collectionDescriptionInput: EditText
    private lateinit var collectionMasterSwitch: SwitchCompat
    private lateinit var collectionOptionsSwitch: SwitchCompat
    private lateinit var fieldNameInput: EditText
    private lateinit var collectionSelector: AutoCompleteTextView
    private lateinit var fieldTypeSelector: AutoCompleteTextView
    private lateinit var fieldOptionSourceLayout: TextInputLayout
    private lateinit var fieldOptionSourceSelector: AutoCompleteTextView
    private lateinit var fieldOptionDisplayRoleLayout: TextInputLayout
    private lateinit var fieldOptionDisplayRoleSelector: AutoCompleteTextView
    private lateinit var fieldSearchModeSelector: AutoCompleteTextView
    private lateinit var fieldValueRuleSelector: AutoCompleteTextView
    private lateinit var createCollectionButton: Button
    private lateinit var createFieldButton: Button
    private lateinit var cancelFieldEditButton: Button
    private lateinit var reloadButton: Button
    private lateinit var deleteAllButton: Button

    private var collectionOptions: List<CollectionOption> = emptyList()
    private var optionSourceOptions: List<CollectionOption> = emptyList()
    private var collectionHealthById: Map<Long, SchemaCollectionHealth> = emptyMap()
    private var suppressSelectionRefresh = false
    private var selectedCollectionOption: CollectionOption? = null
    private var currentTabIndex: Int = 0
    private var editingFieldCard: FieldCard? = null
    private lateinit var collectionCardAdapter: CollectionCardAdapter
    private lateinit var fieldCardAdapter: FieldCardAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_schema, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        databaseHelper = AppDatabaseHelper(requireContext())
        bindViews(view)
        configureFieldTypes()
        bindListeners()
        configureKeyboardHandling()
        loadInitialSnapshot()
    }

    override fun onDestroy() {
        databaseHelper.close()
        super.onDestroy()
    }

    private fun bindViews(view: View) {
        collectionsCountText = view.findViewById(R.id.collectionsCountText)
        selectedTableHintText = view.findViewById(R.id.selectedTableHintText)
        statusText = view.findViewById(R.id.statusText)
        schemaLoadingOverlay = view.findViewById(R.id.schemaLoadingOverlay)
        schemaLoadingText = view.findViewById(R.id.schemaLoadingText)
        schemaScrollView = view.findViewById(R.id.schemaScrollView)
        schemaContentContainer = view.findViewById(R.id.schemaContentContainer)
        schemaTabLayout = view.findViewById(R.id.schemaTabLayout)
        schemaTablesTabContent = view.findViewById(R.id.schemaTablesTabContent)
        schemaFieldsTabContent = view.findViewById(R.id.schemaFieldsTabContent)
        collectionsRecyclerView = view.findViewById(R.id.collectionsRecyclerView)
        fieldsRecyclerView = view.findViewById(R.id.fieldsRecyclerView)
        collectionsEmptyText = view.findViewById(R.id.collectionsEmptyText)
        fieldsEmptyText = view.findViewById(R.id.fieldsEmptyText)
        collectionNameInput = view.findViewById(R.id.collectionNameInput)
        collectionDescriptionInput = view.findViewById(R.id.collectionDescriptionInput)
        collectionMasterSwitch = view.findViewById(R.id.collectionMasterSwitch)
        collectionOptionsSwitch = view.findViewById(R.id.collectionOptionsSwitch)
        fieldNameInput = view.findViewById(R.id.fieldNameInput)
        collectionSelector = view.findViewById(R.id.collectionSelector)
        fieldTypeSelector = view.findViewById(R.id.fieldTypeSelector)
        fieldOptionSourceLayout = view.findViewById(R.id.fieldOptionSourceLayout)
        fieldOptionSourceSelector = view.findViewById(R.id.fieldOptionSourceSelector)
        fieldOptionDisplayRoleLayout = view.findViewById(R.id.fieldOptionDisplayRoleLayout)
        fieldOptionDisplayRoleSelector = view.findViewById(R.id.fieldOptionDisplayRoleSelector)
        fieldSearchModeSelector = view.findViewById(R.id.fieldSearchModeSelector)
        fieldValueRuleSelector = view.findViewById(R.id.fieldValueRuleSelector)
        createCollectionButton = view.findViewById(R.id.createCollectionButton)
        createFieldButton = view.findViewById(R.id.createFieldButton)
        cancelFieldEditButton = view.findViewById(R.id.cancelFieldEditButton)
        reloadButton = view.findViewById(R.id.reloadButton)
        deleteAllButton = view.findViewById(R.id.deleteAllButton)

        collectionCardAdapter = CollectionCardAdapter(
            onDelete = ::confirmDeleteCollection,
            onSetMaster = ::setCollectionAsMaster,
            descriptionProvider = ::buildCollectionDescription,
            issueProvider = { card -> collectionHealthById[card.id]?.primaryIssue?.let(::resolveIssueMessage) },
            masterActionProvider = ::buildCollectionActionUi
        )
        fieldCardAdapter = FieldCardAdapter(
            onEdit = ::startEditingField,
            onDelete = ::confirmDeleteField,
            fieldTypeLabelProvider = ::resolveFieldTypeLabel,
            optionRoleLabelProvider = ::resolveOptionRoleLabel
        )
        collectionsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        collectionsRecyclerView.adapter = collectionCardAdapter
        fieldsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        fieldsRecyclerView.adapter = fieldCardAdapter
    }

    private fun configureFieldTypes() {
        configureTabs()

        fieldTypeOptions = SchemaFieldType.defaults.map { type ->
            SchemaFieldTypeOption(type = type, label = getString(type.labelResId))
        }
        fieldTypeSelector.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                fieldTypeOptions
            )
        )

        searchModeOptions = SchemaSearchMode.defaults.map { mode ->
            SchemaSearchModeOption(mode = mode, label = getString(mode.labelResId))
        }
        fieldSearchModeSelector.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                searchModeOptions
            )
        )

        valueRuleOptions = SchemaValueRule.defaults.map { rule ->
            SchemaValueRuleOption(rule = rule, label = getString(rule.labelResId))
        }
        fieldValueRuleSelector.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                valueRuleOptions
            )
        )

        optionDisplayRoleOptions = SchemaOptionDisplayRole.defaults.map { role ->
            SchemaOptionDisplayRoleOption(role = role, label = getString(role.labelResId))
        }
        fieldOptionDisplayRoleSelector.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                optionDisplayRoleOptions
            )
        )

        if (fieldTypeOptions.isNotEmpty()) {
            fieldTypeSelector.setText(fieldTypeOptions.first().label, false)
        }
        if (searchModeOptions.isNotEmpty()) {
            fieldSearchModeSelector.setText(searchModeOptions.first().label, false)
        }
        if (valueRuleOptions.isNotEmpty()) {
            fieldValueRuleSelector.setText(valueRuleOptions.first().label, false)
        }
        if (optionDisplayRoleOptions.isNotEmpty()) {
            fieldOptionDisplayRoleSelector.setText(optionDisplayRoleOptions.first().label, false)
        }
    }

    private fun bindListeners() {
        createCollectionButton.setOnClickListener { createCollection() }
        createFieldButton.setOnClickListener { createField() }
        cancelFieldEditButton.setOnClickListener { clearFieldEditor(clearInputs = true) }
        reloadButton.setOnClickListener {
            setBusyState(true, getString(R.string.status_loading))
            viewLifecycleOwner.lifecycleScope.launch {
                refreshSnapshot(getString(R.string.status_ready))
            }
        }
        deleteAllButton.setOnClickListener { confirmDeleteAll() }
        collectionMasterSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) collectionOptionsSwitch.isChecked = false
        }
        collectionOptionsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) collectionMasterSwitch.isChecked = false
        }
        fieldTypeSelector.setOnItemClickListener { _, _, _, _ ->
            syncFieldOptionSourceVisibility()
        }

        collectionSelector.setOnItemClickListener { _, _, position, _ ->
            if (suppressSelectionRefresh) return@setOnItemClickListener
            selectedCollectionOption = collectionOptions.getOrNull(position)
            renderSelectedCollection(selectedCollectionOption?.id)
        }
    }

    private fun configureTabs() {
        if (schemaTabLayout.tabCount == 0) {
            schemaTabLayout.addTab(schemaTabLayout.newTab().setText(R.string.schema_tab_tables))
            schemaTabLayout.addTab(schemaTabLayout.newTab().setText(R.string.schema_tab_fields))
        }
        schemaTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showSchemaTab(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        showSchemaTab(0)
    }

    private fun showSchemaTab(position: Int) {
        currentTabIndex = position
        val showTables = position == 0
        schemaTablesTabContent.visibility = if (showTables) View.VISIBLE else View.GONE
        schemaFieldsTabContent.visibility = if (showTables) View.GONE else View.VISIBLE
        if (!showTables) {
            loadFieldCardsForCurrentSelection()
        }
        schemaScrollView.post { schemaScrollView.smoothScrollTo(0, 0) }
    }

    private fun loadInitialSnapshot() {
        setBusyState(true, getString(R.string.status_booting))
        viewLifecycleOwner.lifecycleScope.launch {
            refreshSnapshot(getString(R.string.status_ready))
        }
    }

    private fun createCollection() {
        val displayName = collectionNameInput.text.toString().trim()
        val description = collectionDescriptionInput.text.toString().trim()
        val isMaster = collectionMasterSwitch.isChecked
        val isOptions = collectionOptionsSwitch.isChecked
        if (displayName.isBlank()) {
            statusText.text = getString(R.string.error_collection_name_required)
            return
        }

        setBusyState(true, getString(R.string.status_creating_collection))
        viewLifecycleOwner.lifecycleScope.launch {
            val collection = withContext(Dispatchers.IO) {
                databaseHelper.createCollection(displayName, description, isMaster, isOptions)
            }
            refreshSnapshot(
                getString(R.string.status_collection_created, collection.displayName),
                preferredCollectionId = collection.id,
                clearCollectionForm = true
            )
        }
    }

    private fun createField() {
        val selectedCollection = selectedCollectionOption
        val fieldName = fieldNameInput.text.toString().trim()
        val fieldType = selectedFieldType()
        val searchMode = selectedSearchMode()
        val valueRule = selectedValueRule()
        val optionSourceCollection = selectedOptionSourceCollection()
        val optionDisplayRole = selectedOptionDisplayRole()

        if (selectedCollection == null || selectedCollection.id < 0) {
            statusText.text = getString(R.string.error_collection_select)
            return
        }
        if (fieldName.isBlank()) {
            statusText.text = getString(R.string.error_field_name_required)
            return
        }
        if (fieldType == null) {
            statusText.text = getString(R.string.status_ready)
            return
        }
        if (searchMode == null || valueRule == null) {
            statusText.text = getString(R.string.status_ready)
            return
        }
        if (fieldType == SchemaFieldType.LIST && optionSourceCollection == null) {
            statusText.text = getString(R.string.error_option_source_required)
            return
        }
        val editingCard = editingFieldCard
        val isUpdating = editingCard != null
        setBusyState(
            true,
            if (isUpdating) getString(R.string.schema_updating_field) else getString(R.string.status_creating_field)
        )
        viewLifecycleOwner.lifecycleScope.launch {
            val field = withContext(Dispatchers.IO) {
                if (editingCard == null) {
                    databaseHelper.createField(
                        selectedCollection.id,
                        fieldName,
                        fieldType.storageValue,
                        searchMode.queryRoleValue,
                        valueRule.isUniqueValue,
                        valueRule.isRequiredValue,
                        optionSourceCollection?.id,
                        optionDisplayRole.storageValue
                    )
                } else {
                    databaseHelper.updateField(
                        fieldId = editingCard.id,
                        displayName = fieldName,
                        fieldType = fieldType.storageValue,
                        queryRole = searchMode.queryRoleValue,
                        isUniqueValue = valueRule.isUniqueValue,
                        isRequiredValue = valueRule.isRequiredValue,
                        optionSourceCollectionId = optionSourceCollection?.id,
                        optionDisplayRole = optionDisplayRole.storageValue
                    )
                }
            }
            clearFieldEditor(clearInputs = false)
            refreshSnapshot(
                if (isUpdating) {
                    getString(R.string.schema_field_updated, field.displayName)
                } else {
                    getString(R.string.status_field_created, field.displayName)
                },
                preferredCollectionId = selectedCollection.id,
                clearFieldForm = true
            )
        }
    }

    private suspend fun refreshSnapshot(
        status: String,
        preferredCollectionId: Long? = null,
        clearCollectionForm: Boolean = false,
        clearFieldForm: Boolean = false
    ) {
        val selectedCollectionId = preferredCollectionId ?: currentSelectedCollectionId(collectionOptions)
            val snapshot = withContext(Dispatchers.IO) {
                val options = databaseHelper.listCollectionOptions()
                val optionSources = databaseHelper.listOptionCollectionOptions()
                val selectedCollection = options.firstOrNull { it.id == selectedCollectionId } ?: options.firstOrNull()
                val collectionCards = databaseHelper.listCollectionCards()
                SchemaSnapshot(
                    options = options,
                    optionSources = optionSources,
                    selectedCollection = selectedCollection,
                    collectionCards = collectionCards,
                    fieldCards = emptyList(),
                    collectionHealthById = SchemaValidator.validateCollections(collectionCards)
                )
            }

        collectionOptions = snapshot.options
        optionSourceOptions = snapshot.optionSources
        collectionHealthById = snapshot.collectionHealthById
        collectionsCountText.text = getString(R.string.collections_count, snapshot.options.size)
        renderCollectionCards(
            cards = snapshot.collectionCards,
            collectionHealthById = snapshot.collectionHealthById
        )
        bindCollectionSelector(snapshot.options, snapshot.selectedCollection?.id)
        bindOptionSourceSelector(optionSourceOptions)
        if (currentTabIndex == 1) {
            loadFieldCardsForCurrentSelection()
        } else {
            renderFieldCards(emptyList())
        }
        selectedTableHintText.text = buildSelectedCollectionHint(
            collectionName = snapshot.selectedCollection?.displayName,
            health = snapshot.selectedCollection?.id?.let(snapshot.collectionHealthById::get)
        )
        syncFieldOptionSourceVisibility()

        if (clearCollectionForm) {
            collectionNameInput.text.clear()
            collectionDescriptionInput.text.clear()
            collectionMasterSwitch.isChecked = false
            collectionOptionsSwitch.isChecked = false
        }
        if (clearFieldForm) {
            clearFieldEditor(clearInputs = true)
        }

        setBusyState(false, status)
    }

    private fun renderSelectedCollection(collectionId: Long?) {
        if (editingFieldCard?.collectionId != null && editingFieldCard?.collectionId != collectionId) {
            clearFieldEditor(clearInputs = true)
        }
        val tableName = collectionOptions.firstOrNull { it.id == collectionId }?.displayName.orEmpty()
        selectedTableHintText.text = buildSelectedCollectionHint(
            collectionName = tableName.takeIf { it.isNotBlank() },
            health = collectionId?.let(collectionHealthById::get)
        )
        syncFieldOptionSourceVisibility()
        if (currentTabIndex == 1) {
            loadFieldCardsForCurrentSelection()
        } else {
            statusText.text = getString(R.string.status_ready)
        }
    }

    private fun loadFieldCardsForCurrentSelection() {
        val collectionId = selectedCollectionOption?.id
        viewLifecycleOwner.lifecycleScope.launch {
            val fieldCards = withContext(Dispatchers.IO) {
                collectionId?.let { databaseHelper.listFieldCards(it) }.orEmpty()
            }
            renderFieldCards(fieldCards)
            statusText.text = getString(R.string.status_ready)
        }
    }

    private fun renderCollectionCards(
        cards: List<CollectionCard>,
        collectionHealthById: Map<Long, SchemaCollectionHealth>
    ) {
        this.collectionHealthById = collectionHealthById
        val isEmpty = cards.isEmpty()
        collectionsEmptyText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        collectionsRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        collectionCardAdapter.submitList(cards)
    }

    private fun renderFieldCards(cards: List<FieldCard>) {
        val isEmpty = cards.isEmpty()
        fieldsEmptyText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        fieldsRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        fieldCardAdapter.submitList(cards)
    }

    private fun buildCollectionDescription(card: CollectionCard): String {
        return card.description.ifBlank {
            if (card.isOptions) {
                getString(R.string.schema_options_table_description)
            } else {
                getString(R.string.schema_no_description)
            }
        }
    }

    private fun buildCollectionActionUi(card: CollectionCard): CollectionActionUi {
        val health = collectionHealthById[card.id]
        return if (card.isMaster || card.isOptions) {
            CollectionActionUi(
                label = when {
                    health?.hasBlockingIssues == true -> getString(R.string.schema_master_needs_attention_label)
                    health?.hasWarnings == true -> getString(R.string.schema_master_needs_attention_label)
                    card.isOptions -> getString(R.string.schema_options_badge)
                    else -> getString(R.string.schema_master_ready_label)
                },
                enabled = false
            )
        } else {
            CollectionActionUi(
                label = getString(R.string.schema_master_action_label),
                enabled = true
            )
        }
    }

    private fun buildSelectedCollectionHint(
        collectionName: String?,
        health: SchemaCollectionHealth?
    ): String {
        if (collectionName.isNullOrBlank()) {
            return getString(R.string.schema_selected_table_empty_short)
        }

        val issue = health?.primaryIssue
        return if (issue == null) {
            getString(R.string.schema_selected_table_ready, collectionName)
        } else {
            getString(
                R.string.schema_selected_table_warning,
                collectionName,
                resolveIssueMessage(issue)
            )
        }
    }

    private fun resolveIssueMessage(issue: SchemaCollectionIssue): String {
        return getString(issue.type.messageResId)
    }

    private fun setCollectionAsMaster(card: CollectionCard) {
        setBusyState(true, getString(R.string.schema_setting_master))
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                databaseHelper.setCollectionAsMaster(card.id)
            }
            refreshSnapshot(
                getString(R.string.schema_master_updated, card.displayName),
                preferredCollectionId = card.id
            )
        }
    }

    private fun configureKeyboardHandling() {
        ViewCompat.setOnApplyWindowInsetsListener(schemaScrollView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = maxOf(systemBars.bottom, ime.bottom)
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                bottomInset + dpToPx(16)
            )
            insets
        }

        registerFocusScroll(collectionNameInput)
        registerFocusScroll(collectionDescriptionInput)
        registerFocusScroll(fieldNameInput)
    }

    private fun registerFocusScroll(target: View) {
        target.setOnFocusChangeListener { focusedView, hasFocus ->
            if (!hasFocus) return@setOnFocusChangeListener
            schemaScrollView.post {
                val focusBottom = focusedView.bottomWithin(schemaContentContainer)
                val visibleBottom = schemaScrollView.scrollY + schemaScrollView.height - schemaScrollView.paddingBottom
                if (focusBottom > visibleBottom) {
                    schemaScrollView.smoothScrollTo(
                        0,
                        focusBottom - schemaScrollView.height + schemaScrollView.paddingBottom + dpToPx(24)
                    )
                }
            }
        }
    }

    private fun View.bottomWithin(parent: ViewGroup): Int {
        var totalBottom = bottom
        var current: View? = this
        while (current != null && current.parent is View && current.parent != parent) {
            current = current.parent as? View
            totalBottom += current?.top ?: 0
        }
        return totalBottom
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun confirmDeleteCollection(card: CollectionCard) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_delete_table_title)
            .setMessage(getString(R.string.dialog_delete_table_message, card.displayName))
            .setNegativeButton(R.string.dialog_delete_cancel, null)
            .setPositiveButton(R.string.dialog_delete_confirm) { _, _ ->
                setBusyState(true, getString(R.string.schema_deleting_table))
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        databaseHelper.deleteCollection(card.id)
                    }
                    refreshSnapshot(getString(R.string.schema_table_deleted))
                }
            }
            .show()
    }

    private fun confirmDeleteField(card: FieldCard) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_delete_column_title)
            .setMessage(getString(R.string.dialog_delete_column_message, card.displayName))
            .setNegativeButton(R.string.dialog_delete_cancel, null)
            .setPositiveButton(R.string.dialog_delete_confirm) { _, _ ->
                setBusyState(true, getString(R.string.schema_deleting_field))
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        databaseHelper.deleteField(card.id)
                    }
                    if (editingFieldCard?.id == card.id) {
                        clearFieldEditor(clearInputs = true)
                    }
                    refreshSnapshot(getString(R.string.schema_field_deleted), preferredCollectionId = card.collectionId)
                }
            }
            .show()
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_delete_title)
            .setMessage(R.string.dialog_delete_message)
            .setNegativeButton(R.string.dialog_delete_cancel, null)
            .setPositiveButton(R.string.dialog_delete_confirm) { _, _ ->
                setBusyState(true, getString(R.string.status_deleting_all))
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        databaseHelper.clearAllData()
                    }
                    refreshSnapshot(
                        getString(R.string.status_all_deleted),
                        clearCollectionForm = true,
                        clearFieldForm = true
                    )
                }
            }
            .show()
    }

    private fun bindCollectionSelector(options: List<CollectionOption>, selectedCollectionId: Long?) {
        suppressSelectionRefresh = true
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            options.ifEmpty { listOf(CollectionOption.EMPTY) }
        )
        collectionSelector.setAdapter(adapter)

        val selectionIndex = if (options.isEmpty()) 0
        else options.indexOfFirst { it.id == selectedCollectionId }.takeIf { it >= 0 } ?: 0

        val selected = options.getOrNull(selectionIndex)
        selectedCollectionOption = selected
        collectionSelector.setText((selected ?: CollectionOption.EMPTY).toString(), false)
        suppressSelectionRefresh = false
    }

    private fun bindOptionSourceSelector(options: List<CollectionOption>) {
        fieldOptionSourceSelector.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                options
            )
        )
    }

    private fun currentSelectedCollectionId(options: List<CollectionOption>): Long? {
        val selected = selectedCollectionOption ?: return null
        return options.firstOrNull { it.id == selected.id }?.id
    }

    private fun selectedFieldType(): SchemaFieldType? {
        val selectedLabel = fieldTypeSelector.text?.toString().orEmpty()
        return fieldTypeOptions.firstOrNull { it.label == selectedLabel }?.type
    }

    private fun selectedSearchMode(): SchemaSearchMode? {
        val selectedLabel = fieldSearchModeSelector.text?.toString().orEmpty()
        return searchModeOptions.firstOrNull { it.label == selectedLabel }?.mode
    }

    private fun selectedValueRule(): SchemaValueRule? {
        val selectedLabel = fieldValueRuleSelector.text?.toString().orEmpty()
        return valueRuleOptions.firstOrNull { it.label == selectedLabel }?.rule
    }

    private fun selectedOptionSourceCollection(): CollectionOption? {
        val selectedLabel = fieldOptionSourceSelector.text?.toString().orEmpty()
        return optionSourceOptions.firstOrNull { it.toString() == selectedLabel }
    }

    private fun selectedOptionDisplayRole(): SchemaOptionDisplayRole {
        val selectedLabel = fieldOptionDisplayRoleSelector.text?.toString().orEmpty()
        return optionDisplayRoleOptions.firstOrNull { it.label == selectedLabel }?.role
            ?: SchemaOptionDisplayRole.NONE
    }

    private fun resolveFieldTypeLabel(storageValue: String): String {
        val type = SchemaFieldType.fromStorageValue(storageValue)
        return if (type != null) getString(type.labelResId) else storageValue
    }

    private fun resolveOptionRoleLabel(storageValue: String): String {
        return when (storageValue) {
            SchemaOptionDisplayRole.PRIMARY.storageValue -> getString(R.string.schema_option_display_role_primary_badge)
            SchemaOptionDisplayRole.SUPPORT.storageValue -> getString(R.string.schema_option_display_role_support_badge)
            else -> storageValue
        }
    }

    private fun setBusyState(busy: Boolean, status: String) {
        createCollectionButton.isEnabled = !busy
        createFieldButton.isEnabled = !busy && collectionOptions.isNotEmpty()
        cancelFieldEditButton.isEnabled = !busy
        reloadButton.isEnabled = !busy
        deleteAllButton.isEnabled = !busy
        collectionSelector.isEnabled = !busy && collectionOptions.isNotEmpty()
        fieldTypeSelector.isEnabled = !busy && collectionOptions.isNotEmpty()
        fieldSearchModeSelector.isEnabled = !busy && collectionOptions.isNotEmpty()
        fieldValueRuleSelector.isEnabled = !busy && collectionOptions.isNotEmpty()
        fieldOptionDisplayRoleSelector.isEnabled = !busy && fieldOptionDisplayRoleLayout.visibility == View.VISIBLE
        collectionNameInput.isEnabled = !busy
        collectionDescriptionInput.isEnabled = !busy
        collectionMasterSwitch.isEnabled = !busy
        collectionOptionsSwitch.isEnabled = !busy
        fieldNameInput.isEnabled = !busy && collectionOptions.isNotEmpty()
        fieldOptionSourceSelector.isEnabled = !busy && fieldOptionSourceLayout.visibility == View.VISIBLE
        statusText.text = status
        schemaLoadingText.text = status
        animateLoadingState(busy)
    }

    private fun animateLoadingState(show: Boolean) {
        if (show) {
            if (schemaLoadingOverlay.visibility != View.VISIBLE) {
                schemaLoadingOverlay.alpha = 0f
                schemaLoadingOverlay.visibility = View.VISIBLE
            }
            schemaLoadingOverlay.animate().cancel()
            schemaContentContainer.animate().cancel()
            schemaLoadingOverlay.animate()
                .alpha(1f)
                .setDuration(180L)
                .start()
            schemaContentContainer.animate()
                .alpha(0.55f)
                .setDuration(180L)
                .start()
        } else {
            if (schemaLoadingOverlay.visibility != View.VISIBLE) {
                schemaContentContainer.alpha = 1f
                return
            }
            schemaLoadingOverlay.animate().cancel()
            schemaContentContainer.animate().cancel()
            schemaLoadingOverlay.animate()
                .alpha(0f)
                .setDuration(160L)
                .withEndAction {
                    schemaLoadingOverlay.visibility = View.GONE
                }
                .start()
            schemaContentContainer.animate()
                .alpha(1f)
                .setDuration(160L)
                .start()
        }
    }

    private fun syncFieldOptionSourceVisibility() {
        val isListType = selectedFieldType() == SchemaFieldType.LIST
        val isOptionsTable = selectedCollectionOption?.isOptions == true
        fieldOptionSourceLayout.visibility = if (isListType) View.VISIBLE else View.GONE
        fieldOptionDisplayRoleLayout.visibility = if (isOptionsTable) View.VISIBLE else View.GONE
        if (isListType && !isOptionsTable) {
            fieldSearchModeSelector.setText(
                searchModeOptions.firstOrNull { it.mode == SchemaSearchMode.NONE }?.label.orEmpty(),
                false
            )
        }
        if (!isListType) {
            fieldOptionSourceSelector.setText("", false)
        }
        if (!isOptionsTable) {
            fieldOptionDisplayRoleSelector.setText(
                optionDisplayRoleOptions.firstOrNull()?.label.orEmpty(),
                false
            )
        }
    }

    private fun startEditingField(card: FieldCard) {
        if (selectedCollectionOption?.id != card.collectionId) {
            val targetCollection = collectionOptions.firstOrNull { it.id == card.collectionId }
            if (targetCollection != null) {
                selectedCollectionOption = targetCollection
                bindCollectionSelector(collectionOptions, targetCollection.id)
                renderSelectedCollection(targetCollection.id)
            }
        }

        editingFieldCard = card
        fieldNameInput.setText(card.displayName)
        fieldTypeSelector.setText(
            fieldTypeOptions.firstOrNull { it.type.storageValue == card.fieldType }?.label.orEmpty(),
            false
        )
        fieldSearchModeSelector.setText(
            searchModeOptions.firstOrNull { it.mode.queryRoleValue == card.queryRole }?.label.orEmpty(),
            false
        )
        fieldValueRuleSelector.setText(resolveValueRuleLabel(card), false)
        syncFieldOptionSourceVisibility()
        fieldOptionSourceSelector.setText(
            optionSourceOptions.firstOrNull { it.id == card.optionSourceCollectionId }?.toString().orEmpty(),
            false
        )
        fieldOptionDisplayRoleSelector.setText(
            optionDisplayRoleOptions.firstOrNull { it.role.storageValue == card.optionDisplayRole }?.label.orEmpty(),
            false
        )
        createFieldButton.text = getString(R.string.button_update_field)
        cancelFieldEditButton.visibility = View.VISIBLE
        statusText.text = getString(R.string.schema_editing_field_status, card.displayName)
        schemaScrollView.post { schemaScrollView.smoothScrollTo(0, schemaFieldsTabContent.top) }
    }

    private fun clearFieldEditor(clearInputs: Boolean) {
        editingFieldCard = null
        createFieldButton.text = getString(R.string.button_create_field)
        cancelFieldEditButton.visibility = View.GONE
        if (!clearInputs) return

        fieldNameInput.text.clear()
        fieldTypeSelector.setText(fieldTypeOptions.firstOrNull()?.label.orEmpty(), false)
        fieldSearchModeSelector.setText(searchModeOptions.firstOrNull()?.label.orEmpty(), false)
        fieldValueRuleSelector.setText(valueRuleOptions.firstOrNull()?.label.orEmpty(), false)
        fieldOptionSourceSelector.setText("", false)
        fieldOptionDisplayRoleSelector.setText(optionDisplayRoleOptions.firstOrNull()?.label.orEmpty(), false)
        syncFieldOptionSourceVisibility()
    }

    private fun resolveValueRuleLabel(card: FieldCard): String {
        val rule = when {
            card.isUniqueValue && card.isRequiredValue -> SchemaValueRule.REQUIRED_UNIQUE
            card.isUniqueValue -> SchemaValueRule.UNIQUE
            card.isRequiredValue -> SchemaValueRule.REQUIRED
            else -> SchemaValueRule.REPEATED_ALLOWED
        }
        return valueRuleOptions.firstOrNull { it.rule == rule }?.label.orEmpty()
    }
}
