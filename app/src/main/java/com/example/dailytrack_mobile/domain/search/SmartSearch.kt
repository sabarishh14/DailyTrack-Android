package com.example.dailytrack_mobile.domain.search

import java.text.Normalizer

/**
 * Smart search: the rules every search box follows, here, on the web
 * (frontend/src/utils/search.js) and on the server (backend/smart_search.py).
 * - Words in any order, each matched anywhere: "dinner swiggy" finds "Swiggy dinner".
 * - Case, accents, spaces and punctuation don't matter: "supermoney" finds "super.money".
 * - Part of a word is enough: "swig" finds "Swiggy".
 * - A typo is forgiven in a word of 5+ letters (two in 8+), but only when the word
 *   as typed matches nothing: "swigy" finds "Swiggy", while "food" never finds "good".
 * [rank] puts the best first: the whole text, then its start, whole words, word starts.
 */
object SmartSearch {
    private val marks = Regex("\\p{M}+")
    private val notWord = Regex("[^\\p{L}\\p{N}]+")

    fun normalize(text: String?): String =
        Normalizer.normalize(text.orEmpty(), Normalizer.Form.NFKD).replace(marks, "")
            .lowercase().replace(notWord, " ").trim()

    fun words(text: String?): List<String> = normalize(text).let { if (it.isEmpty()) emptyList() else it.split(' ') }

    private fun typoLimit(word: String) = when {
        word.length >= 8 -> 2
        word.length >= 5 -> 1
        else -> 0
    }

    /** Edits (insert, delete, change, swap two neighbours) from a to b; limit + 1 once it's more. */
    internal fun distance(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var prev2: IntArray? = null
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val row = IntArray(b.length + 1)
            row[0] = i
            for (j in 1..b.length) {
                row[j] = minOf(prev[j] + 1, row[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                if (prev2 != null && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    row[j] = minOf(row[j], prev2[j - 2] + 1)
                }
            }
            if (row.min() > limit) return limit + 1
            prev2 = prev
            prev = row
        }
        return prev[b.length]
    }

    private class Doc(text: String) {
        val flat = normalize(text)
        val words = if (flat.isEmpty()) emptyList() else flat.split(' ')
        val joined = flat.replace(" ", "")
    }

    /** How well one search word matches a text as typed (null: it doesn't). */
    private fun asTyped(word: String, doc: Doc): Int? = when {
        word in doc.words -> 10
        doc.words.any { it.startsWith(word) } -> 8
        word in doc.flat -> 5
        word in doc.joined -> 4
        else -> null
    }

    private fun withTypo(word: String, doc: Doc): Int? {
        val k = typoLimit(word)
        if (k == 0) return null
        return if (doc.words.any {
                distance(word, it, k) <= k || (it.length > word.length && distance(word, it.substring(0, word.length), k) <= k)
            }) 2 else null
    }

    private fun score(query: List<String>, doc: Doc, loose: List<Boolean>): Int? {
        var total = 0
        for (i in query.indices) {
            total += asTyped(query[i], doc) ?: (if (loose[i]) withTypo(query[i], doc) else null) ?: return null
        }
        val phrase = query.joinToString(" ")
        return total + when {
            doc.flat == phrase -> 100
            doc.flat.startsWith(phrase) -> 50
            phrase in doc.flat -> 20
            else -> 0
        }
    }

    private fun <T> scored(items: List<T>, query: String, text: (T) -> String): List<Pair<Int, Int>>? {
        val words = words(query)
        if (words.isEmpty()) return null
        val docs = items.map { Doc(text(it)) }
        val loose = words.map { w -> docs.none { asTyped(w, it) != null } }
        return docs.mapIndexedNotNull { i, doc -> score(words, doc, loose)?.let { i to it } }
    }

    /** The items whose text matches [query], best first (ties keep their order). */
    fun <T> rank(items: List<T>, query: String, text: (T) -> String): List<T> {
        val hits = scored(items, query, text) ?: return items
        return hits.sortedByDescending { it.second }.map { items[it.first] }
    }

    /** The items whose text matches [query], in their own order (a list sorted by date stays so). */
    fun <T> filter(items: List<T>, query: String, text: (T) -> String): List<T> {
        val hits = scored(items, query, text) ?: return items
        return hits.map { items[it.first] }
    }
}

fun List<String>.smartSearch(query: String): List<String> = SmartSearch.rank(this, query) { it }
