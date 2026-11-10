package com.example.dailytrack_mobile.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.abs

/** Each account's currency, for entry forms deep in the tree (provided by MainScreen). */
val LocalAccountCurrencies = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

/** The currency [account] holds; rupees when it isn't known. */
@Composable
fun accountCurrency(account: String?): String =
    account?.let { LocalAccountCurrencies.current[it] } ?: Currencies.HOME

/**
 * Accounts can hold another currency (a USD account beside the rupee ones);
 * mirrors the server's currencies.py. A transaction on one is entered in its
 * currency and stored in rupees too, so budgets and analytics stay in rupees
 * while the account's balance, minimum and card budget stay in its own.
 */
object Currencies {
    const val HOME = "INR"

    /** The offered currencies, rupees first, with how each is written before an amount. */
    val symbols: Map<String, String> = linkedMapOf(
        "INR" to "₹", "USD" to "$", "EUR" to "€", "GBP" to "£", "AED" to "AED ", "SGD" to "S$",
        "AUD" to "A$", "CAD" to "C$", "NZD" to "NZ$", "JPY" to "¥", "CHF" to "CHF ", "HKD" to "HK$",
        "SAR" to "SAR ", "QAR" to "QAR ", "KWD" to "KWD ", "BHD" to "BHD ", "MYR" to "RM ", "THB" to "฿"
    )

    val names: Map<String, String> = mapOf(
        "INR" to "Indian rupee", "USD" to "US dollar", "EUR" to "Euro", "GBP" to "British pound",
        "AED" to "UAE dirham", "SGD" to "Singapore dollar", "AUD" to "Australian dollar",
        "CAD" to "Canadian dollar", "NZD" to "New Zealand dollar", "JPY" to "Japanese yen",
        "CHF" to "Swiss franc", "HKD" to "Hong Kong dollar", "SAR" to "Saudi riyal",
        "QAR" to "Qatari riyal", "KWD" to "Kuwaiti dinar", "BHD" to "Bahraini dinar", "MYR" to "Malaysian ringgit", "THB" to "Thai baht"
    )

    fun symbol(code: String?): String = symbols[code ?: HOME] ?: "${code ?: HOME} "

    fun isForeign(code: String?): Boolean = code != null && code != HOME
}

/** "₹1,250.00", "$12.50", "-AED 40.00": an amount in its currency. */
fun formatMoney(amount: Double, currency: String?, decimals: Int = 2): String {
    val sign = if (amount < 0) "-" else ""
    return "$sign${Currencies.symbol(currency)}${"%,.${decimals}f".format(abs(amount))}"
}
