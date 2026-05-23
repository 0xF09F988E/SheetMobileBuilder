package com.pwa.offline

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement
import java.text.Normalizer
import java.util.Locale

class AppDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val appContext: Context = context.applicationContext

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_COLLECTIONS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                display_name TEXT NOT NULL,
                slug TEXT NOT NULL UNIQUE,
                description TEXT NOT NULL DEFAULT '',
                is_master INTEGER NOT NULL DEFAULT 0,
                is_options INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_FIELDS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                collection_id INTEGER NOT NULL,
                display_name TEXT NOT NULL,
                slug TEXT NOT NULL,
                field_type TEXT NOT NULL,
                query_role TEXT NOT NULL DEFAULT '$QUERY_ROLE_DEFAULT',
                is_unique_value INTEGER NOT NULL DEFAULT 0,
                is_required_value INTEGER NOT NULL DEFAULT 0,
                option_source_collection_id INTEGER,
                option_display_role TEXT NOT NULL DEFAULT '$OPTION_DISPLAY_ROLE_NONE',
                position_index INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(collection_id, slug),
                FOREIGN KEY(collection_id) REFERENCES $TABLE_COLLECTIONS(id) ON DELETE CASCADE,
                FOREIGN KEY(option_source_collection_id) REFERENCES $TABLE_COLLECTIONS(id) ON DELETE SET NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_RECORDS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                collection_id INTEGER NOT NULL,
                review_status TEXT NOT NULL DEFAULT '${ReviewStatusCodes.PENDING}',
                review_action TEXT NOT NULL DEFAULT '${ReviewActionCodes.IMPORTED}',
                reviewed_at TEXT,
                changed_fields_text TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(collection_id) REFERENCES $TABLE_COLLECTIONS(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_RECORD_VALUES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                record_id INTEGER NOT NULL,
                field_id INTEGER NOT NULL,
                value_text TEXT,
                value_number REAL,
                value_date TEXT,
                value_boolean INTEGER,
                value_reference_id INTEGER,
                UNIQUE(record_id, field_id),
                FOREIGN KEY(record_id) REFERENCES $TABLE_RECORDS(id) ON DELETE CASCADE,
                FOREIGN KEY(field_id) REFERENCES $TABLE_FIELDS(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_LOOKUP_INDEX (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                collection_id INTEGER NOT NULL,
                record_id INTEGER NOT NULL,
                field_id INTEGER NOT NULL,
                normalized_value TEXT NOT NULL,
                UNIQUE(record_id, field_id),
                FOREIGN KEY(collection_id) REFERENCES $TABLE_COLLECTIONS(id) ON DELETE CASCADE,
                FOREIGN KEY(record_id) REFERENCES $TABLE_RECORDS(id) ON DELETE CASCADE,
                FOREIGN KEY(field_id) REFERENCES $TABLE_FIELDS(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_UNIQUE_INDEX (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                collection_id INTEGER NOT NULL,
                record_id INTEGER NOT NULL,
                field_id INTEGER NOT NULL,
                normalized_value TEXT NOT NULL,
                UNIQUE(field_id, normalized_value),
                FOREIGN KEY(collection_id) REFERENCES $TABLE_COLLECTIONS(id) ON DELETE CASCADE,
                FOREIGN KEY(record_id) REFERENCES $TABLE_RECORDS(id) ON DELETE CASCADE,
                FOREIGN KEY(field_id) REFERENCES $TABLE_FIELDS(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_REVIEW_LOG (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                record_id INTEGER NOT NULL,
                collection_id INTEGER NOT NULL,
                action_type TEXT NOT NULL,
                changed_fields_text TEXT NOT NULL DEFAULT '',
                latitude REAL,
                longitude REAL,
                accuracy_meters REAL,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(record_id) REFERENCES $TABLE_RECORDS(id) ON DELETE CASCADE,
                FOREIGN KEY(collection_id) REFERENCES $TABLE_COLLECTIONS(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX idx_collections_options ON $TABLE_COLLECTIONS(is_options)")
        db.execSQL("CREATE INDEX idx_fields_collection ON $TABLE_FIELDS(collection_id, position_index)")
        db.execSQL("CREATE INDEX idx_fields_query_role ON $TABLE_FIELDS(collection_id, query_role)")
        db.execSQL("CREATE INDEX idx_fields_unique ON $TABLE_FIELDS(collection_id, is_unique_value)")
        db.execSQL("CREATE INDEX idx_fields_required ON $TABLE_FIELDS(collection_id, is_required_value)")
        db.execSQL("CREATE INDEX idx_fields_option_source ON $TABLE_FIELDS(option_source_collection_id)")
        db.execSQL("CREATE INDEX idx_fields_option_display_role ON $TABLE_FIELDS(collection_id, option_display_role)")
        db.execSQL("CREATE INDEX idx_records_collection ON $TABLE_RECORDS(collection_id)")
        db.execSQL("CREATE INDEX idx_record_values_record ON $TABLE_RECORD_VALUES(record_id)")
        db.execSQL("CREATE INDEX idx_record_values_field ON $TABLE_RECORD_VALUES(field_id)")
        db.execSQL("CREATE INDEX idx_record_values_reference ON $TABLE_RECORD_VALUES(value_reference_id)")
        db.execSQL("CREATE INDEX idx_lookup_search ON $TABLE_LOOKUP_INDEX(collection_id, field_id, normalized_value)")
        db.execSQL("CREATE INDEX idx_unique_search ON $TABLE_UNIQUE_INDEX(collection_id, field_id, normalized_value)")
        db.execSQL("CREATE INDEX idx_review_log_record ON $TABLE_REVIEW_LOG(record_id, created_at DESC)")
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_REVIEW_LOG")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_UNIQUE_INDEX")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LOOKUP_INDEX")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_RECORD_VALUES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_RECORDS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FIELDS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_COLLECTIONS")
        onCreate(db)
    }

    fun createCollection(
        displayName: String,
        description: String,
        isMaster: Boolean,
        isOptions: Boolean
    ): CollectionOption {
        val slug = makeUniqueSlug(TABLE_COLLECTIONS, normalizeIdentifier(displayName))
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (isMaster) {
                db.execSQL("UPDATE $TABLE_COLLECTIONS SET is_master = 0")
            }

            val values = ContentValues().apply {
                put("display_name", displayName.trim())
                put("slug", slug)
                put("description", description.trim())
                put("is_master", if (isOptions) 0 else if (isMaster) 1 else 0)
                put("is_options", if (isOptions) 1 else 0)
            }

            val id = db.insertOrThrow(TABLE_COLLECTIONS, null, values)
            db.setTransactionSuccessful()
            return CollectionOption(
                id = id,
                displayName = displayName.trim(),
                slug = slug,
                isMaster = !isOptions && isMaster,
                isOptions = isOptions
            )
        } finally {
            db.endTransaction()
        }
    }

    fun createField(
        collectionId: Long,
        displayName: String,
        fieldType: String,
        queryRole: String,
        isUniqueValue: Boolean,
        isRequiredValue: Boolean,
        optionSourceCollectionId: Long? = null,
        optionDisplayRole: String = OPTION_DISPLAY_ROLE_NONE
    ): FieldDefinition {
        val db = writableDatabase
        val baseSlug = normalizeIdentifier(displayName)
        val slug = makeUniqueFieldSlug(collectionId, baseSlug)

        db.beginTransaction()
        try {
            val targetCollection = getCollectionOption(collectionId)
                ?: throw IllegalStateException("La tabla seleccionada ya no existe.")
            if (fieldType == FIELD_TYPE_LIST && optionSourceCollectionId == null) {
                throw IllegalStateException("Debes elegir una tabla de opciones para esta columna.")
            }
            if (fieldType == FIELD_TYPE_LIST) {
                val sourceCollection = getCollectionOption(optionSourceCollectionId!!)
                    ?: throw IllegalStateException("La tabla de opciones seleccionada ya no existe.")
                if (!sourceCollection.isOptions) {
                    throw IllegalStateException("La columna Lista solo puede usar tablas de opciones.")
                }
                if (!hasOptionAutocompleteColumns(optionSourceCollectionId)) {
                    throw IllegalStateException("La tabla de opciones necesita al menos una columna visible o de apoyo para usarse como Lista.")
                }
            }
            if (!targetCollection.isOptions && optionDisplayRole != OPTION_DISPLAY_ROLE_NONE) {
                throw IllegalStateException("Solo las tablas de opciones pueden definir columna visible o de apoyo.")
            }
            if (targetCollection.isOptions &&
                optionDisplayRole != OPTION_DISPLAY_ROLE_NONE &&
                hasOptionDisplayRole(collectionId, optionDisplayRole)
            ) {
                val roleLabel = when (optionDisplayRole) {
                    OPTION_DISPLAY_ROLE_PRIMARY -> "visible principal"
                    OPTION_DISPLAY_ROLE_SUPPORT -> "de apoyo"
                    else -> optionDisplayRole
                }
                throw IllegalStateException("Ya existe una columna $roleLabel en esta tabla de opciones.")
            }

            val values = ContentValues().apply {
                put("collection_id", collectionId)
                put("display_name", displayName.trim())
                put("slug", slug)
                put("field_type", fieldType)
                put("query_role", queryRole)
                put("is_unique_value", if (isUniqueValue) 1 else 0)
                put("is_required_value", if (isRequiredValue) 1 else 0)
                put("option_display_role", optionDisplayRole)
                if (optionSourceCollectionId != null) {
                    put("option_source_collection_id", optionSourceCollectionId)
                } else {
                    putNull("option_source_collection_id")
                }
                put("position_index", nextFieldPosition(collectionId))
            }

            val id = db.insertOrThrow(TABLE_FIELDS, null, values)
            db.setTransactionSuccessful()

            return FieldDefinition(
                id = id,
                collectionId = collectionId,
                displayName = displayName.trim(),
                slug = slug,
                fieldType = fieldType,
                queryRole = queryRole,
                isUniqueValue = isUniqueValue,
                isRequiredValue = isRequiredValue,
                optionSourceCollectionId = optionSourceCollectionId,
                optionDisplayRole = optionDisplayRole
            )
        } finally {
            db.endTransaction()
        }
    }

    fun updateField(
        fieldId: Long,
        displayName: String,
        fieldType: String,
        queryRole: String,
        isUniqueValue: Boolean,
        isRequiredValue: Boolean,
        optionSourceCollectionId: Long? = null,
        optionDisplayRole: String = OPTION_DISPLAY_ROLE_NONE
    ): FieldDefinition {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val existingField = getFieldDefinitionById(fieldId, db)
                ?: throw IllegalStateException("La columna seleccionada ya no existe.")
            val targetCollection = getCollectionOption(existingField.collectionId)
                ?: throw IllegalStateException("La tabla seleccionada ya no existe.")

            if (!isSafeFieldTypeTransition(existingField.fieldType, fieldType)) {
                throw IllegalStateException(appContext.getString(R.string.schema_unsafe_type_change))
            }
            if (fieldType == FIELD_TYPE_LIST && optionSourceCollectionId == null) {
                throw IllegalStateException("Debes elegir una tabla de opciones para esta columna.")
            }
            if (fieldType == FIELD_TYPE_LIST) {
                val sourceCollection = getCollectionOption(optionSourceCollectionId!!)
                    ?: throw IllegalStateException("La tabla de opciones seleccionada ya no existe.")
                if (!sourceCollection.isOptions) {
                    throw IllegalStateException("La columna Lista solo puede usar tablas de opciones.")
                }
                if (!hasOptionAutocompleteColumns(optionSourceCollectionId)) {
                    throw IllegalStateException("La tabla de opciones necesita al menos una columna visible o de apoyo para usarse como Lista.")
                }
            }
            if (!targetCollection.isOptions && optionDisplayRole != OPTION_DISPLAY_ROLE_NONE) {
                throw IllegalStateException("Solo las tablas de opciones pueden definir columna visible o de apoyo.")
            }
            if (targetCollection.isOptions &&
                optionDisplayRole != OPTION_DISPLAY_ROLE_NONE &&
                hasOptionDisplayRole(existingField.collectionId, optionDisplayRole, fieldId)
            ) {
                val roleLabel = when (optionDisplayRole) {
                    OPTION_DISPLAY_ROLE_PRIMARY -> "visible principal"
                    OPTION_DISPLAY_ROLE_SUPPORT -> "de apoyo"
                    else -> optionDisplayRole
                }
                throw IllegalStateException("Ya existe una columna $roleLabel en esta tabla de opciones.")
            }

            if (isRequiredValue && hasEmptyRequiredValues(existingField.collectionId, fieldId, db)) {
                throw IllegalStateException(appContext.getString(R.string.schema_required_existing_data_error))
            }
            if (isUniqueValue && hasDuplicateUniqueValues(existingField.copy(fieldType = fieldType), db)) {
                throw IllegalStateException(appContext.getString(R.string.schema_unique_existing_data_error))
            }

            val normalizedName = displayName.trim()
            db.update(
                TABLE_FIELDS,
                ContentValues().apply {
                    put("display_name", normalizedName)
                    put("field_type", fieldType)
                    put("query_role", queryRole)
                    put("is_unique_value", if (isUniqueValue) 1 else 0)
                    put("is_required_value", if (isRequiredValue) 1 else 0)
                    put("option_display_role", optionDisplayRole)
                    if (fieldType == FIELD_TYPE_LIST) {
                        put("option_source_collection_id", optionSourceCollectionId)
                    } else {
                        putNull("option_source_collection_id")
                    }
                },
                "id = ?",
                arrayOf(fieldId.toString())
            )

            val updatedField = existingField.copy(
                displayName = normalizedName,
                fieldType = fieldType,
                queryRole = queryRole,
                isUniqueValue = isUniqueValue,
                isRequiredValue = isRequiredValue,
                optionSourceCollectionId = optionSourceCollectionId?.takeIf { fieldType == FIELD_TYPE_LIST },
                optionDisplayRole = optionDisplayRole
            )

            rebuildFieldIndexes(updatedField, db)
            db.setTransactionSuccessful()
            return updatedField
        } finally {
            db.endTransaction()
        }
    }

    fun listCollectionOptions(): List<CollectionOption> {
        val result = mutableListOf<CollectionOption>()
        readableDatabase.rawQuery(
            """
            SELECT id, display_name, slug, is_master, is_options
            FROM $TABLE_COLLECTIONS
            ORDER BY display_name COLLATE NOCASE ASC
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += CollectionOption(
                    id = cursor.getLong(0),
                    displayName = cursor.getString(1),
                    slug = cursor.getString(2),
                    isMaster = cursor.getInt(3) == 1,
                    isOptions = cursor.getInt(4) == 1
                )
            }
        }
        return result
    }

    fun listMasterCollectionOptions(): List<CollectionOption> {
        val result = mutableListOf<CollectionOption>()
        readableDatabase.rawQuery(
            """
            SELECT id, display_name, slug, is_master, is_options
            FROM $TABLE_COLLECTIONS
            WHERE is_master = 1
            ORDER BY display_name COLLATE NOCASE ASC
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += CollectionOption(
                    id = cursor.getLong(0),
                    displayName = cursor.getString(1),
                    slug = cursor.getString(2),
                    isMaster = cursor.getInt(3) == 1,
                    isOptions = cursor.getInt(4) == 1
                )
            }
        }
        return result
    }

    fun listOptionCollectionOptions(): List<CollectionOption> {
        val result = mutableListOf<CollectionOption>()
        readableDatabase.rawQuery(
            """
            SELECT id, display_name, slug, is_master, is_options
            FROM $TABLE_COLLECTIONS
            WHERE is_options = 1
            ORDER BY display_name COLLATE NOCASE ASC
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += CollectionOption(
                    id = cursor.getLong(0),
                    displayName = cursor.getString(1),
                    slug = cursor.getString(2),
                    isMaster = cursor.getInt(3) == 1,
                    isOptions = cursor.getInt(4) == 1
                )
            }
        }
        return result
    }

    fun listCollectionCards(): List<CollectionCard> {
        val result = mutableListOf<CollectionCard>()
        readableDatabase.rawQuery(
            """
            SELECT
                c.id,
                c.display_name,
                c.slug,
                c.description,
                c.is_master,
                c.is_options,
                COUNT(f.id) AS field_count,
                COALESCE(SUM(CASE WHEN f.query_role = '$QUERY_ROLE_EXACT' THEN 1 ELSE 0 END), 0) AS lookup_field_count,
                COALESCE(SUM(CASE WHEN f.is_unique_value = 1 THEN 1 ELSE 0 END), 0) AS unique_field_count,
                COALESCE(SUM(CASE WHEN f.query_role = '$QUERY_ROLE_EXACT' AND f.is_unique_value = 1 THEN 1 ELSE 0 END), 0) AS lookup_unique_field_count
            FROM $TABLE_COLLECTIONS c
            LEFT JOIN $TABLE_FIELDS f ON f.collection_id = c.id
            GROUP BY c.id, c.display_name, c.slug, c.description, c.is_master, c.is_options
            ORDER BY c.display_name COLLATE NOCASE ASC
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += CollectionCard(
                    id = cursor.getLong(0),
                    displayName = cursor.getString(1),
                    slug = cursor.getString(2),
                    description = cursor.getString(3),
                    isMaster = cursor.getInt(4) == 1,
                    isOptions = cursor.getInt(5) == 1,
                    fieldCount = cursor.getInt(6),
                    lookupFieldCount = cursor.getInt(7),
                    uniqueFieldCount = cursor.getInt(8),
                    lookupUniqueFieldCount = cursor.getInt(9)
                )
            }
        }
        return result
    }

    fun listCollectionsSummary(): String {
        val objects = mutableListOf<String>()
        readableDatabase.rawQuery(
            """
            SELECT c.display_name, c.slug, c.description, c.is_options, COUNT(f.id) AS field_count
            FROM $TABLE_COLLECTIONS c
            LEFT JOIN $TABLE_FIELDS f ON f.collection_id = c.id
            GROUP BY c.id, c.display_name, c.slug, c.description, c.is_options
            ORDER BY c.display_name COLLATE NOCASE ASC
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val displayName = cursor.getString(0)
                val slug = cursor.getString(1)
                val description = cursor.getString(2)
                val isOptions = cursor.getInt(3) == 1
                val fieldCount = cursor.getInt(4)
                objects += buildString {
                    appendLine("  {")
                    appendLine("    \"tabla\": \"${escapeJson(displayName)}\",")
                    appendLine("    \"slug\": \"${escapeJson(slug)}\",")
                    appendLine("    \"descripcion\": \"${escapeJson(description)}\",")
            appendLine("    \"es_tabla_opciones\": $isOptions,")
                    append("    \"columnas\": $fieldCount")
                    appendLine()
                    append("  }")
                }
            }
        }

        return if (objects.isEmpty()) "[]" else "[\n${objects.joinToString(",\n")}\n]"
    }

    fun listFieldsSummary(collectionId: Long): String {
        val objects = mutableListOf<String>()
        readableDatabase.rawQuery(
            """
            SELECT display_name, slug, field_type, query_role, is_unique_value, is_required_value, option_source_collection_id
            FROM $TABLE_FIELDS
            WHERE collection_id = ?
            ORDER BY position_index ASC, id ASC
            """.trimIndent(),
            arrayOf(collectionId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val displayName = cursor.getString(0)
                val slug = cursor.getString(1)
                val fieldType = cursor.getString(2)
                val queryRole = cursor.getString(3)
                val isUniqueValue = cursor.getInt(4) == 1
                val isRequiredValue = cursor.getInt(5) == 1
                val optionSourceCollectionId = cursor.getLong(6).takeIf { !cursor.isNull(6) }
                objects += buildString {
                    appendLine("  {")
                    appendLine("    \"columna\": \"${escapeJson(displayName)}\",")
                    appendLine("    \"slug\": \"${escapeJson(slug)}\",")
                    appendLine("    \"tipo\": \"${escapeJson(fieldType)}\",")
                    appendLine("    \"rol_consulta\": \"${escapeJson(queryRole)}\",")
                    appendLine("    \"valor_unico\": $isUniqueValue,")
                    appendLine("    \"valor_obligatorio\": $isRequiredValue,")
                    append("    \"tabla_opciones_id\": ${optionSourceCollectionId ?: "null"}")
                    appendLine()
                    append("  }")
                }
            }
        }

        return if (objects.isEmpty()) "[]" else "[\n${objects.joinToString(",\n")}\n]"
    }

    fun listFieldDefinitions(collectionId: Long): List<FieldDefinition> {
        val fields = mutableListOf<FieldDefinition>()
        readableDatabase.rawQuery(
            """
            SELECT id, display_name, slug, field_type, query_role, is_unique_value, is_required_value, option_source_collection_id, option_display_role
            FROM $TABLE_FIELDS
            WHERE collection_id = ?
            ORDER BY position_index ASC, id ASC
            """.trimIndent(),
            arrayOf(collectionId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                fields += FieldDefinition(
                    id = cursor.getLong(0),
                    collectionId = collectionId,
                    displayName = cursor.getString(1),
                    slug = cursor.getString(2),
                    fieldType = cursor.getString(3),
                    queryRole = cursor.getString(4),
                    isUniqueValue = cursor.getInt(5) == 1,
                    isRequiredValue = cursor.getInt(6) == 1,
                    optionSourceCollectionId = cursor.getLong(7).takeIf { !cursor.isNull(7) },
                    optionDisplayRole = cursor.getString(8).orEmpty().ifBlank { OPTION_DISPLAY_ROLE_NONE }
                )
            }
        }
        return fields
    }

    fun listFieldCards(collectionId: Long): List<FieldCard> {
        val fields = mutableListOf<FieldCard>()
        readableDatabase.rawQuery(
            """
            SELECT f.id, f.display_name, f.slug, f.field_type, f.query_role, f.is_unique_value, f.is_required_value, f.option_source_collection_id, c.display_name, f.option_display_role
            FROM $TABLE_FIELDS f
            LEFT JOIN $TABLE_COLLECTIONS c ON c.id = f.option_source_collection_id
            WHERE f.collection_id = ?
            ORDER BY f.position_index ASC, f.id ASC
            """.trimIndent(),
            arrayOf(collectionId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                fields += FieldCard(
                    id = cursor.getLong(0),
                    collectionId = collectionId,
                    displayName = cursor.getString(1),
                    slug = cursor.getString(2),
                    fieldType = cursor.getString(3),
                    queryRole = cursor.getString(4),
                    isUniqueValue = cursor.getInt(5) == 1,
                    isRequiredValue = cursor.getInt(6) == 1,
                    optionSourceCollectionId = cursor.getLong(7).takeIf { !cursor.isNull(7) },
                    optionSourceCollectionName = cursor.getString(8).orEmpty(),
                    optionDisplayRole = cursor.getString(9).orEmpty().ifBlank { OPTION_DISPLAY_ROLE_NONE }
                )
            }
        }
        return fields
    }

    fun listExactLookupFields(collectionId: Long): List<FieldDefinition> {
        return listFieldDefinitions(collectionId).filter { it.isLookupKey }
    }

    fun normalizeIdentifierValue(input: String): String = normalizeIdentifier(input)

    fun normalizeUniqueImportValue(input: String): String = normalizeUniqueText(input)

    fun loadExistingOptionImportValueCache(fieldId: Long): MutableSet<String> {
        val values = linkedSetOf<String>()
        val field = listFieldDefinitions(getFieldCollectionId(fieldId) ?: return values)
            .firstOrNull { it.id == fieldId }
            ?: return values

        readableDatabase.rawQuery(
            """
            SELECT value_text, value_number, value_date, value_boolean, value_reference_id
            FROM $TABLE_RECORD_VALUES
            WHERE field_id = ?
            """.trimIndent(),
            arrayOf(fieldId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val rawValue = readStoredFieldValue(field, cursor).trim()
                if (rawValue.isBlank()) continue
                val normalized = normalizeUniqueText(rawValue)
                if (normalized.isNotBlank()) {
                    values += normalized
                }
            }
        }
        return values
    }

    fun clearAllData() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_REVIEW_LOG, null, null)
            db.delete(TABLE_UNIQUE_INDEX, null, null)
            db.delete(TABLE_LOOKUP_INDEX, null, null)
            db.delete(TABLE_RECORD_VALUES, null, null)
            db.delete(TABLE_RECORDS, null, null)
            db.delete(TABLE_FIELDS, null, null)
            db.delete(TABLE_COLLECTIONS, null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteCollection(collectionId: Long) {
        writableDatabase.delete(TABLE_COLLECTIONS, "id = ?", arrayOf(collectionId.toString()))
    }

    fun setCollectionAsMaster(collectionId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE $TABLE_COLLECTIONS SET is_master = 0")
            db.execSQL(
                "UPDATE $TABLE_COLLECTIONS SET is_master = 1, is_options = 0 WHERE id = ?",
                arrayOf(collectionId)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getCollectionOption(collectionId: Long): CollectionOption? {
        return readableDatabase.rawQuery(
            """
            SELECT id, display_name, slug, is_master, is_options
            FROM $TABLE_COLLECTIONS
            WHERE id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(collectionId.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                CollectionOption(
                    id = cursor.getLong(0),
                    displayName = cursor.getString(1),
                    slug = cursor.getString(2),
                    isMaster = cursor.getInt(3) == 1,
                    isOptions = cursor.getInt(4) == 1
                )
            }
        }
    }

    fun deleteField(fieldId: Long) {
        writableDatabase.delete(TABLE_FIELDS, "id = ?", arrayOf(fieldId.toString()))
    }

    fun clearRecordsForCollection(collectionId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Keep delete paths indexed for large collections even on existing local databases.
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_review_log_collection ON $TABLE_REVIEW_LOG(collection_id)"
            )

            db.delete(TABLE_REVIEW_LOG, "collection_id = ?", arrayOf(collectionId.toString()))
            db.delete(TABLE_UNIQUE_INDEX, "collection_id = ?", arrayOf(collectionId.toString()))
            db.delete(TABLE_LOOKUP_INDEX, "collection_id = ?", arrayOf(collectionId.toString()))

            db.execSQL(
                """
                DELETE FROM $TABLE_RECORD_VALUES
                WHERE record_id IN (
                    SELECT id
                    FROM $TABLE_RECORDS
                    WHERE collection_id = ?
                )
                """.trimIndent(),
                arrayOf(collectionId)
            )

            db.delete(TABLE_RECORDS, "collection_id = ?", arrayOf(collectionId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun insertImportedRows(
        collectionId: Long,
        rows: List<ImportRowData>,
        startingRowNumber: Int,
        uniqueValueCache: MutableMap<Long, MutableSet<String>>? = null
    ): BatchImportResult {
        if (rows.isEmpty()) return BatchImportResult.EMPTY

        val db = writableDatabase
        var insertedRecords = 0
        val conflicts = mutableListOf<ImportConflict>()
        val activeUniqueValueCache = uniqueValueCache ?: loadExistingUniqueValueCacheForRows(collectionId, rows)

        db.beginTransaction()
        try {
            val recordStatement = db.compileStatement(
                """
                INSERT INTO $TABLE_RECORDS (collection_id)
                VALUES (?)
                """.trimIndent()
            )

            val valueStatement = db.compileStatement(
                """
                INSERT INTO $TABLE_RECORD_VALUES (
                    record_id,
                    field_id,
                    value_text,
                    value_number,
                    value_date,
                    value_boolean,
                    value_reference_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            )

            val lookupStatement = db.compileStatement(
                """
                INSERT OR REPLACE INTO $TABLE_LOOKUP_INDEX (
                    collection_id,
                    record_id,
                    field_id,
                    normalized_value
                ) VALUES (?, ?, ?, ?)
                """.trimIndent()
            )

            val uniqueStatement = db.compileStatement(
                """
                INSERT INTO $TABLE_UNIQUE_INDEX (
                    collection_id,
                    record_id,
                    field_id,
                    normalized_value
                ) VALUES (?, ?, ?, ?)
                """.trimIndent()
            )

            rows.forEachIndexed { index, row ->
                val rowValues = mutableListOf<PreparedValue>()
                var rowConflict: ImportConflict? = null

                row.values.forEach { rowValue ->
                    val field = rowValue.field
                    val rawValue = rowValue.rawValue
                    val prepared = try {
                        prepareValue(field, rawValue)
                    } catch (error: ImportValueException) {
                        rowConflict = ImportConflict(
                            rowNumber = startingRowNumber + index,
                            reason = error.reason,
                            fieldName = error.fieldName,
                            value = error.value
                        )
                        null
                    } catch (_: IllegalStateException) {
                        rowConflict = ImportConflict(
                            rowNumber = startingRowNumber + index,
                            reason = ImportConflictReason.OTHER,
                            fieldName = field.displayName,
                            value = rawValue.trim()
                        )
                        null
                    } ?: return@forEach
                    rowValues += prepared
                }

                rowConflict?.let {
                    conflicts += it
                    return@forEachIndexed
                }

                if (rowValues.isEmpty()) return@forEachIndexed

                val pendingUniqueValues = linkedMapOf<Long, Pair<String, PreparedValue>>()
                rowValues.forEach { prepared ->
                    if (!prepared.isUniqueValue) return@forEach
                    val normalizedUniqueValue = buildUniqueIndexValue(prepared)
                    if (normalizedUniqueValue.isNullOrBlank()) return@forEach

                    val fieldCache = activeUniqueValueCache.getOrPut(prepared.fieldId) { linkedSetOf() }
                    if (normalizedUniqueValue in fieldCache) {
                        rowConflict = ImportConflict(
                            rowNumber = startingRowNumber + index,
                            reason = ImportConflictReason.DUPLICATE,
                            fieldName = prepared.fieldDisplayName,
                            value = prepared.uniqueRawValue.orEmpty()
                        )
                        return@forEach
                    }
                    pendingUniqueValues[prepared.fieldId] = normalizedUniqueValue to prepared
                }

                rowConflict?.let {
                    conflicts += it
                    return@forEachIndexed
                }

                recordStatement.clearBindings()
                recordStatement.bindLong(1, collectionId)
                val recordId = recordStatement.executeInsert()
                var skipRow = false

                rowValues.forEach { prepared ->
                    if (skipRow) return@forEach

                    if (prepared.isUniqueValue) {
                        val normalizedUniqueValue = pendingUniqueValues[prepared.fieldId]?.first
                        if (!normalizedUniqueValue.isNullOrBlank()) {
                            try {
                                uniqueStatement.clearBindings()
                                uniqueStatement.bindLong(1, collectionId)
                                uniqueStatement.bindLong(2, recordId)
                                uniqueStatement.bindLong(3, prepared.fieldId)
                                uniqueStatement.bindString(4, normalizedUniqueValue)
                                uniqueStatement.executeInsert()
                                activeUniqueValueCache.getOrPut(prepared.fieldId) { linkedSetOf() }
                                    .add(normalizedUniqueValue)
                            } catch (_: SQLiteConstraintException) {
                                db.delete(TABLE_RECORDS, "id = ?", arrayOf(recordId.toString()))
                                conflicts += ImportConflict(
                                    rowNumber = startingRowNumber + index,
                                    reason = ImportConflictReason.DUPLICATE,
                                    fieldName = prepared.fieldDisplayName,
                                    value = prepared.uniqueRawValue.orEmpty()
                                )
                                skipRow = true
                                return@forEach
                            }
                        }
                    }

                    valueStatement.clearBindings()
                    valueStatement.bindLong(1, recordId)
                    valueStatement.bindLong(2, prepared.fieldId)
                    bindNullableString(valueStatement, 3, prepared.textValue)
                    bindNullableDouble(valueStatement, 4, prepared.numberValue)
                    bindNullableString(valueStatement, 5, prepared.dateValue)
                    bindNullableLong(valueStatement, 6, prepared.booleanValue?.toLong())
                    bindNullableLong(valueStatement, 7, prepared.referenceId)
                    valueStatement.executeInsert()

                    if (prepared.isLookupKey || prepared.isFlexibleSearch || prepared.isOptionAutocompleteField) {
                        val normalizedLookupValue = prepared.lookupRawValue
                            ?.takeIf { it.isNotBlank() }
                            ?.let(::normalizeLookupValue)

                        if (!normalizedLookupValue.isNullOrBlank()) {
                            lookupStatement.clearBindings()
                            lookupStatement.bindLong(1, collectionId)
                            lookupStatement.bindLong(2, recordId)
                            lookupStatement.bindLong(3, prepared.fieldId)
                            lookupStatement.bindString(4, normalizedLookupValue)
                            lookupStatement.executeInsert()
                        }
                    }
                }

                if (!skipRow) {
                    insertedRecords += 1
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return BatchImportResult(
            insertedRecords = insertedRecords,
            conflicts = conflicts
        )
    }

    fun loadExistingUniqueValueCache(
        collectionId: Long,
        fieldIds: List<Long>
    ): MutableMap<Long, MutableSet<String>> {
        if (fieldIds.isEmpty()) return mutableMapOf()

        val placeholders = fieldIds.joinToString(",") { "?" }
        val args = arrayOf(collectionId.toString(), *fieldIds.map(Long::toString).toTypedArray())
        val cache = linkedMapOf<Long, MutableSet<String>>()

        readableDatabase.rawQuery(
            """
            SELECT field_id, normalized_value
            FROM $TABLE_UNIQUE_INDEX
            WHERE collection_id = ?
              AND field_id IN ($placeholders)
            """.trimIndent(),
            args
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val fieldId = cursor.getLong(0)
                val normalizedValue = cursor.getString(1).orEmpty()
                cache.getOrPut(fieldId) { linkedSetOf() }.add(normalizedValue)
            }
        }

        return cache
    }

    private fun loadExistingUniqueValueCacheForRows(
        collectionId: Long,
        rows: List<ImportRowData>
    ): MutableMap<Long, MutableSet<String>> {
        val uniqueFieldIds = rows.asSequence()
            .flatMap { row -> row.values.asSequence().map { it.field } }
            .filter { it.isUniqueValue }
            .map { it.id }
            .distinct()
            .toList()
        return loadExistingUniqueValueCache(collectionId, uniqueFieldIds)
    }

    fun countRecordsForCollection(collectionId: Long): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_RECORDS WHERE collection_id = ?",
            arrayOf(collectionId.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun deleteRecord(recordId: Long): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            db.delete(TABLE_REVIEW_LOG, "record_id = ?", arrayOf(recordId.toString()))
            db.delete(TABLE_UNIQUE_INDEX, "record_id = ?", arrayOf(recordId.toString()))
            db.delete(TABLE_LOOKUP_INDEX, "record_id = ?", arrayOf(recordId.toString()))
            db.delete(TABLE_RECORD_VALUES, "record_id = ?", arrayOf(recordId.toString()))
            val deleted = db.delete(TABLE_RECORDS, "id = ?", arrayOf(recordId.toString()))
            db.setTransactionSuccessful()
            deleted > 0
        } finally {
            db.endTransaction()
        }
    }

    fun countRecordsForExport(
        collectionId: Long,
        criterion: ExportCriterion
    ): Int {
        val selection = buildExportCriteriaSelection(criterion)
        return readableDatabase.rawQuery(
            """
            SELECT COUNT(*)
            FROM $TABLE_RECORDS
            WHERE $selection
            """.trimIndent(),
            arrayOf(collectionId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun streamExportRows(
        collectionId: Long,
        fields: List<FieldDefinition>,
        criterion: ExportCriterion,
        chunkSize: Int = ExportConfig.rowChunkSize,
        onRow: (List<String>) -> Unit,
        onCancellationCheck: () -> Unit = {}
    ) {
        if (fields.isEmpty()) return

        val fieldById = fields.associateBy { it.id }
        var lastRecordId = 0L

        while (true) {
            onCancellationCheck()
            val recordIds = loadExportRecordIds(
                collectionId = collectionId,
                criterion = criterion,
                afterRecordId = lastRecordId,
                limit = chunkSize
            )
            if (recordIds.isEmpty()) {
                return
            }

            lastRecordId = recordIds.last()
            val valuesByRecord = linkedMapOf<Long, LinkedHashMap<Long, String>>()
            val metadataByRecord = linkedMapOf<Long, ExportRecordMeta>()
            recordIds.forEach { recordId ->
                valuesByRecord[recordId] = linkedMapOf()
            }

            val placeholders = recordIds.joinToString(",") { "?" }
            readableDatabase.rawQuery(
                """
                SELECT
                    r.id,
                    r.created_at,
                    r.updated_at,
                    r.review_status,
                    r.review_action,
                    r.reviewed_at,
                    r.changed_fields_text
                FROM $TABLE_RECORDS r
                WHERE r.id IN ($placeholders)
                ORDER BY r.id ASC
                """.trimIndent(),
                recordIds.map(Long::toString).toTypedArray()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    metadataByRecord[cursor.getLong(0)] = ExportRecordMeta(
                        recordId = cursor.getLong(0),
                        createdAt = cursor.getString(1).orEmpty(),
                        updatedAt = cursor.getString(2).orEmpty(),
                        reviewStatus = cursor.getString(3).orEmpty(),
                        reviewAction = cursor.getString(4).orEmpty(),
                        reviewedAt = if (cursor.isNull(5)) "" else cursor.getString(5),
                        changedFieldsText = cursor.getString(6).orEmpty()
                    )
                }
            }
            readableDatabase.rawQuery(
                """
                SELECT record_id, field_id, value_text, value_number, value_date, value_boolean, value_reference_id
                FROM $TABLE_RECORD_VALUES
                WHERE record_id IN ($placeholders)
                ORDER BY record_id ASC, field_id ASC
                """.trimIndent(),
                recordIds.map(Long::toString).toTypedArray()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val recordId = cursor.getLong(0)
                    val fieldId = cursor.getLong(1)
                    val field = fieldById[fieldId] ?: continue
                    valuesByRecord[recordId]?.put(fieldId, readDisplayValue(cursor, field))
                }
            }

            recordIds.forEach { recordId ->
                onCancellationCheck()
                val recordValues = valuesByRecord[recordId].orEmpty()
                val metadata = metadataByRecord[recordId]
                val row = ArrayList<String>(fields.size + 7)
                row += recordId.toString()
                row += TimestampFormatters.sqliteUtcToDeviceMx(metadata?.createdAt.orEmpty())
                row += TimestampFormatters.sqliteUtcToDeviceMx(metadata?.updatedAt.orEmpty())
                row += metadata?.reviewStatus.orEmpty()
                row += metadata?.reviewAction.orEmpty()
                row += TimestampFormatters.sqliteUtcToDeviceMx(metadata?.reviewedAt.orEmpty())
                row += metadata?.changedFieldsText.orEmpty()
                fields.forEach { field ->
                    row += recordValues[field.id].orEmpty()
                }
                onRow(row)
            }
        }
    }

    fun findRecordByLookupValue(
        collectionId: Long,
        rawQuery: String
    ): AssetRecordDetail? {
        val lookupFields = listExactLookupFields(collectionId)
        if (lookupFields.isEmpty()) return null
        val normalizedValue = normalizeLookupValue(rawQuery)
        if (normalizedValue.isBlank()) return null

        val fieldPlaceholders = lookupFields.joinToString(",") { "?" }
        val recordId = readableDatabase.rawQuery(
            """
            SELECT record_id
            FROM $TABLE_LOOKUP_INDEX
            WHERE collection_id = ?
              AND field_id IN ($fieldPlaceholders)
              AND normalized_value = ?
            ORDER BY record_id DESC
            LIMIT 1
            """.trimIndent(),
            buildList {
                add(collectionId.toString())
                addAll(lookupFields.map { it.id.toString() })
                add(normalizedValue)
            }.toTypedArray()
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else return null
        }

        return getRecordDetail(collectionId, recordId)
    }

    fun getRecordDetail(collectionId: Long, recordId: Long): AssetRecordDetail? {
        val fields = listFieldDefinitions(collectionId)
        if (fields.isEmpty()) return null
        val fieldById = fields.associateBy { it.id }

        val recordMeta = readableDatabase.rawQuery(
            """
            SELECT created_at, updated_at, review_status, review_action, reviewed_at, changed_fields_text
            FROM $TABLE_RECORDS
            WHERE id = ? AND collection_id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(recordId.toString(), collectionId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                RecordMeta(
                    createdAt = cursor.getString(0),
                    updatedAt = cursor.getString(1),
                    reviewStatus = cursor.getString(2),
                    reviewAction = cursor.getString(3),
                    reviewedAt = cursor.getString(4).orEmpty(),
                    changedFieldsText = cursor.getString(5).orEmpty()
                )
            } else {
                null
            }
        } ?: return null

        val valueMap = mutableMapOf<Long, String>()
        readableDatabase.rawQuery(
            """
            SELECT record_id, field_id, value_text, value_number, value_date, value_boolean
                   , value_reference_id
            FROM $TABLE_RECORD_VALUES
            WHERE record_id = ?
            """.trimIndent(),
            arrayOf(recordId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val fieldId = cursor.getLong(1)
                val field = fieldById[fieldId] ?: continue
                valueMap[fieldId] = readDisplayValue(cursor, field)
            }
        }

        return AssetRecordDetail(
            recordId = recordId,
            collectionId = collectionId,
            createdAt = recordMeta.createdAt,
            updatedAt = recordMeta.updatedAt,
            reviewStatus = recordMeta.reviewStatus,
            reviewAction = recordMeta.reviewAction,
            reviewedAt = recordMeta.reviewedAt,
            changedFieldsText = recordMeta.changedFieldsText,
            fields = fields.map { field ->
                AssetFieldValue(
                    fieldId = field.id,
                    fieldDisplayName = field.displayName,
                    fieldSlug = field.slug,
                    fieldType = field.fieldType,
                    isLookupKey = field.isLookupKey,
                    isUniqueValue = field.isUniqueValue,
                    isRequiredValue = field.isRequiredValue,
                    value = valueMap[field.id].orEmpty(),
                    optionSourceCollectionId = field.optionSourceCollectionId
                )
            }
        )
    }

    fun searchOptionSuggestions(
        collectionId: Long,
        rawQuery: String,
        limit: Int = 20
    ): List<OptionSuggestion> {
        val normalizedQuery = normalizeLookupValue(rawQuery)
        if (normalizedQuery.isNotEmpty() && normalizedQuery.length < 2) return emptyList()

        val likePattern = if (normalizedQuery.isEmpty()) "%" else "%$normalizedQuery%"

        val recordIds = mutableListOf<Long>()
        readableDatabase.rawQuery(
            """
            SELECT DISTINCT record_id
            FROM $TABLE_LOOKUP_INDEX
            WHERE collection_id = ?
              AND normalized_value LIKE ?
            ORDER BY normalized_value ASC
            LIMIT ?
            """.trimIndent(),
            arrayOf(collectionId.toString(), likePattern, limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                recordIds += cursor.getLong(0)
            }
        }

        if (recordIds.isEmpty()) return emptyList()

        val fields = listFieldDefinitions(collectionId)
        val fieldById = fields.associateBy { it.id }
        val primaryField = fields.firstOrNull { it.optionDisplayRole == OPTION_DISPLAY_ROLE_PRIMARY }
        val supportField = fields.firstOrNull { it.optionDisplayRole == OPTION_DISPLAY_ROLE_SUPPORT }
        val valuesByRecordId = linkedMapOf<Long, LinkedHashMap<Long, String>>()
        recordIds.forEach { recordId ->
            valuesByRecordId[recordId] = linkedMapOf()
        }

        val placeholders = recordIds.joinToString(",") { "?" }
        readableDatabase.rawQuery(
            """
            SELECT record_id, field_id, value_text, value_number, value_date, value_boolean, value_reference_id
            FROM $TABLE_RECORD_VALUES
            WHERE record_id IN ($placeholders)
            ORDER BY record_id ASC, id ASC
            """.trimIndent(),
            recordIds.map(Long::toString).toTypedArray()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val recordId = cursor.getLong(0)
                val fieldId = cursor.getLong(1)
                val field = fieldById[fieldId] ?: continue
                valuesByRecordId[recordId]?.put(fieldId, readDisplayValue(cursor, field))
            }
        }

        return recordIds.mapNotNull { recordId ->
            val recordValues = valuesByRecordId[recordId].orEmpty()
            val primaryValue = primaryField?.let { recordValues[it.id]?.trim()?.takeIf(String::isNotEmpty) }
            val supportValue = supportField?.let { recordValues[it.id]?.trim()?.takeIf(String::isNotEmpty) }
            val label = when {
                !primaryValue.isNullOrEmpty() && !supportValue.isNullOrEmpty() -> "$primaryValue - $supportValue"
                !primaryValue.isNullOrEmpty() -> primaryValue
                !supportValue.isNullOrEmpty() -> supportValue
                else -> fields.mapNotNull { field ->
                    recordValues[field.id]?.trim()?.takeIf { it.isNotEmpty() }
                }.joinToString(" · ").trim()
            }
            val selectedValue = primaryValue ?: supportValue ?: label
            if (label.isEmpty() || selectedValue.isEmpty()) {
                null
            } else {
                OptionSuggestion(
                    recordId = recordId,
                    displayLabel = label,
                    selectedValue = selectedValue
                )
            }
        }
    }

    fun updateRecordValues(
        recordId: Long,
        collectionId: Long,
        fields: List<FieldDefinition>,
        updates: Map<Long, String>
    ) {
        if (updates.isEmpty()) return
        val db = writableDatabase
        val fieldsById = fields.associateBy { it.id }
        val changedFields = linkedSetOf<String>()

        db.beginTransaction()
        try {
            val valueUpsert = db.compileStatement(
                """
                INSERT OR REPLACE INTO $TABLE_RECORD_VALUES (
                    id,
                    record_id,
                    field_id,
                    value_text,
                    value_number,
                    value_date,
                    value_boolean,
                    value_reference_id
                ) VALUES (
                    (SELECT id FROM $TABLE_RECORD_VALUES WHERE record_id = ? AND field_id = ?),
                    ?, ?, ?, ?, ?, ?, ?
                )
                """.trimIndent()
            )

            updates.forEach { (fieldId, rawValue) ->
                val field = fieldsById[fieldId] ?: return@forEach
                val trimmed = rawValue.trim()
                val previousValue = currentFieldValue(recordId, field)
                val storedSnapshot = loadStoredFieldSnapshot(recordId, fieldId)

                if (field.isRequiredValue && trimmed.isEmpty()) {
                    throw IllegalStateException("La columna \"${field.displayName}\" es obligatoria.")
                }

                if (trimmed.isEmpty() && storedSnapshot == null) {
                    return@forEach
                }

                if (trimmed.isEmpty()) {
                    db.delete(
                        TABLE_LOOKUP_INDEX,
                        "record_id = ? AND field_id = ?",
                        arrayOf(recordId.toString(), fieldId.toString())
                    )
                    db.delete(
                        TABLE_UNIQUE_INDEX,
                        "record_id = ? AND field_id = ?",
                        arrayOf(recordId.toString(), fieldId.toString())
                    )
                    db.delete(
                        TABLE_RECORD_VALUES,
                        "record_id = ? AND field_id = ?",
                        arrayOf(recordId.toString(), fieldId.toString())
                    )
                    if (previousValue.isNotBlank()) {
                        changedFields += field.displayName
                    }
                    return@forEach
                }

                val prepared = prepareValue(field, trimmed)
                    ?: throw IllegalStateException("No se pudo preparar el valor de ${field.displayName}.")

                if (storedSnapshot != null && isPreparedValueEquivalent(storedSnapshot, prepared)) {
                    return@forEach
                }

                db.delete(
                    TABLE_LOOKUP_INDEX,
                    "record_id = ? AND field_id = ?",
                    arrayOf(recordId.toString(), fieldId.toString())
                )
                db.delete(
                    TABLE_UNIQUE_INDEX,
                    "record_id = ? AND field_id = ?",
                    arrayOf(recordId.toString(), fieldId.toString())
                )

                if (prepared.isUniqueValue) {
                    val normalizedUniqueValue = buildUniqueIndexValue(prepared)
                    if (!normalizedUniqueValue.isNullOrBlank()) {
                        try {
                            db.insertOrThrow(
                                TABLE_UNIQUE_INDEX,
                                null,
                                ContentValues().apply {
                                    put("collection_id", collectionId)
                                    put("record_id", recordId)
                                    put("field_id", fieldId)
                                    put("normalized_value", normalizedUniqueValue)
                                }
                            )
                        } catch (_: SQLiteConstraintException) {
                            throw IllegalStateException(
                                "La columna \"${field.displayName}\" no permite valores repetidos."
                            )
                        }
                    }
                }

                valueUpsert.clearBindings()
                valueUpsert.bindLong(1, recordId)
                valueUpsert.bindLong(2, fieldId)
                valueUpsert.bindLong(3, recordId)
                valueUpsert.bindLong(4, fieldId)
                bindNullableString(valueUpsert, 5, prepared.textValue)
                bindNullableDouble(valueUpsert, 6, prepared.numberValue)
                bindNullableString(valueUpsert, 7, prepared.dateValue)
                bindNullableLong(valueUpsert, 8, prepared.booleanValue?.toLong())
                bindNullableLong(valueUpsert, 9, prepared.referenceId)
                valueUpsert.executeInsert()

                if (prepared.isLookupKey || prepared.isFlexibleSearch || prepared.isOptionAutocompleteField) {
                    val normalizedLookupValue = prepared.lookupRawValue
                        ?.takeIf { it.isNotBlank() }
                        ?.let(::normalizeLookupValue)
                    if (!normalizedLookupValue.isNullOrBlank()) {
                        db.insertWithOnConflict(
                            TABLE_LOOKUP_INDEX,
                            null,
                            ContentValues().apply {
                                put("collection_id", collectionId)
                                put("record_id", recordId)
                                put("field_id", fieldId)
                                put("normalized_value", normalizedLookupValue)
                            },
                            SQLiteDatabase.CONFLICT_REPLACE
                        )
                    }
                }

                if (trimmed != previousValue.trim()) {
                    changedFields += field.displayName
                }
            }

            if (changedFields.isNotEmpty()) {
                updateRecordReviewState(
                    db = db,
                    recordId = recordId,
                    reviewStatus = ReviewStatusCodes.UPDATED,
                    reviewAction = ReviewActionCodes.EDIT_SAVED,
                    changedFields = changedFields.toList()
                )
                insertReviewLog(
                    db = db,
                    recordId = recordId,
                    collectionId = collectionId,
                    actionType = ReviewActionCodes.EDIT_SAVED,
                    changedFields = changedFields.toList()
                )
            } else {
                db.execSQL(
                    "UPDATE $TABLE_RECORDS SET updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    arrayOf(recordId)
                )
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun createRecordValues(
        collectionId: Long,
        fields: List<FieldDefinition>,
        updates: Map<Long, String>
    ): Long {
        val db = writableDatabase
        val fieldsById = fields.associateBy { it.id }
        val preparedValues = updates.mapNotNull { (fieldId, rawValue) ->
            val field = fieldsById[fieldId] ?: return@mapNotNull null
            val trimmed = rawValue.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            prepareValue(field, trimmed)
                ?: throw IllegalStateException("No se pudo preparar el valor de ${field.displayName}.")
        }

        if (preparedValues.isEmpty()) {
            throw IllegalStateException("Completa al menos un campo antes de guardar.")
        }

        fields.filter { it.isRequiredValue }.forEach { field ->
            if (updates[field.id]?.trim().isNullOrEmpty()) {
                throw IllegalStateException("La columna \"${field.displayName}\" es obligatoria.")
            }
        }

        db.beginTransaction()
        try {
            val recordId = db.insertOrThrow(
                TABLE_RECORDS,
                null,
                ContentValues().apply {
                    put("collection_id", collectionId)
                    put("review_status", ReviewStatusCodes.PENDING)
                    put("review_action", ReviewActionCodes.CREATED_MANUAL)
                    put("changed_fields_text", preparedValues.joinToString(",") { it.fieldDisplayName })
                }
            )

            preparedValues.forEach { prepared ->
                if (prepared.isUniqueValue) {
                    val normalizedUniqueValue = buildUniqueIndexValue(prepared)
                    if (!normalizedUniqueValue.isNullOrBlank()) {
                        try {
                            db.insertOrThrow(
                                TABLE_UNIQUE_INDEX,
                                null,
                                ContentValues().apply {
                                    put("collection_id", collectionId)
                                    put("record_id", recordId)
                                    put("field_id", prepared.fieldId)
                                    put("normalized_value", normalizedUniqueValue)
                                }
                            )
                        } catch (_: SQLiteConstraintException) {
                            throw IllegalStateException(
                                "La columna \"${prepared.fieldDisplayName}\" no permite valores repetidos."
                            )
                        }
                    }
                }

                db.insertOrThrow(
                    TABLE_RECORD_VALUES,
                    null,
                    ContentValues().apply {
                        put("record_id", recordId)
                        put("field_id", prepared.fieldId)
                        put("value_text", prepared.textValue)
                        put("value_number", prepared.numberValue)
                        put("value_date", prepared.dateValue)
                        put("value_boolean", prepared.booleanValue)
                        put("value_reference_id", prepared.referenceId)
                    }
                )

                if (prepared.isLookupKey || prepared.isFlexibleSearch || prepared.isOptionAutocompleteField) {
                    val normalizedLookupValue = prepared.lookupRawValue
                        ?.takeIf { it.isNotBlank() }
                        ?.let(::normalizeLookupValue)
                    if (!normalizedLookupValue.isNullOrBlank()) {
                        db.insertWithOnConflict(
                            TABLE_LOOKUP_INDEX,
                            null,
                            ContentValues().apply {
                                put("collection_id", collectionId)
                                put("record_id", recordId)
                                put("field_id", prepared.fieldId)
                                put("normalized_value", normalizedLookupValue)
                            },
                            SQLiteDatabase.CONFLICT_REPLACE
                        )
                    }
                }
            }

            insertReviewLog(
                db = db,
                recordId = recordId,
                collectionId = collectionId,
                actionType = ReviewActionCodes.CREATED_MANUAL,
                changedFields = preparedValues.map { it.fieldDisplayName }
            )

            db.setTransactionSuccessful()
            return recordId
        } finally {
            db.endTransaction()
        }
    }

    fun markRecordAsConforme(
        recordId: Long,
        collectionId: Long
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            updateRecordReviewState(
                db = db,
                recordId = recordId,
                reviewStatus = ReviewStatusCodes.CONFIRMED,
                reviewAction = ReviewActionCodes.CONFIRMED_MANUAL,
                changedFields = emptyList()
            )
            insertReviewLog(
                db = db,
                recordId = recordId,
                collectionId = collectionId,
                actionType = ReviewActionCodes.CONFIRMED_MANUAL,
                changedFields = emptyList()
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun fetchDashboardSummary(): DashboardSummary {
        val totalTables = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_COLLECTIONS",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

        val totalRecords = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_RECORDS",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

        val tableCards = mutableListOf<TableDashboardCard>()
        readableDatabase.rawQuery(
            """
            SELECT c.id, c.display_name, c.slug, COUNT(r.id) AS record_count
            FROM $TABLE_COLLECTIONS c
            LEFT JOIN $TABLE_RECORDS r ON r.collection_id = c.id
            GROUP BY c.id, c.display_name, c.slug
            ORDER BY record_count DESC, c.display_name COLLATE NOCASE ASC
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tableCards += TableDashboardCard(
                    collectionId = cursor.getLong(0),
                    displayName = cursor.getString(1),
                    slug = cursor.getString(2),
                    recordCount = cursor.getInt(3)
                )
            }
        }

        return DashboardSummary(
            totalTables = totalTables,
            totalRecords = totalRecords,
            tableCards = tableCards
        )
    }

    fun fetchReviewStatusDashboard(collectionId: Long): ReviewStatusDashboardSummary {
        return readableDatabase.rawQuery(
            """
            SELECT
                COUNT(*) AS total_count,
                COALESCE(SUM(CASE WHEN review_status = '${ReviewStatusCodes.PENDING}' THEN 1 ELSE 0 END), 0) AS pending_count,
                COALESCE(SUM(CASE WHEN review_status = '${ReviewStatusCodes.CONFIRMED}' THEN 1 ELSE 0 END), 0) AS confirmed_count,
                COALESCE(SUM(CASE WHEN review_status = '${ReviewStatusCodes.UPDATED}' THEN 1 ELSE 0 END), 0) AS updated_count
            FROM $TABLE_RECORDS
            WHERE collection_id = ?
            """.trimIndent(),
            arrayOf(collectionId.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                return@use ReviewStatusDashboardSummary()
            }
            ReviewStatusDashboardSummary(
                totalCount = cursor.getInt(0),
                pendingCount = cursor.getInt(1),
                confirmedCount = cursor.getInt(2),
                updatedCount = cursor.getInt(3)
            )
        }
    }

    fun fetchGroupedDashboardCards(
        collectionId: Long,
        fieldId: Long,
        reviewStatus: String? = null
    ): List<GroupedDashboardCard> {
        val field = getFieldDefinitionById(fieldId) ?: return emptyList()
        if (field.collectionId != collectionId) return emptyList()

        val args = mutableListOf(fieldId.toString(), collectionId.toString())
        val statusClause = if (reviewStatus.isNullOrBlank()) {
            ""
        } else {
            args += reviewStatus
            "AND r.review_status = ?"
        }

        val result = mutableListOf<GroupedDashboardCard>()
        readableDatabase.rawQuery(
            """
            SELECT
                rv.value_text,
                rv.value_number,
                rv.value_date,
                rv.value_boolean,
                COUNT(r.id) AS record_count
            FROM $TABLE_RECORDS r
            LEFT JOIN $TABLE_RECORD_VALUES rv
                ON rv.record_id = r.id
               AND rv.field_id = ?
            WHERE r.collection_id = ?
            $statusClause
            GROUP BY rv.value_text, rv.value_number, rv.value_date, rv.value_boolean
            ORDER BY record_count DESC, rv.value_text COLLATE NOCASE ASC, rv.value_date ASC, rv.value_number ASC
            """.trimIndent(),
            args.toTypedArray()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += GroupedDashboardCard(
                    valueLabel = readGroupedDashboardValue(field, cursor),
                    recordCount = cursor.getInt(4)
                )
            }
        }
        return result
    }

    fun listRecordPage(
        collectionId: Long,
        page: Int,
        pageSize: Int
    ): List<RecordPreview> {
        val offset = page * pageSize
        val fields = listFieldDefinitions(collectionId)
        val fieldById = fields.associateBy { it.id }
        val records = mutableListOf<RecordPreview>()
        val recordIds = mutableListOf<Long>()

        readableDatabase.rawQuery(
            """
            SELECT id, created_at, updated_at
            FROM $TABLE_RECORDS
            WHERE collection_id = ?
            ORDER BY updated_at DESC, id DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            arrayOf(collectionId.toString(), pageSize.toString(), offset.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val recordId = cursor.getLong(0)
                records += RecordPreview(
                    recordId = recordId,
                    createdAt = cursor.getString(1),
                    updatedAt = cursor.getString(2),
                    title = "",
                    values = linkedMapOf()
                )
                recordIds += recordId
            }
        }

        if (recordIds.isEmpty()) return emptyList()

        val recordsById = records.associateBy { it.recordId }
        val placeholders = recordIds.joinToString(",") { "?" }
        val args = recordIds.map { it.toString() }.toTypedArray()

        readableDatabase.rawQuery(
            """
            SELECT record_id, field_id, value_text, value_number, value_date, value_boolean
                   , value_reference_id
            FROM $TABLE_RECORD_VALUES
            WHERE record_id IN ($placeholders)
            ORDER BY id ASC
            """.trimIndent(),
            args
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val recordId = cursor.getLong(0)
                val fieldId = cursor.getLong(1)
                val record = recordsById[recordId] ?: continue
                val field = fieldById[fieldId] ?: continue
                record.values[field.displayName] = readDisplayValue(cursor, field)
            }
        }

        val primaryField = fields.firstOrNull { it.optionDisplayRole == OPTION_DISPLAY_ROLE_PRIMARY }
        val lookupField = fields.firstOrNull { it.isLookupKey }
        val uniqueField = fields.firstOrNull { it.isUniqueValue }
        val requiredField = fields.firstOrNull { it.isRequiredValue }

        records.forEach { record ->
            record.title = selectRecordPreviewTitle(
                record = record,
                primaryFieldName = primaryField?.displayName,
                lookupFieldName = lookupField?.displayName,
                uniqueFieldName = uniqueField?.displayName,
                requiredFieldName = requiredField?.displayName
            )
        }

        return records
    }

    private fun selectRecordPreviewTitle(
        record: RecordPreview,
        primaryFieldName: String?,
        lookupFieldName: String?,
        uniqueFieldName: String?,
        requiredFieldName: String?
    ): String {
        val candidates = listOfNotNull(primaryFieldName, lookupFieldName, uniqueFieldName, requiredFieldName)
        candidates.forEach { fieldName ->
            record.values[fieldName]?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return record.values.values.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }

    private fun nextFieldPosition(collectionId: Long): Int {
        readableDatabase.rawQuery(
            "SELECT COALESCE(MAX(position_index), -1) + 1 FROM $TABLE_FIELDS WHERE collection_id = ?",
            arrayOf(collectionId.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun countFieldsForCollection(collectionId: Long): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_FIELDS WHERE collection_id = ?",
            arrayOf(collectionId.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun hasOptionDisplayRole(
        collectionId: Long,
        optionDisplayRole: String,
        excludingFieldId: Long? = null
    ): Boolean {
        val sql = buildString {
            append(
                """
                SELECT COUNT(*)
                FROM $TABLE_FIELDS
                WHERE collection_id = ?
                  AND option_display_role = ?
                """.trimIndent()
            )
            if (excludingFieldId != null) {
                append(" AND id != ?")
            }
        }
        val args = buildList {
            add(collectionId.toString())
            add(optionDisplayRole)
            excludingFieldId?.let { add(it.toString()) }
        }.toTypedArray()
        readableDatabase.rawQuery(sql, args).use { cursor ->
            return cursor.moveToFirst() && cursor.getInt(0) > 0
        }
    }

    private fun hasOptionAutocompleteColumns(collectionId: Long): Boolean {
        readableDatabase.rawQuery(
            """
            SELECT COUNT(*)
            FROM $TABLE_FIELDS
            WHERE collection_id = ?
              AND option_display_role IN (?, ?)
            """.trimIndent(),
            arrayOf(
                collectionId.toString(),
                OPTION_DISPLAY_ROLE_PRIMARY,
                OPTION_DISPLAY_ROLE_SUPPORT
            )
        ).use { cursor ->
            return cursor.moveToFirst() && cursor.getInt(0) > 0
        }
    }

    private fun getFieldDefinitionById(fieldId: Long, db: SQLiteDatabase = readableDatabase): FieldDefinition? {
        return db.rawQuery(
            """
            SELECT id, collection_id, display_name, slug, field_type, query_role, is_unique_value, is_required_value, option_source_collection_id, option_display_role
            FROM $TABLE_FIELDS
            WHERE id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(fieldId.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                FieldDefinition(
                    id = cursor.getLong(0),
                    collectionId = cursor.getLong(1),
                    displayName = cursor.getString(2),
                    slug = cursor.getString(3),
                    fieldType = cursor.getString(4),
                    queryRole = cursor.getString(5),
                    isUniqueValue = cursor.getInt(6) == 1,
                    isRequiredValue = cursor.getInt(7) == 1,
                    optionSourceCollectionId = cursor.getLong(8).takeIf { !cursor.isNull(8) },
                    optionDisplayRole = cursor.getString(9).orEmpty().ifBlank { OPTION_DISPLAY_ROLE_NONE }
                )
            }
        }
    }

    private fun hasEmptyRequiredValues(
        collectionId: Long,
        fieldId: Long,
        db: SQLiteDatabase = readableDatabase
    ): Boolean {
        return db.rawQuery(
            """
            SELECT COUNT(*)
            FROM $TABLE_RECORDS r
            LEFT JOIN $TABLE_RECORD_VALUES rv
              ON rv.record_id = r.id
             AND rv.field_id = ?
            WHERE r.collection_id = ?
              AND (
                rv.record_id IS NULL OR (
                    COALESCE(TRIM(rv.value_text), '') = ''
                    AND rv.value_number IS NULL
                    AND COALESCE(TRIM(rv.value_date), '') = ''
                    AND rv.value_boolean IS NULL
                )
              )
            """.trimIndent(),
            arrayOf(fieldId.toString(), collectionId.toString())
        ).use { cursor ->
            cursor.moveToFirst() && cursor.getInt(0) > 0
        }
    }

    private fun hasDuplicateUniqueValues(
        field: FieldDefinition,
        db: SQLiteDatabase = readableDatabase
    ): Boolean {
        val seen = linkedSetOf<String>()
        return db.rawQuery(
            """
            SELECT rv.value_text, rv.value_number, rv.value_date, rv.value_boolean, rv.record_id
            FROM $TABLE_RECORD_VALUES rv
            INNER JOIN $TABLE_RECORDS r ON r.id = rv.record_id
            WHERE rv.field_id = ?
              AND r.collection_id = ?
            ORDER BY rv.record_id ASC
            """.trimIndent(),
            arrayOf(field.id.toString(), field.collectionId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val rawValue = readStoredFieldValue(field, cursor)
                val prepared = prepareValue(field, rawValue) ?: continue
                val normalizedUniqueValue = buildUniqueIndexValue(prepared) ?: continue
                if (!seen.add(normalizedUniqueValue)) {
                    return@use true
                }
            }
            false
        }
    }

    private fun rebuildFieldIndexes(field: FieldDefinition, db: SQLiteDatabase) {
        db.delete(
            TABLE_LOOKUP_INDEX,
            "collection_id = ? AND field_id = ?",
            arrayOf(field.collectionId.toString(), field.id.toString())
        )
        db.delete(
            TABLE_UNIQUE_INDEX,
            "collection_id = ? AND field_id = ?",
            arrayOf(field.collectionId.toString(), field.id.toString())
        )

        val values = db.rawQuery(
            """
            SELECT rv.value_text, rv.value_number, rv.value_date, rv.value_boolean, rv.record_id
            FROM $TABLE_RECORD_VALUES rv
            INNER JOIN $TABLE_RECORDS r ON r.id = rv.record_id
            WHERE rv.field_id = ?
              AND r.collection_id = ?
            ORDER BY rv.record_id ASC
            """.trimIndent(),
            arrayOf(field.id.toString(), field.collectionId.toString())
        )

        values.use { cursor ->
            while (cursor.moveToNext()) {
                val recordId = cursor.getLong(4)
                val rawValue = readStoredFieldValue(field, cursor)
                val prepared = prepareValue(field, rawValue) ?: continue

                if (prepared.isUniqueValue) {
                    val normalizedUniqueValue = buildUniqueIndexValue(prepared)
                    if (!normalizedUniqueValue.isNullOrBlank()) {
                        try {
                            db.insertOrThrow(
                                TABLE_UNIQUE_INDEX,
                                null,
                                ContentValues().apply {
                                    put("collection_id", field.collectionId)
                                    put("record_id", recordId)
                                    put("field_id", field.id)
                                    put("normalized_value", normalizedUniqueValue)
                                }
                            )
                        } catch (_: SQLiteConstraintException) {
                            throw IllegalStateException(appContext.getString(R.string.schema_unique_existing_data_error))
                        }
                    }
                }

                if (prepared.isLookupKey || prepared.isFlexibleSearch || prepared.isOptionAutocompleteField) {
                    val normalizedLookupValue = prepared.lookupRawValue
                        ?.takeIf { it.isNotBlank() }
                        ?.let(::normalizeLookupValue)
                    if (!normalizedLookupValue.isNullOrBlank()) {
                        db.insertWithOnConflict(
                            TABLE_LOOKUP_INDEX,
                            null,
                            ContentValues().apply {
                                put("collection_id", field.collectionId)
                                put("record_id", recordId)
                                put("field_id", field.id)
                                put("normalized_value", normalizedLookupValue)
                            },
                            SQLiteDatabase.CONFLICT_REPLACE
                        )
                    }
                }
            }
        }
    }

    private fun isSafeFieldTypeTransition(currentType: String, targetType: String): Boolean {
        return fieldStorageFamily(currentType) == fieldStorageFamily(targetType)
    }

    private fun fieldStorageFamily(fieldType: String): String {
        return when (fieldType) {
            "text", "textarea", FIELD_TYPE_LIST -> "text"
            "number" -> "number"
            "date" -> "date"
            "boolean" -> "boolean"
            else -> fieldType
        }
    }

    private fun makeUniqueSlug(tableName: String, baseSlug: String): String {
        var attempt = 1
        var candidate = baseSlug
        while (slugExists(tableName, candidate)) {
            attempt += 1
            candidate = "${baseSlug}_$attempt"
        }
        return candidate
    }

    private fun makeUniqueFieldSlug(collectionId: Long, baseSlug: String): String {
        var attempt = 1
        var candidate = baseSlug
        while (fieldSlugExists(collectionId, candidate)) {
            attempt += 1
            candidate = "${baseSlug}_$attempt"
        }
        return candidate
    }

    private fun slugExists(tableName: String, slug: String): Boolean {
        readableDatabase.rawQuery(
            "SELECT 1 FROM $tableName WHERE slug = ? LIMIT 1",
            arrayOf(slug)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun fieldSlugExists(collectionId: Long, slug: String): Boolean {
        readableDatabase.rawQuery(
            "SELECT 1 FROM $TABLE_FIELDS WHERE collection_id = ? AND slug = ? LIMIT 1",
            arrayOf(collectionId.toString(), slug)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun getFieldCollectionId(fieldId: Long): Long? {
        readableDatabase.rawQuery(
            "SELECT collection_id FROM $TABLE_FIELDS WHERE id = ? LIMIT 1",
            arrayOf(fieldId.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    private fun normalizeIdentifier(input: String): String {
        val normalized = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase(Locale.US)
            .replace("[^a-z0-9]+".toRegex(), "_")
            .replace("_+".toRegex(), "_")
            .trim('_')

        return when {
            normalized.isBlank() -> "item"
            normalized.first().isDigit() -> "item_$normalized"
            else -> normalized
        }
    }

    private fun normalizeLookupValue(input: String): String {
        return Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase(Locale.US)
            .replace("[^a-z0-9]+".toRegex(), " ")
            .trim()
            .replace("\\s+".toRegex(), " ")
    }

    private fun normalizeUniqueText(input: String): String {
        return Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase(Locale.US)
            .replace("\\s+".toRegex(), " ")
    }

    private fun buildUniqueIndexValue(prepared: PreparedValue): String? {
        return when {
            prepared.numberValue != null -> prepared.numberValue.toString()
            prepared.booleanValue != null -> prepared.booleanValue.toString()
            !prepared.dateValue.isNullOrBlank() -> normalizeUniqueText(prepared.dateValue)
            !prepared.textValue.isNullOrBlank() -> normalizeUniqueText(prepared.textValue)
            else -> prepared.uniqueRawValue?.takeIf { it.isNotBlank() }?.let(::normalizeUniqueText)
        }
    }

    private fun prepareValue(
        field: FieldDefinition,
        rawValue: String
    ): PreparedValue? {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return null

        return when (field.fieldType) {
            "number" -> trimmed.replace(",", ".").toDoubleOrNull()?.let { parsed ->
                PreparedValue(
                    fieldId = field.id,
                    fieldDisplayName = field.displayName,
                    numberValue = parsed,
                    isLookupKey = field.isLookupKey,
                    isFlexibleSearch = field.isFlexibleSearch,
                    isOptionAutocompleteField = field.isOptionAutocompleteField,
                    isUniqueValue = field.isUniqueValue,
                    lookupRawValue = trimmed,
                    uniqueRawValue = trimmed
                )
            } ?: PreparedValue(
                fieldId = field.id,
                fieldDisplayName = field.displayName,
                textValue = trimmed,
                isLookupKey = field.isLookupKey,
                isFlexibleSearch = field.isFlexibleSearch,
                isOptionAutocompleteField = field.isOptionAutocompleteField,
                isUniqueValue = field.isUniqueValue,
                lookupRawValue = trimmed,
                uniqueRawValue = trimmed
            )

            "boolean" -> parseBooleanValue(trimmed)?.let { parsed ->
                PreparedValue(
                    fieldId = field.id,
                    fieldDisplayName = field.displayName,
                    booleanValue = parsed,
                    isLookupKey = field.isLookupKey,
                    isFlexibleSearch = field.isFlexibleSearch,
                    isOptionAutocompleteField = field.isOptionAutocompleteField,
                    isUniqueValue = field.isUniqueValue,
                    lookupRawValue = trimmed,
                    uniqueRawValue = trimmed
                )
            } ?: PreparedValue(
                fieldId = field.id,
                fieldDisplayName = field.displayName,
                textValue = trimmed,
                isLookupKey = field.isLookupKey,
                isFlexibleSearch = field.isFlexibleSearch,
                isOptionAutocompleteField = field.isOptionAutocompleteField,
                isUniqueValue = field.isUniqueValue,
                lookupRawValue = trimmed,
                uniqueRawValue = trimmed
            )

            "date" -> PreparedValue(
                fieldId = field.id,
                fieldDisplayName = field.displayName,
                dateValue = trimmed,
                isLookupKey = field.isLookupKey,
                isFlexibleSearch = field.isFlexibleSearch,
                isOptionAutocompleteField = field.isOptionAutocompleteField,
                isUniqueValue = field.isUniqueValue,
                lookupRawValue = trimmed,
                uniqueRawValue = trimmed
            )

            FIELD_TYPE_LIST -> {
                PreparedValue(
                    fieldId = field.id,
                    fieldDisplayName = field.displayName,
                    textValue = trimmed,
                    isLookupKey = field.isLookupKey,
                    isFlexibleSearch = field.isFlexibleSearch,
                    isOptionAutocompleteField = field.isOptionAutocompleteField,
                    isUniqueValue = field.isUniqueValue,
                    lookupRawValue = trimmed,
                    uniqueRawValue = trimmed
                )
            }

            else -> PreparedValue(
                fieldId = field.id,
                fieldDisplayName = field.displayName,
                textValue = trimmed,
                isLookupKey = field.isLookupKey,
                isFlexibleSearch = field.isFlexibleSearch,
                isOptionAutocompleteField = field.isOptionAutocompleteField,
                isUniqueValue = field.isUniqueValue,
                lookupRawValue = trimmed,
                uniqueRawValue = trimmed
            )
        }
    }

    private fun parseBooleanValue(input: String): Int? {
        return when (input.trim().lowercase(Locale.US)) {
            "1", "true", "si", "yes", "x" -> 1
            "0", "false", "no" -> 0
            else -> null
        }
    }

    private fun readDisplayValue(cursor: Cursor, field: FieldDefinition): String {
        return when (field.fieldType) {
            "number" -> when {
                !cursor.isNull(3) -> cursor.getDouble(3).toString()
                !cursor.isNull(2) -> cursor.getString(2)
                else -> ""
            }

            "date" -> when {
                !cursor.isNull(4) -> cursor.getString(4)
                !cursor.isNull(2) -> cursor.getString(2)
                else -> ""
            }

            "boolean" -> when {
                !cursor.isNull(5) -> if (cursor.getInt(5) == 1) "Si" else "No"
                !cursor.isNull(2) -> cursor.getString(2)
                else -> ""
            }

            FIELD_TYPE_LIST -> if (cursor.isNull(2)) "" else cursor.getString(2)

            else -> if (cursor.isNull(2)) "" else cursor.getString(2)
        }
    }

    private fun readGroupedDashboardValue(field: FieldDefinition, cursor: Cursor): String {
        val value = when (field.fieldType) {
            "number" -> if (cursor.isNull(1)) "" else cursor.getDouble(1).toString()
            "date" -> cursor.getString(2).orEmpty()
            "boolean" -> when {
                cursor.isNull(3) -> ""
                cursor.getInt(3) == 1 -> "Si"
                else -> "No"
            }

            else -> cursor.getString(0).orEmpty()
        }.trim()

        return if (value.isBlank()) "Sin valor" else value
    }

    private fun bindNullableString(statement: SQLiteStatement, index: Int, value: String?) {
        if (value == null) statement.bindNull(index) else statement.bindString(index, value)
    }

    private fun bindNullableDouble(statement: SQLiteStatement, index: Int, value: Double?) {
        if (value == null) statement.bindNull(index) else statement.bindDouble(index, value)
    }

    private fun bindNullableLong(statement: SQLiteStatement, index: Int, value: Long?) {
        if (value == null) statement.bindNull(index) else statement.bindLong(index, value)
    }

    private fun currentFieldValue(recordId: Long, field: FieldDefinition): String {
        return readableDatabase.rawQuery(
            """
            SELECT value_text, value_number, value_date, value_boolean, value_reference_id
            FROM $TABLE_RECORD_VALUES
            WHERE record_id = ? AND field_id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(recordId.toString(), field.id.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use ""
            readStoredFieldValue(field, cursor)
        }
    }

    private fun loadStoredFieldSnapshot(recordId: Long, fieldId: Long): StoredFieldSnapshot? {
        return readableDatabase.rawQuery(
            """
            SELECT value_text, value_number, value_date, value_boolean, value_reference_id
            FROM $TABLE_RECORD_VALUES
            WHERE record_id = ? AND field_id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(recordId.toString(), fieldId.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            StoredFieldSnapshot(
                textValue = cursor.getString(0),
                numberValue = cursor.getDouble(1).takeIf { !cursor.isNull(1) },
                dateValue = cursor.getString(2),
                booleanValue = cursor.getInt(3).takeIf { !cursor.isNull(3) },
                referenceId = cursor.getLong(4).takeIf { !cursor.isNull(4) }
            )
        }
    }

    private fun isPreparedValueEquivalent(
        stored: StoredFieldSnapshot,
        prepared: PreparedValue
    ): Boolean {
        return stored.textValue == prepared.textValue &&
            stored.numberValue == prepared.numberValue &&
            stored.dateValue == prepared.dateValue &&
            stored.booleanValue == prepared.booleanValue &&
            stored.referenceId == prepared.referenceId
    }

    private fun readStoredFieldValue(field: FieldDefinition, cursor: Cursor): String {
        return when (field.fieldType) {
            "number" -> when {
                !cursor.isNull(1) -> cursor.getDouble(1).toString()
                !cursor.isNull(0) -> cursor.getString(0)
                else -> ""
            }
            "date" -> when {
                !cursor.isNull(2) -> cursor.getString(2)
                !cursor.isNull(0) -> cursor.getString(0)
                else -> ""
            }
            "boolean" -> when {
                !cursor.isNull(3) -> if (cursor.getInt(3) == 1) "Si" else "No"
                !cursor.isNull(0) -> cursor.getString(0)
                else -> ""
            }
            FIELD_TYPE_LIST -> if (cursor.isNull(0)) "" else cursor.getString(0)
            else -> if (cursor.isNull(0)) "" else cursor.getString(0)
        }
    }

    private fun loadExportRecordIds(
        collectionId: Long,
        criterion: ExportCriterion,
        afterRecordId: Long,
        limit: Int
    ): List<Long> {
        val ids = mutableListOf<Long>()
        val selection = buildExportCriteriaSelection(criterion, includeRecordCursor = true)
        readableDatabase.rawQuery(
            """
            SELECT id
            FROM $TABLE_RECORDS
            WHERE $selection
            ORDER BY id ASC
            LIMIT ?
            """.trimIndent(),
            arrayOf(collectionId.toString(), afterRecordId.toString(), limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                ids += cursor.getLong(0)
            }
        }
        return ids
    }

    private fun buildExportCriteriaSelection(
        criterion: ExportCriterion,
        includeRecordCursor: Boolean = false
    ): String {
        val cursorSelection = if (includeRecordCursor) " AND id > ?" else ""
        return when (criterion) {
            ExportCriterion.FULL -> "collection_id = ?$cursorSelection"
            ExportCriterion.UPDATED_LAST_24_HOURS ->
                "collection_id = ?$cursorSelection AND datetime(updated_at) >= datetime('now', '-1 day')"
            ExportCriterion.REVIEWED_LAST_24_HOURS ->
                "collection_id = ?$cursorSelection AND reviewed_at IS NOT NULL AND datetime(reviewed_at) >= datetime('now', '-1 day')"
        }
    }

    private fun updateRecordReviewState(
        db: SQLiteDatabase,
        recordId: Long,
        reviewStatus: String,
        reviewAction: String,
        changedFields: List<String>
    ) {
        db.execSQL(
            """
            UPDATE $TABLE_RECORDS
            SET review_status = ?,
                review_action = ?,
                reviewed_at = CURRENT_TIMESTAMP,
                changed_fields_text = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """.trimIndent(),
            arrayOf(reviewStatus, reviewAction, changedFields.joinToString(","), recordId)
        )
    }

    private fun insertReviewLog(
        db: SQLiteDatabase,
        recordId: Long,
        collectionId: Long,
        actionType: String,
        changedFields: List<String>
    ) {
        db.insertOrThrow(
            TABLE_REVIEW_LOG,
            null,
            ContentValues().apply {
                put("record_id", recordId)
                put("collection_id", collectionId)
                put("action_type", actionType)
                put("changed_fields_text", changedFields.joinToString(","))
            }
        )
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
    }

    companion object {
        private const val DATABASE_NAME = "offline_admin.db"
        private const val DATABASE_VERSION = 13
        private const val TABLE_COLLECTIONS = "collections"
        private const val TABLE_FIELDS = "fields"
        private const val TABLE_RECORDS = "records"
        private const val TABLE_RECORD_VALUES = "record_values"
        private const val TABLE_LOOKUP_INDEX = "record_lookup_index"
        private const val TABLE_UNIQUE_INDEX = "record_unique_index"
        private const val TABLE_REVIEW_LOG = "record_review_log"
        private const val QUERY_ROLE_DEFAULT = "default"
        private const val QUERY_ROLE_EXACT = "exact"
        private const val QUERY_ROLE_FLEXIBLE = "flexible"
        private const val FIELD_TYPE_LIST = "list"
        private const val OPTION_DISPLAY_ROLE_NONE = "none"
        private const val OPTION_DISPLAY_ROLE_PRIMARY = "primary"
        private const val OPTION_DISPLAY_ROLE_SUPPORT = "support"
    }
}

private data class RecordMeta(
    val createdAt: String,
    val updatedAt: String,
    val reviewStatus: String,
    val reviewAction: String,
    val reviewedAt: String,
    val changedFieldsText: String
)

private data class ExportRecordMeta(
    val recordId: Long,
    val createdAt: String,
    val updatedAt: String,
    val reviewStatus: String,
    val reviewAction: String,
    val reviewedAt: String,
    val changedFieldsText: String
)

private data class PreparedValue(
    val fieldId: Long,
    val fieldDisplayName: String,
    val textValue: String? = null,
    val numberValue: Double? = null,
    val dateValue: String? = null,
    val booleanValue: Int? = null,
    val referenceId: Long? = null,
    val isLookupKey: Boolean = false,
    val isFlexibleSearch: Boolean = false,
    val isOptionAutocompleteField: Boolean = false,
    val isUniqueValue: Boolean = false,
    val lookupRawValue: String? = null,
    val uniqueRawValue: String? = null
)

private data class StoredFieldSnapshot(
    val textValue: String? = null,
    val numberValue: Double? = null,
    val dateValue: String? = null,
    val booleanValue: Int? = null,
    val referenceId: Long? = null
)

data class CollectionOption(
    val id: Long,
    val displayName: String,
    val slug: String,
    val isMaster: Boolean = false,
    val isOptions: Boolean = false
) {
    override fun toString(): String {
        return displayName
    }

    companion object {
        val EMPTY = CollectionOption(-1, "Sin colecciones", "", false, false)
    }
}

data class FieldDefinition(
    val id: Long,
    val collectionId: Long,
    val displayName: String,
    val slug: String,
    val fieldType: String,
    val queryRole: String = "default",
    val isUniqueValue: Boolean = false,
    val isRequiredValue: Boolean = false,
    val optionSourceCollectionId: Long? = null,
    val optionDisplayRole: String = "none"
) {
    val isLookupKey: Boolean
        get() = queryRole == "exact"

    val isFlexibleSearch: Boolean
        get() = queryRole == "flexible"

    val isOptionAutocompleteField: Boolean
        get() = optionDisplayRole == "primary" || optionDisplayRole == "support"
}

data class RecordPreview(
    val recordId: Long,
    val createdAt: String,
    val updatedAt: String,
    var title: String,
    val values: LinkedHashMap<String, String>
)

data class AssetRecordDetail(
    val recordId: Long,
    val collectionId: Long,
    val createdAt: String,
    val updatedAt: String,
    val reviewStatus: String,
    val reviewAction: String,
    val reviewedAt: String,
    val changedFieldsText: String,
    val fields: List<AssetFieldValue>
)

data class AssetFieldValue(
    val fieldId: Long,
    val fieldDisplayName: String,
    val fieldSlug: String,
    val fieldType: String,
    val isLookupKey: Boolean,
    val isUniqueValue: Boolean,
    val isRequiredValue: Boolean,
    val value: String,
    val optionSourceCollectionId: Long? = null
)

data class OptionSuggestion(
    val recordId: Long,
    val displayLabel: String,
    val selectedValue: String
) {
    override fun toString(): String = displayLabel
}

data class DashboardSummary(
    val totalTables: Int,
    val totalRecords: Int,
    val tableCards: List<TableDashboardCard>
)

data class ReviewStatusDashboardSummary(
    val totalCount: Int = 0,
    val pendingCount: Int = 0,
    val confirmedCount: Int = 0,
    val updatedCount: Int = 0
)

data class GroupedDashboardCard(
    val valueLabel: String,
    val recordCount: Int
)

data class TableDashboardCard(
    val collectionId: Long,
    val displayName: String,
    val slug: String,
    val recordCount: Int
)

data class CollectionCard(
    val id: Long,
    val displayName: String,
    val slug: String,
    val description: String,
    val isMaster: Boolean,
    val isOptions: Boolean,
    val fieldCount: Int,
    val lookupFieldCount: Int,
    val uniqueFieldCount: Int,
    val lookupUniqueFieldCount: Int
)

data class FieldCard(
    val id: Long,
    val collectionId: Long,
    val displayName: String,
    val slug: String,
    val fieldType: String,
    val queryRole: String,
    val isUniqueValue: Boolean,
    val isRequiredValue: Boolean,
    val optionSourceCollectionId: Long? = null,
    val optionSourceCollectionName: String = "",
    val optionDisplayRole: String = "none"
) {
    val isLookupKey: Boolean
        get() = queryRole == "exact"

    val isFlexibleSearch: Boolean
        get() = queryRole == "flexible"
}

data class ImportConflict(
    val rowNumber: Int,
    val reason: ImportConflictReason,
    val fieldName: String,
    val value: String
)

data class BatchImportResult(
    val insertedRecords: Int,
    val conflicts: List<ImportConflict>
) {
    companion object {
        val EMPTY = BatchImportResult(0, emptyList())
    }
}

enum class ImportConflictReason {
    DUPLICATE,
    INVALID_VALUE,
    OTHER
}

class ImportValueException(
    val reason: ImportConflictReason,
    val fieldName: String,
    val value: String,
    override val message: String
) : IllegalStateException(message)
