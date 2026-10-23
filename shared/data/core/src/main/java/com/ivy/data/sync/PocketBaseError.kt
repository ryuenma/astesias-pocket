package com.ivy.data.sync

/**
 * Shared classifier for PocketBase HTTP 400 unique-index rejections.
 *
 * PocketBase 0.40 emits `validation_not_unique` for SQL `UNIQUE INDEX`
 * constraints in `indexes` and `validation_invalid_unique` for field-level
 * `unique: true`. Both codes are accepted, gated on at least one of the
 * collection's indexed [fields] appearing in [body] so an unrelated 400
 * validation error is never mistaken for an idempotent duplicate replay.
 */
internal object PocketBaseError {
    fun isUniqueViolation(body: String?, vararg fields: String): Boolean =
        body != null &&
            fields.isNotEmpty() &&
            (
                body.contains("validation_not_unique", ignoreCase = true) ||
                    body.contains("validation_invalid_unique", ignoreCase = true)
                ) &&
            fields.any { field -> body.contains(field, ignoreCase = true) }
}
