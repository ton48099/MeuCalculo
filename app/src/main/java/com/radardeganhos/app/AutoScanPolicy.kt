package com.radardeganhos.app

/**
 * Decide se a auto-análise pode rodar. Fica separado do serviço para poder ser
 * testado e para deixar explícito que ela obedece às configurações.
 *
 * A automática imita o botão manual: a cada X segundos ela aperta o botão sozinha,
 * em QUALQUER tela (Uber, 99, print, galeria, Facebook…). Não existe mais lista de
 * apps liberados — aquilo trancava a leitura em silêncio sempre que o pacote do app
 * mudava (foi o caso do 99 Motorista) e o motorista só via o botão funcionando.
 * Quem decide se há card na tela é o parser depois da leitura, não o pacote.
 *
 * Regras, na ordem em que são verificadas:
 *  1. auto-análise ligada;
 *  2. não há leitura em andamento;
 *  3. o card aberto na tela passou do período de graça (para não trocar a leitura
 *     que o motorista ainda está olhando);
 *  4. o intervalo entre leituras já passou.
 *
 * Nada mais: quando o intervalo vence, o serviço dispara a mesma leitura do botão
 * manual. [motivoParaNaoLer] devolve por que não pode ler (null = pode) e o serviço
 * escreve esse texto no diagnóstico, exceto o "esperando Xs" do ritmo normal —
 * assim todo bloqueio aparece na tela do app, nunca em silêncio.
 */
object AutoScanPolicy {

    /** Raízes de pacote das apps de corrida aceitas. */
    val RAIZES_CORRIDA = listOf(
        "com.ubercab",      // Uber e Uber Driver (com.ubercab.driver)
        "com.taxis99",      // 99 passageiro
        "com.app99",        // 99 Motorista e Entregador (com.app99.driver)
        "com.99app",
        "br.com.99",
        "com.nine"
    )

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
        @Suppress("UNUSED_PARAMETER") pkg: String,
        now: Long,
        lastScanAt: Long,
        scanning: Boolean,
        panelVisible: Boolean,
        panelAbertoHaMs: Long = 0L
    ): String? {
        if (!s.autoScan) return "auto-análise desligada"
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

/**
 * Decide de qual tela a leitura acontece (pacote + displayId).
 *
 * No modo desktop (Smart Connect / DeX) o Uber/99 roda numa tela separada do
 * celular: a leitura precisa mirar aquela tela, senão o screenshot sai com a
 * área de trabalho e o OCR não acha o card. As regras:
 *
 *  1. modo desktop desligado → sempre a tela principal (comportamento original);
 *  2. modo desktop ligado e um app de corrida aberto na tela externa → aquela tela;
 *  3. modo desktop ligado sem card na tela externa → volta para a tela principal.
 *
 * O card continua sendo desenhado no celular: é a parte que não depende de o
 * Motorola liberar janela de terceiro na tela externa.
 */
object MultiTela {
    /** displayId da tela do celular ([android.view.Display.DEFAULT_DISPLAY]). */
    const val TELA_PRINCIPAL = 0

    fun alvo(
        modoDesktop: Boolean,
        corridaNaTelaExterna: Pair<String, Int>?,
        principal: String?
    ): Pair<String, Int>? {
        if (modoDesktop && corridaNaTelaExterna != null) return corridaNaTelaExterna
        return principal?.let { it to TELA_PRINCIPAL }
    }
}
