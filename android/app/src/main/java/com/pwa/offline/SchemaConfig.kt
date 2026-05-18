package com.pwa.offline

import androidx.annotation.StringRes

enum class SchemaFieldType(
    val storageValue: String,
    @StringRes val labelResId: Int
) {
    TEXT("text", R.string.schema_field_type_text),
    TEXTAREA("textarea", R.string.schema_field_type_textarea),
    NUMBER("number", R.string.schema_field_type_number),
    DATE("date", R.string.schema_field_type_date),
    BOOLEAN("boolean", R.string.schema_field_type_boolean),
    LIST("list", R.string.schema_field_type_list);

    companion object {
        val defaults: List<SchemaFieldType> = entries.toList()

        fun fromStorageValue(value: String): SchemaFieldType? {
            return entries.firstOrNull { it.storageValue == value }
        }
    }
}

data class SchemaFieldTypeOption(
    val type: SchemaFieldType,
    val label: String
) {
    override fun toString(): String = label
}

enum class SchemaSearchMode(
    val queryRoleValue: String,
    @StringRes val labelResId: Int
) {
    NONE(
        queryRoleValue = "default",
        labelResId = R.string.schema_search_mode_display_only
    ),
    PRIMARY(
        queryRoleValue = "exact",
        labelResId = R.string.schema_search_mode_exact
    ),
    FLEXIBLE(
        queryRoleValue = "flexible",
        labelResId = R.string.schema_search_mode_flexible
    );

    companion object {
        val defaults: List<SchemaSearchMode> = entries.toList()
    }
}

data class SchemaSearchModeOption(
    val mode: SchemaSearchMode,
    val label: String
) {
    override fun toString(): String = label
}

enum class SchemaValueRule(
    val isUniqueValue: Boolean,
    val isRequiredValue: Boolean,
    @StringRes val labelResId: Int
) {
    REPEATED_ALLOWED(
        isUniqueValue = false,
        isRequiredValue = false,
        labelResId = R.string.schema_value_rule_repeated
    ),
    UNIQUE(
        isUniqueValue = true,
        isRequiredValue = false,
        labelResId = R.string.schema_value_rule_unique
    ),
    REQUIRED(
        isUniqueValue = false,
        isRequiredValue = true,
        labelResId = R.string.schema_value_rule_required
    ),
    REQUIRED_UNIQUE(
        isUniqueValue = true,
        isRequiredValue = true,
        labelResId = R.string.schema_value_rule_required_unique
    );

    companion object {
        val defaults: List<SchemaValueRule> = entries.toList()
    }
}

data class SchemaValueRuleOption(
    val rule: SchemaValueRule,
    val label: String
) {
    override fun toString(): String = label
}

enum class SchemaOptionDisplayRole(
    val storageValue: String,
    @StringRes val labelResId: Int
) {
    NONE(
        storageValue = "none",
        labelResId = R.string.schema_option_display_role_none
    ),
    PRIMARY(
        storageValue = "primary",
        labelResId = R.string.schema_option_display_role_primary
    ),
    SUPPORT(
        storageValue = "support",
        labelResId = R.string.schema_option_display_role_support
    );

    companion object {
        val defaults: List<SchemaOptionDisplayRole> = entries.toList()

        fun fromStorageValue(value: String): SchemaOptionDisplayRole? {
            return entries.firstOrNull { it.storageValue == value }
        }
    }
}

data class SchemaOptionDisplayRoleOption(
    val role: SchemaOptionDisplayRole,
    val label: String
) {
    override fun toString(): String = label
}

data class SchemaSnapshot(
    val options: List<CollectionOption>,
    val optionSources: List<CollectionOption>,
    val selectedCollection: CollectionOption?,
    val collectionCards: List<CollectionCard>,
    val fieldCards: List<FieldCard>,
    val collectionHealthById: Map<Long, SchemaCollectionHealth>
)

enum class SchemaIssueSeverity {
    WARNING,
    BLOCKING
}

enum class SchemaCollectionIssueType(
    @StringRes val messageResId: Int
) {
    MASTER_WITHOUT_COLUMNS(R.string.schema_issue_master_without_columns),
    MASTER_WITHOUT_LOOKUP(R.string.schema_issue_master_without_lookup),
    MASTER_LOOKUP_NOT_UNIQUE(R.string.schema_issue_master_lookup_not_unique)
}

data class SchemaCollectionIssue(
    val type: SchemaCollectionIssueType,
    val severity: SchemaIssueSeverity
)

data class SchemaCollectionHealth(
    val collectionId: Long,
    val issues: List<SchemaCollectionIssue>
) {
    val hasBlockingIssues: Boolean
        get() = issues.any { it.severity == SchemaIssueSeverity.BLOCKING }

    val hasWarnings: Boolean
        get() = issues.any { it.severity == SchemaIssueSeverity.WARNING }

    val isReadyForAssetQuery: Boolean
        get() = issues.isEmpty()

    val primaryIssue: SchemaCollectionIssue?
        get() = issues.firstOrNull()
}

object SchemaValidator {

    fun validateCollections(cards: List<CollectionCard>): Map<Long, SchemaCollectionHealth> {
        return cards.associate { card ->
            card.id to validateCollection(card)
        }
    }

    fun validateCollection(card: CollectionCard): SchemaCollectionHealth {
        if (!card.isMaster) {
            return SchemaCollectionHealth(collectionId = card.id, issues = emptyList())
        }

        val issues = mutableListOf<SchemaCollectionIssue>()

        if (card.fieldCount == 0) {
            issues += SchemaCollectionIssue(
                type = SchemaCollectionIssueType.MASTER_WITHOUT_COLUMNS,
                severity = SchemaIssueSeverity.BLOCKING
            )
        }

        if (card.lookupFieldCount == 0) {
            issues += SchemaCollectionIssue(
                type = SchemaCollectionIssueType.MASTER_WITHOUT_LOOKUP,
                severity = SchemaIssueSeverity.BLOCKING
            )
        }

        if (card.lookupFieldCount > 0 && card.lookupUniqueFieldCount == 0) {
            issues += SchemaCollectionIssue(
                type = SchemaCollectionIssueType.MASTER_LOOKUP_NOT_UNIQUE,
                severity = SchemaIssueSeverity.BLOCKING
            )
        }

        return SchemaCollectionHealth(collectionId = card.id, issues = issues)
    }
}
