package com.rton.expensebucket.data

import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object HistoricalCashRateProvider {
    data class ExchangeRateLookup(
        val rate: Double,
        val debugLines: List<String>
    )

    class ExchangeRateLookupException(
        message: String,
        val debugLines: List<String>,
        cause: Throwable? = null
    ) : IllegalStateException(message, cause)

    private data class QuotedRate(
        val dateTime: LocalDateTime,
        val cashSell: Double
    )

    private val cache = mutableMapOf<String, List<QuotedRate>>()
    private val recentCache = mutableMapOf<String, List<QuotedRate>>()
    private val cacheMutex = Mutex()
    private val datePathFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val dateTokenFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
    private val timeTokenFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val dateTimeTokenFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    private val tableRowRegex = Regex("""<tr[^>]*>(.*?)</tr>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val tableCellRegex = Regex("""<td[^>]*>(.*?)</td>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val rowRegex = Regex(
        """(\d{4}/\d{2}/\d{2})\s+(\d{2}:\d{2}:\d{2}).*?\(([A-Z]{3})\)\s+(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)"""
    )

    suspend fun getExchangeRate(
        fromCurrency: String,
        toCurrency: String,
        dateMillis: Long
    ): Result<Double> = getExchangeRateWithDebug(
        fromCurrency = fromCurrency,
        toCurrency = toCurrency,
        dateMillis = dateMillis
    ).map { it.rate }

    suspend fun getExchangeRateWithDebug(
        fromCurrency: String,
        toCurrency: String,
        dateMillis: Long
    ): Result<ExchangeRateLookup> {
        val debugLines = mutableListOf<String>()
        return try {
            if (fromCurrency == toCurrency) {
                debugLines += "same currency: $fromCurrency -> 1.0"
                return Result.success(ExchangeRateLookup(1.0, debugLines))
            }

            val targetDateTime = Instant.ofEpochMilli(dateMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
            debugLines += "target: $targetDateTime"
            debugLines += "pair: $fromCurrency -> $toCurrency"

            val fromRateInTwd = if (fromCurrency == "TWD") {
                debugLines += "from currency is TWD -> 1.0"
                1.0
            } else {
                getCashSellRateInTwd(fromCurrency, targetDateTime, debugLines)
            }
            val toRateInTwd = if (toCurrency == "TWD") {
                debugLines += "to currency is TWD -> 1.0"
                1.0
            } else {
                getCashSellRateInTwd(toCurrency, targetDateTime, debugLines)
            }

            if (toRateInTwd <= 0.0) {
                error("找不到 $toCurrency 的歷史現鈔匯率")
            }

            val rate = fromRateInTwd / toRateInTwd
            debugLines += "from in TWD: $fromRateInTwd"
            debugLines += "to in TWD: $toRateInTwd"
            debugLines += "result: $fromCurrency/$toCurrency = $rate"
            Result.success(ExchangeRateLookup(rate, debugLines))
        } catch (throwable: Throwable) {
            debugLines += "error: ${throwable.message ?: throwable::class.java.simpleName}"
            Result.failure(
                ExchangeRateLookupException(
                    message = throwable.message ?: "匯率抓取失敗",
                    debugLines = debugLines.toList(),
                    cause = throwable
                )
            )
        }
    }

    fun extractDebugLines(throwable: Throwable): List<String> {
        return (throwable as? ExchangeRateLookupException)?.debugLines
            ?: listOf("error: ${throwable.message ?: throwable::class.java.simpleName}")
    }

    private suspend fun getCashSellRateInTwd(
        currency: String,
        targetDateTime: LocalDateTime,
        debugLines: MutableList<String>
    ): Double {
        repeat(14) { dayOffset ->
            val candidateDate = targetDateTime.toLocalDate().minusDays(dayOffset.toLong())
            val rates = getDailyRates(currency, candidateDate, debugLines)
            debugLines += "daily lookup $currency $candidateDate -> ${rates.size} parsed rows"
            if (rates.isEmpty()) return@repeat

            val matched = if (candidateDate == targetDateTime.toLocalDate()) {
                rates.filter { !it.dateTime.toLocalTime().isAfter(targetDateTime.toLocalTime()) }
                    .maxByOrNull { it.dateTime }
                    ?: rates.maxByOrNull { it.dateTime }
            } else {
                rates.maxByOrNull { it.dateTime }
            }

            if (matched != null && matched.cashSell > 0.0) {
                debugLines += "matched daily $currency at ${matched.dateTime} cashSell=${matched.cashSell}"
                return matched.cashSell
            }
        }

        val recentRates = getRecentRates(currency, debugLines)
        debugLines += "recent lookup $currency -> ${recentRates.size} parsed rows"
        val fallback = recentRates
            .filter { !it.dateTime.isAfter(targetDateTime) && it.cashSell > 0.0 }
            .maxByOrNull { it.dateTime }
            ?: recentRates
                .filter { it.cashSell > 0.0 }
                .maxByOrNull { it.dateTime }

        if (fallback != null) {
            debugLines += "matched recent $currency at ${fallback.dateTime} cashSell=${fallback.cashSell}"
            return fallback.cashSell
        }

        debugLines += "no rate found for $currency near ${targetDateTime.toLocalDate()}"
        error("找不到 $currency 在 ${targetDateTime.toLocalDate()} 附近的歷史現鈔匯率")
    }

    private suspend fun getDailyRates(
        currency: String,
        date: LocalDate,
        debugLines: MutableList<String>
    ): List<QuotedRate> {
        val cacheKey = "$currency:$date"
        cacheMutex.withLock {
            cache[cacheKey]?.let {
                debugLines += "cache hit daily $cacheKey -> ${it.size} rows"
                return it
            }
        }

        val fetched = fetchDailyRates(currency, date, debugLines)
        cacheMutex.withLock {
            cache[cacheKey] = fetched
        }
        return fetched
    }

    private suspend fun getRecentRates(
        currency: String,
        debugLines: MutableList<String>
    ): List<QuotedRate> {
        cacheMutex.withLock {
            recentCache[currency]?.let {
                debugLines += "cache hit recent $currency -> ${it.size} rows"
                return it
            }
        }

        val fetched = fetchRecentRates(currency, debugLines)
        cacheMutex.withLock {
            recentCache[currency] = fetched
        }
        return fetched
    }

    private suspend fun fetchDailyRates(
        currency: String,
        date: LocalDate,
        debugLines: MutableList<String>
    ): List<QuotedRate> =
        withContext(Dispatchers.IO) {
            val primary = fetchRatesFromUrl(
                "https://rate.bot.com.tw/xrt/quote/${date.format(datePathFormatter)}/$currency/cash",
                date,
                debugLines
            )
            if (primary.isNotEmpty()) {
                return@withContext primary
            }

            fetchRatesFromUrl(
                "https://rate.bot.com.tw/xrt/quote/${date.format(datePathFormatter)}/$currency/",
                date,
                debugLines
            )
        }

    private suspend fun fetchRecentRates(
        currency: String,
        debugLines: MutableList<String>
    ): List<QuotedRate> =
        withContext(Dispatchers.IO) {
            val urlString = "https://rate.bot.com.tw/xrt/quote/ltm/$currency/cash"
            debugLines += "request recent: $urlString"
            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "ExpenseBucket/1.0")
            }

            try {
                val responseCode = connection.responseCode
                debugLines += "response recent: $responseCode"
                if (responseCode !in 200..299) {
                    return@withContext emptyList()
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val parsed = parseRatesFromHtml(body)
                debugLines += "parsed recent rows: ${parsed.size}"
                parsed
            } finally {
                connection.disconnect()
            }
        }

    private fun fetchRatesFromUrl(
        urlString: String,
        date: LocalDate,
        debugLines: MutableList<String>
    ): List<QuotedRate> {
        debugLines += "request daily: $urlString"
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "ExpenseBucket/1.0")
        }

        return try {
            val responseCode = connection.responseCode
            debugLines += "response daily: $responseCode"
            if (responseCode !in 200..299) {
                emptyList()
            } else {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val parsed = parseDailyRates(body, date)
                debugLines += "parsed daily rows: ${parsed.size}"
                parsed
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseDailyRates(html: String, expectedDate: LocalDate): List<QuotedRate> {
        val expectedDateToken = expectedDate.format(dateTokenFormatter)
        return parseRatesFromHtml(html)
            .filter { it.dateTime.toLocalDate().format(dateTokenFormatter) == expectedDateToken }
    }

    private fun parseRatesFromHtml(html: String): List<QuotedRate> {
        val parsedFromTable = tableRowRegex.findAll(html)
            .mapNotNull { rowMatch ->
                val cells = tableCellRegex.findAll(rowMatch.groupValues[1])
                    .map { sanitizeHtmlCell(it.groupValues[1]) }
                    .toList()

                if (cells.size < 4) return@mapNotNull null

                val dateTime = runCatching {
                    LocalDateTime.parse(cells[0], dateTimeTokenFormatter)
                }.getOrNull() ?: return@mapNotNull null

                val cashSell = cells[3].toDoubleOrNull() ?: return@mapNotNull null

                QuotedRate(
                    dateTime = dateTime,
                    cashSell = cashSell
                )
            }
            .toList()

        if (parsedFromTable.isNotEmpty()) {
            return parsedFromTable
        }

        val plainText = HtmlCompat.fromHtml(
            html,
            HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toString()

        return rowRegex.findAll(plainText)
            .mapNotNull { match ->
                val rowDate = runCatching {
                    LocalDate.parse(match.groupValues[1], dateTokenFormatter)
                }.getOrNull() ?: return@mapNotNull null

                val parsedTime = runCatching {
                    LocalTime.parse(match.groupValues[2], timeTokenFormatter)
                }.getOrNull() ?: return@mapNotNull null

                val cashSell = match.groupValues[5].toDoubleOrNull() ?: return@mapNotNull null

                QuotedRate(
                    dateTime = LocalDateTime.of(rowDate, parsedTime),
                    cashSell = cashSell
                )
            }
            .toList()
    }

    private fun sanitizeHtmlCell(cellHtml: String): String {
        return HtmlCompat.fromHtml(cellHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace('\u00A0', ' ')
            .trim()
    }
}
