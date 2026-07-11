package com.rton.expensebucket.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
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

    private data class CachedRate(
        val quotedDate: LocalDate,
        val cashSell: Double
    )

    private val cache = mutableMapOf<String, CachedRate>()
    private val cacheMutex = Mutex()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

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
        val normalizedFrom = fromCurrency.trim().uppercase()
        val normalizedTo = toCurrency.trim().uppercase()
        return try {
            if (normalizedFrom == normalizedTo) {
                debugLines += "same currency: $normalizedFrom -> 1.0"
                return Result.success(ExchangeRateLookup(1.0, debugLines))
            }

            val targetDate = Instant.ofEpochMilli(dateMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            debugLines += "target date: $targetDate"
            debugLines += "pair: $normalizedFrom -> $normalizedTo"

            val fromRateInTwd = getRateInTwd(normalizedFrom, targetDate, debugLines)
            val toRateInTwd = getRateInTwd(normalizedTo, targetDate, debugLines)
            require(toRateInTwd > 0.0) { "找不到 $normalizedTo 的歷史現鈔匯率" }

            val rate = fromRateInTwd / toRateInTwd
            debugLines += "from in TWD: $fromRateInTwd"
            debugLines += "to in TWD: $toRateInTwd"
            debugLines += "result: $normalizedFrom/$normalizedTo = $rate"
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

    fun extractDebugLines(throwable: Throwable): List<String> =
        (throwable as? ExchangeRateLookupException)?.debugLines
            ?: listOf("error: ${throwable.message ?: throwable::class.java.simpleName}")

    private suspend fun getRateInTwd(
        currency: String,
        targetDate: LocalDate,
        debugLines: MutableList<String>
    ): Double {
        if (currency == "TWD") {
            debugLines += "$currency is TWD -> 1.0"
            return 1.0
        }

        val cacheKey = "$currency:$targetDate"
        cacheMutex.withLock {
            cache[cacheKey]?.let {
                debugLines += "cache hit $cacheKey -> ${it.cashSell} (${it.quotedDate})"
                return it.cashSell
            }
        }

        val fetched = fetchFinMindCashSellRate(currency, targetDate, debugLines)
            ?: error("找不到 $currency 在 $targetDate 附近的歷史現鈔匯率")
        cacheMutex.withLock { cache[cacheKey] = fetched }
        return fetched.cashSell
    }

    private suspend fun fetchFinMindCashSellRate(
        currency: String,
        targetDate: LocalDate,
        debugLines: MutableList<String>
    ): CachedRate? = withContext(Dispatchers.IO) {
        val startDate = targetDate.minusDays(14)
        val urlString = "https://api.finmindtrade.com/api/v4/data" +
            "?dataset=TaiwanExchangeRate" +
            "&data_id=${URLEncoder.encode(currency, Charsets.UTF_8.name())}" +
            "&start_date=${startDate.format(dateFormatter)}" +
            "&end_date=${targetDate.plusDays(1).format(dateFormatter)}"
        debugLines += "request FinMind: $currency $startDate..$targetDate"
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "ExpenseBucket/1.0")
        }

        try {
            val responseCode = connection.responseCode
            debugLines += "response FinMind: $responseCode"
            if (responseCode !in 200..299) return@withContext null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            if (root.optInt("status") != 200) {
                debugLines += "FinMind error: ${root.optString("msg", "unknown error")}"
                return@withContext null
            }

            val data = root.optJSONArray("data") ?: return@withContext null
            var matched: CachedRate? = null
            for (index in 0 until data.length()) {
                val row = data.optJSONObject(index) ?: continue
                val rowDate = runCatching {
                    LocalDate.parse(row.optString("date"), dateFormatter)
                }.getOrNull() ?: continue
                val cashSell = row.optDouble("cash_sell", Double.NaN)
                if (!rowDate.isAfter(targetDate) && cashSell.isFinite() && cashSell > 0.0 &&
                    (matched == null || rowDate.isAfter(matched.quotedDate))
                ) {
                    matched = CachedRate(rowDate, cashSell)
                }
            }
            matched?.also {
                debugLines += "matched FinMind $currency at ${it.quotedDate} cashSell=${it.cashSell}"
            } ?: run {
                debugLines += "no FinMind rate for $currency near $targetDate"
            }
            matched
        } catch (throwable: Exception) {
            debugLines += "FinMind error: ${throwable.message ?: throwable::class.java.simpleName}"
            null
        } finally {
            connection.disconnect()
        }
    }
}
