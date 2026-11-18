package com.rton.expensebucket.nfc

data class TransitStation(
    val company: String,
    val line: String,
    val name: String
)

internal object TransitStationLookup {
    private const val RESOURCE_PATH = "/com/rton/expensebucket/nfc/station_codes.csv"

    private val stations: Map<Int, TransitStation> by lazy { loadStations() }

    fun find(region: Int, line: Int, station: Int): TransitStation? {
        if (region !in 0..3 || line <= 0 || station !in 0..0xff) return null
        return stations[packKey(region, line, station)]
    }

    private fun packKey(region: Int, line: Int, station: Int): Int =
        (region shl 16) or (line shl 8) or station

    private fun loadStations(): Map<Int, TransitStation> {
        val stream = TransitStationLookup::class.java.getResourceAsStream(RESOURCE_PATH)
            ?: return emptyMap()
        val result = HashMap<Int, TransitStation>(6144)
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { rawLine ->
                val parts = rawLine.split(',', limit = 6)
                if (parts.size != 6) return@forEach
                val region = parts[0].toIntOrNull(16) ?: return@forEach
                val line = parts[1].toIntOrNull(16) ?: return@forEach
                val station = parts[2].toIntOrNull(16) ?: return@forEach
                result[packKey(region, line, station)] = TransitStation(
                    company = parts[3],
                    line = parts[4],
                    name = parts[5]
                )
            }
        }
        return result
    }
}
