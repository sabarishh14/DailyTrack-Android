package com.example.dailytrack_mobile.data.local.datastore

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A half-typed transaction, kept so that leaving the form — or the process being
 * killed behind a bank app or a camera — does not cost the user their typing.
 *
 * Only the fields worth restoring are stored: date is left out deliberately, so
 * a draft picked up the next day defaults to that day rather than silently
 * filing the entry under the date it was started.
 */
data class TransactionDraft(
    val type: String,
    val category: String,
    val amount: String,
    val note: String,
    val account: String?,
    val excludeAnalytics: Boolean
) {
    val isEmpty: Boolean
        get() = category.isBlank() && amount.isBlank() && note.isBlank() && !excludeAnalytics
}

@Singleton
class TransactionDraftStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        const val PREFS_NAME = "transaction_draft"
        const val KEY_DRAFT = "pending_draft"

        const val FIELD_TYPE = "type"
        const val FIELD_CATEGORY = "category"
        const val FIELD_AMOUNT = "amount"
        const val FIELD_NOTE = "note"
        const val FIELD_ACCOUNT = "account"
        const val FIELD_EXCLUDE = "exclude"
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    fun read(): TransactionDraft? {
        val raw = prefs.getString(KEY_DRAFT, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val draft = TransactionDraft(
                type = json.optString(FIELD_TYPE, ""),
                category = json.optString(FIELD_CATEGORY, ""),
                amount = json.optString(FIELD_AMOUNT, ""),
                note = json.optString(FIELD_NOTE, ""),
                account = json.optString(FIELD_ACCOUNT, "").takeIf { it.isNotBlank() },
                excludeAnalytics = json.optBoolean(FIELD_EXCLUDE, false)
            )
            draft.takeUnless { it.isEmpty }
        } catch (_: Exception) {
            null
        }
    }

    /** Writing an empty draft clears it, so the form self-cleans as it is emptied. */
    fun write(draft: TransactionDraft) {
        if (draft.isEmpty) {
            clear()
            return
        }
        val json = JSONObject().apply {
            put(FIELD_TYPE, draft.type)
            put(FIELD_CATEGORY, draft.category)
            put(FIELD_AMOUNT, draft.amount)
            put(FIELD_NOTE, draft.note)
            put(FIELD_ACCOUNT, draft.account ?: "")
            put(FIELD_EXCLUDE, draft.excludeAnalytics)
        }
        prefs.edit().putString(KEY_DRAFT, json.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_DRAFT).apply()
    }
}
