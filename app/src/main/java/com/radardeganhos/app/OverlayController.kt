package com.radardeganhos.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max

/**
 * Botão flutuante (arrastável) + painel de resultado desenhados por cima
 * de qualquer app, usando a janela do serviço de acessibilidade.
 *
 * O painel respeita todas as configurações na hora em que elas mudam:
 * duração na tela, tamanho da fonte, opacidade, posição, quais itens aparecem
 * e — com "empilhar" ligado — quantos cards ficam abertos ao mesmo tempo.
 *
 * Um controlador por tela: o do celular vive na janela do serviço e o da tela
 * externa (modo desktop) numa janela de acessibilidade criada naquele display —
 * mesmo código, só o contexto e as métricas mudam.
 */
class OverlayController(
    private val service: AccessibilityService,
    /** Tela em que este overlay vive: 0 = celular, outro = tela externa do modo desktop. */
    private val displayId: Int = MultiTela.TELA_PRINCIPAL,
    private val onAnalyze: () -> Unit,
    /** Card fechou (tempo esgotou ou toque): o intervalo da auto-leitura recomeça. */
    private val onPanelHide: () -> Unit = {}
) {

    /** Janela de acessibilidade da tela-alvo; null quando o fabricante recusou. */
    private val ctx: Context? = contextoDaTela()
    private val wm: WindowManager? =
        ctx?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    /** false quando não deu para criar janela nessa tela (aí o card cai no celular). */
    val disponivel: Boolean get() = ctx != null && wm != null

    /** Qual tela este overlay ocupa (0 = celular). */
    val tela: Int get() = displayId

    private val handler = Handler(Looper.getMainLooper())

    /**
     * No celular, o próprio serviço já é um contexto de janela de acessibilidade.
     * Numa tela externa, a receita é a do CTS do Android: `createDisplayContext` +
     * `createWindowContext(TYPE_ACCESSIBILITY_OVERLAY)` — sem isso a janela sai
     * na tela errada (ou o sistema recusa).
     */
    private fun contextoDaTela(): Context? {
        if (displayId == MultiTela.TELA_PRINCIPAL) return service
        return try {
            val gerenciador = service.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display: Display = gerenciador.getDisplay(displayId) ?: return null
            service.createDisplayContext(display)
                .createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
        } catch (t: Throwable) {
            null
        }
    }

    /** Métricas da tela alvo (no celular, as de sempre). */
    private fun metricos(): DisplayMetrics {
        if (displayId == MultiTela.TELA_PRINCIPAL) return service.resources.displayMetrics
        val externo = DisplayMetrics()
        val display = (service.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(displayId)
        runCatching { display?.getMetrics(externo) }
        return if (externo.widthPixels > 0) externo else service.resources.displayMetrics
    }

    private var bubble: View? = null
    private var bubbleLp: WindowManager.LayoutParams? = null

    /** Um card aberto: janela, posição e quando ele apareceu (para a regra de graça). */
    private class Card(
        val view: View,
        val lp: WindowManager.LayoutParams,
        val abertoEm: Long,
        var timer: Runnable?
    )

    private val cards = mutableListOf<Card>()

    /** Altura do último card desenhado (px): reserva a área dele quando ele não está na tela. */
    private var alturaCardMemorizada = 0
    private var ultimas: List<RideAnalysis> = emptyList()
    private var escalaAtual = 0f
    private var gravidadeAtual = 0

    /** O card está aberto na tela? A auto-análise usa isso para não atropelar a leitura. */
    val isPanelVisible: Boolean get() = cards.any { it.view.visibility == View.VISIBLE }

    /** Há quanto tempo o card mais recente está na tela (Long.MAX_VALUE se nenhum). */
    fun panelAbertoHa(now: Long): Long =
        cards.filter { it.view.visibility == View.VISIBLE }
            .minOfOrNull { now - it.abertoEm }
            ?: Long.MAX_VALUE

    private fun dp(v: Float): Int = (v * service.resources.displayMetrics.density).toInt()

    fun showBubble() {
        if (bubble != null) return
        val janela = wm ?: return
        val v = LayoutInflater.from(ctx).inflate(R.layout.overlay_bubble, null)
        val dm = metricos()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (dm.widthPixels - dp(76f)).coerceAtLeast(0)
            y = (dm.heightPixels * 0.60f).toInt()
        }

        v.elevation = dp(8f).toFloat()
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        v.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y
                    touchX = ev.rawX; touchY = ev.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - touchX).toInt()
                    val dy = (ev.rawY - touchY).toInt()
                    if (abs(dx) > dp(6f) || abs(dy) > dp(6f)) moved = true
                    val maxX = (dm.widthPixels - dp(48f)).coerceAtLeast(0)
                    val maxY = (dm.heightPixels - dp(48f)).coerceAtLeast(0)
                    lp.x = (startX + dx).coerceIn(-dp(10f), maxX)
                    lp.y = (startY + dy).coerceIn(-dp(10f), maxY)
                    runCatching { janela.updateViewLayout(v, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onAnalyze()
                    true
                }
                else -> false
            }
        }

        runCatching { janela.addView(v, lp) }
        bubble = v
        bubbleLp = lp
    }

    /** Esconde o overlay para que ele não apareça na captura da tela. */
    fun hideForCapture() {
        bubble?.visibility = View.INVISIBLE
        cards.forEach {
            if (it.view.visibility == View.VISIBLE) it.view.visibility = View.INVISIBLE
        }
    }

    fun restoreAfterCapture() {
        bubble?.visibility = View.VISIBLE
        cards.forEach {
            if (it.view.visibility == View.INVISIBLE) it.view.visibility = View.VISIBLE
        }
    }

    fun toast(msg: String) {
        runCatching { Toast.makeText(service, msg, Toast.LENGTH_SHORT).show() }
    }

    fun showResult(a: RideAnalysis, s: Settings) = showResults(listOf(a), s)

    /**
     * Desenha um ou mais resultados. Com Uber e 99 abertos lado a lado na tela
     * externa, uma leitura devolve dois cards e eles saem empilhados — um embaixo
     * do outro — para o motorista ver as duas ofertas de uma vez.
     */
    fun showResults(analises: List<RideAnalysis>, s: Settings) {
        if (analises.isEmpty()) return
        ultimas = analises
        // Sem empilhar, o card novo substitui o anterior (comportamento original).
        if (!s.cardEmpilhar) fecharCards(notificar = false)
        analises.forEach { desenhar(it, s) }
    }

    /** Fecha o card sozinho depois do tempo configurado (0 = só no toque). */
    fun hidePanel() = fecharCards(notificar = true)

    /**
     * Aplica na hora as configurações no card que está na tela. Para tamanho da fonte,
     * posição e opacidade o card é redesenhado com a última leitura — era o que faltava
     * para a configuração valer na prática sem precisar ler de novo.
     */
    fun applySettings(s: Settings) {
        val visivel = isPanelVisible
        val analises = ultimas
        if (visivel && analises.isNotEmpty()) {
            fecharCards(notificar = false)
            analises.forEach { desenhar(it, s) }
        } else {
            cards.forEach { aplicaOpacidade(it.view, s) }
        }
    }

    /** Desenha um card novo, respeitando escala, posição, empilhar e opacidade. */
    private fun desenhar(a: RideAnalysis, s: Settings): View? {
        val janela = wm ?: return null
        val escala = escalaDe(s.cardTamanhoFonte)
        val grav = gravityDe(s.cardGravity)
        val v = LayoutInflater.from(ctx).inflate(R.layout.overlay_panel, null)
        scaleTree(v, escala)

        val dm = metricos()
        val maxWidth = (dm.widthPixels * 0.94f).toInt()
        val width = minOf(maxWidth, dp(360f * escala))
        val lp = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = grav
        // No topo o card sai embaixo da barra de status (senão cobre o relógio);
        // empilhado, ele pega abaixo dos cards que já estão na tela.
        lp.y = (if (noTopo(grav)) alturaDaBarraStatus() else 0) + deslocamentoParaEmpilhar()
        v.elevation = dp(10f).toFloat()
        aplicaOpacidade(v, s)

        val card = Card(v, lp, android.os.SystemClock.elapsedRealtime(), null)
        cards.add(card)

        // Guarda a altura real do card: fechado, ela é a área que a leitura respeita.
        v.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            if (view.height > 0) alturaCardMemorizada = view.height
        }

        PanelBinder.bind(v, a, s)
        v.visibility = View.VISIBLE
        escalaAtual = escala
        gravidadeAtual = grav

        // Toca em qualquer lugar do card para fechá-lo.
        v.setOnClickListener { fecha(card, notificar = true) }

        runCatching { janela.addView(v, lp) }

        v.findViewById<View>(R.id.btnRepeat).setOnClickListener {
            fecha(card, notificar = true)
            onAnalyze()
        }

        // Arrasta o card pelo cabeçalho. Segurar ali pausa o fechamento e, ao soltar,
        // ele volta a contar do zero — antes o toque cancelava o timer para SEMPRE e o
        // card ficava na tela travando a leitura automática até alguém fechar à mão.
        val header = v.findViewById<View>(R.id.tvVerdict).parent as View
        header.setOnTouchListener(object : View.OnTouchListener {
            var px = 0
            var py = 0
            var tx = 0f
            var ty = 0f
            var moveu = false
            override fun onTouch(view: View, ev: MotionEvent): Boolean {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        px = lp.x; py = lp.y; tx = ev.rawX; ty = ev.rawY
                        moveu = false
                        cancela(card)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        moveu = true
                        lp.x = px + (ev.rawX - tx).toInt()
                        lp.y = py + (ev.rawY - ty).toInt()
                        runCatching { janela.updateViewLayout(v, lp) }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (moveu) agendaFechamento(card, s.cardDuracaoSeg)
                        else fecha(card, notificar = true)
                        return true
                    }
                }
                return false
            }
        })

        agendaFechamento(card, s.cardDuracaoSeg)
        return v
    }

    /** Empilha: altura estimada dos cards que já estão na tela (0 se não houver). */
    private fun deslocamentoParaEmpilhar(): Int {
        val abertos = cards.filter { it.view.visibility == View.VISIBLE }
        if (abertos.isEmpty()) return 0
        return abertos.sumOf { c ->
            val alt = alturaDoCard(c.view).takeIf { it > 0 } ?: dp(150f)
            alt + dp(10f)
        }
    }

    /** A janela está presa no topo da tela (posições "topo…")? */
    private fun noTopo(gravidade: Int): Boolean = (gravidade and Gravity.TOP) == Gravity.TOP

    /** Altura da barra de status: o card no topo nasce abaixo dela. */
    private fun alturaDaBarraStatus(): Int {
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        val h = if (id > 0) runCatching { service.resources.getDimensionPixelSize(id) }.getOrDefault(0) else 0
        return if (h > 0) h else dp(24f)
    }

    /** Altura já medida da janela do card (0 enquanto ela não foi medida). */
    private fun alturaDoCard(v: View): Int =
        v.height.takeIf { it > 0 } ?: v.measuredHeight.takeIf { it > 0 } ?: 0

    /** Fim do card aberto, em pixels desta tela; 0 quando nenhum card está na tela. */
    private fun fimDoCardNaTela(): Int {
        val abertos = cards.filter { it.view.visibility != View.GONE }
        if (abertos.isEmpty()) return 0
        val dm = metricos()
        var fim = 0
        for (c in abertos) {
            val altura = alturaDoCard(c.view)
            if (altura > 0) alturaCardMemorizada = altura
            fim = maxOf(
                fim,
                AreaLeitura.fimDoCard(dm.heightPixels, c.lp.y, altura, noTopo(c.lp.gravity))
            )
        }
        return fim.coerceIn(0, dm.heightPixels)
    }

    /**
     * De onde a leitura desta tela deve começar, em pixels da tela: o fim do card
     * mais uma folga. O serviço corta a captura nesta linha, para o OCR não ler o
     * próprio card do Radar nem o topo do app (saldo, menu, notificação).
     *
     * Com um card na tela — mesmo escondido para a captura — a conta usa a posição
     * real da janela. Sem card nenhum, que é justamente o caso da leitura automática
     * (ela dispara depois que o card some), reserva a área pelo que o card costuma
     * ocupar, senão a leitura voltaria a pegar o topo do app.
     */
    fun inicioAbaixoDoCard(s: Settings): Int {
        val folga = dp(AreaLeitura.FOLGA_DP.toFloat())
        val fim = fimDoCardNaTela().takeIf { it > 0 } ?: AreaLeitura.fimReservado(
            alturaTela = metricos().heightPixels,
            ancoradoNoTopo = noTopo(gravityDe(s.cardGravity)),
            deslocamentoY = alturaDaBarraStatus(),
            alturaUltimoCard = alturaCardMemorizada,
            alturaReservada = dp(AreaLeitura.ALTURA_RESERVADA_DP.toFloat())
        )
        return if (fim <= 0) 0 else fim + folga
    }

    private fun agendaFechamento(card: Card, segundos: Int) {
        cancela(card)
        if (segundos <= 0) return
        val runnable = Runnable { fecha(card, notificar = true) }
        card.timer = runnable
        handler.postDelayed(runnable, segundos * 1000L)
    }

    private fun cancela(card: Card) {
        card.timer?.let { handler.removeCallbacks(it) }
        card.timer = null
    }

    private fun fecha(card: Card, notificar: Boolean) {
        cancela(card)
        if (card.view.visibility != View.GONE) {
            card.view.visibility = View.GONE
            runCatching { wm?.removeView(card.view) }
        }
        cards.remove(card)
        if (notificar && cards.none { it.view.visibility == View.VISIBLE }) onPanelHide()
    }

    private fun fecharCards(notificar: Boolean) {
        cards.toList().forEach { fecha(it, notificar = false) }
        if (notificar) onPanelHide()
    }

    private fun aplicaOpacidade(v: View, s: Settings) {
        v.alpha = s.cardOpacidade.coerceIn(0, 100) / 100f
    }

    /**
     * Tamanho da fonte escolhido nas configurações. "Pequena" é o tamanho original
     * do card; média e grande ampliam os textos e espaçamentos.
     */
    private fun escalaDe(tamanho: String): Float = when (tamanho) {
        "grande" -> 1.30f
        "media" -> 1.15f
        else -> 0.92f
    }

    /**
     * Aplica a escala escolhida nas configurações direto nos tamanhos do layout.
     * Escalar textSize/paddings (em vez de scaleX/scaleY) evita o corte que o
     * scaleX sofre porque a janela é medida antes da transformação.
     */
    private fun scaleTree(v: View, k: Float) {
        if (k == 1f) return

        if (v is android.widget.TextView) {
            v.setTextSize(TypedValue.COMPLEX_UNIT_PX, v.textSize * k)
        }
        v.setPadding(
            (v.paddingLeft * k).toInt(),
            (v.paddingTop * k).toInt(),
            (v.paddingRight * k).toInt(),
            (v.paddingBottom * k).toInt()
        )
        (v.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            lp.setMargins(
                (lp.leftMargin * k).toInt(),
                (lp.topMargin * k).toInt(),
                (lp.rightMargin * k).toInt(),
                (lp.bottomMargin * k).toInt()
            )
            if (lp.height > 0) lp.height = max(1, (lp.height * k).toInt())
            v.layoutParams = lp
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) scaleTree(v.getChildAt(i), k)
        }
    }

    /** Converte a opção salva nas configurações em uma Gravity da janela. */
    private fun gravityDe(opcao: String): Int = when (opcao) {
        "esquerda" -> Gravity.LEFT or Gravity.CENTER_VERTICAL
        "direita" -> Gravity.RIGHT or Gravity.CENTER_VERTICAL
        "topo" -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        "topo-esquerda" -> Gravity.TOP or Gravity.LEFT
        "topo-direita" -> Gravity.TOP or Gravity.RIGHT
        else -> Gravity.CENTER // "centro" ou qualquer outro valor
    }

    fun removeAll() {
        handler.removeCallbacksAndMessages(null)
        cards.toList().forEach { fecha(it, notificar = false) }
        cards.clear()
        ultimas = emptyList()
        bubble?.let { runCatching { wm?.removeView(it) } }
        bubble = null
        bubbleLp = null
        escalaAtual = 0f
        gravidadeAtual = 0
    }
}
