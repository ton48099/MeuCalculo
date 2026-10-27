package com.radardeganhos.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Serviço que permite ler a tela (API 30+) e desenhar o botão flutuante.
 * Nenhum dado sai do aparelho: a captura é analisada na memória e descartada.
 *
 * Duas telas: com o "modo desktop" ligado, o card também é procurado na tela externa
 * do Smart Connect/DeX — onde o Uber/99 roda em janela — e a captura vai para aquele
 * display. O card de resultado continua aparecendo no celular.
 */
class RideAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: RideAccessibilityService? = null
            private set

        /** Frequência do relógio automático (não é o intervalo entre leituras). */
        private const val POLL_MS = 1200L
    }

    /**
     * A leitura está ligada? Diferente de "serviço conectado": o botão único da tela
     * principal liga e desliga por aqui sem precisar sair para os ajustes do sistema.
     */
    @Volatile
    var ativo: Boolean = false
        private set

    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private var screenReader: ScreenReader? = null
    private var overlay: OverlayController? = null

    private var scanning = false
    private var lastScanAt = 0L
    private var lastPackage: String? = null
    /** Assinatura do último card analisado, para não repetir a mesma leitura. */
    private var lastCardSignature: String? = null

    /** Display que a próxima captura deve usar (tela principal ou a do modo desktop). */
    @Volatile
    private var displayAlvo: Int = MultiTela.TELA_PRINCIPAL

    /** Overlay da tela externa: é ele que leva o botão R$ e o card para a 2ª tela. */
    private var overlayExterno: OverlayController? = null

    /** Cache da varredura das telas externas (varrer janelas a cada tique custa caro). */
    private var conferiuExternaEm = 0L
    private var cacheExterno: Pair<String, Int>? = null

    /** Quando o overlay externo foi reavaliado (a tela pode ligar depois do modo desktop). */
    private var atualizouOverlayEm = 0L

    /** Desde quando a leitura atual está rodando — watchdog de captura que não volta. */
    private var scanningDesde = 0L

    /** Último tique do relógio automático: prova, na tela, de que ele está vivo. */
    @Volatile
    private var ultimoTique = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        iniciar()
    }

    /**
     * Liga o botão flutuante e o relógio da leitura automática. Chamado ao conectar
     * e também quando o motorista reativa depois de desligar pelo botão único.
     */
    private fun iniciar() {
        if (!scope.isActive) scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        if (screenReader == null) screenReader = ScreenReader()
        if (overlay == null) {
            overlay = OverlayController(
                service = this,
                displayId = MultiTela.TELA_PRINCIPAL,
                // O botão do celular lê a tela do celular.
                onAnalyze = { analyzeScreen(manual = true, tela = MultiTela.TELA_PRINCIPAL) },
                // Card sumiu: o intervalo entre leituras volta a contar daqui.
                onPanelHide = {
                    lastScanAt = SystemClock.elapsedRealtime()
                    // Card fechado (no toque ou no tempo): a recusa agendada morre com ele.
                    cancelarRecusas()
                }
            )
        }
        overlay?.showBubble()
        atualizarOverlayExterno()
        ativo = true
        handler.removeCallbacks(poller)
        handler.postDelayed(poller, POLL_MS)
    }

    /**
     * Cria (ou derruba) o overlay da tela externa — mesma janela de acessibilidade do
     * celular, só que montada naquele display. Se o fabricante recusar, o card continua
     * saindo no celular e o diagnóstico explica o porquê.
     */
    private fun atualizarOverlayExterno() {
        atualizouOverlayEm = SystemClock.elapsedRealtime()
        val desejado = if (Prefs.load(this).modoDesktop) displayExternoDisponivel() else null
        val atual = overlayExterno
        if (desejado == null) {
            if (atual != null) {
                atual.removeAll()
                overlayExterno = null
            }
            return
        }
        if (atual != null && atual.tela == desejado) return
        atual?.removeAll()
        overlayExterno = null
        val novo = OverlayController(
            service = this,
            displayId = desejado,
            onAnalyze = { analyzeScreen(manual = true, tela = desejado) },
            onPanelHide = { lastScanAt = SystemClock.elapsedRealtime() }
        )
        if (novo.disponivel) {
            novo.showBubble()
            overlayExterno = novo
            AutoDiag.anotar(this, "botão R$ aberto na tela externa (display $desejado)", forcar = true)
        } else {
            novo.removeAll()
            AutoDiag.anotar(
                this,
                "a tela externa recusou a janela do Radar (display $desejado) — o card sai no celular",
                forcar = true
            )
        }
    }

    /** Primeira tela que não é a do celular, se houver conectada agora. */
    private fun displayExternoDisponivel(): Int? = try {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        dm.displays.firstOrNull { it.displayId != MultiTela.TELA_PRINCIPAL }?.displayId
    } catch (t: Throwable) {
        null
    }

    /** Os dois overlays juntos: o card aberto em qualquer tela vale como card aberto. */
    private fun painelVisivel(): Boolean =
        overlay?.isPanelVisible == true || overlayExterno?.isPanelVisible == true

    private fun painelAbertoHa(now: Long): Long =
        minOf(overlay?.panelAbertoHa(now) ?: Long.MAX_VALUE, overlayExterno?.panelAbertoHa(now) ?: Long.MAX_VALUE)

    /**
     * Uma linha com o que a leitura automática está fazendo AGORA — atualizada ao vivo
     * na tela de Card e Cores, para não depender do motorista adivinhar o que travou.
     */
    fun estadoAoVivo(s: Settings): String {
        if (!ativo) return "leitura desligada — toque em ATIVAR LEITURA DE TELA"
        val now = SystemClock.elapsedRealtime()
        val motivo = AutoScanPolicy.motivoParaNaoLer(
            s = s,
            pkg = pacoteAtivo() ?: lastPackage ?: "?",
            now = now,
            lastScanAt = lastScanAt,
            scanning = scanning,
            panelVisible = painelVisivel(),
            panelAbertoHaMs = painelAbertoHa(now)
        )
        val tela = if (displayAlvo == MultiTela.TELA_PRINCIPAL) "celular" else "2ª tela"
        val onde = if (overlayExterno?.disponivel == true) " · botão R$ na 2ª tela" else ""
        val relogio = if (ultimoTique == 0L) "relógio parado (feche e abra o app)"
        else "relógio rodando · último tique há ${(now - ultimoTique) / 1000}s"
        val base = when {
            scanning -> "lendo a tela agora · tela: $tela$onde"
            motivo == null -> "pronta · lê a cada ${s.autoIntervaloSeg}s · tela: $tela$onde"
            motivo.startsWith("esperando") -> "$motivo para a próxima leitura · tela: $tela$onde"
            else -> "$motivo · tela: $tela$onde"
        }
        val recusa = recusas.minByOrNull { it.fimEm }
            ?.let { " · ${if (it.aviso) "recusaria" else "recusando"} ${it.pkg.substringAfterLast('.')} em ${it.restantes(now)}s" }
            ?: ""
        return "$base$recusa · $relogio"
    }

    /** Tique do relógio automático: 1,2 em 1,2 s decide se está na hora de ler. */
    private val poller = object : Runnable {
        override fun run() {
            if (ativo) tickAutomatico()
            handler.postDelayed(this, POLL_MS)
        }
    }

    private fun tickAutomatico() {
        val settings = Prefs.load(this)
        val now = SystemClock.elapsedRealtime()
        ultimoTique = now

        // Captura que nunca voltou: destrava sozinho, senão a leitura morre até
        // alguém reiniciar o serviço. É o tipo de coisa que "parou de funcionar".
        if (scanning && now - scanningDesde > 25_000L) {
            scanning = false
            AutoDiag.anotar(this, "leitura presa há 25s — destravada", forcar = true)
        }
        // A tela externa pode ter sido ligada (ou desligada) depois do modo desktop.
        if (settings.modoDesktop && now - atualizouOverlayEm > 5_000L) atualizarOverlayExterno()

        val alvo = resolverAlvo(settings)
        val pkg = alvo?.first
        // O próprio Radar em primeiro plano: não faz sentido ler a tela do app.
        if (pkg == packageName) return
        if (alvo != null) {
            lastPackage = alvo.first
            displayAlvo = alvo.second
        }

        val motivo = AutoScanPolicy.motivoParaNaoLer(
            s = settings,
            pkg = pkg ?: lastPackage ?: "?",
            now = now,
            lastScanAt = lastScanAt,
            scanning = scanning,
            panelVisible = painelVisivel(),
            panelAbertoHaMs = painelAbertoHa(now)
        )
        if (motivo == null) {
            // Intervalo venceu: dispara EXATAMENTE o que o botão manual faria.
            lastScanAt = now
            AutoDiag.anotar(this, "${pkg ?: lastPackage ?: "tela"} · lendo sozinho agora", forcar = true)
            handler.removeCallbacks(autoScan)
            handler.postDelayed(autoScan, 150L)
        } else if (!motivo.startsWith("esperando")) {
            // Tudo que barra a leitura aparece no diagnóstico, nunca em silêncio.
            AutoDiag.anotar(this, "${pkg ?: lastPackage ?: "?"} · $motivo")
        }
    }

    /**
     * Escolhe de onde ler: o app de corrida da tela externa (modo desktop) tem
     * prioridade; senão, a tela principal. Devolve null quando não há nada a ler.
     */
    private fun resolverAlvo(s: Settings): Pair<String, Int>? =
        MultiTela.alvo(s.modoDesktop, corridaNaTelaExterna(s), pacoteAtivo() ?: lastPackage)

    /** Varredura das telas externas, com cache — só roda com o modo desktop ligado. */
    private fun corridaNaTelaExterna(s: Settings): Pair<String, Int>? {
        if (!s.modoDesktop) return null
        val agora = SystemClock.elapsedRealtime()
        if (agora - conferiuExternaEm < 1500L) return cacheExterno
        conferiuExternaEm = agora
        cacheExterno = procurarCorridaEmTelaExterna()
        return cacheExterno
    }

    /**
     * App de corrida aberto numa tela que não é a do celular (modo desktop do Smart
     * Connect / DeX). Só funciona com a janela recuperável, que a configuração do
     * serviço já libera (flagRetrieveInteractiveWindows).
     */
    private fun procurarCorridaEmTelaExterna(): Pair<String, Int>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val janelas = try {
            windowsOnAllDisplays
        } catch (t: Throwable) {
            null
        } ?: return null

        for (i in 0 until janelas.size()) {
            val displayId = janelas.keyAt(i)
            if (displayId == MultiTela.TELA_PRINCIPAL) continue
            for (janela in janelas.valueAt(i)) {
                val pkg = try {
                    janela.root?.packageName?.toString()
                } catch (t: Throwable) {
                    null
                }
                if (pkg != null && AutoScanPolicy.isRideApp(pkg)) return pkg to displayId
            }
        }
        return null
    }

    /** Pacote em primeiro plano, lido da árvore de acessibilidade (sem depender de evento). */
    private fun pacoteAtivo(): String? = try {
        rootInActiveWindow?.packageName?.toString()
    } catch (t: Throwable) {
        null
    }

    /**
     * Só acompanha qual app está em primeiro plano. Quem dispara a leitura é o
     * relógio de [POLL_MS]: disparar por evento não funcionava na prática (Uber/99
     * quase nunca emitem evento quando o card aparece) e ainda varria a árvore de
     * acessibilidade a cada frame de animação, sem nunca virar leitura.
     *
     * O único evento que interessa além disso é o clique: é por ele que o app aprende
     * onde fica o X de cada app quando o motorista recusa uma oferta à mão.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return
        lastPackage = pkg
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) aprenderBotaoX(pkg, event)
    }

    // ------------------------------------------------------------- recusa do X

    /** Uma recusa agendada: de qual app, qual oferta, quando vence e se é só aviso. */
    private class RecusaPendente(
        val pkg: String,
        val displayId: Int,
        val assinatura: String,
        val motivos: String,
        val valor: Double,
        val aviso: Boolean,
        val fimEm: Long,
        val overlay: OverlayController?
    ) {
        fun restantes(agora: Long): Int =
            ((fimEm - agora + 999L) / 1000L).toInt().coerceAtLeast(0)
    }

    /** Botão achado na árvore: os dados para a escolha e o nó que recebe o clique. */
    private class CandidatoX(val dados: BotaoCandidato, val paraClicar: AccessibilityNodeInfo)

    /** Resultado da procura pelo X: o nó (quando achou), o que é ele, a raiz e o erro. */
    private class AlvoX(
        val paraClicar: AccessibilityNodeInfo?,
        val dados: BotaoCandidato?,
        val erro: String?,
        val raiz: AccessibilityNodeInfo? = null
    )

    private val recusas = mutableListOf<RecusaPendente>()
    private var tickRecusa: Runnable? = null
    private var overlayDaRecusa: OverlayController? = null

    /** Ofertas recusadas há pouco: a mesma oferta não é recusada duas vezes. */
    private val recusadas = mutableMapOf<String, Long>()

    /**
     * Agenda a recusa das ofertas que não batem os mínimos. Uma leitura nova cancela
     * as anteriores — o que vale é o card que está na tela agora. Cada card sabe de
     * qual app ele veio, senão o app apertaria o X do app errado no modo desktop.
     */
    private fun agendarRecusas(
        analises: List<RideAnalysis>,
        pacotes: List<String?>,
        s: Settings,
        destino: OverlayController?,
        displayId: Int
    ) {
        cancelarRecusas()
        if (!s.recusaAtiva) return
        val agora = SystemClock.elapsedRealtime()
        recusadas.entries.removeAll { agora - it.value > 90_000L }

        analises.forEachIndexed { i, a ->
            val motivos = Recusa.motivos(a, s)
            if (motivos.isEmpty()) return@forEachIndexed
            val pkg = pacotes.getOrNull(i) ?: return@forEachIndexed
            if (!AutoScanPolicy.isRideApp(pkg)) return@forEachIndexed
            val assinatura = cardSignature(a.card)
            if (recusadas.containsKey(assinatura)) return@forEachIndexed
            recusas.add(
                RecusaPendente(
                    pkg = pkg,
                    displayId = displayId,
                    assinatura = assinatura,
                    motivos = motivos.joinToString(" · "),
                    valor = a.bruto,
                    aviso = s.recusaSoAviso,
                    fimEm = agora + s.recusaSegundos * 1000L,
                    overlay = destino
                )
            )
        }
        if (recusas.isEmpty()) return

        if (destino != null) overlayDaRecusa = destino
        AutoDiag.anotar(
            this,
            "${if (s.recusaSoAviso) "aviso" else "recusa"} em ${s.recusaSegundos}s · ${recusas.first().motivos}",
            forcar = true
        )
        mostrarContagem()
        agendarTickRecusa()
    }

    /** Relógio da contagem: 4 vezes por segundo, só enquanto há recusa pendente. */
    private fun agendarTickRecusa() {
        if (tickRecusa != null || recusas.isEmpty()) return
        val tick = object : Runnable {
            override fun run() {
                tickRecusa = null
                if (recusas.isEmpty()) return
                val agora = SystemClock.elapsedRealtime()
                val venceram = recusas.filter { agora >= it.fimEm }
                recusas.removeAll(venceram)
                venceram.forEach { dispararRecusa(it) }
                mostrarContagem()
                if (recusas.isNotEmpty()) agendarTickRecusa()
            }
        }
        tickRecusa = tick
        handler.postDelayed(tick, 250L)
    }

    /** Escreve (ou apaga) a linha da contagem no card. */
    private fun mostrarContagem() {
        val pendente = recusas.minByOrNull { it.fimEm }
        val alvo = pendente?.overlay ?: overlayDaRecusa ?: return
        val texto = if (pendente == null) {
            null
        } else {
            val faltam = pendente.restantes(SystemClock.elapsedRealtime())
            val inicio = if (pendente.aviso) "🚫 RECUSARIA em ${faltam}s (só aviso)" else "🚫 RECUSANDO em ${faltam}s"
            "$inicio · ${pendente.motivos} · toque no card para cancelar"
        }
        alvo.atualizarContagem(texto)
        if (texto == null) overlayDaRecusa = null
    }

    /** Chegou a hora: acha o X daquele app e clica (ou só avisa, no modo aviso). */
    private fun dispararRecusa(p: RecusaPendente) {
        recusadas[p.assinatura] = SystemClock.elapsedRealtime()
        val alvo = localizarX(p.displayId, p.pkg)
        val achado = alvo.dados
        val resumo = if (achado != null) Recusa.descrever(achado) else alvo.erro ?: "sem o X"

        // A oferta ainda está aí? Se o motorista aceitou (ou ela expirou) enquanto a
        // contagem corria, clicar no X acertaria outra coisa — então nada é clicado.
        val confirmado = alvo.raiz?.let { ofertaAindaNaTela(it, p.valor) }

        if (p.aviso) {
            val linha = if (achado != null) "achei o X ($resumo)" else resumo
            val conf = when (confirmado) {
                true -> " · oferta conferida na tela"
                false -> " · a oferta saiu da tela"
                null -> " · não deu para conferir a oferta"
            }
            AutoDiag.anotar(this, "aviso: recusaria · $linha$conf", forcar = true)
            p.overlay?.atualizarContagem("🚫 MODO AVISO · $linha · não cliquei")
            return
        }
        if (confirmado == false) {
            AutoDiag.anotar(
                this,
                "não recusei: a oferta de ${Report.money(p.valor)} não está mais na tela",
                forcar = true
            )
            p.overlay?.atualizarContagem("🚫 não recusei · a oferta saiu da tela")
            return
        }
        if (alvo.paraClicar == null) {
            AutoDiag.anotar(this, "não recusei: $resumo", forcar = true)
            p.overlay?.atualizarContagem("🚫 não recusei · $resumo")
            return
        }
        val clicou = try {
            alvo.paraClicar.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } catch (t: Throwable) {
            false
        }
        if (clicou) {
            AutoDiag.anotar(
                this,
                "recusei (${p.motivos}) no ${appCurto(p.pkg)} · $resumo",
                forcar = true
            )
            p.overlay?.atualizarContagem("🚫 RECUSADA · ${p.motivos}")
        } else {
            AutoDiag.anotar(this, "não consegui clicar no X do ${appCurto(p.pkg)}", forcar = true)
            p.overlay?.atualizarContagem("🚫 não consegui clicar no X do ${appCurto(p.pkg)}")
        }
    }

    /** Derruba as recusas agendadas (leitura nova, card fechado, leitura desligada). */
    private fun cancelarRecusas() {
        recusas.clear()
        tickRecusa?.let { handler.removeCallbacks(it) }
        tickRecusa = null
        overlayDaRecusa?.atualizarContagem(null)
        overlayDaRecusa = null
    }

    /**
     * Procura o X do app: a janela dele na tela lida, a árvore desses botões e a
     * escolha pelo [Recusa]. Nada é clicado aqui — quem clica é o [dispararRecusa].
     */
    private fun localizarX(displayId: Int, pkg: String): AlvoX {
        val janela = janelaDoApp(displayId, pkg)
            ?: return AlvoX(null, null, "não achei a janela do ${appCurto(pkg)}")
        val raiz = try {
            janela.root
        } catch (t: Throwable) {
            null
        } ?: return AlvoX(null, null, "sem acesso à tela do ${appCurto(pkg)}")
        val bounds = Rect()
        runCatching { janela.getBoundsInScreen(bounds) }
        if (bounds.isEmpty) return AlvoX(null, null, "janela do ${appCurto(pkg)} sem posição")

        val candidatos = candidatosDaJanela(raiz, bounds)
        if (candidatos.isEmpty()) return AlvoX(null, null, "nada clicável na tela do ${appCurto(pkg)}")
        val idx = Recusa.escolherX(
            candidatos.map { it.dados },
            bounds,
            Prefs.xAprendido(this, pkg)
        )
        if (idx == null) {
            return AlvoX(
                null,
                null,
                "não achei o X do ${appCurto(pkg)} — recuse uma oferta à mão com o card na tela que eu aprendo"
            )
        }
        return AlvoX(candidatos[idx].paraClicar, candidatos[idx].dados, null, raiz)
    }

    /**
     * A oferta lida ainda está na tela? Confere o valor lido com os textos que o app
     * expõe na árvore de acessibilidade: aceitou ou expirou, o texto não está mais lá.
     *
     * Devolve null quando a tela não expõe texto nenhum (app desenhado em canvas, por
     * exemplo): aí não há o que conferir e a contagem decide. "Não sei" não pode virar
     * "não clique" — senão a recusa nunca funcionaria nesses apps.
     */
    private fun ofertaAindaNaTela(
        raiz: AccessibilityNodeInfo,
        valor: Double,
        limite: Int = 900
    ): Boolean? {
        val textos = mutableListOf<String>()
        val fila = ArrayDeque<AccessibilityNodeInfo>()
        fila.add(raiz)
        var visitados = 0
        while (fila.isNotEmpty() && visitados < limite) {
            val no = fila.removeFirst()
            visitados++
            no.text?.toString()?.let { textos.add(it) }
            no.contentDescription?.toString()?.let { textos.add(it) }
            for (i in 0 until no.childCount) {
                runCatching { no.getChild(i) }.getOrNull()?.let { fila.add(it) }
            }
        }
        return Recusa.ofertaNaTela(valor, textos)
    }

    /**
     * Botões clicáveis da janela do app (com o pai clicável mais próximo, que é quem
     * recebe o clique quando o alvo é um ícone). Percorre uma vez, com teto de nós:
     * a árvore de um app de corrida com o mapa aberto é grande, e isso roda só quando
     * a oferta é ruim e a contagem chega ao fim.
     */
    private fun candidatosDaJanela(
        raiz: AccessibilityNodeInfo,
        janela: Rect,
        limite: Int = 900
    ): List<CandidatoX> {
        val saida = mutableListOf<CandidatoX>()
        val fila = ArrayDeque<Pair<AccessibilityNodeInfo, AccessibilityNodeInfo?>>()
        fila.add(raiz to null)
        var visitados = 0
        while (fila.isNotEmpty() && visitados < limite) {
            val (no, clicavelAcima) = fila.removeFirst()
            visitados++
            val clicavel = if (no.isClickable) no else clicavelAcima
            val r = Rect()
            runCatching { no.getBoundsInScreen(r) }
            val cabeNaJanela = !r.isEmpty &&
                r.width() <= janela.width() && r.height() <= janela.height()
            if (clicavel != null && cabeNaJanela) {
                saida.add(
                    CandidatoX(
                        dados = BotaoCandidato(
                            id = no.viewIdResourceName?.substringAfterLast('/') ?: "",
                            descricao = no.contentDescription?.toString() ?: "",
                            texto = no.text?.toString() ?: "",
                            esquerda = r.left,
                            topo = r.top,
                            direita = r.right,
                            base = r.bottom,
                            clicavel = no.isClickable
                        ),
                        paraClicar = clicavel
                    )
                )
            }
            for (i in 0 until no.childCount) {
                val filho = runCatching { no.getChild(i) }.getOrNull() ?: continue
                fila.add(filho to clicavel)
            }
        }
        return saida
    }

    /** Janela do app de corrida na tela que está sendo lida. */
    private fun janelaDoApp(displayId: Int, pkg: String): AccessibilityWindowInfo? {
        val porTela = runCatching { windowsOnAllDisplays }.getOrNull()
        porTela?.get(displayId)?.let { janelas ->
            for (i in 0 until janelas.size) {
                if (pacoteDaJanela(janelas[i]) == pkg) return janelas[i]
            }
        }
        if (displayId == MultiTela.TELA_PRINCIPAL && pacoteAtivo() == pkg) {
            return runCatching { windows }.getOrNull()?.firstOrNull { pacoteDaJanela(it) == pkg }
        }
        return null
    }

    private fun pacoteDaJanela(janela: AccessibilityWindowInfo): String? =
        runCatching { janela.root?.packageName?.toString() }.getOrNull()

    /**
     * O motorista recusou uma oferta à mão, com o card do Radar na tela: é exatamente
     * o X que a recusa automática precisa achar. Guarda a impressão do botão (id,
     * descrição e posição dentro da janela) por app — o Uber e a 99 põem o X em
     * lugares diferentes, e assim o app não depende de adivinhar layout de ninguém.
     */
    private fun aprenderBotaoX(pkg: String, event: AccessibilityEvent) {
        if (!AutoScanPolicy.isRideApp(pkg)) return
        val pendente = recusas.firstOrNull { it.pkg == pkg }
        if (pendente == null && !painelVisivel()) return

        val no = try {
            event.source
        } catch (t: Throwable) {
            null
        } ?: return
        val janela = janelaDoApp(pendente?.displayId ?: displayAlvo, pkg) ?: return
        val bounds = Rect()
        runCatching { janela.getBoundsInScreen(bounds) }
        val r = Rect()
        runCatching { no.getBoundsInScreen(r) }
        if (bounds.isEmpty || r.isEmpty) return

        val dados = BotaoCandidato(
            id = no.viewIdResourceName?.substringAfterLast('/') ?: "",
            descricao = no.contentDescription?.toString() ?: "",
            texto = no.text?.toString() ?: "",
            esquerda = r.left,
            topo = r.top,
            direita = r.right,
            base = r.bottom,
            clicavel = no.isClickable
        )
        // Sem recusa pendente, só aprende se o que foi tocado PARECE o botão de
        // recusar (pequeno, no alto, colado numa borda): assim tocar no mapa não
        // ensina o app a clicar no lugar errado.
        if (pendente == null && Recusa.pontuar(dados, bounds, aprendido = null) <= 0) return

        Prefs.salvarXAprendido(this, pkg, XAprendido.de(dados, bounds))
        AutoDiag.anotar(
            this,
            "aprendi o X do ${appCurto(pkg)}: ${Recusa.descrever(dados)}",
            forcar = true
        )
        // Ele recusou à mão: a contagem pendente não tem mais o que fazer.
        cancelarRecusas()
    }

    /** "Uber" / "99" para as mensagens do diagnóstico. */
    private fun appCurto(pkg: String): String = when {
        pkg.contains("uber", ignoreCase = true) -> "Uber"
        AutoScanPolicy.isRideApp(pkg) -> "99"
        else -> pkg
    }

    private val autoScan = Runnable { analyzeScreen(manual = false, tela = null) }

    override fun onInterrupt() {
        scanning = false
    }

    /**
     * Captura uma tela, lê com OCR e mostra o painel de resultado.
     *
     * @param tela qual tela ler (0 = celular, outro = tela externa). null = deixa a
     *             política decidir, que é o que a auto-análise usa.
     */
    fun analyzeScreen(manual: Boolean, tela: Int? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (manual) overlay?.toast("A leitura de tela requer Android 11 ou superior")
            return
        }
        if (!ativo && !manual) return
        val capacidade = serviceInfo?.capabilities ?: 0
        if (capacidade and AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT == 0) {
            if (manual) overlay?.toast("Reative o serviço de acessibilidade para liberar a captura de tela")
            else AutoDiag.anotar(this, "captura de tela bloqueada", forcar = true)
            return
        }
        if (scanning) return

        val settings = Prefs.load(this)
        val resolvido = resolverAlvo(settings)
        val displayId = tela ?: resolvido?.second ?: displayAlvo
        val pkgLido = resolvido?.first ?: lastPackage
        // Sem app identificado o botão manual orienta o motorista; a automática
        // lê mesmo assim (é o botão apertado sozinho — o parser é quem decide
        // se há card na tela, não o pacote).
        if (pkgLido == null && tela == null && manual) {
            overlay?.toast("Abra o card do Uber ou da 99 para eu ler")
            return
        }
        if (pkgLido != null) lastPackage = pkgLido
        displayAlvo = displayId

        scanning = true
        scanningDesde = SystemClock.elapsedRealtime()
        val naTelaPrincipal = displayId == MultiTela.TELA_PRINCIPAL
        val nomeTela = if (naTelaPrincipal) "" else " (2ª tela)"
        val overlayTela = if (naTelaPrincipal) overlay else overlayExterno

        scope.launch {
            var bmp: Bitmap? = null
            try {
                // Esconde o overlay da tela que vai ser capturada, senão ele vira parte da print.
                (overlayTela ?: overlay)?.hideForCapture()
                delay(160L)
                bmp = captureScreen(displayId)
                if (bmp == null) {
                    // O sistema limita a frequência das capturas; a auto-análise
                    // pode cair logo após outra leitura. Tenta mais uma vez.
                    delay(360L)
                    bmp = captureScreen(displayId)
                }
                (overlayTela ?: overlay)?.restoreAfterCapture()

                if (bmp == null) {
                    if (manual) overlay?.toast("Não consegui capturar a tela. Verifique a permissão de acessibilidade.")
                    else AutoDiag.anotar(
                        this@RideAccessibilityService,
                        "captura falhou$nomeTela",
                        forcar = true
                    )
                    return@launch
                }
                val captura = bmp ?: return@launch

                val reader = screenReader ?: ScreenReader().also { screenReader = it }

                // A leitura começa abaixo do card do Radar. Sem isso o OCR lia o
                // próprio card e o topo do app (saldo, menu) — e o parser, que pega
                // o maior R$ do texto, devolvia o saldo como se fosse a corrida.
                val limiteLeitura = limiteDaLeitura(displayId, settings)
                val abaixoDoCard = inicioNaCaptura(displayId, captura, limiteLeitura)

                // Uber e 99 lado a lado: recorta a janela de cada um e lê separado.
                val recortes = recortesPorJanela(captura, displayId, limiteLeitura)
                var (lidos, falhaOcr) = lerCards(recortes, reader, pkgLido)
                recortes.forEach { if (it.bmp !== captura) it.bmp.recycle() }
                if (lidos.isEmpty()) {
                    // Ou não havia janelas para dividir, ou o corte não deu certo:
                    // nesse caso o resto da tela é lido do jeito de sempre — só que
                    // sem o pedaço de cima, onde estão o card e o saldo.
                    val areaLida = recorteAbaixoDaTela(captura, abaixoDoCard)
                    val inteira = lerCards(listOf(ParLeitura(areaLida ?: captura, pkgLido)), reader, pkgLido)
                    areaLida?.recycle()
                    if (inteira.first.isNotEmpty()) lidos = inteira.first
                    else falhaOcr = falhaOcr ?: inteira.second
                }
                val cards = lidos.map { it.card }

                if (falhaOcr != null && cards.isEmpty()) {
                    if (manual) overlay?.toast("Falha no OCR: $falhaOcr")
                    else AutoDiag.anotar(this@RideAccessibilityService, "falha no OCR: $falhaOcr", forcar = true)
                    return@launch
                }
                if (cards.isEmpty()) {
                    if (manual) overlay?.toast("Nenhum card de corrida nesta tela")
                    else AutoDiag.anotar(
                        this@RideAccessibilityService,
                        "sem card em ${pkgLido ?: "?"}$nomeTela",
                        forcar = true
                    )
                    return@launch
                }

                // Auto-análise não repete o mesmo conjunto de cards; a manual sempre responde.
                val signature = cards.map { cardSignature(it) }.sorted().joinToString(",")
                if (!manual && signature == lastCardSignature) {
                    AutoDiag.anotar(this@RideAccessibilityService, "mesmo card de ${pkgLido ?: "?"}")
                    return@launch
                }
                lastCardSignature = signature

                val analises = cards.map { Calculator.analyze(it, settings) }
                // Guarda no histórico (o mesmo card lido duas vezes não duplica).
                var primeiroRegistrado = false
                analises.forEach { analysis ->
                    if (RideHistory.record(this@RideAccessibilityService, analysis) && !primeiroRegistrado) {
                        primeiroRegistrado = true
                        // Print da tela da corrida, quando a opção está ligada.
                        if (settings.printCorrida) {
                            val nome = withContext(Dispatchers.IO) {
                                Prints.salvar(this@RideAccessibilityService, captura)
                            }
                            if (nome != null) {
                                RideHistory.attachPrint(this@RideAccessibilityService, nome)
                            }
                        }
                    }
                }
                if (!manual) {
                    AutoDiag.anotar(
                        this@RideAccessibilityService,
                        "${analises.size} card(s) lidos em ${pkgLido ?: "?"}$nomeTela",
                        forcar = true
                    )
                }
                // O resultado aparece na tela que foi lida; sem overlay ali, cai no celular.
                val destino = overlayTela?.takeIf { it.disponivel } ?: overlay
                destino?.showResults(analises, settings)

                // Recusa automática: só depois de mostrar o card, e só para as ofertas
                // que não batem os mínimos (cada card sabe de qual app ele veio).
                agendarRecusas(analises, lidos.map { it.pkg }, settings, destino, displayId)
            } catch (t: Throwable) {
                (overlayTela ?: overlay)?.restoreAfterCapture()
                if (manual) overlay?.toast("Erro: ${t.message}")
                else AutoDiag.anotar(this@RideAccessibilityService, "erro: ${t.message ?: "?"}", forcar = true)
            } finally {
                bmp?.recycle()
                scanning = false
            }
        }
    }

    /**
     * Fim do card + folga na tela que está sendo lida, em pixels de tela
     * (0 = sem card no caminho: lê desde o topo, como antes).
     */
    private fun limiteDaLeitura(displayId: Int, s: Settings): Int {
        val dono = if (displayId == MultiTela.TELA_PRINCIPAL) overlay else overlayExterno
        return dono?.inicioAbaixoDoCard(s) ?: 0
    }

    /** O mesmo limite em pixels da captura, que pode ter outra resolução (0 = sem corte). */
    private fun inicioNaCaptura(displayId: Int, captura: Bitmap, limiteNaTela: Int): Int {
        if (limiteNaTela <= 0) return 0
        val metricas = metricasDaTela(displayId) ?: return 0
        return AreaLeitura.inicioNaCaptura(
            alturaCaptura = captura.height,
            alturaTela = metricas.heightPixels,
            inicioNaTela = limiteNaTela
        )
    }

    /** O pedaço da captura abaixo de [inicio]; null quando é para ler a tela inteira. */
    private fun recorteAbaixoDaTela(captura: Bitmap, inicio: Int): Bitmap? {
        if (inicio <= 0 || inicio >= captura.height) return null
        return try {
            Bitmap.createBitmap(captura, 0, inicio, captura.width, captura.height - inicio)
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Recorta a janela de cada app de corrida da captura da tela externa. Com o Uber
     * e a 99 abertos lado a lado isso separa os dois cards, que um OCR da tela inteira
     * misturaria. Devolve vazio quando não dá para dividir (um app só, ou sem janelas).
     *
     * @param limiteNaTela limite da leitura (px de tela): cada janela é cortada nele,
     *        então também aqui o topo do app não entra na conta.
     */
    private fun recortesPorJanela(captura: Bitmap, displayId: Int, limiteNaTela: Int): List<ParLeitura> {
        if (displayId == MultiTela.TELA_PRINCIPAL) return emptyList()
        val janelas = try {
            windowsOnAllDisplays?.get(displayId)
        } catch (t: Throwable) {
            null
        } ?: return emptyList()

        val metricas = metricasDaTela(displayId) ?: return emptyList()
        // Janela que sobrar menor que isso não vale um OCR próprio.
        val alturaMinima = metricas.heightPixels / 20

        // Uma janela por app: se o mesmo app tiver duas janelas, fica só a primeira.
        val porApp = LinkedHashMap<String, android.graphics.Rect>()
        for (janela in janelas) {
            val pkg = try {
                janela.root?.packageName?.toString()
            } catch (t: Throwable) {
                null
            } ?: continue
            if (!AutoScanPolicy.isRideApp(pkg)) continue
            val retangulo = android.graphics.Rect()
            try {
                janela.getBoundsInScreen(retangulo)
            } catch (t: Throwable) {
                continue
            }
            if (retangulo.isEmpty) continue
            if (limiteNaTela > 0) retangulo.top = maxOf(retangulo.top, limiteNaTela)
            if (retangulo.height() < alturaMinima) continue
            porApp.putIfAbsent(pkg, retangulo)
        }
        if (porApp.size < 2) return emptyList()

        val ordenados = porApp.entries.toList()
        val retangulos = Recortes.dividir(
            larguraCaptura = captura.width,
            alturaCaptura = captura.height,
            larguraTela = metricas.widthPixels,
            alturaTela = metricas.heightPixels,
            janelas = ordenados.map { it.value }
        )
        if (retangulos.isEmpty()) return emptyList()

        val recortes = mutableListOf<ParLeitura>()
        for ((i, retangulo) in retangulos.withIndex()) {
            val corte = try {
                Bitmap.createBitmap(captura, retangulo.left, retangulo.top, retangulo.width(), retangulo.height())
            } catch (t: Throwable) {
                recortes.forEach { it.bmp.recycle() }
                return emptyList()
            }
            // Guarda de qual janela veio o pedaço: é o que diz de qual app é o card
            // (e, por consequência, qual X apertar se a oferta for ruim).
            recortes.add(ParLeitura(corte, ordenados[i].key))
        }
        return recortes
    }

    /** Tamanho de uma tela (para mapear as janelas para os pixels da captura). */
    private fun metricasDaTela(displayId: Int): android.util.DisplayMetrics? {
        return try {
            val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = dm.getDisplay(displayId) ?: return null
            val metricas = android.util.DisplayMetrics()
            display.getMetrics(metricas)
            if (metricas.widthPixels > 0) metricas else null
        } catch (t: Throwable) {
            null
        }
    }

    /** Um pedaço de captura e de qual app ele veio (null quando veio da tela inteira). */
    private class ParLeitura(val bmp: Bitmap, val pkg: String?)

    /** Um card lido, com o app de onde ele veio. */
    private class CardLido(val card: RideCard, val pkg: String?)

    /**
     * OCR + parser de cada pedaço de imagem. Devolve os cards encontrados e a última
     * falha de OCR (para o diagnóstico não virar "sem card" quando o erro foi outro).
     */
    private suspend fun lerCards(
        pedacos: List<ParLeitura>,
        reader: ScreenReader,
        pkgPadrao: String?
    ): Pair<List<CardLido>, String?> {
        if (pedacos.isEmpty()) return emptyList<CardLido>() to null
        val lidos = mutableListOf<CardLido>()
        var erro: String? = null
        for (pedaco in pedacos) {
            val paraOcr = pedaco.bmp.scaledForOcr()
            val texto = try {
                reader.read(paraOcr)
            } catch (t: Throwable) {
                erro = t.message ?: "?"
                null
            } finally {
                if (paraOcr !== pedaco.bmp) paraOcr.recycle()
            }
            val pkg = pedaco.pkg ?: pkgPadrao
            val card = texto?.let { RideParser.parse(it, pkg) } ?: continue
            if (lidos.none { cardSignature(it.card) == cardSignature(card) }) {
                lidos.add(CardLido(card, pkg))
            }
        }
        return lidos to erro
    }

    /** Repassa às configurações salvas para o card que já está na tela. */
    fun applySettings(s: Settings) {
        overlay?.applySettings(s)
        overlayExterno?.applySettings(s)
        // Modo desktop pode ter sido ligado/desligado agora: recria o overlay externo.
        atualizarOverlayExterno()
    }

    private fun cardSignature(card: RideCard): String =
        "${card.app}|${card.valor}|${card.kmTotal}|${card.minTotal}"

    /** Captura uma tela inteira (a principal ou a externa do modo desktop). */
    private suspend fun captureScreen(displayId: Int): Bitmap? = suspendCancellableCoroutine { cont ->
        val callback = object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val bitmap = try {
                    Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                } catch (t: Throwable) {
                    null
                } finally {
                    runCatching { screenshot.hardwareBuffer.close() }
                }
                if (cont.isActive) cont.resume(bitmap)
            }

            override fun onFailure(errorCode: Int) {
                if (cont.isActive) cont.resume(null)
            }
        }
        try {
            takeScreenshot(displayId, mainExecutor, callback)
        } catch (t: Throwable) {
            if (cont.isActive) cont.resume(null)
        }
    }

    /**
     * Desliga a leitura pelo botão único: some com o botão flutuante e o card, para
     * o relógio automático, mas mantém o serviço vivo para religar sem sair do app.
     */
    fun desativar() {
        ativo = false
        cancelarRecusas()
        handler.removeCallbacksAndMessages(null)
        scanning = false
        lastCardSignature = null
        overlayExterno?.removeAll()
        overlayExterno = null
        overlay?.removeAll()
        overlay = null
        screenReader?.close()
        screenReader = null
        scope.cancel()
        AutoDiag.anotar(this, "leitura desligada pelo botão", forcar = true)
    }

    /** Reativa a leitura: recarrega configurações, refaz o overlay e o relógio. */
    fun ativar() {
        if (!scope.isActive) scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        iniciar()
        AutoDiag.anotar(this, "leitura ligada pelo botão", forcar = true)
    }

    override fun onDestroy() {
        instance = null
        ativo = false
        cancelarRecusas()
        handler.removeCallbacksAndMessages(null)
        overlayExterno?.removeAll()
        overlayExterno = null
        overlay?.removeAll()
        overlay = null
        lastCardSignature = null
        screenReader?.close()
        screenReader = null
        scope.cancel()
        super.onDestroy()
    }
}
