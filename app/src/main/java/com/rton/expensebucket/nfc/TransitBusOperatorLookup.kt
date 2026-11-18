package com.rton.expensebucket.nfc

internal object TransitBusOperatorLookup {
    private const val RESOURCE_PATH = "/com/rton/expensebucket/nfc/bus_operators.csv"

    private val operators: Map<Int, String> by lazy { loadOperators() }

    /**
     * Resolves a bus operator name from its region and 2-byte operator code,
     * falling back to the uppercase 4-digit hex code when unmapped.
     */
    fun resolve(region: Int, highByte: Int, lowByte: Int): String? {
        if (region !in 0..3 || highByte !in 0..0xff || lowByte !in 0..0xff) return null
        if (highByte == 0 && lowByte == 0) return null
        return operators[packKey(region, highByte, lowByte)] ?: "%02X%02X".format(highByte, lowByte)
    }

    private fun packKey(region: Int, highByte: Int, lowByte: Int): Int =
        (region shl 16) or (highByte shl 8) or lowByte

    private fun loadOperators(): Map<Int, String> {
        val stream = TransitBusOperatorLookup::class.java.getResourceAsStream(RESOURCE_PATH)
            ?: return emptyMap()
        val result = HashMap<Int, String>()
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { rawLine ->
                val parts = rawLine.split(',', limit = 4)
                if (parts.size != 4) return@forEach
                val region = parts[0].toIntOrNull(16) ?: return@forEach
                val highByte = parts[1].toIntOrNull(16) ?: return@forEach
                val lowByte = parts[2].toIntOrNull(16) ?: return@forEach
                val name = parts[3].trim()
                if (name.isNotEmpty()) {
                    result[packKey(region, highByte, lowByte)] = name
                }
            }
        }
        return result
    }
}
