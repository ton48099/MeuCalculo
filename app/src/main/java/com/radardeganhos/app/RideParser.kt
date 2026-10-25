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
    val textoOriginal: String,
    /** Endereço de busca do passageiro, quando o card mostra. */
    val origem: String? = null,
    /** Endereço de desembarque, quando o card mostra. */
    val destino: String? = null
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

        // Endereços precisam das quebras de linha do texto original.
        val enderecos = extractAddresses(raw)

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
            textoOriginal = raw,
            origem = enderecos.first,
            destino = enderecos.second
        )

        if (card.kmTotal < 0.2) return null
        return card
    }

    // ---- Endereços (origem e destino) -------------------------------------

    private val minToken = Regex("""\d+\s*(?:min|mins|minuto|minutos|h|hr|hrs|hora|horas)\b""")
    private val kmToken = Regex("""\d+(?:[.,]\d+)?\s*km\b""")
    private val metrosToken = Regex("""\(\s*\d{2,5}\s*m\b""")

    /** Linha do card que traz "tempo (distância)", como "5min (2,5km)". */
    private fun isPairLine(linha: String): Boolean =
        minToken.containsMatchIn(linha) && (kmToken.containsMatchIn(linha) || metrosToken.containsMatchIn(linha))

    private val paradasDeEndereco = listOf(
        "aceitar", "recusar", "cancelar", "corrida", "distancia", "distância",
        "viagem", "parada", "deslocamento", "km", "min", "r$", "taxa", "perfil",
        "corridas", "verif", "incluso", "dinheiro", "longa", "risco", "avalia",
        "nota", "passageiro", "concluir", "chegar", "tela", "app", "promo"
    )

    private val viasDeEndereco = listOf(
        "rua", "r.", "av", "av.", "avenida", "alameda", "al.", "travessa", "tv.",
        "rodovia", "estrada", "praça", "praca", "jardim", "vila", "parque", "bairro",
        "centro", "shopping", "terminal", "aeroporto", "hospital", "estação", "estacao",
        "universidade", "estádio", "estadio", "igreja", "mercado", "hotel"
    )

    /**
     * Lê os endereços do card usando as linhas do texto do OCR: cada linha de
     * "tempo (km)" vem seguida pelo endereço correspondente (Uber e 99 fazem assim).
     * A primeira é a busca do passageiro; a segunda é o desembarque.
     */
    fun extractAddresses(raw: String): Pair<String?, String?> {
        // Só o começo é limpo (bolinhas/ícones do card); reticências no fim são do endereço.
        val linhas = raw.split('\n').map { it.trim().trimStart { ch -> !ch.isLetterOrDigit() }.trim() }
            .filter { it.isNotEmpty() }
        val achados = mutableListOf<String>()
        for (i in linhas.indices) {
            if (achados.size >= 2) break
            if (!isPairLine(linhas[i].lowercase())) continue
            collectAddress(linhas, i + 1)?.let { achados.add(it) }
        }
        return achados.getOrNull(0) to achados.getOrNull(1)
    }

    private fun collectAddress(linhas: List<String>, inicio: Int): String? {
        val partes = mutableListOf<String>()
        var i = inicio
        var vazias = 0
        while (i < linhas.size && partes.size < 3) {
            val original = linhas[i]
            val baixa = original.lowercase()
            if (isPairLine(baixa)) break
            when {
                isAddressLine(baixa, original) ||
                    (partes.isNotEmpty() && isContinuation(baixa, original)) -> {
                    partes.add(original.trim().trimEnd(',', '-', '–').trim())
                    vazias = 0
                }
                partes.isEmpty() -> {
                    vazias++
                    if (vazias > 2) break
                }
                else -> break
            }
            i++
        }
        if (partes.isEmpty()) return null
        return partes.filter { it.isNotBlank() }.joinToString(", ")
    }

    /** Linha que parece um endereço completo (tem via, número ou vírgula). */
    private fun isAddressLine(baixa: String, original: String): Boolean {
        if (original.length < 5) return false
        if (!original.any { it.isLetter() }) return false
        if (temParada(baixa)) return false
        if (Regex("""\d+\s*(?:min|h)\b""").containsMatchIn(baixa)) return false
        val temVia = viasDeEndereco.any { via ->
            Regex("""(?:^|[^a-z])${Regex.escape(via)}(?:$|[^a-z])""").containsMatchIn(baixa)
        }
        return temVia || baixa.contains(",") || Regex("""\d{1,5}\s*-\s*[a-z]{2}\b""").containsMatchIn(baixa)
    }

    /** Complemento do endereço (bairro, cidade/UF) logo abaixo da linha principal. */
    private fun isContinuation(baixa: String, original: String): Boolean {
        if (original.length < 3) return false
        if (!original.any { it.isLetter() }) return false
        if (temParada(baixa)) return false
        return !Regex("""\d+\s*(?:min|h)\b""").containsMatchIn(baixa)
    }

    /** Palavras que indicam linha de botão/aviso, não de endereço. */
    private fun temParada(baixa: String): Boolean = paradasDeEndereco.any { p ->
        Regex("""(?:^|[^a-z$])${Regex.escape(p)}(?:$|[^a-z])""").containsMatchIn(baixa)
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
