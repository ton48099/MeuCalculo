package com.radardeganhos.app

/**
 * Tempo escrito no card, em minutos.
 *
 * Existe porque o tempo com hora e minuto ("1 h e 19 min") quebrava em toda parte
 * que lia o tempo com uma regex solta de minutos: o `pairMin` casava só o "19 min"
 * do fim da frase e a corrida de 1 h 19 min entrava na conta como 19 min. Pior: com
 * o OCR comendo um dígito ("9 min"), a conta dava 478 km/h e o motorista via um
 * valor/hora que não existia. Aqui a frase inteira é lida de uma vez — se tem "h",
 * a hora entra na conta, sempre.
 *
 * Formatos aceitos (o card do Uber/99 e o que o OCR costuma devolver deles):
 *
 * ```
 * 1 h e 19 min   1h19min   1h 19min   1h19   1 hora e 19 minutos
 * 1h 3o min (zero lido como "o")    lh e l9 min (um lido como "l")
 * 1 h e 1 9 min (dígitos separados)   45 min   5Min   2 horas   1h
 * ```
 *
 * Tudo o que não é tempo devolve `null` — quem chama decide o que fazer.
 */
object Duracao {

    /** "1h19" / "1 h e 19 min" / "1h19min": hora + minuto. */
    private val horasMinutos = Regex("""(?<!\d)(\d{1,2})\s*h\s*(?:e\s*)?(\d{1,2})\s*(?:min|m)?$""")

    /** Só hora: "2 h", "2h", "2 horas". */
    private val soHoras = Regex("""(?<!\d)(\d{1,2})\s*h$""")

    /** Só minutos: "45 min", "5min", "12 minutos", "45 m". */
    private val soMinutos = Regex("""(?<!\d)(\d{1,3})\s*(?:min|m)$""")

    private val inicioHorasMinutos = Regex("""^(\d{1,2})\s*h\s*(?:e\s*)?(\d{1,2})\s*(?:min|m)?""")
    private val inicioSoHoras = Regex("""^(\d{1,2})\s*h\b""")
    private val inicioSoMinutos = Regex("""^(\d{1,3})\s*(?:min|m)\b""")

    /** Palavras que ficam entre o tempo e o km e não fazem parte do tempo. */
    private val enchimento = listOf(
        "de distancia", "distancia", "de", "e", "aprox", "aprox.", "cerca de", "viagem de", "viagem"
    )

    /** Quantos caracteres antes do km ainda podem ser o tempo daquele trecho. */
    private const val JANELA = 48

    /**
     * Normaliza a escrita do tempo para a forma curta ("1h19min"), tolerando o que o
     * OCR faz com os números. Idempotente: pode rodar sobre texto já normalizado.
     */
    fun limpar(texto: String): String {
        var t = texto.lowercase().replace('\u00A0', ' ')
        // "l"/"i"/"|" no lugar de 1: "lh e l9 min", "l2 min". O "h" logo depois
        // conta como pista: "lh e 19 min" é "1 h e 19 min" com o um lido como "l".
        t = t.replace(Regex("""(?<![\w])[li|](?=\s*(?:\d|h\b|min\b))"""), "1")
        t = t.replace(Regex("""(?<=\d)[li|](?=\d)"""), "1")
        // "3o min" -> "30 min" (zero lido como a letra o).
        t = t.replace(Regex("""(?<=\d)o(?=\s*(?:\d|min|h)\b)"""), "0")
        // Dígitos que o OCR separou: "1 9 min" -> "19 min".
        var antes: String
        do {
            antes = t
            t = t.replace(
                Regex("""(?<![\d.,])(\d)\s+(\d)(?=\s*(?:[.,]\s*\d+)?\s*(?:min|m|h|minutos?|horas?)\b)"""),
                "$1$2"
            )
        } while (t != antes)
        t = t.replace(Regex("""\bhoras?\b"""), "h")
        t = t.replace(Regex("""\bminutos?\b"""), "min")
        t = t.replace(Regex("""\bmins\b"""), "min")
        t = t.replace(Regex("""\s+"""), " ")
        return t.trim()
    }

    /** Tempo no fim do trecho (o texto todo é ou termina com um tempo). */
    fun minutos(texto: String): Double? = casarNoFim(limpar(texto))

    /**
     * Tempo escrito antes da posição [fim] (na prática: antes do "71,7 km" do trecho).
     *
     * Lê a frase inteira, não só o último número: em "1 h e 19 min (" quem manda é
     * o par hora+minuto (79), e não o "19 min" solto.
     */
    fun minutosAntesDe(texto: String, fim: Int): Double? {
        if (fim <= 0) return null
        val inicio = (fim - JANELA).coerceAtLeast(0)
        var t = limpar(texto.substring(inicio, fim.coerceAtMost(texto.length)))
        repeat(8) {
            val anterior = t
            t = t.trimEnd { !it.isLetterOrDigit() }
            for (palavra in enchimento) t = t.removeSuffix(palavra).trimEnd()
            if (t == anterior) return@repeat
        }
        return casarNoFim(t)
    }

    /**
     * Tempo escrito depois da posição [inicio] — cards que põem o km primeiro
     * ("12 km • 25 min").
     */
    fun minutosDepoisDe(texto: String, inicio: Int): Double? {
        if (inicio >= texto.length) return null
        val fim = (inicio + 16).coerceAtMost(texto.length)
        val trecho = texto.substring(inicio.coerceAtLeast(0), fim)
        // Se outro "km" aparece nesse pedaço, o tempo que vem depois é do PRÓXIMO
        // trecho: o card da 99 lista "283,5 km" sozinho em cima e o "5min • 2,3km"
        // embaixo — sem essa parada, os 5 min viravam o tempo de uma viagem de 283 km.
        if (trecho.lowercase().contains("km")) return null
        var t = limpar(trecho)
        repeat(6) { t = t.trimStart { !it.isLetterOrDigit() } }
        inicioHorasMinutos.find(t)?.let { m ->
            val h = m.groupValues[1].toDoubleOrNull() ?: return@let
            val mi = m.groupValues[2].toDoubleOrNull() ?: 0.0
            if (mi <= 59) return h * 60 + mi
        }
        inicioSoHoras.find(t)?.let { m ->
            return (m.groupValues[1].toDoubleOrNull() ?: return@let) * 60
        }
        inicioSoMinutos.find(t)?.let { m ->
            return m.groupValues[1].toDoubleOrNull()
        }
        return null
    }

    private fun casarNoFim(t: String): Double? {
        horasMinutos.find(t)?.let { m ->
            val h = m.groupValues[1].toDoubleOrNull() ?: return@let
            val mi = m.groupValues[2].toDoubleOrNull() ?: 0.0
            // "19 min" não é "1 9 min": minuto de hora vai até 59.
            if (mi > 59) return@let
            return h * 60 + mi
        }
        soHoras.find(t)?.let { m ->
            return (m.groupValues[1].toDoubleOrNull() ?: return@let) * 60
        }
        soMinutos.find(t)?.let { m ->
            return m.groupValues[1].toDoubleOrNull()
        }
        return null
    }
}
