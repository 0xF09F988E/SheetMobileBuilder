package com.pwa.offline.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.pwa.offline.AssetFieldValue
import com.pwa.offline.FieldValueTextNormalizer
import com.pwa.offline.OptionSuggestion
import com.pwa.offline.R

object FieldEditDialogHelper {

    fun show(
        context: Context,
        inflater: LayoutInflater,
        field: AssetFieldValue,
        loadOptionSuggestions: (AssetFieldValue, String, (List<OptionSuggestion>) -> Unit) -> Unit,
        onSaveRequested: (String, Dialog) -> Unit
    ) {
        val shellView = inflater.inflate(R.layout.dialog_field_editor_shell, null)
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(shellView)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCanceledOnTouchOutside(true)

        shellView.findViewById<TextView>(R.id.dialogFieldEditorTitleText).text = field.fieldDisplayName
        val cancelButton = shellView.findViewById<TextView>(R.id.dialogFieldEditorCancelButton)
        val saveButton = shellView.findViewById<TextView>(R.id.dialogFieldEditorSaveButton)
        val contentContainer = shellView.findViewById<ViewGroup>(R.id.dialogFieldEditorContentContainer)

        val valueProvider = when (field.fieldType) {
            "boolean" -> bindBooleanEditor(inflater, contentContainer, field)
            "list" -> bindListEditor(inflater, contentContainer, field, loadOptionSuggestions)
            else -> bindTextEditor(inflater, contentContainer, field)
        }

        cancelButton.setOnClickListener { dialog.dismiss() }
        saveButton.setOnClickListener { onSaveRequested(valueProvider.invoke(), dialog) }
        dialog.show()
    }

