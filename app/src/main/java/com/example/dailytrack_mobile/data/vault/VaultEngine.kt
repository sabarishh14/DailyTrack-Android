package com.example.dailytrack_mobile.data.vault

import com.example.dailytrack_mobile.data.remote.dto.VAsset
import com.example.dailytrack_mobile.data.remote.dto.VEquity
import com.example.dailytrack_mobile.data.remote.dto.VFund
import com.example.dailytrack_mobile.data.remote.dto.VFundTx
import com.example.dailytrack_mobile.data.remote.dto.VFundValue
import com.example.dailytrack_mobile.data.remote.dto.VMf
import com.example.dailytrack_mobile.data.remote.dto.VSip
import com.example.dailytrack_mobile.data.remote.dto.VSnapshot
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

    /** funds_service.recompute_snapshot: the day's totals from Kite's latest sync, funds, and manual assets. */
    private fun recomputeSnapshot(t: VTables, day: String): VSnapshot? {
        val kiteDay = lastKiteDay(t, day)
        val equity = if (kiteDay != null) t.equity.filter { it.date == kiteDay } else emptyList()
        val kiteFunds = if (kiteDay != null) t.mf.filter { it.date == kiteDay } else emptyList()
        val own = t.fund_values.filter { it.date == day }
        var snap = t.snapshots.find { it.date == day }
        if (kiteDay != null && kiteDay != day && own.isEmpty()) {
            if (snap != null && day == today()) t.snapshots.remove(snap)
            return null
        }
        if (snap == null) {
            if (equity.isEmpty() && kiteFunds.isEmpty() && own.isEmpty() && t.assets.isEmpty()) return null
            snap = newSnapshot(day).also { t.snapshots.add(it) }
        }
        snap.total_equity_inv = equity.sumOf { it.invested_value }
        snap.total_equity_curr = equity.sumOf { it.current_value }
        snap.total_mf_inv = kiteFunds.sumOf { it.invested_value } + own.sumOf { it.invested.z }
        snap.total_mf_curr = kiteFunds.sumOf { it.current_value } + own.sumOf { it.value.z }
        setManualTotals(snap, t.assets)
        return snap
    }

    private fun valueFunds(t: VTables, today: String) {
        val byCode = navs().byCode
        val held = linkedMapOf<String, Triple<String, Double, Double>>()
        for (f in t.funds.filter { it.units.z > 0 }) {
            val row = held[f.amfi_code] ?: Triple(f.name, 0.0, 0.0)
            held[f.amfi_code] = Triple(row.first, row.second + f.units.z, row.third + f.invested.z)
        }
        t.fund_values.removeAll { it.date == today }
        for ((code, row) in held) {
            val scheme = byCode[code]
            val (nav, navDate) = if (scheme != null) scheme.nav to scheme.date else {
                val last = t.fund_values.filter { it.amfi_code == code }.maxByOrNull { it.date } ?: continue
                last.nav.z to last.nav_date
            }
            t.fund_values.add(VFundValue(newId(), today, code, row.first, r3(row.second), nav, navDate, r2(row.third), r2(row.second * nav)))
        }
        recomputeSnapshot(t, today)
    }

    private fun refreshFunds(t: VTables, fresh: Boolean): Boolean {
        if (t.funds.none { it.units.z > 0 } && t.sips.none { it.active == true }) return false
        val today = today()
        val due = t.sips.any { it.active == true && it.next_date <= today }
        val valued = t.fund_values.filter { it.date == today }
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
        return JSONObject()
            .put("mode", mode)
            .put("synced", synced ?: JSONObject.NULL)
            .put("stocks_at", if (quoted != null && daily) quoted else JSONObject.NULL)
            .put("checked_at", if (daily && t.state.priced_at != null) t.state.priced_at else JSONObject.NULL)
            .put("stale", daily && held && marketOpen(now) && !closed && (quoted == null || Duration.between(stamp(quoted), stamp(now)).toMinutes() > 45))
            .put("closed_today", held && closed)
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
                        runCatching { store.transact { t -> processRecurring(t, today) } }
                        runCatching { store.transact { t -> repriceIfDue(t, fresh) } }
                        runCatching { store.transact { t -> refreshFunds(t, fresh) } }
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
                        type == "EQUITY" -> t.equity.filter { it.symbol == symbol }.sortedBy { it.date }.map { point(it.date, it.current_value, it.invested_value) }
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
                    ok(JSONArray(if (day == null) emptyList() else t.equity.filter { it.date == day }.map { h ->
                        JSONObject().put("symbol", h.symbol).put("quantity", h.quantity).put("average_price", h.average_price)
                            .put("ltp", h.ltp).put("invested_value", h.invested_value).put("current_value", h.current_value)
                    }))
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
                        }))
                    } else {
                        ok(JSONArray(t.mf.filter { it.date == kiteDay }.map { h ->
                            JSONObject().put("symbol", h.symbol).put("quantity", h.quantity).put("average_price", h.average_price)
                                .put("nav", h.nav).put("invested_value", h.invested_value).put("current_value", h.current_value)
                                .put("ret_pct", pct(h.current_value, h.invested_value))
                        } + t.fund_values.filter { it.date == d }.map { f ->
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
                        t.mf.removeAll { it.date == d }; t.fund_values.removeAll { it.date == d }
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
                        val lumpsum = !json.isNull("amount") && json.has("amount") && json.opt("amount") != ""
                        val amount = parseAmount(json.opt("amount"))
                        val d = parseDay(json.opt("date")) ?: today
                        if (lumpsum && amount == null) throw Refused("Enter the amount invested")
                        if (lumpsum && d > today) throw Refused("That date is in the future")
                        val sip = json.optJSONObject("sip")?.let { sipFields(it) }
                        if (!lumpsum && sip == null) throw Refused("Enter an amount, a SIP, or both")
                        // A SIP belongs with the statement's folio when there is one.
                        val fund = (if (sip != null && !lumpsum) t.funds.filter { it.amfi_code == scheme.code && it.folio_ref != manualFolio }
                            .maxByOrNull { it.units.z } else null)
                            ?: t.funds.find { it.amfi_code == scheme.code && it.folio_ref == manualFolio }
                            ?: VFund(newId(), scheme.code, scheme.isins.firstOrNull(), scheme.name, scheme.amc, manualFolio, null, 0.0, 0.0,
                                "manual", null, now()).also { t.funds.add(it) }
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
            "/funds/search" -> Answer(200, """{"success":true,"results":[]}""")
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
        fun isInvestPath(path: String) = Regex("^/api/(investments(/|$)|manual_assets(/|$)|assets/list$|equity$|cron/(process-recurring|tasks)(/|$)|sync/(kite|investments-to-sheets)$|funds(/|$))").containsMatchIn(path)
    }
}
