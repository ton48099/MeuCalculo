package com.radardeganhos.app

/**
 * Card de corrida extraído da tela (Uber, 99 ou qualquer print/foto).
 */
data class RideCard(
    val app: String?,
    /** Valor bruto oferecido pelo app. */
    val valor: Double,
    val kmDeslocamento: Double,
    val minDeslocamento: Double,
    val kmViagem: Double,
    val minViagem: Double,
    /** Preço por km já informado pelo app (Uber "R$2,24/km est." ou 99 "R$2,20 por km"). */
    val precoPorKmApp: Double?,
    /** "Taxa de deslocamento" que a 99 mostra separada — informativa, não somada. */
    val taxaDeslocamento: Double?,
    val avisos: List<String>,
    val textoOriginal: String
) {
    val kmTotal: Double get() = kmDeslocamento + kmViagem
    val minTotal: Double get() = minDeslocamento + minViagem
}

/**
 * Leitor dos cards. Aceita o texto do OCR sem ordem garantida, com pontos ou vírgulas
 * e com os dois formatos de tempo que Uber e 99 usam:
 *
 *  Uber: "2 min (0.3 km) de distância" / "Viagem de 12 min (4.9 km)" / "1 h e 43 min (95,7 km)"
 *  99:   "5Min (2,5km)" / "32Min (6,3km)"
 */
object RideParser {

    private const val MONEY = """(?:r\$|rs\.?)\s*(\d{1,3}(?:\.\d{3})+(?:,\d{2})?|\d+(?:[.,]\d{1,2})?)"""

    private val perKmRegex = Regex("""(?:r\$|rs\.?)\s*(\d+(?:[.,]\d+)?)\s*(?:/\s*km|por\s*km)""")
    private val moneyRegex = Regex(MONEY)
    private val taxaRegex = Regex("""taxa\s+de\s+deslocamento[^0-9]{0,16}(?:r\$|rs\.?)\s*(\d+(?:[.,]\d{1,2})?)""")

    private val pairHM = Regex("""(\d+)\s*h\s*(\d{1,2})\s*min\s*[\(\[]?\s*(\d+(?:[.,]\d+)?)\s*km""")
    private val pairH = Regex("""(\d+)\s*h\s*[\(\[]?\s*(\d+(?:[.,]\d+)?)\s*km""")
    private val pairMin = Regex("""(\d+)\s*min\s*\(?\s*(\d+(?:[.,]\d+)?)\s*km""")
    private val pairMinMeters = Regex("""(\d+)\s*min\s*\(?\s*(\d{2,5})\s*m\b""")
    private val pairKmMin = Regex("""(\d+(?:[.,]\d+)?)\s*km[^0-9]{0,12}?(\d+)\s*min""")

    private val anyKm = Regex("""(\d+(?:[.,]\d+)?)\s*km\b""")
    private val anyMin = Regex("""(\d+)\s*min\b""")

    private data class Par(val idx: Int, val min: Double, val km: Double)

    /** Normaliza texto do OCR: minúsculas, tempos unificados e espaços colapsados. */
    fun normalize(raw: String): String {
        var t = raw.lowercase()
            .replace('\u00A0', ' ')
            .replace('–', '-')
            .replace('—', '-')

        // "1 h e 43 min" / "1 hora e 43 minutos" / "1h43" -> "1h43min"
        t = t.replace(Regex("""(\d+)\s*h(?:oras?)?\s*e\s*(\d+)\s*(?:min(?:utos?)?)?"""), "$1h$2min")
        t = t.replace(Regex("""(\d+)\s*horas?\b"""), "$1h")
        t = t.replace(Regex("""(\d+)\s*h\b"""), "$1h")
        // "12 minutos" / "12 min" / "5Min" -> "12min"
        t = t.replace(Regex("""(\d+)\s*minutos?\b"""), "$1min")
        t = t.replace(Regex("""(\d+)\s*mins?\b"""), "$1min")

        t = t.replace(Regex("""[ \t]*\n[ \t]*"""), " ")
        t = t.replace(Regex("""\s+"""), " ")
        return t.trim()
    }

    fun detectApp(text: String, packageHint: String? = null): String? {
        val pkg = packageHint?.lowercase() ?: ""
        val t = text.lowercase()
        return when {
            pkg.contains("ubercab") || pkg.contains("uber") -> "Uber"
            pkg.contains("taxis99") || pkg == "com.taxis99" -> "99"
            t.contains("uberx") || t.contains("uber black") || t.contains("uber comfort") -> "Uber"
            t.contains("uber") -> "Uber"
            t.contains("99app") || Regex("""\b99\b""").containsMatchIn(t) -> "99"
            else -> null
        }
    }

