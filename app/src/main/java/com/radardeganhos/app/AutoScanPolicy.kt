package com.radardeganhos.app

/**
 * Decide se a auto-análise pode rodar. Fica separada do serviço para poder ser
 * testado e para deixar explícito que ela obedece às configurações.
 *
 * Regras, na ordem em que são verificadas:
 *  1. auto-análise ligada;
 *  2. app é de corrida (Uber/99, por prefixo do pacote) ou o motorista liberou
 *     a leitura em qualquer app;
 *  3. não há leitura em andamento;
 *  4. o card aberto na tela passou do período de graça (para não trocar a leitura
 *     que o motorista ainda está olhando);
 *  5. o intervalo entre leituras já passou.
 *
 * [motivoParaNaoLer] devolve por que não pode ler (null = pode). O serviço manda
 * esse texto para o diagnóstico, então dá para saber no celular o que travou.
 */
object AutoScanPolicy {

    /** Prefixos de pacote das apps de corrida aceitas. */
    val RAIZES_CORRIDA = listOf(
        "com.ubercab",      // Uber e Uber Driver
        "com.taxis99",      // 99 e variantes
        "com.99app",
        "br.com.99",
        "com.nine"
    )

    /** Mantidos por compatibilidade com quem já usava as listas. */
    val UBER_PKGS = setOf("com.ubercab.driver", "com.ubercab", "com.ubercab.driver.lite")
    val NINE_PKGS = setOf("com.taxis99", "com.taxis99.driver", "com.taxis99.passenger")

    fun isRideApp(pkg: String): Boolean {
        val p = pkg.lowercase()
        return RAIZES_CORRIDA.any { raiz -> p == raiz || p.startsWith("$raiz.") }
    }

    fun intervaloMs(s: Settings): Long = s.autoIntervaloSeg.coerceIn(2, 120) * 1000L

    /**
     * Por quanto tempo o card aberto segura a auto-análise. Com duração fixada (0)
     * o card não some sozinho, então sem esta regra a leitura automática morreria
     * depois da primeira leitura — só voltaria ao botão manual.
     */
    fun gracaMs(s: Settings): Long = maxOf(s.cardDuracaoSeg, 10) * 1000L

    fun motivoParaNaoLer(
        s: Settings,
        pkg: String,
        now: Long,
        lastScanAt: Long,
        scanning: Boolean,
        panelVisible: Boolean,
        panelAbertoHaMs: Long = 0L
    ): String? {
        if (!s.autoScan) return "auto-análise desligada"
        if (!isRideApp(pkg) && !s.autoQualquerApp) return "app fora da lista"
        if (scanning) return "leitura em andamento"
        if (panelVisible && panelAbertoHaMs < gracaMs(s)) return "card aberto na tela"
        val restante = intervaloMs(s) - (now - lastScanAt)
        if (restante > 0) return "esperando ${restante / 1000}s"
        return null
    }

    fun shouldScan(
        s: Settings,
        pkg: String,
        now: Long,
        lastScanAt: Long,
        scanning: Boolean,
        panelVisible: Boolean,
        panelAbertoHaMs: Long = 0L
    ): Boolean = motivoParaNaoLer(s, pkg, now, lastScanAt, scanning, panelVisible, panelAbertoHaMs) == null
}