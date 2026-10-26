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
        "viagem", "parada", "paradas", "deslocamento", "km", "min", "r$", "taxa",
        "perfil", "premium", "corridas", "verif", "incluso", "dinheiro", "longa",
        "risco", "avalia", "avaliacao", "nota", "passageiro", "concluir", "chegar",
        "tela", "app", "promo", "cartao", "cartão", "pagamento", "oferta", "aceite",
        "exclusiva", "uberx", "comfort", "cupom", "desconto", "estrela", "estrelas",
        "classificacao", "proxima", "proxima", "motorista", "trocar", "ver detalhes",
        "destino", "desembarque", "busca", "origem", "coleta", "embarque", "chegada",
        "rota", "editar"
    )

    private val viasDeEndereco = listOf(
        "rua", "r.", "av", "av.", "avenida", "alameda", "al.", "travessa", "tv.",
        "rodovia", "estrada", "praça", "praca", "jardim", "vila", "parque", "bairro",
        "centro", "shopping", "terminal", "aeroporto", "hospital", "estação", "estacao",
        "universidade", "estádio", "estadio", "igreja", "mercado", "hotel"
    )

    /**
     * Lê os endereços do card usando as linhas do OCR. Cada linha "tempo (km)" vem
     * seguida pelo endereço correspondente (Uber e 99) — mas o OCR pode juntar tudo
     * numa linha só ou quebrar o par no meio, então há várias estratégias:
     *
     *  1. endereço escrito na mesma linha, depois do "tempo (km)";
     *  2. bloco de linhas logo abaixo do par (até 4 linhas, pulando rótulos);
     *  3. rótulo explícito ("destino", "desembarque", "origem", "busca");
     *  4. fim: varre o restante do card atrás de um endereço forte não usado.
     *
     * O rótulo do par ("de distância" = busca, "Viagem" = desembarque) decide quem é
     * quem; sem rótulo vale a ordem em que aparecem.
     */
    fun extractAddresses(raw: String): Pair<String?, String?> {
        val linhas = prepararLinhas(raw)
        val blocos = blocosDeEndereco(linhas)
        if (blocos.isEmpty()) return fallbackPorRotulo(linhas)

        val iVia = blocos.indexOfFirst { it.contexto == true }
        var destino: String? = if (iVia >= 0) blocos[iVia].endereco else null

        // Primeiro bloco que é busca; senão, o primeiro que vem antes do bloco de viagem.
        var iOrigem: Int? = blocos.indexOfFirst { it.contexto == false }.takeIf { it >= 0 }
        if (iOrigem == null) {
            val limite = if (iVia >= 0) iVia else blocos.size
            iOrigem = if (limite > 0) 0 else null
        }
        var origem: String? = iOrigem?.let { blocos[it].endereco }

        // Bloco de viagem sem busca antes dele: procura um endereço forte acima.
        if (origem == null && iVia >= 0) {
            val antes = enderecosFortes(linhas).firstOrNull { it.fim < blocos[iVia].idx }
            origem = antes?.texto
            iOrigem = null
        }

        // Sem bloco de viagem: o segundo bloco encontrado é o desembarque.
        val iO = iOrigem
        if (destino == null) {
            destino = if (iO == null) blocos.getOrNull(1)?.endereco
            else blocos.firstOrNull { it.idx > blocos[iO].idx }?.endereco
        }

        // Último recurso: um endereço forte depois do bloco de origem.
        if (destino == null && iO != null) {
            destino = enderecosFortes(linhas).firstOrNull { it.inicio > blocos[iO].idx }?.texto
        }

        return origem to destino
    }

    private data class Bloco(val idx: Int, val endereco: String, val contexto: Boolean?)
    private data class Forte(val inicio: Int, val fim: Int, val texto: String)

    /** Limpa, junta linhas partidas pelo OCR e devolve as linhas do card. */
    private fun prepararLinhas(raw: String): List<String> {
        val brutas = raw.split('\n')
            .map { it.trim().trimStart { ch -> !ch.isLetterOrDigit() }.trim() }
            .filter { it.isNotBlank() }
        val out = mutableListOf<String>()
        var i = 0
        while (i < brutas.size) {
            val atual = brutas[i]
            val prox = brutas.getOrNull(i + 1)
            val a = atual.lowercase()
            // "Viagem de 12 min" separado de "(4.9 km)" na linha seguinte.
            val quebrado = prox != null &&
                minToken.containsMatchIn(a) && !kmToken.containsMatchIn(a) && !metrosToken.containsMatchIn(a) &&
                kmToken.containsMatchIn(prox.lowercase()) && !minToken.containsMatchIn(prox.lowercase())
            if (quebrado) {
                out.add("$atual $prox")
                i += 2
            } else {
                out.add(atual)
                i++
            }
        }
        return out
    }

    /** Um endereço por par "tempo (km)": no par, depois dele, ou nada. */
    private fun blocosDeEndereco(linhas: List<String>): List<Bloco> {
        val blocos = mutableListOf<Bloco>()
        for (i in linhas.indices) {
            val n = linhas[i].lowercase()
            if (!isPairLine(n)) continue
            val contexto = when {
                n.contains("viagem") -> true
                n.contains("distancia") || n.contains("distância") -> false
                else -> null
            }
            val inicial = restoAposPar(linhas[i])?.let { mutableListOf(it) } ?: mutableListOf()
            val endereco = coletarEndereco(linhas, i + 1, inicial)
            if (endereco.isNotBlank()) blocos.add(Bloco(i, endereco, contexto))
        }
        return blocos
    }

    /** Texto que o card colou na própria linha do par, depois de "(x,x km)". */
    private fun restoAposPar(linha: String): String? {
        val m = kmToken.find(linha) ?: metrosToken.find(linha) ?: return null
        val restante = linha.substring(m.range.last + 1)
            .replace(Regex("""^[\s)\].,:;-]+"""), "")
            .trim()
        if (restante.length < 4) return null
        val b = restante.lowercase()
        if (!restante.any { it.isLetter() }) return null
        if (temParada(b)) return null
        if (minToken.containsMatchIn(b)) return null   // outro par colado na mesma linha
        return restante
    }

    /** Monta o endereço a partir da linha seguinte ao par (ou do texto colado no par). */
    private fun coletarEndereco(linhas: List<String>, inicio: Int, iniciais: List<String>): String {
        val partes = iniciais.toMutableList()
        var i = inicio
        var rejeitadas = 0
        while (i < linhas.size && partes.size < 4) {
            val original = linhas[i]
            val baixa = original.lowercase()
            if (isPairLine(baixa)) break
            if (temParada(baixa)) {
                if (partes.isNotEmpty()) break
                i++; rejeitadas++
                if (rejeitadas > 2) break
                continue
            }
            if (pareceEndereco(original, baixa, primeiraLinha = partes.isEmpty())) {
                partes.add(original.trim().trimEnd(',', '-', '–').trim())
                rejeitadas = 0
            } else {
                if (partes.isEmpty()) {
                    rejeitadas++
                    if (rejeitadas > 2) break
                } else break
            }
            i++
        }
        return partes.filter { it.isNotBlank() }.joinToString(", ")
    }

    /**
     * Linha de endereço. Logo abaixo do par o card mostra o endereço, então a primeira
     * linha é aceita sem exigir via/número; as seguintes precisam continuar o endereço.
     */
    private fun pareceEndereco(original: String, baixa: String, primeiraLinha: Boolean): Boolean {
        if (original.length < 4) return false
        if (!original.any { it.isLetter() }) return false
        if (temParada(baixa)) return false
        if (baixa.contains("r$")) return false              // valor não é endereço
        if (Regex("""\d+\s*(?:min|h)\b""").containsMatchIn(baixa)) return false
        val temVia = viasDeEndereco.any { via ->
            Regex("""(?:^|[^a-z])${Regex.escape(via)}(?:$|[^a-z])""").containsMatchIn(baixa)
        }
        val temSinal = baixa.contains(",") || Regex("""\d{1,5}\s*-\s*[a-z]{2}\b""").containsMatchIn(baixa)
        val temNumero = Regex("""\d""").containsMatchIn(original)
        val duasPalavras = original.split(Regex("""\s+""")).size >= 2
        // Logo abaixo do par o card traz o endereço, mesmo quando é só um bairro
        // ("Pinheiros"), um trecho partido pelo OCR ou uma linha sem via/número.
        if (primeiraLinha) return true
        return temVia || temSinal || temNumero || duasPalavras
    }

    /** Endereço forte (via, número ou cidade/UF) em uma ou mais linhas seguidas. */
    private fun enderecosFortes(linhas: List<String>): List<Forte> {
        val achados = mutableListOf<Forte>()
        var i = 0
        while (i < linhas.size) {
            val n = linhas[i].lowercase()
            if (!forte(n, linhas[i]) || temParada(n) || isPairLine(n)) { i++; continue }
            val inicio = i
            var texto = linhas[i]
            var j = i + 1
            while (j < linhas.size && j - i < 3) {
                val p = linhas[j]
                val pb = p.lowercase()
                if (isPairLine(pb) || temParada(pb)) break
                if (!forte(pb, p) && !forteContinuacao(pb, p)) break
                texto += ", " + p.trim().trimEnd(',', '-', '–').trim()
                j++
            }
            achados.add(Forte(inicio, j - 1, texto.trim()))
            i = j
        }
        return achados
    }

    private fun forte(baixa: String, original: String): Boolean {
        if (original.length < 5) return false
        if (!original.any { it.isLetter() }) return false
        if (baixa.contains("r$")) return false
        if (Regex("""\d+\s*(?:min|h)\b""").containsMatchIn(baixa)) return false
        val temVia = viasDeEndereco.any { via ->
            Regex("""(?:^|[^a-z])${Regex.escape(via)}(?:$|[^a-z])""").containsMatchIn(baixa)
        }
        return temVia || baixa.contains(",") || Regex("""\d{1,5}\s*-\s*[a-z]{2}\b""").containsMatchIn(baixa)
    }

    private fun forteContinuacao(baixa: String, original: String): Boolean {
        if (original.length < 3) return false
        if (!original.any { it.isLetter() }) return false
        if (Regex("""\d+\s*(?:min|h)\b""").containsMatchIn(baixa)) return false
        val duasPalavras = original.split(Regex("""\s+""")).size >= 2
        return duasPalavras || Regex("""\d""").containsMatchIn(original)
    }

    /** Sem par "tempo (km)", usa os rótulos do card ("Destino", "Busca", ...). */
    private fun fallbackPorRotulo(linhas: List<String>): Pair<String?, String?> {
        var origem: String? = null
        var destino: String? = null
        for (i in linhas.indices) {
            val n = linhas[i].lowercase()
            val ehDestino = Regex("""destino|desembarque|chegada|vai para|deixe em""").containsMatchIn(n)
            val ehOrigem = Regex("""origem|busca|coleta|embarque|busque""").containsMatchIn(n)
            if (!ehDestino && !ehOrigem) continue
            val alvo = coletarEndereco(linhas, i + 1, emptyList())
            if (alvo.isBlank()) continue
            if (ehDestino) destino = destino ?: alvo else origem = origem ?: alvo
        }
        return origem to destino
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
