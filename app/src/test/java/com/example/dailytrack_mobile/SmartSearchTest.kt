package com.example.dailytrack_mobile

import com.example.dailytrack_mobile.domain.search.SmartSearch
import com.example.dailytrack_mobile.domain.search.smartSearch
import org.junit.Assert.assertEquals
import org.junit.Test

/** The same cases the web (utils/search.js) and server (tests/test_smart_search.py) check. */
class SmartSearchTest {
    private val items = listOf(
        "Swiggy dinner with Arun", "Zomato lunch", "Good day biscuits",
        "Recharge via super.money", "PAY TM wallet", "Instamart", "Dinner at home",
    )

    private fun find(query: String) = items.smartSearch(query)

    @Test
    fun `words match in any order`() {
        assertEquals(listOf("Swiggy dinner with Arun"), find("arun swiggy"))
    }

    @Test
    fun `part of a word is enough`() {
        assertEquals(listOf("Swiggy dinner with Arun"), find("swig"))
    }

    @Test
    fun `spacing and punctuation dont matter`() {
        assertEquals(listOf("Recharge via super.money"), find("supermoney"))
        assertEquals(listOf("PAY TM wallet"), find("paytm"))
        assertEquals(listOf("Café Coffee Day"), listOf("Café Coffee Day").smartSearch("cafe coffee"))
    }

    @Test
    fun `a typo is forgiven only when nothing matches as typed`() {
        assertEquals(listOf("Swiggy dinner with Arun"), find("swigy"))
        assertEquals(listOf("Zomato lunch"), find("zomtao lunch"))
        assertEquals(listOf("Instamart"), find("instma"))
        assertEquals(emptyList<String>(), find("food"))     // never "Good"
        assertEquals(emptyList<String>(), find("pizza"))
    }

    @Test
    fun `best first, or in their own order`() {
        assertEquals(listOf("Dinner at home", "Swiggy dinner with Arun"), find("dinner"))
        assertEquals(listOf("Swiggy dinner with Arun", "Dinner at home"), SmartSearch.filter(items, "dinner") { it })
        assertEquals(items, find("  "))
    }
}