    /**
     * Tenta ler um card a partir de um texto (OCR ou digitado).
     * Retorna null quando não há elementos mínimos (valor + alguma distância).
     */
    fun parse(raw: String, packageHint: String? = null): RideCard? {
        if (raw.isBlank()) return null
        val norm = normalize(raw)
        val avisos = mutableListOf<String>()

        // 1) Preço por km informado pelo app não é distância — separa antes.
        var precoPorKm: Double? = null
        perKmRegex.find(norm)?.let {
            precoPorKm = NumberParse.toDouble(it.groupValues[1])
        }
        val semPrecoKm = perKmRegex.replace(norm, " § ")

        // 2) Taxa de deslocamento (99) — informativa.
        val taxa = taxaRegex.find(semPrecoKm)?.let {
            NumberParse.toDouble(it.groupValues[1])
        }

        // 3) Pares tempo x distância, na ordem em que aparecem na tela.
        val pares = findPairs(semPrecoKm)

        var kmDes = 0.0
        var minDes = 0.0
        var kmVia = 0.0
        var minVia = 0.0

        when {
            pares.size >= 2 -> {
                // Primeiro par = deslocamento até o passageiro, segundo = viagem.
                kmDes = pares[0].km; minDes = pares[0].min
                kmVia = pares[1].km; minVia = pares[1].min
            }
            pares.size == 1 -> {
                val p = pares[0]
                val temViagem = semPrecoKm.contains("viagem")
                val temDistancia = semPrecoKm.contains("distancia") || semPrecoKm.contains("distância")
                if (temDistancia && !temViagem) {
                    kmDes = p.km; minDes = p.min
                    avisos.add("Distância da viagem não identificada")
                } else {
                    kmVia = p.km; minVia = p.min
                    avisos.add("Deslocamento até o passageiro não identificado")
                }
            }
            else -> {
                // Fallback: maior distância = viagem, maior tempo = viagem.
                val kms = anyKm.findAll(semPrecoKm).mapNotNull { NumberParse.toDouble(it.groupValues[1]) }
                    .filter { it > 0.1 }.toList()
                val mins = anyMin.findAll(semPrecoKm).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
                if (kms.isNotEmpty()) {
                    kmVia = kms.max()
                    if (kms.size >= 2) kmDes = kms.min()
                }
                if (mins.isNotEmpty()) minVia = mins.max().toDouble()
                avisos.add("Card lido sem o padrão completo (tempo/distância)")
            }
        }

        // 4) Valor: o maior "R$" do card (o preço por km já foi removido).
        val valores = moneyRegex.findAll(semPrecoKm)
            .mapNotNull { NumberParse.toDouble(it.groupValues[1]) }
            .filter { it >= 1.0 }
            .toList()
        val valor = valores.maxOrNull() ?: return null

        val card = RideCard(
            app = detectApp(raw, packageHint),
            valor = valor,
            kmDeslocamento = kmDes,
            minDeslocamento = minDes,
            kmViagem = kmVia,
            minViagem = minVia,
            precoPorKmApp = precoPorKm,
            taxaDeslocamento = taxa,
            avisos = avisos,
            textoOriginal = raw
        )

        if (card.kmTotal < 0.2) return null
        return card
    }

    private fun findPairs(text: String): List<Par> {
        val found = mutableListOf<Par>()

        fun add(idx: Int, min: Double, km: Double) {
            if (km <= 0.0) return
            if (found.any { kotlin.math.abs(it.idx - idx) < 14 }) return
            found.add(Par(idx, min, km))
        }

        pairHM.findAll(text).forEach { m ->
            val h = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val mi = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val km = NumberParse.toDouble(m.groupValues[3]) ?: return@forEach
            add(m.range.first, h * 60 + mi, km)
        }
        pairH.findAll(text).forEach { m ->
            val h = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val km = NumberParse.toDouble(m.groupValues[2]) ?: return@forEach
            add(m.range.first, h * 60, km)
        }
        pairMin.findAll(text).forEach { m ->
            val mi = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val km = NumberParse.toDouble(m.groupValues[2]) ?: return@forEach
            add(m.range.first, mi, km)
        }
        pairMinMeters.findAll(text).forEach { m ->
            val mi = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val mts = m.groupValues[2].toDoubleOrNull() ?: return@forEach
            if (mts >= 100) add(m.range.first, mi, mts / 1000.0)
        }
        pairKmMin.findAll(text).forEach { m ->
            val km = NumberParse.toDouble(m.groupValues[1]) ?: return@forEach
            val mi = m.groupValues[2].toDoubleOrNull() ?: 0.0
            add(m.range.first, mi, km)
        }

        return found.sortedBy { it.idx }
    }
}
