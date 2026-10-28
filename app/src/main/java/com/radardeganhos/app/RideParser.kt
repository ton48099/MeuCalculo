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

    private val pairHM = Regex("""(\d+)\s*h\s*(?:e\s*)?(\d{1,2})\s*min\s*[\(\[]?\s*(\d+(?:[.,]\d+)?)\s*km""")
    private val pairH = Regex("""(\d+)\s*h\s*[\(\[]?\s*(\d+(?:[.,]\d+)?)\s*km""")
    private val pairMin = Regex("""(\d+)\s*min\s*\(?\s*(\d+(?:[.,]\d+)?)\s*km""")
    private val pairMinMeters = Regex("""(\d+)\s*min\s*\(?\s*(\d{2,5})\s*m\b""")
    private val pairKmMin = Regex("""(\d+(?:[.,]\d+)?)\s*km[^0-9]{0,12}?(\d+)\s*min""")

    private val anyKm = Regex("""(\d+(?:[.,]\d+)?)\s*km\b""")
    private val anyMin = Regex("""(\d+)\s*min\b""")

    /** Tempo isolado sem km ao lado (ex.: "1 h e 19 min", "1h19min", "45 min", "2 h"). */
    private val tempoIsoladoHM = Regex("""(\d+)\s*h\s*(?:e\s*)?\s*(\d{1,2})\s*min\b""", RegexOption.IGNORE_CASE)
    private val tempoIsoladoHMin = Regex("""(\d+)h(\d{1,2})min\b""", RegexOption.IGNORE_CASE)
    private val tempoIsoladoH = Regex("""(\d+)\s*h\b""", RegexOption.IGNORE_CASE)
    private val tempoIsoladoMin = Regex("""(\d+)\s*min\b""", RegexOption.IGNORE_CASE)

    private data class Par(val idx: Int, val min: Double, val km: Double)

    /** Normaliza texto do OCR: minúsculas, tempos unificados e espaços colapsados. */
    // Regexes pré-compilados: com a automática ligada de 2 em 2 segundos, criar
    // objetos Regex a cada leitura (e a cada linha) era o maior custo do parser.
    private val normHm = Regex("""(\d+)\s*h(?:oras?)?\s*e\s*(\d+)\s*(?:min(?:utos?)?)?""")
    private val normHoras = Regex("""(\d+)\s*horas?\b""")
    private val normH = Regex("""(\d+)\s*h\b""")
    private val normMinutos = Regex("""(\d+)\s*minutos?\b""")
    private val normMins = Regex("""(\d+)\s*mins?\b""")
    private val normQuebra = Regex("""[ \t]*\n[ \t]*""")
    private val normEspacos = Regex("""\s+""")
    private val noventaENove = Regex("""\b99\b""")
    private val simbolosPar = Regex("""[()\[\]·|]""")

    /**
     * O OCR às vezes separa os dígitos de um número: "1 9 min" em vez de "19 min",
     * "7 1,7 km" em vez de "71,7 km". Sem juntar, "1 h e 1 9 min (71,7 km)" virava
     * "9 min" — o motorista via 9 min numa corrida de 1 h e 19 min.
     */
    private val numeroQuebrado = Regex(
        """(?<![\d.,])(\d)\s+(\d)(?=\s*(?:[.,]\s*\d+)?\s*(?:minutos?|horas?|min|km|m|h)\b)"""
    )
    private val separadorEspacado = Regex("""(\d)\s*([.,])\s*(\d)""")

    fun normalize(raw: String): String {
        var t = raw.lowercase()
            .replace('\u00A0', ' ')
            .replace('–', '-')
            .replace('—', '-')

        // Junta dígitos que o OCR separou ("1 9 min", "7 1,7 km", "9 , 5 km").
        var anterior: String
        do {
            anterior = t
            t = t.replace(numeroQuebrado, "$1$2")
        } while (t != anterior)
        t = t.replace(separadorEspacado, "$1$2$3")

        // "1 h e 43 min" / "1 hora e 43 minutos" / "1h43" -> "1h43min"
        t = t.replace(normHm, "$1h$2min")
        t = t.replace(normHoras, "$1h")
        t = t.replace(normH, "$1h")
        // "12 minutos" / "12 min" / "5Min" -> "12min"
        t = t.replace(normMinutos, "$1min")
        t = t.replace(normMins, "$1min")

        t = t.replace(normQuebra, " ")
        t = t.replace(normEspacos, " ")
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
            t.contains("99app") || noventaENove.containsMatchIn(t) -> "99"
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

                // Tempo isolado sem km (ex.: "1 h e 19 min", "1h19min") — evita ler "9 min" de outro lugar da tela.
                val tempoIsolado = tempoIsoladoHM.find(semPrecoKm)?.let {
                    (it.groupValues[1].toDoubleOrNull() ?: 0.0) * 60 + (it.groupValues[2].toDoubleOrNull() ?: 0.0)
                } ?: tempoIsoladoHMin.find(semPrecoKm)?.let {
                    (it.groupValues[1].toDoubleOrNull() ?: 0.0) * 60 + (it.groupValues[2].toDoubleOrNull() ?: 0.0)
                } ?: tempoIsoladoH.find(semPrecoKm)?.let {
                    (it.groupValues[1].toDoubleOrNull() ?: 0.0) * 60
                } ?: tempoIsoladoMin.find(semPrecoKm)?.let {
                    it.groupValues[1].toDoubleOrNull() ?: 0.0
                } ?: 0.0

                if (kms.isNotEmpty()) {
                    kmVia = kms.max()
                    if (kms.size >= 2) kmDes = kms.min()
                }
                if (mins.isNotEmpty()) minVia = mins.max().toDouble()
                // Se achou tempo isolado e nenhum min válido veio dos pares, usa ele.
                if (tempoIsolado > 0 && minVia == 0.0 && minDes == 0.0) {
                    minVia = tempoIsolado
                }
                avisos.add("Card lido sem o padrão completo (tempo/distância)")
            }
        }

        // O Uber escreve "1 h e 19 min (71,7 km)". Quando o OCR perde o "1h" e sobra
        // "9 min (71,7 km)", a conta dá 478 km/h — impossível. Se o texto traz a hora
        // escrita em algum lugar, esse é o tempo real da viagem.
        val horasNoTexto = tempoIsoladoHM.find(norm)?.let {
            (it.groupValues[1].toDoubleOrNull() ?: 0.0) * 60 +
                (it.groupValues[2].toDoubleOrNull() ?: 0.0)
        }
        if (horasNoTexto != null && horasNoTexto > 0 && kmVia > 0) {
            val velocidade = if (minVia > 0) kmVia / (minVia / 60.0) else 0.0
            // A hora sumiu da conta (leu "9 min" de "1 h e 19 min") ou o tempo com a
            // hora ainda dá uma velocidade impossível: em ambos os casos vale a hora
            // escrita no card, que é o que o motorista está vendo. Só quando o par da
            // viagem foi identificado (2 pares), senão o km seria o do deslocamento.
            val perdeuAHora = pares.size >= 2 && horasNoTexto >= 60.0 && minVia < 60.0
            val impossivel = velocidade > 120.0
            if (minVia <= 0.0 || (horasNoTexto > minVia && (perdeuAHora || impossivel))) {
                minVia = horasNoTexto
                avisos.add("Tempo da viagem recuperado do formato \"1 h e 19 min\"")
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

    private val minTokenCi = Regex("""\d+\s*(?:min|mins|minuto|minutos|h|hr|hrs|hora|horas)\b""", RegexOption.IGNORE_CASE)
    private val kmTokenCi = Regex("""\d+(?:[.,]\d+)?\s*km\b""", RegexOption.IGNORE_CASE)
    private val metrosTokenCi = Regex("""\(\s*\d{2,5}\s*m\b""", RegexOption.IGNORE_CASE)

    /** Linha do card que traz "tempo (distância)", como "5min (2,5km)". */
    private fun isPairLine(linha: String): Boolean =
        minToken.containsMatchIn(linha) && (kmToken.containsMatchIn(linha) || metrosToken.containsMatchIn(linha))

    private val paradasDeEndereco = listOf(
        "aceitar", "recusar", "cancelar", "corrida", "distancia", "distância",
        "viagem", "parada", "deslocamento", "km", "min", "r$", "taxa", "perfil",
        "corridas", "verif", "incluso", "dinheiro", "longa", "risco", "avalia",
        "nota", "passageiro", "concluir", "chegar", "tela", "app", "promo",
        // linhas de oferta/aviso que às vezes ficam coladas ao trajeto no OCR
        "oferta", "tarifa", "dinâmica", "dinamica", "sugest", "vantagem",
        "surge", "promoção", "promocao", "desconto", "bônus", "bonus", "cupom",
        "pague", "pagamento", "cartão", "cartao", "crédito", "credito", "débito", "debito"
    )

    private val viasDeEndereco = listOf(
        "rua", "r.", "av", "av.", "avenida", "alameda", "al.", "travessa", "tv.",
        "rodovia", "estrada", "praça", "praca", "jardim", "vila", "parque", "bairro",
        "centro", "shopping", "terminal", "aeroporto", "hospital", "estação", "estacao",
        "universidade", "estádio", "estadio", "igreja", "mercado", "hotel",
        // pontos comerciais e locais muito usados como destino nas corridas
        "condominio", "condomínio", "residencial", "escola", "colégio", "colegio",
        "posto", "supermercado", "atacad", "farmácia", "farmacia", "clínica", "clinica",
        "restaurante", "lanchonete", "academia", "ginásio", "ginasio",
        "rodoviária", "rodoviaria", "loja", "galpão", "galpao", "garagem",
        "distrito", "loteamento", "setor", "campus", "fórum", "forum",
        "cartório", "cartorio", "conjunto", "cond.", "viela", "largo", "lago", "lagoa"
    )

    /** Rótulos que antecedem o endereço ("Origem", "Destino", "Buscar em"...). */
    private val rotulosEndereco = listOf(
        "origem", "destino", "embarque", "desembarque", "buscar", "busca",
        "buscando", "deixar", "retirada", "entrega", "local", "ponto de",
        "ponto de embarque", "ponto de desembarque", "de", "para", "ir para",
        "buscar em", "deixar em", "local de", "seu destino", "seu embarque",
        "cliente", "passageiro", "minha localiza"
    )

    // As regexes das listas acima ficam prontas uma única vez: elas eram recriadas
    // para CADA linha de CADA leitura (40+ paradas × 55+ vias), que era o maior
    // custo de CPU do parser com a leitura automática rodando de 2 em 2 segundos.
    private val paradaRegexes = paradasDeEndereco.map { p ->
        Regex("""(?:^|[^a-z$])${Regex.escape(p)}(?:$|[^a-z])""")
    }
    private val viaRegexes = viasDeEndereco.map { v ->
        Regex("""(?:^|[^a-z])${Regex.escape(v)}(?:$|[^a-z])""")
    }
    private val minHoraLinha = Regex("""\d+\s*(?:min|h)\b""")
    private val numeroHifen = Regex("""\d{1,5}\s*-\s*[a-z]{2}\b""")

    /**
     * Lê os endereços do card usando as linhas do texto do OCR.
     *
     * Estratégia principal: cada linha de "tempo (km)" costuma vir seguida do
     * endereço correspondente (Uber e 99 fazem assim); a primeira é a busca do
     * passageiro e a segunda é o desembarque. Quando o OCR junta a distância com
     * o endereço na mesma linha, ou perde uma das linhas, cai num pareamento
     * posicional (primeiro bloco = busca, segundo = desembarque).
     */
    fun extractAddresses(raw: String): Pair<String?, String?> {
        // Só o começo é limpo (bolinhas/ícones do card); reticências no fim são do endereço.
        val linhas = raw.split('\n').map { it.trim().trimStart { ch -> !ch.isLetterOrDigit() }.trim() }
            .filter { it.isNotEmpty() }
        if (linhas.isEmpty()) return null to null

        val porPar = mutableListOf<String>()
        for (i in linhas.indices) {
            if (porPar.size >= 2) break
            if (!isPairLine(linhas[i].lowercase())) continue
            enderecoDaPar(linhas, i)?.let { if (it !in porPar) porPar.add(it) }
        }
        if (porPar.size >= 2) return porPar[0] to porPar[1]

        // Fallback: blocos de endereço na ordem da tela. Preferimos a ordem da
        // tela quando ela reconhece os dois; senão completamos com o que veio
        // colado às linhas "tempo (km)".
        val blocos = blocosDeEndereco(linhas)
        val achados = if (blocos.size >= 2) blocos else (porPar + blocos).distinct()
        return when {
            achados.size >= 2 -> achados[0] to achados[1]
            achados.size == 1 -> achados[0] to null
            else -> null to null
        }
    }

    /**
     * Endereço de uma linha "tempo (km)": aceita o texto que o OCR colou na
     * mesma linha da distância e/ou as linhas logo abaixo.
     */
    private fun enderecoDaPar(linhas: List<String>, parIdx: Int): String? {
        val semente = restoDaLinhaDePar(linhas[parIdx])
        return collectAddress(linhas, parIdx + 1, semente)
    }

    /**
     * Bloco único de leitura em sequência. `semente` é o endereço que veio na
     * própria linha do "tempo (km)"; quando existe, a primeira linha seguinte já
     * entra como complemento. Enquanto o bloco está vazio, aceita a primeira
     * linha "plausível" (o endereço costuma ser a linha logo após o par).
     */
    private fun collectAddress(linhas: List<String>, inicio: Int, semente: String? = null): String? {
        val partes = mutableListOf<String>()
        var i = inicio
        var vazias = 0

        if (!semente.isNullOrBlank()) partes.add(semente)

        while (i < linhas.size && partes.size < 4) {
            val original = linhas[i]
            val baixa = original.lowercase()
            if (isPairLine(baixa)) break
            if (isRotulo(baixa)) { i++; continue }   // rótulo não consome tentativas
            when {
                isAddressLine(baixa, original) ||
                    (partes.isEmpty() && isEnderecoPlausivel(baixa, original)) ||
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

    /** Percorre a tela juntando blocos de endereço, sem depender dos pares. */
    private fun blocosDeEndereco(linhas: List<String>): List<String> {
        val blocos = mutableListOf<String>()
        val partes = mutableListOf<String>()
        fun fechar() {
            if (partes.isNotEmpty()) {
                blocos.add(partes.joinToString(", "))
                partes.clear()
            }
        }
        for (l in linhas) {
            val baixa = l.lowercase()
            if (isPairLine(baixa)) { fechar(); continue }
            if (isRotulo(baixa)) continue
            val ehEndereco = isAddressLine(baixa, l)
            val ehComplemento = partes.isNotEmpty() && isContinuation(baixa, l)
            if (ehEndereco || ehComplemento) {
                partes.add(l.trim().trimEnd(',', '-', '–').trim())
            } else {
                fechar()
            }
        }
        fechar()
        return blocos
    }

    /** Endereço que o OCR colou na própria linha "tempo (km)", se houver. */
    private val ruidosDePar = Regex(
        """\b(?:de\s+)?(?:dist[âa]ncia|viagem|estimad[ao]s?|aprox(?:imadamente)?|at[ée]|id[aá])\b""",
        RegexOption.IGNORE_CASE
    )

    private fun restoDaLinhaDePar(linha: String): String? {
        var s = linha
        s = minTokenCi.replace(s, " ")
        s = kmTokenCi.replace(s, " ")
        s = metrosTokenCi.replace(s, " ")
        s = ruidosDePar.replace(s, " ")
        s = s.replace(simbolosPar, " ")
        s = s.replace(normEspacos, " ").trim().trim(',', '-', '–', ' ').trim()
        if (s.length < 4 || s.count { it.isLetter() } < 3) return null
        val baixa = s.lowercase()
        if (temParada(baixa) || isRotulo(baixa)) return null
        return if (isAddressLine(baixa, s)) s else null
    }

    /** Linha que parece um endereço completo (tem via, número ou vírgula). */
    private fun isAddressLine(baixa: String, original: String): Boolean {
        if (original.length < 5) return false
        if (!original.any { it.isLetter() }) return false
        if (temParada(baixa)) return false
        if (minHoraLinha.containsMatchIn(baixa)) return false
        val temVia = viaRegexes.any { via -> via.containsMatchIn(baixa) }
        return temVia || baixa.contains(",") || numeroHifen.containsMatchIn(baixa)
    }

    /**
     * Linha "plausível" para abrir um bloco logo após um par "tempo (km)":
     * só exige texto real no lugar onde o endereço costuma ficar, para não
     * perder destinos que não têm "rua/av." nem vírgula (ex.: "Shopping Ibirapuera").
     */
    private fun isEnderecoPlausivel(baixa: String, original: String): Boolean {
        if (original.length < 4) return false
        if (original.count { it.isLetter() } < 3) return false
        if (temParada(baixa)) return false
        if (isRotulo(baixa)) return false
        if (minToken.containsMatchIn(baixa)) return false
        return true
    }

    /** Complemento do endereço (bairro, cidade/UF) logo abaixo da linha principal. */
    private fun isContinuation(baixa: String, original: String): Boolean {
        if (original.length < 3) return false
        if (!original.any { it.isLetter() }) return false
        if (temParada(baixa)) return false
        return !minHoraLinha.containsMatchIn(baixa)
    }

    /** Palavras que indicam linha de botão/aviso, não de endereço. */
    private fun temParada(baixa: String): Boolean = paradaRegexes.any { it.containsMatchIn(baixa) }

    /** Rótulo de campo ("Origem", "Destino", "Buscar em"): não é endereço. */
    private fun isRotulo(baixa: String): Boolean {
        val t = baixa.trim().trimEnd(':').trim()
        if (t.isEmpty()) return false
        if (t in rotulosEndereco) return true
        return rotulosEndereco.any { it.contains(' ') && t.startsWith(it) }
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
