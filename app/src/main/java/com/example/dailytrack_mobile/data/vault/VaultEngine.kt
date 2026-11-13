package com.example.dailytrack_mobile.data.vault

import com.example.dailytrack_mobile.data.remote.dto.VAsset
import com.example.dailytrack_mobile.data.remote.dto.VEquity
import com.example.dailytrack_mobile.data.remote.dto.VFund
import com.example.dailytrack_mobile.data.remote.dto.VFundTx
import com.example.dailytrack_mobile.data.remote.dto.VFundValue
import com.example.dailytrack_mobile.data.remote.dto.VMf
import com.example.dailytrack_mobile.data.remote.dto.VSip
import com.example.dailytrack_mobile.data.remote.dto.VSnapshot
import com.example.dailytrack_mobile.data.remote.dto.VStock
import com.example.dailytrack_mobile.data.remote.dto.VStockTrade
import com.example.dailytrack_mobile.data.remote.dto.VStockValue
import com.example.dailytrack_mobile.data.remote.dto.VTables
import com.example.dailytrack_mobile.data.remote.dto.VTask
import okhttp3.HttpUrl
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * With the private vault on, the server can't read investments, so this does on
 * the phone what it did: answers the same Investments requests, in the same
 * shapes, from the opened vault. It follows the web's vault/engine.js, which
 * follows the server (invest.py, funds.py, funds_service.py, kite_daily.py);
 * keep them in step.
 *
 * What only the server can reach (AMFI's NAVs, stock prices, reading a CAS PDF)
 * it asks for by name, never with amounts (blueprints/vault.py).
 */
@Singleton
class VaultEngine @Inject constructor(
    private val store: VaultStore,
    private val kiteMode: KiteModeSource,
) {
    class Answer(val code: Int, val json: String)
    private class Refused(message: String, val status: Int = 400) : Exception(message)

    // ---- days and times (IST, as the server keeps them) ----

    private val ist = ZoneId.of("Asia/Kolkata")
    private val stampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    private fun today() = LocalDate.now(ist).toString()
    private fun now() = LocalDateTime.now(ist).format(stampFormat)
    private fun stamp(s: String) = LocalDateTime.parse(s.take(19))
    private fun day(s: String) = LocalDate.parse(s.take(10))
    private fun clock(s: String) = s.substring(11, 16)
    private fun daysBetween(a: String, b: String) = ChronoUnit.DAYS.between(day(a), day(b))
    /** relativedelta(months=k, day=day): the month moved, the day kept (or set), short months clamped. */
    private fun addMonths(d: String, k: Long, dayOfMonth: Int? = null): String {
        val moved = day(d).plusMonths(k)
        return (if (dayOfMonth == null) moved else moved.withDayOfMonth(minOf(dayOfMonth, moved.lengthOfMonth()))).toString()
    }
    private fun weekend(d: String) = day(d).dayOfWeek.let { it == DayOfWeek.SATURDAY || it == DayOfWeek.SUNDAY }
    private fun minDay(a: String, b: String) = if (a < b) a else b
    private fun isDay(v: String?) = v != null && runCatching { LocalDate.parse(v).toString() == v }.getOrDefault(false)

    private fun r2(x: Double) = Math.round(x * 100.0) / 100.0
    private fun r3(x: Double) = Math.round(x * 1000.0) / 1000.0
    private val Double?.z get() = this ?: 0.0

    private var lastId = 0L
    /** A new row's id: unique across everyone's rows, so turning the vault off puts it back as it is. */
    @Synchronized private fun newId(): Long {
        lastId = maxOf(lastId + 1, System.currentTimeMillis() * 1000)
        return lastId
    }

    /** Python's str() of a number in a key (funds_service._key), so re-imports match keys made elsewhere:
     *  the shortest digits, plain between 1e-4 and 1e16, else as 5e-05. */
    private fun pyStr(v: Double?): String {
        if (v == null) return "None"
        if (v == Math.floor(v) && abs(v) < 1e16) return "${v.toLong()}.0"
        val bd = java.math.BigDecimal(v.toString()).stripTrailingZeros()
        if (abs(v) >= 1e-4) return bd.toPlainString()
        val digits = bd.unscaledValue().abs().toString()
        val exp = digits.length - 1 - bd.scale()
        val mantissa = digits.take(1) + if (digits.length > 1) "." + digits.drop(1) else ""
        return "${if (v < 0) "-" else ""}${mantissa}e${if (exp < 0) "-" else "+"}${"%02d".format(abs(exp))}"
    }
    private fun fundKey(vararg parts: Any?) = VaultCrypto.sha1Hex(parts.joinToString("|") { it.toString() })

    // ---- what only the server can reach ----

    private class Scheme(val code: String, val name: String, val fullName: String, val amc: String?, val isins: List<String>, val nav: Double, val date: String)
    private class Navs(val at: Long, val byCode: Map<String, Scheme>, val byIsin: Map<String, Scheme>, val newest: String)

    @Volatile private var navCache: Navs? = null
    private val history = mutableMapOf<String, Pair<String, List<Pair<String, Double>>>>()

    private fun navs(fresh: Boolean = false): Navs {
        navCache?.let { if (!fresh && System.currentTimeMillis() - it.at < 10 * 60 * 1000) return it }
        return try {
            val rows = store.call("GET", "/api/vault/navs" + if (fresh) "?refresh=1" else "").getJSONArray("navs")
            val byCode = HashMap<String, Scheme>()
            val byIsin = HashMap<String, Scheme>()
            var newest = ""
            for (i in 0 until rows.length()) {
                val r = rows.getJSONArray(i)
                val isins = r.getJSONArray(4).let { a -> (0 until a.length()).map { a.getString(it) } }
                val s = Scheme(r.getString(0), r.getString(1), r.getString(2), r.optString(3).takeIf { !r.isNull(3) }, isins, r.getDouble(5), r.getString(6))
                byCode[s.code] = s
                isins.forEach { byIsin[it] = s }
                if (s.date > newest) newest = s.date
            }
            Navs(System.currentTimeMillis(), byCode, byIsin, newest).also { navCache = it }
        } catch (e: Exception) {
            navCache ?: throw Refused(e.message ?: "Couldn't reach AMFI for today's NAVs", 503)
        }
    }

    private fun navHistory(code: String): List<Pair<String, Double>> {
        val today = today()
        synchronized(history) { history[code]?.let { if (it.first == today) return it.second } }
        val rows = try {
            store.call("GET", "/api/vault/nav-history/" + URLEncoder.encode(code, "UTF-8")).getJSONArray("navs")
        } catch (_: Exception) {
            throw Refused("Couldn't fetch past NAVs", 503)
        }
        val list = (0 until rows.length()).map { rows.getJSONArray(it).let { r -> r.getString(0) to r.getDouble(1) } }
        synchronized(history) { history[code] = today to list }
        return list
    }

    // ---- funds held outside Kite (funds_service.py) ----

    private val stampDuty = 0.00005
    private val manualFolio = "manual"
    private val manualGroups = mapOf("fixed" to listOf("FD", "RD", "Cash"), "provident" to listOf("EPF", "PPF", "NPS"), "gold" to listOf("SGB", "RealEstate"))

    private fun cleanName(name: String) = listOf("Direct", "Regular", "Growth", "IDCW", "Dividend")
        .fold(name) { n, w -> n.replace(Regex("\\s*-\\s*$w.*", RegexOption.IGNORE_CASE), "") }.trim()

    private fun purchaseUnits(amount: Double, nav: Double) = r3(amount * (1 - stampDuty) / nav)

    private fun navOn(code: String, day: String): Pair<String, Double>? {
        val latest = navs().byCode[code]
        if (latest != null && latest.date == day) return day to latest.nav
        navHistory(code).firstOrNull { it.first >= day }?.let { return it }
        if (latest != null && latest.date >= day) return latest.date to latest.nav
        return null
    }

    private fun nextInstalment(sip: VSip, after: String) = addMonths(after, 1, sip.day)
    private fun firstInstalment(dayOfMonth: Int, since: String): String {
        val first = addMonths(since, 0, dayOfMonth)
        return if (first >= since) first else addMonths(first, 1, dayOfMonth)
    }

    private fun processSips(t: VTables, today: String): Int {
        var added = 0
        for (sip in t.sips.filter { it.active == true && it.next_date <= today }) {
            val fund = t.funds.find { it.id == sip.fund_id }
            if (fund == null) { sip.active = false; continue }
            var i = 0
            while (i++ < 600 && sip.next_date <= today) {
                val priced = navOn(fund.amfi_code, sip.next_date) ?: break
                if (priced.first > today) break
                val units = purchaseUnits(sip.amount.z, priced.second)
                t.fund_tx.add(VFundTx(newId(), fund.id, sip.next_date, "PURCHASE_SIP", sip.amount, units, priced.second, "sip",
                    fundKey("sip", fund.id, sip.next_date)))
                fund.units = r3(fund.units.z + units)
                fund.invested = fund.invested.z + sip.amount.z
                sip.next_date = nextInstalment(sip, sip.next_date)
                added++
            }
        }
        return added
    }

    private fun lastKiteDay(t: VTables, day: String): String? =
        (t.equity.map { it.date } + t.mf.map { it.date }).filter { it <= day }.maxOrNull()

    private fun newSnapshot(day: String) = VSnapshot(newId(), day, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false)

    private fun setManualTotals(s: VSnapshot, manual: List<VAsset>) {
        fun total(group: String, current: Boolean) = manual.filter { it.category in manualGroups.getValue(group) }
            .sumOf { if (current) it.current_value.z else it.invested_value.z }
        s.total_fixed_income_inv = total("fixed", false); s.total_fixed_income_curr = total("fixed", true)
        s.total_provident_inv = total("provident", false); s.total_provident_curr = total("provident", true)
        s.total_gold_inv = total("gold", false); s.total_gold_curr = total("gold", true)
        s.grand_total_inv = s.total_equity_inv.z + s.total_mf_inv.z + s.total_fixed_income_inv.z + s.total_provident_inv.z + s.total_gold_inv.z
        s.grand_total_curr = s.total_equity_curr.z + s.total_mf_curr.z + s.total_fixed_income_curr.z + s.total_provident_curr.z + s.total_gold_curr.z
        s.synced = false
    }

    /** funds_service.recompute_snapshot: the day's totals from Kite's latest sync, funds and stocks held outside it, and manual assets. */
    private fun recomputeSnapshot(t: VTables, day: String): VSnapshot? {
        val kiteDay = lastKiteDay(t, day)
        val equity = if (kiteDay != null) t.equity.filter { it.date == kiteDay } else emptyList()
        val kiteFunds = if (kiteDay != null) t.mf.filter { it.date == kiteDay } else emptyList()
        val fundDay = lastFundDay(t, day)
        val own = if (fundDay != null) t.fund_values.filter { it.date == fundDay } else emptyList()
        val stockDay = lastStockDay(t, day)
        val ownStocks = if (stockDay != null) t.stock_values.filter { it.date == stockDay } else emptyList()
        var snap = t.snapshots.find { it.date == day }
        if (kiteDay != null && kiteDay != day && fundDay != day && stockDay != day) {
            if (snap != null && day == today()) t.snapshots.remove(snap)
            return null
        }
        if (snap == null) {
            if (equity.isEmpty() && kiteFunds.isEmpty() && own.isEmpty() && ownStocks.isEmpty() && t.assets.isEmpty()) return null
            snap = newSnapshot(day).also { t.snapshots.add(it) }
        }
        snap.total_equity_inv = equity.sumOf { it.invested_value } + ownStocks.sumOf { it.invested }
        snap.total_equity_curr = equity.sumOf { it.current_value } + ownStocks.sumOf { it.value }
        snap.total_mf_inv = kiteFunds.sumOf { it.invested_value } + own.sumOf { it.invested.z }
        snap.total_mf_curr = kiteFunds.sumOf { it.current_value } + own.sumOf { it.value.z }
        setManualTotals(snap, t.assets)
        return snap
    }

    /** The latest day on or before [day] with values for funds held outside Kite: they stand until the next. */
    private fun lastFundDay(t: VTables, day: String): String? = t.fund_values.map { it.date }.filter { it <= day }.maxOrNull()

    /** funds_service.value_funds: every fund held at its latest NAV, filed under the day those NAVs are for. */
    private fun valueFunds(t: VTables, today: String): String {
        val byCode = navs().byCode
        val held = linkedMapOf<String, Triple<String, Double, Double>>()
        for (f in t.funds.filter { it.units.z > 0 }) {
            val row = held[f.amfi_code] ?: Triple(f.name, 0.0, 0.0)
            held[f.amfi_code] = Triple(row.first, row.second + f.units.z, row.third + f.invested.z)
        }
        val priced = linkedMapOf<String, Pair<Double, String?>>()
        for (code in held.keys) {
            val scheme = byCode[code]
            if (scheme != null) priced[code] = scheme.nav to scheme.date
            else t.fund_values.filter { it.amfi_code == code }.maxByOrNull { it.date }?.let { priced[code] = it.nav.z to it.nav_date }
        }
        val navDay = minDay(priced.values.mapNotNull { it.second }.maxOrNull() ?: today, today)
        val last = lastFundDay(t, today)
        // A later day already filed holds what's held now: the NAVs bring it up to date.
        val day = if (last != null) (if (navDay > last) navDay else last) else navDay
        val previous = if (last != null) t.fund_values.filter { it.date == last }.associateBy { it.amfi_code } else emptyMap()
        t.fund_values.removeAll { it.date == day }
        for ((code, row) in held) {
            val (nav, navDate) = priced[code] ?: continue
            t.fund_values.add(VFundValue(newId(), day, code, row.first, r3(row.second), nav, navDate, r2(row.third), r2(row.second * nav)))
        }
        // Sold out since: a zero that day, so the days after don't carry it on.
        for ((code, before) in previous) {
            if (before.units.z > 0 && code !in held) {
                t.fund_values.add(VFundValue(newId(), day, code, before.name, 0.0, before.nav, before.nav_date, 0.0, 0.0))
            }
        }
        recomputeSnapshot(t, day)
        for (later in t.snapshots.filter { it.date > day }.map { it.date }) {
            if (lastFundDay(t, later) == day) recomputeSnapshot(t, later)
        }
        return day
    }

    // ---- tidying records filed before values went under their NAV's day ----

    /** A record's stocks and funds from what was held on [day]; its manual assets as recorded (funds_service._held_totals). */
    private fun heldTotals(t: VTables, snap: VSnapshot, day: String) {
        val kiteDay = lastKiteDay(t, day)
        val fundDay = lastFundDay(t, day)
        val stockDay = lastStockDay(t, day)
        val equity = if (kiteDay != null) t.equity.filter { it.date == kiteDay } else emptyList()
        val kiteFunds = if (kiteDay != null) t.mf.filter { it.date == kiteDay } else emptyList()
        val own = if (fundDay != null) t.fund_values.filter { it.date == fundDay } else emptyList()
        val stocks = if (stockDay != null) t.stock_values.filter { it.date == stockDay } else emptyList()
        snap.total_equity_inv = equity.sumOf { it.invested_value } + stocks.sumOf { it.invested }
        snap.total_equity_curr = equity.sumOf { it.current_value } + stocks.sumOf { it.value }
        snap.total_mf_inv = kiteFunds.sumOf { it.invested_value } + own.sumOf { it.invested.z }
        snap.total_mf_curr = kiteFunds.sumOf { it.current_value } + own.sumOf { it.value.z }
        snap.grand_total_inv = snap.total_equity_inv.z + snap.total_mf_inv.z + snap.total_fixed_income_inv.z + snap.total_provident_inv.z + snap.total_gold_inv.z
        snap.grand_total_curr = snap.total_equity_curr.z + snap.total_mf_curr.z + snap.total_fixed_income_curr.z + snap.total_provident_curr.z + snap.total_gold_curr.z
        snap.synced = false
    }

    /** funds_service.tidy_fund_days: each day's fund values once filed under the day the app was opened go to their NAV's day. */
    private fun tidyFundDays(t: VTables): Int {
        val navDayOf = t.fund_values.filter { it.units.z > 0 && it.nav_date != null }.groupBy { it.date }
            .mapValues { (_, rows) -> rows.mapNotNull { it.nav_date }.max() }
        val late = navDayOf.filter { (d, n) -> n < d }.keys.sorted()
        for (day in late) {
            val moved = t.fund_values.filter { it.date == day }
            val target = moved.filter { it.units.z > 0 }.mapNotNull { it.nav_date }.max()
            t.fund_values.removeAll { it.date == target }
            moved.forEach { it.date = target }
            val busy = t.equity.any { it.date == day } || t.mf.any { it.date == day } || t.stock_values.any { it.date == day }
            var left = t.snapshots.find { it.date == day }
            var there = t.snapshots.find { it.date == target }
            if (there == null && left != null && !busy) {
                left.date = target          // the record itself moves to its NAV's day
                there = left
                left = null
            } else if (there == null && left != null) {
                there = newSnapshot(target).apply {
                    total_fixed_income_inv = left.total_fixed_income_inv; total_fixed_income_curr = left.total_fixed_income_curr
                    total_provident_inv = left.total_provident_inv; total_provident_curr = left.total_provident_curr
                    total_gold_inv = left.total_gold_inv; total_gold_curr = left.total_gold_curr
                }
                t.snapshots.add(there)
            }
            there?.let { heldTotals(t, it, target) }
            left?.let { if (busy) heldTotals(t, it, day) else t.snapshots.remove(it) }
        }
        return late.size
    }

    /** Funds held outside Kite as they stood on [day]: their last values on or before it, held ones. */
    private fun ownFunds(t: VTables, day: String): List<VFundValue> {
        val d = lastFundDay(t, day) ?: return emptyList()
        return t.fund_values.filter { it.date == d && it.units.z > 0 }
    }

    /** The day of the latest NAV the funds held are valued at (kite_daily._funds_nav_day). */
    private fun fundsNavDay(t: VTables, kiteDay: String?, daily: Boolean, today: String): String? {
        val days = ownFunds(t, today).mapNotNull { it.nav_date }.toMutableList()
        val cache = navCache
        if (kiteDay != null && daily && cache != null) {
            t.mf.filter { it.date == kiteDay }.forEach { f -> f.isin?.let { cache.byIsin[it] }?.let { days.add(it.date) } }
        }
        return days.maxOrNull()
    }

    private fun refreshFunds(t: VTables, fresh: Boolean): Boolean {
        if (t.funds.none { it.units.z > 0 } && t.sips.none { it.active == true }) return false
        val today = today()
        val due = t.sips.any { it.active == true && it.next_date <= today }
        val lastDay = lastFundDay(t, today)
        val valued = if (lastDay != null) t.fund_values.filter { it.date == lastDay && it.units.z > 0 } else emptyList()
        val byCode = navs(fresh).byCode
        if (valued.isNotEmpty() && !due && valued.none { (byCode[it.amfi_code]?.date ?: it.nav_date.orEmpty()) > it.nav_date.orEmpty() }) return false
        processSips(t, today)
        valueFunds(t, today)
        return true
    }

    private fun kiteTrackedNames(t: VTables): Set<String> {
        val day = t.mf.maxOfOrNull { it.date } ?: return emptySet()
        return t.mf.filter { it.date == day }.map { cleanName(it.symbol).lowercase() }.toSet()
    }

    private fun fundsOverview(t: VTables): JSONArray {
        val byCode = runCatching { navs().byCode }.getOrElse { navCache?.byCode ?: emptyMap() }
        val sips = t.sips.filter { it.active == true }.groupBy { it.fund_id }
        class Row(val code: String, val name: String, val amc: String?) {
            var units = 0.0; var invested = 0.0
            val folios = mutableListOf<String>(); val sold = mutableListOf<String>()
            val sources = sortedSetOf<String>(); val sips = JSONArray()
        }
        val grouped = linkedMapOf<String, Row>()
        for (f in t.funds.sortedBy { it.name }) {
            val row = grouped.getOrPut(f.amfi_code) { Row(f.amfi_code, f.name, f.amc) }
            row.units += f.units.z
            row.invested += f.invested.z
            f.folio_tail?.let { (if (f.units.z > 0) row.folios else row.sold).add(it) }
            f.source?.let { row.sources.add(it) }
            sips[f.id].orEmpty().forEach { s ->
                row.sips.put(JSONObject().put("id", s.id).put("amount", s.amount.z).put("day", s.day).put("next", s.next_date))
            }
        }
        val out = grouped.values.map { row ->
            val scheme = byCode[row.code]
            val value = scheme?.let { r2(row.units * it.nav) }
            JSONObject()
                .put("code", row.code).put("name", row.name).put("amc", row.amc ?: JSONObject.NULL)
                .put("units", r3(row.units)).put("invested", r2(row.invested))
                .put("folios", JSONArray(row.folios.ifEmpty { row.sold })).put("sources", JSONArray(row.sources.toList()))
                .put("sips", row.sips)
                .put("nav", scheme?.nav ?: JSONObject.NULL).put("nav_date", scheme?.date ?: JSONObject.NULL)
                .put("value", value ?: JSONObject.NULL) to (row.units to value)
        }.sortedWith(compareBy<Pair<JSONObject, Pair<Double, Double?>>> { it.second.first <= 0 }.thenByDescending { it.second.second ?: 0.0 })
        return JSONArray(out.map { it.first })
    }

    // ---- Kite, repriced daily (kite_daily.py) ----

    private fun isStale(pricedAt: String?, now: String): Boolean {
        if (pricedAt == null || pricedAt.take(10) != now.take(10)) return true
        if (weekend(now) || clock(pricedAt) >= "15:45") return false
        return Duration.between(stamp(pricedAt), stamp(now)).toMinutes() >= 30
    }

    private fun marketOpen(now: String) = !weekend(now) && clock(now) >= "09:15" && clock(now) <= "15:30"

    private fun closedToday(quoted: String?, seen: String?, now: String) =
        marketOpen(now) && quoted != null && seen != null && quoted.take(10) < now.take(10) &&
            seen.take(10) == now.take(10) && clock(seen) >= "09:30"

    private fun repriceIfDue(t: VTables, fresh: Boolean): Boolean {
        if (kiteMode.mode() != "daily") return false
        val now = now()
        val today = now.take(10)
        val pricedAt = t.state.priced_at
        val day = lastKiteDay(t, today) ?: return false
        val equity = t.equity.filter { it.date == day }
        val funds = t.mf.filter { it.date == day }
        val allRepriced = equity.all { it.repriced == true } && funds.all { it.repriced == true }
        if (day == today && !allRepriced) return false
        val nav = if (funds.isNotEmpty()) runCatching { navs(fresh) }.getOrNull() else null
        fun quote(f: VMf) = f.isin?.let { nav?.byIsin?.get(it) }?.let { it.nav to it.date }
        val asked = fresh && (pricedAt == null || Duration.between(stamp(pricedAt), stamp(now)).seconds >= 60)
        if (!asked && !isStale(pricedAt, now) && funds.none { f -> quote(f)?.let { it.first != f.nav } == true }) return false

        var prices = emptyMap<String, Double>()
        var quoted: String? = null
        if (equity.isNotEmpty()) {
            val symbols = equity.map { it.symbol }.toSortedSet().joinToString(",")
            runCatching { store.call("GET", "/api/vault/quotes?symbols=" + URLEncoder.encode(symbols, "UTF-8")) }.getOrNull()?.let { r ->
                val p = r.optJSONObject("prices")
                prices = p?.keys()?.asSequence()?.associate { it to p.getDouble(it) } ?: emptyMap()
                quoted = r.optString("at").takeIf { !r.isNull("at") && it.isNotEmpty() }
            }
        }
        val quotes = funds.associateWith { quote(it) }
        if (prices.isEmpty() && quotes.values.none { it != null }) return false
        val navDay = quotes.values.filterNotNull().maxOfOrNull { it.second }
        val pricedOn = minDay(quoted?.take(10) ?: navDay ?: today, today)
        val target = if (pricedOn < day || (pricedOn == day && !allRepriced)) null else pricedOn

        if (target != null) {
            val rows = equity.map { e ->
                val ltp = prices[e.symbol] ?: e.ltp
                e.copy(id = newId(), date = target, ltp = ltp, current_value = e.quantity * ltp, repriced = true)
            }
            val fundRows = funds.map { f ->
                val fnav = quotes[f]?.first ?: f.nav
                f.copy(id = newId(), date = target, nav = fnav, current_value = f.quantity * fnav, repriced = true)
            }
            t.equity.removeAll { it.date == target && it.repriced == true }
            t.mf.removeAll { it.date == target && it.repriced == true }
            t.equity.addAll(rows)
            t.mf.addAll(fundRows)
            recomputeSnapshot(t, target)
            // Later days standing on these holdings take the new prices too.
            for (later in t.snapshots.filter { it.date > target }.map { it.date }) {
                if (lastKiteDay(t, later) == target) recomputeSnapshot(t, later)
            }
        }
        t.state.priced_at = now
        quoted?.let { t.state.quoted_at = it.take(19) }
        if (prices.isNotEmpty()) t.state.quotes_seen_at = now
        return true
    }

    private fun priceTimes(t: VTables): JSONObject {
        val mode = kiteMode.mode()
        val daily = mode == "daily"
        val now = now()
        val synced = (t.equity.filter { it.repriced != true }.map { it.date } + t.mf.filter { it.repriced != true }.map { it.date }).maxOrNull()
        val quoted = t.state.quoted_at
        val closed = daily && closedToday(quoted, t.state.quotes_seen_at, now)
        val day = lastKiteDay(t, now.take(10))
        val held = day != null && t.equity.any { it.date == day }
        val fundsAt = fundsNavDay(t, day, daily, now.take(10))
        val own = t.state.stocks_quoted_at
        if (synced == null && own != null && t.stocks.any { it.quantity > 0 }) {
            // No Kite, stocks added by hand: when their prices are from.
            val shut = closedToday(own, t.state.stocks_priced_at, now)
            return JSONObject().put("mode", "daily").put("synced", JSONObject.NULL).put("stocks_at", own)
                .put("checked_at", t.state.stocks_priced_at ?: JSONObject.NULL)
                .put("stale", marketOpen(now) && !shut && Duration.between(stamp(own), stamp(now)).toMinutes() > 45)
                .put("closed_today", shut)
                .put("funds_at", fundsAt ?: JSONObject.NULL)
        }
        return JSONObject()
            .put("funds_at", fundsAt ?: JSONObject.NULL)
            .put("mode", mode)
            .put("synced", synced ?: JSONObject.NULL)
            .put("stocks_at", if (quoted != null && daily) quoted else JSONObject.NULL)
            .put("checked_at", if (daily && t.state.priced_at != null) t.state.priced_at else JSONObject.NULL)
            .put("stale", daily && held && marketOpen(now) && !closed && (quoted == null || Duration.between(stamp(quoted), stamp(now)).toMinutes() > 45))
            .put("closed_today", held && closed)
    }

    // ---- stocks and ETFs added by hand (stocks_service.py) ----

    private class Listed(val symbol: String, val name: String, val isin: String?)

    @Volatile private var stockList: Pair<Long, List<Listed>>? = null

    private fun listedStocks(): List<Listed> {
        stockList?.let { if (System.currentTimeMillis() - it.first < 60 * 60 * 1000) return it.second }
        return try {
            val rows = store.call("GET", "/api/vault/stock-list").getJSONArray("stocks")
            val list = (0 until rows.length()).map { i ->
                rows.getJSONArray(i).let { r -> Listed(r.getString(0), r.getString(1), if (r.isNull(2)) null else r.getString(2)) }
            }
            stockList = System.currentTimeMillis() to list
            list
        } catch (e: Exception) {
            stockList?.second ?: throw Refused(e.message ?: "Couldn't reach NSE's list of stocks", 503)
        }
    }

    private fun lastStockDay(t: VTables, day: String): String? = t.stock_values.map { it.date }.filter { it <= day }.maxOrNull()

    private val longDay = DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH)

    /** Quantity and invested (average cost) from the trades, oldest first; a sale of more than was held then can't be. */
    private fun replayStock(stock: VStock, trades: List<VStockTrade>) {
        var quantity = 0.0
        var invested = 0.0
        for (x in trades.sortedWith(compareBy<VStockTrade>({ it.date }, { it.id }))) {
            if (x.kind == "BUY") {
                quantity += x.quantity
                invested += x.quantity * x.price
            } else {
                if (x.quantity > quantity + 1e-9) {
                    val held = java.math.BigDecimal(quantity).round(java.math.MathContext(6)).stripTrailingZeros().toPlainString()
                    throw Refused("You held $held ${stock.symbol} on ${day(x.date).format(longDay)}")
                }
                invested -= if (quantity != 0.0) invested / quantity * x.quantity else 0.0
                quantity -= x.quantity
                if (quantity <= 1e-9) { quantity = 0.0; invested = 0.0 }
            }
        }
        stock.quantity = Math.round(quantity * 1e6) / 1e6
        stock.invested = r2(invested)
    }

    private fun stockTrade(t: VTables, stock: VStock, kind: String, quantity: Double, price: Double, onDay: String) {
        if (kind != "BUY" && kind != "SELL") throw Refused("Buy or sell?")
        if (!(quantity > 0 && quantity <= 10_000_000)) throw Refused("Enter how many")
        if (!(price > 0 && price <= 10_000_000)) throw Refused("Enter the price")
        if (onDay > today()) throw Refused("That date is in the future")
        t.stock_trades.add(VStockTrade(newId(), stock.id, onDay, kind, quantity, price))
        replayStock(stock, t.stock_trades.filter { it.stock_id == stock.id })
    }

    /** Prices what's held and files it under the day the prices are from (or [on], a day holdings changed). */
    private fun valueStocks(t: VTables, on: String? = null): Boolean {
        val now = now()
        val today = now.take(10)
        val held = t.stocks.filter { it.quantity > 0 }
        var prices = emptyMap<String, Double>()
        var quoted: String? = null
        if (held.isNotEmpty()) {
            val symbols = held.map { it.symbol }.toSortedSet().joinToString(",")
            runCatching { store.call("GET", "/api/vault/quotes?symbols=" + URLEncoder.encode(symbols, "UTF-8")) }.getOrNull()?.let { r ->
                val p = r.optJSONObject("prices")
                prices = p?.keys()?.asSequence()?.associate { it to p.getDouble(it) } ?: emptyMap()
                quoted = r.optString("at").takeIf { !r.isNull("at") && it.isNotEmpty() }
            }
        }
        if (held.isNotEmpty() && prices.isEmpty() && on == null) return false
        val last = lastStockDay(t, today)
        val quoteDay = minDay(quoted?.take(10) ?: today, today)
        // A later day already filed holds what's held now: the prices bring it up to date.
        val target = on ?: if (last != null) (if (quoteDay > last) quoteDay else last) else quoteDay
        val previous = if (last != null) t.stock_values.filter { it.date == last }.associateBy { it.symbol } else emptyMap()
        t.stock_values.removeAll { it.date == target }
        for (s in held) {
            val price = prices[s.symbol]?.takeIf { it != 0.0 } ?: previous[s.symbol]?.price?.takeIf { it != 0.0 } ?: (s.invested.z / s.quantity)
            t.stock_values.add(VStockValue(newId(), target, s.symbol, s.name, s.quantity, price, s.invested.z, r2(s.quantity * price)))
        }
        // Sold out since: a zero that day, so the day after doesn't carry it on.
        val heldNow = held.map { it.symbol }.toSet()
        for ((symbol, before) in previous) {
            if (before.quantity > 0 && symbol !in heldNow) {
                t.stock_values.add(VStockValue(newId(), target, symbol, before.name, 0.0, before.price, 0.0, 0.0))
            }
        }
        recomputeSnapshot(t, target)
        for (later in t.snapshots.filter { it.date > target }.map { it.date }) {
            if (lastStockDay(t, later) == target) recomputeSnapshot(t, later)
        }
        if (prices.isNotEmpty()) t.state.stocks_priced_at = now
        quoted?.let { t.state.stocks_quoted_at = it.take(19) }
        return true
    }

    private fun refreshStocks(t: VTables, fresh: Boolean): Boolean {
        if (t.stocks.none { it.quantity > 0 }) return false
        val now = now()
        val pricedAt = t.state.stocks_priced_at
        val asked = fresh && (pricedAt == null || Duration.between(stamp(pricedAt), stamp(now)).seconds >= 60)
        if (!asked && !isStale(pricedAt, now)) return false
        return valueStocks(t)
    }

    private fun stocksOverview(t: VTables): JSONArray {
        // Each one's latest price, even when its latest day was since deleted.
        val values = HashMap<String, VStockValue>()
        for (v in t.stock_values.filter { it.date <= today() }.sortedBy { it.date }) values[v.symbol] = v
        val trades = t.stock_trades.sortedWith(compareByDescending<VStockTrade> { it.date }.thenByDescending { it.id }).groupBy { it.stock_id }
        val rows = t.stocks.sortedBy { it.symbol }.map { s ->
            val price = values[s.symbol]?.price
            val value = price?.let { r2(s.quantity * it) }
            JSONObject()
                .put("symbol", s.symbol).put("name", s.name).put("quantity", s.quantity).put("invested", r2(s.invested.z))
                .put("avg_price", if (s.quantity != 0.0) r2(s.invested.z / s.quantity) else JSONObject.NULL)
                .put("price", price ?: JSONObject.NULL).put("value", value ?: JSONObject.NULL)
                .put("trades", JSONArray(trades[s.id].orEmpty().map { x ->
                    JSONObject().put("id", x.id).put("date", x.date).put("kind", x.kind).put("quantity", x.quantity).put("price", x.price)
                })) to (s.quantity to value)
        }.sortedWith(compareBy<Pair<JSONObject, Pair<Double, Double?>>> { it.second.first <= 0 }.thenByDescending { it.second.second ?: 0.0 })
        return JSONArray(rows.map { it.first })
    }

    /** Stocks added by hand as they stood on [day], held ones (invest._own_stocks). */
    private fun ownStocks(t: VTables, day: String, withReturn: Boolean = true): List<JSONObject> {
        val d = lastStockDay(t, day) ?: return emptyList()
        return t.stock_values.filter { it.date == d && it.quantity > 0 }.map { s ->
            JSONObject().put("symbol", s.symbol).put("quantity", s.quantity)
                .put("average_price", if (s.quantity != 0.0) (s.invested / s.quantity * 10000).roundToLong() / 10000.0 else 0.0)
                .put("ltp", s.price).put("invested_value", s.invested).put("current_value", s.value)
                .also { if (withReturn) it.put("ret_pct", pct(s.value, s.invested)) }
        }
    }

    // ---- manual assets, RDs and automations (invest.py) ----

    private fun recurringDeposit(installment: Double, rate: Double?, start: String, maturity: String?, today: String): Pair<Double, Double> {
        val end = if (maturity != null) minDay(today, maturity) else today
        var deposited = 0.0
        var worth = 0.0
        for (k in 0 until 1200) {
            val due = addMonths(start, k.toLong())
            if (due > end || (maturity != null && due >= maturity)) break
            val years = daysBetween(due, end) / 365.25
            deposited += installment
            worth += installment * (1 + (rate ?: 0.0) / 400).pow(4 * years)
        }
        return r2(deposited) to r2(worth)
    }

    private fun refreshRDs(t: VTables, today: String): Int {
        var changed = 0
        for (rd in t.assets.filter { it.category == "RD" && it.installment != null && it.start_date != null }) {
            val (deposited, worth) = recurringDeposit(rd.installment!!, rd.interest_rate, rd.start_date!!, rd.maturity_date, today)
            if (deposited != r2(rd.invested_value.z) || worth != r2(rd.current_value.z)) {
                rd.invested_value = deposited; rd.current_value = worth; rd.last_updated = today
                changed++
            }
        }
        return changed
    }

    private fun latestSnapshot(t: VTables) = t.snapshots.maxByOrNull { it.date }

    private fun updateLatestSnapshot(t: VTables) {
        val snap = latestSnapshot(t) ?: if (t.assets.isEmpty()) return else newSnapshot(today()).also { t.snapshots.add(it) }
        setManualTotals(snap, t.assets)
    }

    /** The cron the web runs on opening: interest on FDs, automations that are due, RDs. */
    private fun processRecurring(t: VTables, today: String): Int {
        var processed = 0
        for (a in t.assets.filter { it.interest_rate != null && it.start_date != null && it.category != "RD" }) {
            val end = a.maturity_date?.let { minDay(today, it) } ?: today
            val days = daysBetween(a.start_date!!, end)
            if (days > 0) {
                val value = a.invested_value.z * (1 + a.interest_rate!! / 100 / 4).pow(4 * (days / 365.25))
                if (r2(value) > a.current_value.z) {
                    a.current_value = r2(value); a.last_updated = today
                    processed++
                }
            }
        }
        for (task in t.tasks.filter { it.is_active == true && it.next_run_date != null && it.next_run_date!! <= today }) {
            val asset = t.assets.find { it.name == task.asset_name } ?: continue
            asset.invested_value = asset.invested_value.z + task.amount_to_add.z
            if (asset.interest_rate == null || asset.interest_rate == 0.0) asset.current_value = asset.current_value.z + task.amount_to_add.z
            asset.last_updated = today
            val n = (task.interval_value ?: 1).toLong()
            task.next_run_date = when (task.interval_unit) {
                "days" -> day(task.next_run_date!!).plusDays(n).toString()
                "years" -> addMonths(task.next_run_date!!, 12 * n)
                else -> addMonths(task.next_run_date!!, n)
            }
            processed++
        }
        processed += refreshRDs(t, today)
        if (processed > 0) updateLatestSnapshot(t)
        return processed
    }

    private fun pct(curr: Double, inv: Double) = if (inv > 0) (curr - inv) / inv * 100 else 0.0

    private fun snapshotRow(r: VSnapshot) = JSONObject()
        .put("id", r.id).put("date", r.date)
        .put("inv_stocks", r.total_equity_inv.z).put("curr_stocks", r.total_equity_curr.z)
        .put("inv_mf", r.total_mf_inv.z).put("curr_mf", r.total_mf_curr.z)
        .put("total_inv", r.grand_total_inv.z).put("total_curr", r.grand_total_curr.z)
        .put("inv_fixed", r.total_fixed_income_inv.z).put("curr_fixed", r.total_fixed_income_curr.z)
        .put("inv_prov", r.total_provident_inv.z).put("curr_prov", r.total_provident_curr.z)
        .put("inv_gold", r.total_gold_inv.z).put("curr_gold", r.total_gold_curr.z)
        .put("ret_pct_stocks", pct(r.total_equity_curr.z, r.total_equity_inv.z))
        .put("ret_pct_mf", pct(r.total_mf_curr.z, r.total_mf_inv.z))
        .put("total_ret_pct", pct(r.grand_total_curr.z, r.grand_total_inv.z))
        .put("status_stocks", "").put("status_mf", "").put("total_status", "")

    // ---- CAS statements ----

    private fun importStatement(t: VTables, cas: JSONObject, today: String): JSONObject {
        val period = cas.getJSONObject("period")
        val periodTo = period.getString("to")
        val kite = kiteTrackedNames(t)
        val nav = navs()
        val added = JSONArray(); val updated = JSONArray(); val closed = JSONArray(); val skipped = JSONArray(); val unknown = JSONArray()
        val folios = cas.optJSONArray("folios") ?: JSONArray()
        for (fi in 0 until folios.length()) {
            val folio = folios.getJSONObject(fi)
            val ref = folio.getString("ref")
            val schemes = folio.optJSONArray("schemes") ?: JSONArray()
            for (si in 0 until schemes.length()) {
                val s = schemes.getJSONObject(si)
                val scheme = nav.byCode[s.optString("amfi")] ?: nav.byIsin[s.optString("isin")]
                if (scheme == null) { unknown.put(s.opt("scheme") ?: JSONObject.NULL); continue }
                if (scheme.name.lowercase() in kite) { skipped.put(scheme.name); continue }
                var fund = t.funds.find { it.amfi_code == scheme.code && it.folio_ref == ref }
                val isNew = fund == null
                if (fund == null) {
                    fund = VFund(newId(), scheme.code, null, scheme.name, null, ref, null, 0.0, 0.0, "cas", null, null)
                    t.funds.add(fund)
                }
                fund.name = scheme.name
                fund.isin = s.optString("isin").takeIf { !s.isNull("isin") && it.isNotEmpty() } ?: fund.isin
                fund.amc = folio.optString("amc").takeIf { !folio.isNull("amc") && it.isNotEmpty() } ?: scheme.amc
                fund.folio_tail = folio.optString("tail").takeIf { !folio.isNull("tail") }
                fund.source = "cas"
                fund.statement_to = fund.statement_to?.takeIf { it > periodTo } ?: periodTo
                fund.updated_at = now()
                val existing = t.fund_tx.filter { it.fund_id == fund.id }.map { it.key }.toMutableSet()
                val seen = mutableMapOf<String, Int>()
                val txs = s.optJSONArray("transactions") ?: JSONArray()
                for (xi in 0 until txs.length()) {
                    val x = txs.getJSONObject(xi)
                    val amount = if (x.isNull("amount")) null else x.getDouble("amount")
                    val units = if (x.isNull("units")) null else x.getDouble("units")
                    val ident = listOf(x.getString("date"), x.getString("kind"), pyStr(amount), pyStr(units)).joinToString("|")
                    seen[ident] = (seen[ident] ?: 0) + 1   // two identical rows on one day are both real
                    val key = fundKey("cas", ref, scheme.code, x.getString("date"), x.getString("kind"), pyStr(amount), pyStr(units), seen.getValue(ident))
                    if (!existing.add(key)) continue
                    t.fund_tx.add(VFundTx(newId(), fund.id, x.getString("date"), x.getString("kind"), amount, units,
                        if (x.isNull("nav")) null else x.getDouble("nav"), "cas", key))
                }
                // The statement is the truth up to its last day; later SIP instalments stay on top.
                t.fund_tx.removeAll { it.fund_id == fund.id && it.source == "sip" && it.date <= periodTo }
                val later = t.fund_tx.filter { it.fund_id == fund.id && it.source == "sip" && it.date > periodTo }
                val close = s.getDouble("close")
                fund.units = r3(close + later.sumOf { it.units.z })
                fund.invested = (if (s.isNull("cost")) fund.invested.z else s.getDouble("cost")) + later.sumOf { it.amount.z }
                // Added by hand before: the statement has all of it now.
                t.funds.find { it.amfi_code == scheme.code && it.folio_ref == manualFolio }?.let { byHand ->
                    t.sips.filter { it.fund_id == byHand.id }.forEach { it.fund_id = fund.id }
                    t.fund_tx.removeAll { it.fund_id == byHand.id }
                    t.funds.remove(byHand)
                }
                if (close <= 0 && later.isEmpty()) {
                    fund.invested = 0.0
                    closed.put(scheme.name)
                } else (if (isNew) added else updated).put(scheme.name)
            }
        }
        valueFunds(t, today)
        return JSONObject().put("added", added).put("updated", updated).put("closed", closed)
            .put("skipped_kite", skipped).put("unknown", unknown).put("period", period)
    }

    // ---- requests ----

    private val maxAmount = 10_00_00_000.0
    private fun parseAmount(v: Any?): Double? {
        val n = when (v) { is Number -> v.toDouble(); is String -> v.trim().toDoubleOrNull(); else -> null } ?: return null
        if (n.isNaN() || n.isInfinite()) return null
        val a = r2(n)
        return if (a > 0 && a <= maxAmount) a else null
    }
    private fun parseDay(v: Any?) = (v as? String)?.takeIf { isDay(it) }

    private fun sipFields(body: JSONObject): Triple<Double, Int, String> {
        val amount = parseAmount(body.opt("amount"))
        val dayOfMonth = body.opt("day").let { (it as? Number)?.toInt() ?: (it as? String)?.toIntOrNull() ?: 0 }
        val since = parseDay(body.opt("since")) ?: today()
        if (amount == null) throw Refused("Enter the SIP amount")
        if (dayOfMonth !in 1..31) throw Refused("Pick the SIP day (1–31)")
        if (since > today()) throw Refused("A SIP can't start in the future")
        return Triple(amount, dayOfMonth, since)
    }

    private fun startSip(t: VTables, fund: VFund, sip: Triple<Double, Int, String>) {
        t.sips.add(VSip(newId(), fund.id, sip.first, sip.second, firstInstalment(sip.second, sip.third), true))
    }

    private fun stocksDone(t: VTables, today: String, extra: JSONObject = JSONObject()): JSONObject {
        valueStocks(t, today)
        return extra.put("success", true).put("stocks", stocksOverview(t)).put("quoted_at", t.state.stocks_quoted_at ?: JSONObject.NULL)
    }

    private fun removeStock(t: VTables, stock: VStock) {
        t.stock_trades.removeAll { it.stock_id == stock.id }
        t.stock_values.removeAll { it.symbol == stock.symbol }
        t.stocks.remove(stock)
    }

    /** (day, quantity, price) from a trade's request: no day is today; a bad one is refused. */
    private fun tradeFields(json: JSONObject, today: String): Triple<String, Double, Double> {
        val raw = if (json.isNull("date")) null else json.opt("date")?.toString()
        val onDay = if (raw.isNullOrEmpty()) today else raw.takeIf { isDay(it) } ?: throw Refused("That isn't a date")
        fun num(k: String) = json.opt(k).let { (it as? Number)?.toDouble() ?: (it as? String)?.toDoubleOrNull() }?.takeIf { !it.isNaN() } ?: 0.0
        return Triple(onDay, num("quantity"), num("price"))
    }

    private fun ok(json: Any) = Answer(200, json.toString())

    /**
     * The answer to one Investments request from the opened vault, or null when it
     * isn't one of them. [body]: the request's, as sent.
     */
    fun handle(method: String, url: HttpUrl, body: RequestBody?, bodyText: String?): Answer? {
        val path = url.encodedPath.removePrefix("/api")
        val readOnly = store.readOnly
        val read = { store.tables }
        val today = today()
        val json = bodyText?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        fun done(t: VTables, extra: JSONObject = JSONObject()): JSONObject {
            valueFunds(t, today)
            return extra.put("success", true).put("funds", fundsOverview(t))
        }
        return try {
            when {
                // ---- portfolio ----
                method == "GET" && path == "/investments" -> {
                    if (!readOnly) {
                        val fresh = url.queryParameter("refresh") == "1"
                        // The phone has no separate start-up call, so the web's cron runs here too.
                        runCatching { store.transact { t -> tidyFundDays(t) } }
                        runCatching { store.transact { t -> processRecurring(t, today) } }
                        runCatching { store.transact { t -> repriceIfDue(t, fresh) } }
                        runCatching { store.transact { t -> refreshFunds(t, fresh) } }
                        runCatching { store.transact { t -> refreshStocks(t, fresh) } }
                        runCatching { store.transact { t -> if (refreshRDs(t, today) > 0) updateLatestSnapshot(t) } }
                    }
                    ok(JSONArray(read().snapshots.sortedByDescending { it.date }.map { snapshotRow(it) }))
                }
                method == "GET" && path == "/investments/prices" -> ok(priceTimes(read()))
                method == "GET" && path == "/investments/history" -> {
                    val t = read()
                    val symbol = url.queryParameter("symbol")
                    val type = url.queryParameter("type")
                    fun point(date: String, current: Double, invested: Double) = JSONObject().put("date", date).put("Current", current).put("Invested", invested)
                    ok(JSONArray(when {
                        symbol == null || type == null -> emptyList()
                        type == "EQUITY" -> (t.equity.filter { it.symbol == symbol }.map { Triple(it.date, it.current_value, it.invested_value) } +
                            t.stock_values.filter { it.symbol == symbol }.map { Triple(it.date, it.value, it.invested) })
                            .sortedBy { it.first }.map { point(it.first, it.second, it.third) }
                        type == "MF" -> (t.mf.filter { it.symbol == symbol }.map { Triple(it.date, it.current_value, it.invested_value) } +
                            t.fund_values.filter { it.name == symbol }.map { Triple(it.date, it.value.z, it.invested.z) })
                            .sortedBy { it.first }.map { point(it.first, it.second, it.third) }
                        else -> t.assets.find { it.name == symbol }?.let { a ->
                            listOf(point(a.last_updated ?: today, a.current_value.z, a.invested_value.z), point(today, a.current_value.z, a.invested_value.z))
                        } ?: emptyList()
                    }))
                }
                method == "GET" && path == "/equity" -> {
                    val t = read()
                    val day = t.equity.maxOfOrNull { it.date }
                    ok(JSONArray((if (day == null) emptyList() else t.equity.filter { it.date == day }.map { h ->
                        JSONObject().put("symbol", h.symbol).put("quantity", h.quantity).put("average_price", h.average_price)
                            .put("ltp", h.ltp).put("invested_value", h.invested_value).put("current_value", h.current_value)
                    }) + ownStocks(t, today, withReturn = false)))
                }
                method == "GET" && Regex("^/investments/(\\d{4}-\\d{2}-\\d{2})/(equity_holdings|holdings)$").matches(path) -> {
                    val (d, kind) = Regex("^/investments/(\\d{4}-\\d{2}-\\d{2})/(equity_holdings|holdings)$").find(path)!!.destructured
                    val t = read()
                    val kiteDay = lastKiteDay(t, d)
                    if (kind == "equity_holdings") {
                        ok(JSONArray(t.equity.filter { it.date == kiteDay }.map { h ->
                            JSONObject().put("symbol", h.symbol).put("quantity", h.quantity).put("average_price", h.average_price)
                                .put("ltp", h.ltp).put("invested_value", h.invested_value).put("current_value", h.current_value)
                                .put("ret_pct", pct(h.current_value, h.invested_value))
                        } + ownStocks(t, d)))
                    } else {
                        ok(JSONArray(t.mf.filter { it.date == kiteDay }.map { h ->
                            JSONObject().put("symbol", h.symbol).put("quantity", h.quantity).put("average_price", h.average_price)
                                .put("nav", h.nav).put("invested_value", h.invested_value).put("current_value", h.current_value)
                                .put("ret_pct", pct(h.current_value, h.invested_value))
                        } + ownFunds(t, d).map { f ->
                            JSONObject().put("symbol", f.name).put("quantity", f.units.z)
                                .put("average_price", if (f.units.z != 0.0) (f.invested.z / f.units.z * 10000).roundToLong() / 10000.0 else 0.0)
                                .put("nav", f.nav.z).put("invested_value", f.invested.z).put("current_value", f.value.z)
                                .put("ret_pct", pct(f.value.z, f.invested.z))
                        }))
                    }
                }
                method == "DELETE" && Regex("^/investments/[^/]+$").matches(path) -> {
                    val d = path.substringAfterLast('/')
                    if (!isDay(d)) throw Refused("That isn't a date")
                    ok(store.transact { t ->
                        if (t.snapshots.none { it.date == d }) throw Refused("No snapshot that day", 404)
                        t.snapshots.removeAll { it.date == d }; t.equity.removeAll { it.date == d }
                        t.mf.removeAll { it.date == d }; t.fund_values.removeAll { it.date == d }; t.stock_values.removeAll { it.date == d }
                        JSONObject().put("success", true)
                    })
                }

                // ---- manual assets ----
                method == "GET" && path == "/manual_assets" -> {
                    val t = read()
                    val tasks = t.tasks.associateBy { it.asset_name }
                    ok(JSONArray(t.assets.sortedWith(compareBy({ it.category }, { it.name })).map { a ->
                        val task = tasks[a.name]
                        JSONObject().put("id", a.id).put("category", a.category).put("name", a.name)
                            .put("invested_value", a.invested_value.z).put("current_value", a.current_value.z)
                            .put("interest_rate", a.interest_rate ?: JSONObject.NULL).put("start_date", a.start_date ?: JSONObject.NULL)
                            .put("maturity_date", a.maturity_date ?: JSONObject.NULL).put("last_updated", a.last_updated ?: JSONObject.NULL)
                            .put("installment", a.installment ?: JSONObject.NULL).put("is_recurring", task != null)
                            .put("amount_to_add", task?.amount_to_add ?: JSONObject.NULL).put("interval_value", task?.interval_value ?: JSONObject.NULL)
                            .put("interval_unit", task?.interval_unit ?: JSONObject.NULL).put("next_run_date", task?.next_run_date ?: JSONObject.NULL)
                    }))
                }
                method == "POST" && path == "/manual_assets" -> ok(store.transact { t ->
                    fun num(k: String) = json.opt(k).let { (it as? Number)?.toDouble() ?: (it as? String)?.toDoubleOrNull() }
                    val invested = num("invested_value") ?: 0.0
                    val asset = VAsset(newId(), json.getString("category"), json.getString("name"), invested,
                        (num("current_value") ?: 0.0).takeIf { it != 0.0 } ?: invested,
                        num("interest_rate")?.takeIf { it != 0.0 }, parseDay(json.opt("start_date")), parseDay(json.opt("maturity_date")), today, null)
                    if (asset.category == "RD") {
                        val installment = num("installment") ?: 0.0
                        if (installment <= 0) throw Refused("Enter the monthly instalment")
                        val start = asset.start_date ?: throw Refused("Enter the date of the first instalment")
                        asset.installment = installment
                        val (deposited, worth) = recurringDeposit(installment, asset.interest_rate, start, asset.maturity_date, today)
                        asset.invested_value = deposited; asset.current_value = worth
                    }
                    t.assets.add(asset)
                    if (json.optBoolean("is_recurring") && asset.category != "RD") {
                        t.tasks.add(VTask(newId() + 1, asset.name, num("amount_to_add"), (num("interval_value") ?: 1.0).toInt(),
                            json.optString("interval_unit").ifEmpty { "months" }, parseDay(json.opt("next_run_date")), true))
                    }
                    updateLatestSnapshot(t)
                    JSONObject().put("success", true).put("message", "Asset & Automation added successfully")
                })
                method == "POST" && path == "/sync/investments-to-sheets" ->
                    ok(JSONObject().put("success", false).put("message", "Your investments are in your private vault, so they aren't sent to Google Sheets."))

                // ---- stocks and ETFs added by hand (stocks.py) ----
                method == "GET" && path == "/stocks" -> {
                    if (!readOnly) runCatching { store.transact { t -> refreshStocks(t, false) } }
                    val t = read()
                    ok(JSONObject().put("success", true).put("stocks", stocksOverview(t)).put("quoted_at", t.state.stocks_quoted_at ?: JSONObject.NULL))
                }
                method == "GET" && path == "/stocks/search" -> {
                    val q = url.queryParameter("q").orEmpty().trim()
                    if (q.length < 2) return ok(JSONObject().put("success", true).put("results", JSONArray()))
                    val words = q.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    val upper = q.uppercase()
                    val hits = listedStocks().filter { r -> words.all { w -> r.symbol.lowercase().contains(w) || r.name.lowercase().contains(w) } }
                        .sortedWith(compareBy({ it.symbol != upper }, { !it.symbol.startsWith(upper) }, { it.name.lowercase() }))
                    ok(JSONObject().put("success", true).put("results", JSONArray(hits.take(20).map { r ->
                        JSONObject().put("symbol", r.symbol).put("name", r.name).put("isin", r.isin ?: JSONObject.NULL)
                    })))
                }
                method == "POST" && path == "/stocks" -> {
                    val wanted = json.optString("symbol").trim().uppercase()
                    val listed = listedStocks().find { it.symbol == wanted } ?: throw Refused("Pick a stock from the search")
                    ok(store.transact { t ->
                        val kiteDay = t.equity.maxOfOrNull { it.date }
                        if (kiteDay != null && t.equity.any { it.date == kiteDay && it.symbol.uppercase() == listed.symbol }) throw Refused("Kite already tracks this stock", 409)
                        val stock = t.stocks.find { it.symbol == listed.symbol }
                            ?: VStock(newId(), listed.symbol, listed.name, listed.isin, 0.0, 0.0, now()).also { t.stocks.add(it) }
                        val (onDay, quantity, price) = tradeFields(json, today)
                        stockTrade(t, stock, "BUY", quantity, price, onDay)
                        stocksDone(t, today, JSONObject().put("added", stock.symbol))
                    })
                }
                method == "POST" && Regex("^/stocks/[^/]+/trades$").matches(path) -> {
                    val symbol = java.net.URLDecoder.decode(path.removePrefix("/stocks/").removeSuffix("/trades"), "UTF-8").uppercase()
                    ok(store.transact { t ->
                        val stock = t.stocks.find { it.symbol == symbol } ?: throw Refused("No such stock", 404)
                        val (onDay, quantity, price) = tradeFields(json, today)
                        stockTrade(t, stock, json.optString("kind").uppercase(), quantity, price, onDay)
                        stocksDone(t, today)
                    })
                }
                method == "DELETE" && Regex("^/stocks/[^/]+/trades/\\d+$").matches(path) -> {
                    val parts = path.split("/")
                    val symbol = java.net.URLDecoder.decode(parts[2], "UTF-8").uppercase()
                    val id = parts[4].toLong()
                    ok(store.transact { t ->
                        val stock = t.stocks.find { it.symbol == symbol }
                        if (stock == null || t.stock_trades.none { it.id == id && it.stock_id == stock.id }) throw Refused("No such trade", 404)
                        t.stock_trades.removeAll { it.id == id }
                        val left = t.stock_trades.filter { it.stock_id == stock.id }
                        try {
                            replayStock(stock, left)
                        } catch (e: Refused) {
                            throw Refused("${e.message}, so that buy can't go")
                        }
                        if (left.isEmpty()) removeStock(t, stock)
                        stocksDone(t, today)
                    })
                }
                method == "DELETE" && Regex("^/stocks/[^/]+$").matches(path) -> {
                    val symbol = java.net.URLDecoder.decode(path.removePrefix("/stocks/"), "UTF-8").uppercase()
                    ok(store.transact { t ->
                        val stock = t.stocks.find { it.symbol == symbol } ?: throw Refused("No such stock", 404)
                        removeStock(t, stock)
                        stocksDone(t, today)
                    })
                }

                // ---- funds held outside Kite (funds.py) ----
                method == "GET" && path == "/funds" -> {
                    if (!readOnly) runCatching { store.transact { t -> refreshFunds(t, false) } }
                    ok(JSONObject().put("success", true).put("funds", fundsOverview(read())))
                }
                method == "GET" && path == "/funds/search" -> {
                    val q = url.queryParameter("q").orEmpty().trim()
                    if (q.length < 2) return ok(JSONObject().put("success", true).put("results", JSONArray()))
                    val nav = navs()
                    val words = q.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    val live = day(nav.newest.ifEmpty { today }).minusDays(30).toString()
                    val hits = nav.byCode.values.filter { s ->
                        s.date >= live && words.all { w -> s.fullName.lowercase().contains(w) || s.amc.orEmpty().lowercase().contains(w) }
                    }.sortedWith(compareBy({ !it.fullName.lowercase().contains("direct") }, { !it.fullName.lowercase().contains("growth") }, { it.fullName }))
                    ok(JSONObject().put("success", true).put("results", JSONArray(hits.take(20).map { s ->
                        JSONObject().put("code", s.code).put("name", s.fullName).put("amc", s.amc ?: JSONObject.NULL).put("nav", s.nav).put("nav_date", s.date)
                    })))
                }
                method == "POST" && path == "/funds" -> {
                    val scheme = navs().byCode[json.optString("code")] ?: throw Refused("Pick a fund from the search")
                    ok(store.transact { t ->
                        if (scheme.name.lowercase() in kiteTrackedNames(t)) throw Refused("Kite already tracks this fund", 409)
                        // What's held already, as a fund app shows it: units at an average NAV.
                        fun given(k: String) = json.has(k) && !json.isNull(k) && json.opt(k) != ""
                        fun positive(k: String, most: Double) = json.opt(k).let { (it as? Number)?.toDouble() ?: (it as? String)?.toDoubleOrNull() }
                            ?.takeIf { given(k) && !it.isNaN() && it > 0 && it <= most }
                        val holding = given("units") || given("avg_nav")
                        val units = positive("units", 1_00_00_00_000.0)
                        val avgNav = positive("avg_nav", 10_00_000.0)
                        if (holding && units == null) throw Refused("Enter the units you hold")
                        if (holding && avgNav == null) throw Refused("Enter the average NAV")
                        val lumpsum = !json.isNull("amount") && json.has("amount") && json.opt("amount") != ""
                        val amount = parseAmount(json.opt("amount"))
                        val d = parseDay(json.opt("date")) ?: today
                        if (lumpsum && amount == null) throw Refused("Enter the amount invested")
                        if (lumpsum && d > today) throw Refused("That date is in the future")
                        val sip = json.optJSONObject("sip")?.let { sipFields(it) }
                        if (!lumpsum && !holding && sip == null) throw Refused("Enter what you hold, an amount, or a SIP")
                        // A SIP belongs with the statement's folio when there is one.
                        val fund = (if (sip != null && !lumpsum && !holding) t.funds.filter { it.amfi_code == scheme.code && it.folio_ref != manualFolio }
                            .maxByOrNull { it.units.z } else null)
                            ?: t.funds.find { it.amfi_code == scheme.code && it.folio_ref == manualFolio }
                            ?: VFund(newId(), scheme.code, scheme.isins.firstOrNull(), scheme.name, scheme.amc, manualFolio, null, 0.0, 0.0,
                                "manual", null, now()).also { t.funds.add(it) }
                        if (holding && units != null && avgNav != null) {
                            val held = r2(units * avgNav)
                            t.fund_tx.add(VFundTx(newId(), fund.id, today, "HOLDING", held, units, avgNav, "manual",
                                fundKey("holding", fund.id, today, pyStr(units), pyStr(avgNav), System.currentTimeMillis() / 1000.0)))
                            fund.units = r3(fund.units.z + units)
                            fund.invested = fund.invested.z + held
                        }
                        if (lumpsum && amount != null) {
                            val priced = navOn(fund.amfi_code, d) ?: throw Refused("That day's NAV isn't out yet", 503)
                            val units = purchaseUnits(amount, priced.second)
                            t.fund_tx.add(VFundTx(newId(), fund.id, d, "PURCHASE", amount, units, priced.second, "manual",
                                fundKey("manual", fund.id, d, pyStr(amount), System.currentTimeMillis() / 1000.0)))
                            fund.units = r3(fund.units.z + units)
                            fund.invested = fund.invested.z + amount
                        }
                        if (sip != null) {
                            startSip(t, fund, sip)
                            processSips(t, today)
                        }
                        done(t, JSONObject().put("added", scheme.name))
                    })
                }
                method == "DELETE" && Regex("^/funds/sips/\\d+$").matches(path) -> {
                    val id = path.substringAfterLast('/').toLong()
                    ok(store.transact { t ->
                        val sip = t.sips.find { it.id == id } ?: throw Refused("No such SIP", 404)
                        sip.active = false
                        JSONObject().put("success", true).put("funds", fundsOverview(t))
                    })
                }
                method == "POST" && Regex("^/funds/[^/]+/sip$").matches(path) -> {
                    val code = java.net.URLDecoder.decode(path.removePrefix("/funds/").removeSuffix("/sip"), "UTF-8")
                    val sip = sipFields(json)
                    ok(store.transact { t ->
                        val fund = t.funds.filter { it.amfi_code == code }
                            .sortedWith(compareBy<VFund> { it.folio_ref == manualFolio }.thenByDescending { it.units.z }).firstOrNull()
                            ?: throw Refused("No such fund", 404)
                        startSip(t, fund, sip)
                        processSips(t, today)
                        done(t)
                    })
                }
                method == "POST" && path == "/funds/import" -> {
                    // The PDF is read on the server and handed straight back (blueprints/vault.py).
                    val cas = try {
                        store.call("POST", "/api/vault/cas", body).getJSONObject("cas")
                    } catch (e: VaultHttpError) {
                        return Answer(if (e.status >= 400) e.status else 400, JSONObject().put("success", false).put("message", e.message).toString())
                    }
                    ok(store.transact { t ->
                        importStatement(t, cas, today).put("success", true).put("funds", fundsOverview(t))
                    })
                }
                method == "DELETE" && Regex("^/funds/[^/]+$").matches(path) -> {
                    val code = java.net.URLDecoder.decode(path.removePrefix("/funds/"), "UTF-8")
                    ok(store.transact { t ->
                        val ids = t.funds.filter { it.amfi_code == code }.map { it.id }.toSet()
                        if (ids.isEmpty()) throw Refused("No such fund", 404)
                        t.sips.removeAll { it.fund_id in ids }
                        t.fund_tx.removeAll { it.fund_id in ids }
                        t.fund_values.removeAll { it.amfi_code == code }
                        t.funds.removeAll { it.id in ids }
                        done(t)
                    })
                }
                else -> null
            }
        } catch (e: Refused) {
            Answer(e.status, JSONObject().put("success", false).put("message", e.message).toString())
        } catch (e: VaultHttpError) {
            Answer(if (e.status >= 400) e.status else 500, JSONObject().put("success", false).put("message", e.message).toString())
        } catch (e: Exception) {
            Answer(500, JSONObject().put("success", false).put("message", e.message ?: "Something went wrong").toString())
        }
    }

    /** What a locked vault answers: empty, so screens still load, and writes wait for the unlock. */
    fun lockedAnswer(method: String, url: HttpUrl): Answer {
        val path = url.encodedPath.removePrefix("/api")
        if (method == "GET") return when (path) {
            "/investments/prices" -> Answer(200, """{"mode":"manual","synced":null,"stocks_at":null,"checked_at":null,"stale":false,"closed_today":false}""")
            "/funds" -> Answer(200, """{"success":true,"funds":[]}""")
            "/funds/search", "/stocks/search" -> Answer(200, """{"success":true,"results":[]}""")
            "/stocks" -> Answer(200, """{"success":true,"stocks":[],"quoted_at":null}""")
            "/assets/list" -> Answer(200, "{}")
            else -> Answer(200, "[]")
        }
        return Answer(423, """{"success":false,"message":"Unlock your private vault first (Investments, 🔒)"}""")
    }

    /** Engine bookkeeping for a signed-out phone. */
    fun forget() {
        navCache = null
        synchronized(history) { history.clear() }
        kiteMode.forget()
    }

    companion object {
        /** The Investments requests the vault answers. */
        fun isInvestPath(path: String) = Regex("^/api/(investments(/|$)|manual_assets(/|$)|assets/list$|equity$|cron/(process-recurring|tasks)(/|$)|sync/(kite|investments-to-sheets)$|funds(/|$)|stocks(/|$))").containsMatchIn(path)
    }
}
