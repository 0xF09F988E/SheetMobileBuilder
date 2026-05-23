package com.pwa.offline

import java.util.Locale

object FieldValueTextNormalizer {

    fun shouldForceUppercase(fieldType: String): Boolean {
        return fieldType != "number" && fieldType != "boolean"
    }

    fun normalizeForSave(fieldType: String, value: String): String {
        val trimmedValue = value.trim()
        if (!shouldForceUppercase(fieldType)) {
            return trimmedValue
        }
        return trimmedValue.uppercase(Locale.getDefault())
    }

    fun normalizeForDisplay(fieldType: String, value: String): String {
        if (!shouldForceUppercase(fieldType)) {
            return value
        }
        return value.uppercase(Locale.getDefault())
    }
}
