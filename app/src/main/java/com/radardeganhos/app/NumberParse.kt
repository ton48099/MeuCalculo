package com.radardeganhos.app

/** Conversão de números no formato brasileiro (1.234,56) e no formato do OCR (11.65). */
object NumberParse {

    fun toDouble(raw: String?): Double? {
        if (raw == null) return null
        var t = raw.trim().replace(" ", "").replace("\u00A0", "")
        if (t.isEmpty()) return null
        // remove tudo que não for dígito, vírgula, ponto ou sinal
        t = t.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }
        if (t.isEmpty() || t == "-") return null

        val hasDot = t.contains('.')
        val hasComma = t.contains(',')

        val normalized = when {
            hasDot && hasComma -> {
                // 1.234,56 -> 1234.56 (ponto é milhar, vírgula é decimal)
                if (t.lastIndexOf(',') > t.lastIndexOf('.')) {
                    t.replace(".", "").replace(',', '.')
                } else {
                    // 1,234.56 (formato inglês)
                    t.replace(",", "")
                }
            }
            hasComma -> t.replace(',', '.')
            hasDot -> {
                val parts = t.split('.')
                when {
                    parts.size > 2 -> t.replace(".", "")
                    parts[1].length == 3 && parts[0].length <= 3 && parts[0] != "" ->
                        t.replace(".", "") // 1.234 = mil duzentos e trinta e quatro
                    else -> t
                }
            }
            else -> t
        }
        return normalized.toDoubleOrNull()
    }
}