    fun showListSelector(
        context: Context,
        inflater: LayoutInflater,
        field: AssetFieldValue,
        initialValue: String,
        loadOptionSuggestions: (AssetFieldValue, String, (List<OptionSuggestion>) -> Unit) -> Unit,
        onValueSelected: (String) -> Unit
    ) {
        val shellView = inflater.inflate(R.layout.dialog_field_editor_shell, null)
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(shellView)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCanceledOnTouchOutside(true)

        shellView.findViewById<TextView>(R.id.dialogFieldEditorTitleText).text = field.fieldDisplayName
        val cancelButton = shellView.findViewById<TextView>(R.id.dialogFieldEditorCancelButton)
        val saveButton = shellView.findViewById<TextView>(R.id.dialogFieldEditorSaveButton)
        val contentContainer = shellView.findViewById<ViewGroup>(R.id.dialogFieldEditorContentContainer)

        val valueProvider = bindListEditor(inflater, contentContainer, field, loadOptionSuggestions, initialValue)

        cancelButton.setOnClickListener { dialog.dismiss() }
        saveButton.setOnClickListener {
            onValueSelected(valueProvider.invoke())
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun bindTextEditor(
        inflater: LayoutInflater,
        contentContainer: ViewGroup,
        field: AssetFieldValue
    ): () -> String {
        val contentView = inflater.inflate(R.layout.dialog_field_editor_text_content, contentContainer, false)
        val inputView = contentView.findViewById<TextInputEditText>(R.id.dialogFieldEditorTextInput)
        if (FieldValueTextNormalizer.shouldForceUppercase(field.fieldType)) {
            inputView.filters = arrayOf(InputFilter.AllCaps())
        }
        inputView.setText(FieldValueTextNormalizer.normalizeForDisplay(field.fieldType, field.value))
        inputView.setSelection(inputView.text?.length ?: 0)
        inputView.inputType = when (field.fieldType) {
            "number" -> android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            "date" -> android.text.InputType.TYPE_CLASS_DATETIME
            "textarea" -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else -> android.text.InputType.TYPE_CLASS_TEXT
        }
        inputView.minLines = if (field.fieldType == "textarea") 4 else 1
        inputView.maxLines = if (field.fieldType == "textarea") 6 else 1
        contentContainer.addView(contentView)
        return {
            FieldValueTextNormalizer.normalizeForSave(
                field.fieldType,
                inputView.text?.toString().orEmpty()
            )
        }
    }

    private fun bindBooleanEditor(
        inflater: LayoutInflater,
        contentContainer: ViewGroup,
        field: AssetFieldValue
    ): () -> String {
        val contentView = inflater.inflate(R.layout.dialog_field_editor_boolean_content, contentContainer, false)
        val radioGroup = contentView.findViewById<RadioGroup>(R.id.dialogFieldEditorBooleanGroup)
        val emptyOption = contentView.findViewById<RadioButton>(R.id.dialogFieldEditorBooleanEmpty)
        val yesOption = contentView.findViewById<RadioButton>(R.id.dialogFieldEditorBooleanYes)
        val noOption = contentView.findViewById<RadioButton>(R.id.dialogFieldEditorBooleanNo)

        when {
            field.value.equals("Si", ignoreCase = true) -> yesOption.isChecked = true
            field.value.equals("No", ignoreCase = true) -> noOption.isChecked = true
            else -> emptyOption.isChecked = true
        }

        contentContainer.addView(contentView)
        return {
            when (radioGroup.checkedRadioButtonId) {
                R.id.dialogFieldEditorBooleanYes -> "Si"
                R.id.dialogFieldEditorBooleanNo -> "No"
                else -> ""
            }
        }
    }

    private fun bindListEditor(
        inflater: LayoutInflater,
        contentContainer: ViewGroup,
        field: AssetFieldValue,
        loadOptionSuggestions: (AssetFieldValue, String, (List<OptionSuggestion>) -> Unit) -> Unit,
        initialValue: String = field.value
    ): () -> String {
        val contentView = inflater.inflate(R.layout.dialog_field_editor_list_content, contentContainer, false)
        val inputView = contentView.findViewById<TextInputEditText>(R.id.dialogFieldEditorListInput)
        val recyclerView = contentView.findViewById<RecyclerView>(R.id.dialogFieldEditorListRecyclerView)
        val clearButton = contentView.findViewById<ImageButton>(R.id.dialogFieldEditorListClearButton)
        val expandButton = contentView.findViewById<ImageButton>(R.id.dialogFieldEditorListExpandButton)
        lateinit var adapter: OptionSuggestionAdapter
        var watcherMuted = false

        adapter = OptionSuggestionAdapter { suggestion ->
            watcherMuted = true
            inputView.setText(
                FieldValueTextNormalizer.normalizeForDisplay(field.fieldType, suggestion.selectedValue)
            )
            inputView.setSelection(inputView.text?.length ?: 0)
            adapter.submitSuggestions(emptyList())
            recyclerView.visibility = View.GONE
            watcherMuted = false
        }

        recyclerView.layoutManager = LinearLayoutManager(contentView.context)
        recyclerView.adapter = adapter
        recyclerView.visibility = View.GONE

        inputView.filters = arrayOf(InputFilter.AllCaps())
        inputView.setText(FieldValueTextNormalizer.normalizeForDisplay(field.fieldType, initialValue))
        inputView.setSelection(inputView.text?.length ?: 0)
        inputView.setOnClickListener {
            requestListSuggestions(field, inputView.text?.toString().orEmpty(), adapter, recyclerView, loadOptionSuggestions, requireMatch = false)
        }
        clearButton.setOnClickListener {
            watcherMuted = true
            inputView.setText("")
            adapter.submitSuggestions(emptyList())
            recyclerView.visibility = View.GONE
            watcherMuted = false
        }
        expandButton.setOnClickListener {
            inputView.requestFocus()
            requestListSuggestions(field, "", adapter, recyclerView, loadOptionSuggestions, requireMatch = false)
        }
        inputView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                requestListSuggestions(field, inputView.text?.toString().orEmpty(), adapter, recyclerView, loadOptionSuggestions, requireMatch = false)
            }
        }
        inputView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (watcherMuted) return
                val query = s?.toString().orEmpty()
                if (query.isNotBlank() && query.trim().length < 2) {
                    adapter.submitSuggestions(emptyList())
                    recyclerView.visibility = View.GONE
                    return
                }
                requestListSuggestions(
                    field = field,
                    query = query,
                    adapter = adapter,
                    recyclerView = recyclerView,
                    loadOptionSuggestions = loadOptionSuggestions,
                    requireMatch = true,
                    currentInputProvider = { inputView.text?.toString().orEmpty() }
                )
            }
        })

        contentContainer.addView(contentView)
        return {
            FieldValueTextNormalizer.normalizeForSave(
                field.fieldType,
                inputView.text?.toString().orEmpty()
            )
        }
    }

    private fun requestListSuggestions(
        field: AssetFieldValue,
        query: String,
        adapter: OptionSuggestionAdapter,
        recyclerView: RecyclerView,
        loadOptionSuggestions: (AssetFieldValue, String, (List<OptionSuggestion>) -> Unit) -> Unit,
        requireMatch: Boolean,
        currentInputProvider: (() -> String)? = null
    ) {
        loadOptionSuggestions(field, query) { suggestions ->
            if (requireMatch && currentInputProvider?.invoke() != query) return@loadOptionSuggestions
            adapter.submitSuggestions(suggestions)
            recyclerView.visibility = if (suggestions.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private class OptionSuggestionAdapter(
        private val onClick: (OptionSuggestion) -> Unit
    ) : RecyclerView.Adapter<OptionSuggestionAdapter.OptionSuggestionViewHolder>() {

        private var suggestions: List<OptionSuggestion> = emptyList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionSuggestionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_option_dropdown, parent, false)
            return OptionSuggestionViewHolder(view, onClick)
        }

        override fun onBindViewHolder(holder: OptionSuggestionViewHolder, position: Int) {
            holder.bind(suggestions[position])
        }

        override fun getItemCount(): Int = suggestions.size

        fun submitSuggestions(suggestions: List<OptionSuggestion>) {
            this.suggestions = suggestions
            notifyDataSetChanged()
        }

        class OptionSuggestionViewHolder(
            itemView: View,
            private val onClick: (OptionSuggestion) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            private val textView = itemView.findViewById<TextView>(R.id.optionDropdownText)

            fun bind(suggestion: OptionSuggestion) {
                textView.text = suggestion.displayLabel
                textView.isSingleLine = false
                textView.maxLines = 6
                textView.ellipsize = null
                textView.setHorizontallyScrolling(false)
                itemView.setOnClickListener { onClick(suggestion) }
            }
        }
    }
}
