package com.pwa.offline

import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class RecordCreateFormRenderer(
    private val context: Context,
    private val container: LinearLayout,
    private val emptyValueLabel: String,
    private val booleanLabel: String,
    private val onFieldFocused: (View) -> Unit,
    private val onListFieldEditRequested: (AssetFieldValue, String, (String) -> Unit) -> Unit
) {

    private val inflater = LayoutInflater.from(context)
    private val textInputs = linkedMapOf<Long, TextInputEditText>()
    private val listInputs = linkedMapOf<Long, AutoCompleteTextView>()
    private val switchInputs = linkedMapOf<Long, SwitchCompat>()
    private val booleanTouched = linkedMapOf<Long, Boolean>()
    private val focusTargets = linkedMapOf<Long, View>()
    private var fields: List<AssetFieldValue> = emptyList()

    fun render(fields: List<AssetFieldValue>) {
        this.fields = fields
        textInputs.clear()
        listInputs.clear()
        switchInputs.clear()
        booleanTouched.clear()
        focusTargets.clear()
        container.removeAllViews()

        fields.forEachIndexed { index, field ->
            container.addView(createFieldView(field, index, fields.size))
        }
    }

    fun clear() {
        render(emptyList())
    }

    fun buildUpdates(): Map<Long, String> {
        return buildMap {
            fields.forEach { field ->
                when (field.fieldType) {
                    "boolean" -> {
                        val view = switchInputs[field.fieldId] ?: return@forEach
                        val hasInitialValue = field.value.isNotBlank()
                        val touched = booleanTouched[field.fieldId] == true
                        put(
                            field.fieldId,
                            if (!hasInitialValue && !touched) "" else if (view.isChecked) "Si" else "No"
                        )
                    }

                    "list" -> {
                        val view = listInputs[field.fieldId] ?: return@forEach
                        put(
                            field.fieldId,
                            FieldValueTextNormalizer.normalizeForSave(
                                field.fieldType,
                                view.text?.toString().orEmpty()
                            )
                        )
                    }

                    else -> {
                        val view = textInputs[field.fieldId] ?: return@forEach
                        put(
                            field.fieldId,
                            FieldValueTextNormalizer.normalizeForSave(
                                field.fieldType,
                                view.text?.toString().orEmpty()
                            )
                        )
                    }
                }
            }
        }
    }

    private fun createFieldView(field: AssetFieldValue, index: Int, totalCount: Int): View {
        val itemView = inflater.inflate(R.layout.item_asset_field, container, false)
        val cardContainer = itemView.findViewById<LinearLayout>(R.id.assetFieldCardContainer)
        val labelText = itemView.findViewById<TextView>(R.id.assetFieldLabelText)
        val metaText = itemView.findViewById<TextView>(R.id.assetFieldMetaText)
        val readValueText = itemView.findViewById<TextView>(R.id.assetFieldReadValueText)
        val inputLayout = itemView.findViewById<TextInputLayout>(R.id.assetFieldInputLayout)
        val inputEditText = itemView.findViewById<TextInputEditText>(R.id.assetFieldInputEditText)
        val listInputLayout = itemView.findViewById<TextInputLayout>(R.id.assetFieldListInputLayout)
        val listInputView = itemView.findViewById<AutoCompleteTextView>(R.id.assetFieldListInputView)
        val booleanSwitch = itemView.findViewById<SwitchCompat>(R.id.assetFieldBooleanSwitch)

        labelText.text = field.fieldDisplayName
        metaText.isVisible = false
        metaText.text = ""
        readValueText.isVisible = false
        cardContainer.setBackgroundResource(R.drawable.app_surface_compact_field_background)
        val innerPadding = 10.dp()
        cardContainer.setPadding(innerPadding, innerPadding, innerPadding, innerPadding)
        itemView.setPadding(itemView.paddingLeft, itemView.paddingTop, itemView.paddingRight, 8.dp())

        when (field.fieldType) {
            "boolean" -> {
                inputLayout.isVisible = false
                listInputLayout.isVisible = false
                booleanSwitch.isVisible = true
                booleanSwitch.text = booleanLabel
                booleanSwitch.setOnCheckedChangeListener(null)
                booleanSwitch.isChecked = field.value.equals("Si", ignoreCase = true) ||
                    field.value.equals("1", ignoreCase = true) ||
                    field.value.equals("true", ignoreCase = true)
                booleanTouched[field.fieldId] = false
                booleanSwitch.setOnCheckedChangeListener { _, _ ->
                    booleanTouched[field.fieldId] = true
                }
                booleanSwitch.setOnFocusChangeListener { focusedView, hasFocus ->
                    if (hasFocus) onFieldFocused(focusedView)
                }
                switchInputs[field.fieldId] = booleanSwitch
                focusTargets[field.fieldId] = booleanSwitch
            }

            "list" -> {
                inputLayout.isVisible = false
                listInputLayout.isVisible = true
                booleanSwitch.isVisible = false
                listInputLayout.hint = context.getString(R.string.record_create_list_field_hint)
                listInputView.threshold = 0
                listInputView.filters = arrayOf(InputFilter.AllCaps())
                listInputView.setText(FieldValueTextNormalizer.normalizeForDisplay(field.fieldType, field.value), false)
                listInputView.isFocusable = false
                listInputView.isFocusableInTouchMode = false
                listInputView.isClickable = true
                listInputView.isLongClickable = false
                listInputView.keyListener = null
                val launchEditor = {
                    onListFieldEditRequested.invoke(
                        field,
                        listInputView.text?.toString().orEmpty()
                    ) { selectedValue ->
                        listInputView.setText(
                            FieldValueTextNormalizer.normalizeForDisplay(field.fieldType, selectedValue),
                            false
                        )
                        listInputView.setSelection(listInputView.text?.length ?: 0)
                        moveToNextField(field.fieldId)
                    }
                }
                cardContainer.setOnClickListener { launchEditor.invoke() }
                cardContainer.isClickable = true
                cardContainer.isFocusable = true
                listInputView.setOnClickListener { launchEditor.invoke() }
                listInputLayout.setEndIconOnClickListener { launchEditor.invoke() }
                listInputs[field.fieldId] = listInputView
                focusTargets[field.fieldId] = cardContainer
            }

            else -> {
                inputLayout.isVisible = true
                listInputLayout.isVisible = false
                booleanSwitch.isVisible = false
                if (FieldValueTextNormalizer.shouldForceUppercase(field.fieldType)) {
                    inputEditText.filters = arrayOf(InputFilter.AllCaps())
                }
                inputEditText.setText(FieldValueTextNormalizer.normalizeForDisplay(field.fieldType, field.value))
                inputEditText.inputType = when (field.fieldType) {
                    "number" -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    "date" -> InputType.TYPE_CLASS_DATETIME
                    "textarea" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    else -> InputType.TYPE_CLASS_TEXT
                }
                inputEditText.minLines = if (field.fieldType == "textarea") 3 else 1
                inputEditText.maxLines = if (field.fieldType == "textarea") 4 else 1
                inputEditText.imeOptions = if (index == totalCount - 1) {
                    EditorInfo.IME_ACTION_DONE
                } else {
                    EditorInfo.IME_ACTION_NEXT
                }
                inputEditText.setOnFocusChangeListener { focusedView, hasFocus ->
                    if (hasFocus) onFieldFocused(focusedView)
                }
                if (field.fieldType != "textarea") {
                    bindNextFieldNavigation(field.fieldId, inputEditText)
                } else {
                    inputEditText.setOnEditorActionListener(null)
                    inputEditText.setOnKeyListener(null)
                }
                textInputs[field.fieldId] = inputEditText
                focusTargets[field.fieldId] = inputEditText
            }
        }

        return itemView
    }

    private fun bindNextFieldNavigation(fieldId: Long, targetView: TextView) {
        targetView.setOnEditorActionListener { _, actionId, event ->
            val isImeNextAction = actionId == EditorInfo.IME_ACTION_NEXT ||
                actionId == EditorInfo.IME_ACTION_DONE
            val isHardwareEnter = actionId == EditorInfo.IME_NULL &&
                event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN

            if (isImeNextAction || isHardwareEnter) {
                moveToNextField(fieldId)
                true
            } else {
                false
            }
        }
        targetView.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                moveToNextField(fieldId)
                true
            } else {
                false
            }
        }
    }

    private fun moveToNextField(currentFieldId: Long) {
        val currentIndex = fields.indexOfFirst { it.fieldId == currentFieldId }
        if (currentIndex == -1) return
        val nextField = fields.getOrNull(currentIndex + 1)
        val nextView = nextField?.let { focusTargets[it.fieldId] }
        if (nextView != null) {
            nextView.post {
                nextView.requestFocus()
                onFieldFocused(nextView)
            }
            return
        }

        focusTargets[currentFieldId]?.clearFocus()
    }

    private fun Int.dp(): Int = (this * context.resources.displayMetrics.density).toInt()
}
