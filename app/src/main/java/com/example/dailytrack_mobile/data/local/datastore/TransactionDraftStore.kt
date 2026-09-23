package com.example.dailytrack_mobile.data.local.datastore

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A half-typed transaction, kept so that leaving the form — or the process being
 * killed behind a bank app or a camera — does not cost the user their typing.
 *
 * [date] is only kept when the user picked a day other than the one they were
 * typing on. An entry left on "today" is picked up the next day as that day,
 * rather than silently filing under the date it was started; a backdated one
 * keeps its date.
 */
data class TransactionDraft(
    val type: String,
    val category: String,
    val amount: String,
    val note: String,
    val account: String?,
    val excludeAnalytics: Boolean,
    val date: String? = null
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
        const val KEY_DRAFT = "pending_draft"      // single entry, from before multi-entry
        const val KEY_DRAFTS = "pending_drafts"

        const val FIELD_TYPE = "type"
        const val FIELD_CATEGORY = "category"
        const val FIELD_AMOUNT = "amount"
        const val FIELD_NOTE = "note"
        const val FIELD_ACCOUNT = "account"
        const val FIELD_EXCLUDE = "exclude"
        const val FIELD_DATE = "date"
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    /** Every unsaved entry, in order; empty entries are dropped. */
    fun readAll(): List<TransactionDraft> {
        return try {
            val raw = prefs.getString(KEY_DRAFTS, null)
            if (raw != null) {
                val array = JSONArray(raw)
                (0 until array.length()).mapNotNull { fromJson(array.getJSONObject(it)) }
            } else {
                listOfNotNull(prefs.getString(KEY_DRAFT, null)?.let { fromJson(JSONObject(it)) })
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Writing only empty entries clears the store, so the form self-cleans as it is emptied. */
    fun writeAll(drafts: List<TransactionDraft>) {
        val kept = drafts.filterNot { it.isEmpty }
        if (kept.isEmpty()) {
            clear()
            return
        }
        val array = JSONArray()
        kept.forEach { array.put(toJson(it)) }
        prefs.edit()
            .putString(KEY_DRAFTS, array.toString())
            .remove(KEY_DRAFT)
            .apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_DRAFTS).remove(KEY_DRAFT).apply()
    }

    private fun fromJson(json: JSONObject): TransactionDraft? {
        val draft = TransactionDraft(
            type = json.optString(FIELD_TYPE, ""),
            category = json.optString(FIELD_CATEGORY, ""),
            amount = json.optString(FIELD_AMOUNT, ""),
            note = json.optString(FIELD_NOTE, ""),
            account = json.optString(FIELD_ACCOUNT, "").takeIf { it.isNotBlank() },
            excludeAnalytics = json.optBoolean(FIELD_EXCLUDE, false),
            date = json.optString(FIELD_DATE, "").takeIf { it.isNotBlank() }
        )
        return draft.takeUnless { it.isEmpty }
    }

    private fun toJson(draft: TransactionDraft) = JSONObject().apply {
        put(FIELD_TYPE, draft.type)
        put(FIELD_CATEGORY, draft.category)
        put(FIELD_AMOUNT, draft.amount)
        put(FIELD_NOTE, draft.note)
        put(FIELD_ACCOUNT, draft.account ?: "")
        put(FIELD_EXCLUDE, draft.excludeAnalytics)
        put(FIELD_DATE, draft.date ?: "")
    }
}
