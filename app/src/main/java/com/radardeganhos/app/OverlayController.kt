package com.radardeganhos.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Um resultado a desenhar. [janela] é a janela do app de onde o card foi lido:
 * com dois apps lado a lado, cada card nasce dentro da janela dele em vez de um
 * cobrir o outro. [pkg] diz de qual app é a oferta (e qual X ensinar).
 */
data class CardNaTela(
    val analise: RideAnalysis,
    val pkg: String? = null,
    val janela: Rect? = null
)

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
    private val onPanelHide: () -> Unit = {},
    /** O motorista tocou em "ensinar o X" num card: abre a mira naquela tela. */
    private val onCalibrarX: (pkg: String?, app: String?, displayId: Int, janela: Rect?) -> Unit =
        { _, _, _, _ -> },
    /** Um card foi fechado: cancela só a recusa daquela oferta, não a do outro app. */
    private val onCardFechado: (String) -> Unit = {},
    /** A mira foi solta em cima do X: posição relativa à janela do app (0 a 1). */
    private val onSalvarCalibracao: (pkg: String?, app: String?, relX: Double, relY: Double, displayId: Int) -> Unit =
        { _, _, _, _, _ -> },
    /** A mira fechou (salvou ou cancelou): o serviço pode voltar a ler a tela. */
    private val onCalibracaoFechada: () -> Unit = {}
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
        var timer: Runnable?,
        /** Identifica a oferta para a contagem e o cancelamento serem só dela. */
        val assinatura: String,
        val pkg: String?,
        val janela: Rect?
    )

    private val cards = mutableListOf<Card>()

    /** Altura do último card desenhado (px): reserva a área dele quando ele não está na tela. */
    private var alturaCardMemorizada = 0
    private var ultimas: List<CardNaTela> = emptyList()
    private var escalaAtual = 0f
    private var gravidadeAtual = 0

    /** Mira de calibração do X, quando aberta. */
    private var calibracao: View? = null
    private var calibracaoMira: View? = null
    private var calibracaoPkg: String? = null
    private var calibracaoApp: String? = null
    private var calibracaoJanela: Rect? = null

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
        calibracao?.visibility = View.INVISIBLE
        cards.forEach {
            if (it.view.visibility == View.VISIBLE) it.view.visibility = View.INVISIBLE
        }
    }

    fun restoreAfterCapture() {
        bubble?.visibility = View.VISIBLE
        calibracao?.visibility = View.VISIBLE
        cards.forEach {
            if (it.view.visibility == View.INVISIBLE) it.view.visibility = View.VISIBLE
        }
    }

    fun toast(msg: String) {
        runCatching { Toast.makeText(service, msg, Toast.LENGTH_SHORT).show() }
    }

    /**
     * Desenha um ou mais resultados. Com Uber e 99 abertos lado a lado na tela
     * externa, cada card sai dentro da janela do app que o gerou — assim os dois
     * ficam visíveis ao mesmo tempo e cada um mostra a oferta daquela janela.
     */
    fun showResults(resultados: List<CardNaTela>, s: Settings) {
        if (resultados.isEmpty()) return
        ultimas = resultados
        // Sem empilhar, o card novo substitui o anterior (comportamento original).
        if (!s.cardEmpilhar) fecharCards(notificar = false)
        resultados.forEach { desenhar(it, s) }
    }

    /**
     * Escreve (ou apaga, com null) a linha da contagem regressiva da recusa no card
     * que está na tela. Com [assinatura], só o card daquela oferta é alterado: com
     * duas ofertas na tela, cada uma tem a sua contagem.
     */
    fun atualizarContagem(msg: String?, assinatura: String? = null) {
        cards.forEach { card ->
            if (assinatura != null && card.assinatura != assinatura) return@forEach
            val tv = card.view.findViewById<android.widget.TextView>(R.id.tvContagem) ?: return@forEach
            if (msg.isNullOrBlank()) {
                tv.visibility = View.GONE
            } else {
                tv.text = msg
                tv.visibility = View.VISIBLE
            }
        }
    }

    /** Apaga a contagem de todos os cards desta tela (o tick reescreve as pendentes). */
    fun limparContagem() {
        atualizarContagem(null)
    }

    /**
     * Mantém o card na tela pelo menos [segundos] — usado enquanto a recusa está
     * contando. Sem isso, o fechamento automático (12 s por padrão) podia fechar o
     * card antes do fim da contagem (até 30 s) e a recusa era cancelada sem o
     * motorista ver o clique.
     */
    fun manterAberto(segundos: Int) {
        cards.toList().forEach { agendaFechamento(it, segundos.coerceAtLeast(3)) }
    }

    /**
     * Aplica na hora as configurações no card que está na tela. Para tamanho da fonte,
     * posição e opacidade o card é redesenhado com a última leitura — era o que faltava
     * para a configuração valer na prática sem precisar ler de novo.
     */
    fun applySettings(s: Settings) {
        val visivel = isPanelVisible
        val resultados = ultimas
        if (visivel && resultados.isNotEmpty()) {
            fecharCards(notificar = false)
            resultados.forEach { desenhar(it, s) }
        } else {
            cards.forEach { aplicaOpacidade(it.view, s) }
        }
    }

    /** Desenha um card novo, respeitando escala, posição, empilhar e opacidade. */
    private fun desenhar(entrada: CardNaTela, s: Settings): View? {
        val janela = wm ?: return null
        val escala = escalaDe(s.cardTamanhoFonte)
        val grav = gravityDe(s.cardGravity)
        val v = LayoutInflater.from(ctx).inflate(R.layout.overlay_panel, null)
        scaleTree(v, escala)

        val dm = metricos()
        val area = entrada.janela
        // Com a janela do app conhecida (modo desktop), o card nasce DENTRO dela:
        // dois apps lado a lado mostram dois cards, um em cada janela.
        val larguraDisponivel = area?.width()?.takeIf { it > 0 } ?: dm.widthPixels
        val maxWidth = (larguraDisponivel * 0.94f).toInt()
        val width = minOf(maxWidth, dp(360f * escala)).coerceAtLeast(dp(180f))
        val lp = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        if (area != null) {
            lp.gravity = Gravity.TOP or Gravity.LEFT
            lp.x = area.left + ((area.width() - width) / 2).coerceAtLeast(0)
            // Sem barra de status na janela do app: só uma folga pequena no topo.
            lp.y = area.top + dp(6f) + deslocamentoParaEmpilhar(area)
        } else {
            lp.gravity = grav
            // No topo o card sai embaixo da barra de status (senão cobre o relógio);
            // empilhado, ele pega abaixo dos cards que já estão na tela.
            lp.y = (if (noTopo(grav)) alturaDaBarraStatus() else 0) + deslocamentoParaEmpilhar(null)
        }
        v.elevation = dp(10f).toFloat()
        aplicaOpacidade(v, s)

        val card = Card(
            view = v,
            lp = lp,
            abertoEm = android.os.SystemClock.elapsedRealtime(),
            timer = null,
            assinatura = entrada.analise.assinatura(),
            pkg = entrada.pkg,
            janela = area
        )
        cards.add(card)

        // Guarda a altura real do card: fechado, ela é a área que a leitura respeita.
        v.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            if (view.height > 0) alturaCardMemorizada = view.height
        }

        PanelBinder.bind(v, entrada.analise, s)
        v.visibility = View.VISIBLE
        escalaAtual = escala
        gravidadeAtual = grav

        // Toca em qualquer lugar do card para fechá-lo.
        v.setOnClickListener { fecha(card, notificar = true) }

        val anexou = runCatching { janela.addView(v, lp) }.isSuccess
        if (!anexou) {
            cards.remove(card)
            return null
        }

        v.findViewById<View>(R.id.btnRepeat).setOnClickListener {
            fecha(card, notificar = true)
            onAnalyze()
        }

        v.findViewById<View>(R.id.btnCalibrarX)?.setOnClickListener {
            // Sem pacote conhecido (leitura da tela inteira), usa o nome do app que o
            // parser escreveu no card ("Uber"/"99") para guardar pela família.
            val app = appDe(entrada.pkg) ?: entrada.analise.card.app
            // Fecha os cards para a tela do app aparecer inteira e abre a mira.
            fecharCards(notificar = false)
            onCalibrarX(entrada.pkg, app, displayId, entrada.janela)
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

    /** "Uber"/"99" a partir do pacote, para salvar o X ensinado também pela família. */
    private fun appDe(pkg: String?): String? = when (AutoScanPolicy.familiaDe(pkg ?: "")) {
        AutoScanPolicy.FAMILIA_UBER -> "Uber"
        AutoScanPolicy.FAMILIA_99 -> "99"
        else -> null
    }

    /** Empilha: altura estimada dos cards que já estão na tela (0 se não houver). */
    private fun deslocamentoParaEmpilhar(area: Rect?): Int {
        val abertos = cards.filter {
            it.view.visibility == View.VISIBLE && (area == null || it.janela == area)
        }
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
        if (notificar) onCardFechado(card.assinatura)
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
        "extra" -> 1.60f
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

    /**
     * Abre a mira para o motorista ensinar onde fica o X do app. A mira nasce em
     * cima da janela do app (quando conhecida) e pode ser arrastada para qualquer
     * ponto da tela; o que vale é a posição relativa à janela, então o clique
     * continua certo quando a janela muda de lugar ou de tamanho.
     */
    fun iniciarCalibracao(pkg: String?, app: String?, janelaDoApp: Rect?) {
        if (calibracao != null) return
        val contexto = ctx ?: return
        val gerenciador = wm ?: return
        val dm = metricos()

        val raiz = FrameLayout(contexto)
        raiz.setBackgroundColor(Color.argb(28, 0, 0, 0))

        val dica = TextView(contexto).apply {
            text = "Arraste a mira até o X de recusar do ${app ?: "app"} e toque em SALVAR"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            background = GradientDrawable().apply {
                setColor(Color.argb(225, 18, 18, 18))
                cornerRadius = dp(14f).toFloat()
                setStroke(dp(1f), Color.argb(120, 255, 255, 255))
            }
        }
        raiz.addView(dica, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            leftMargin = dp(14f); rightMargin = dp(14f); topMargin = dp(18f)
        })

        val mira = MiraView(contexto)
        val lado = dp(78f)
        val miraLp = FrameLayout.LayoutParams(lado, lado).apply {
            gravity = Gravity.TOP or Gravity.LEFT
        }
        raiz.addView(mira, miraLp)

        var cx = (janelaDoApp?.let { it.right - it.width() * 0.10 } ?: (dm.widthPixels * 0.86)).toFloat()
        var cy = (janelaDoApp?.let { it.top + it.height() * 0.18 } ?: (dm.heightPixels * 0.16)).toFloat()
        cx = cx.coerceIn(lado / 2f, dm.widthPixels - lado / 2f)
        cy = cy.coerceIn(lado / 2f, dm.heightPixels - lado / 2f)
        fun posicionar(x: Float, y: Float) {
            cx = x.coerceIn(lado / 2f, (dm.widthPixels - lado / 2f).coerceAtLeast(lado / 2f))
            cy = y.coerceIn(lado / 2f, (dm.heightPixels - lado / 2f).coerceAtLeast(lado / 2f))
            miraLp.leftMargin = (cx - lado / 2f).toInt()
            miraLp.topMargin = (cy - lado / 2f).toInt()
            mira.layoutParams = miraLp
        }
        posicionar(cx.toFloat(), cy.toFloat())

        val mover = View.OnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    posicionar(ev.rawX, ev.rawY)
                    true
                }
                else -> true
            }
        }
        raiz.setOnTouchListener(mover)
        mira.setOnTouchListener(mover)

        val botoes = LinearLayout(contexto).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val salvar = TextView(contexto).apply {
            text = "SALVAR"
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(dp(26f), dp(14f), dp(26f), dp(14f))
            background = GradientDrawable().apply {
                setColor(Color.rgb(255, 196, 0))
                cornerRadius = dp(14f).toFloat()
            }
        }
        val cancelar = TextView(contexto).apply {
            text = "CANCELAR"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(dp(20f), dp(14f), dp(20f), dp(14f))
            background = GradientDrawable().apply {
                setColor(Color.argb(225, 45, 45, 45))
                cornerRadius = dp(14f).toFloat()
            }
        }
        botoes.addView(cancelar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = dp(10f) })
        botoes.addView(salvar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        raiz.addView(botoes, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(42f)
        })

        cancelar.setOnClickListener { cancelarCalibracao() }
        salvar.setOnClickListener {
            val tela = calibracaoJanela ?: Rect(0, 0, dm.widthPixels, dm.heightPixels)
            val relX = ((cx.toDouble() - tela.left) / tela.width().coerceAtLeast(1)).coerceIn(0.0, 1.0)
            val relY = ((cy.toDouble() - tela.top) / tela.height().coerceAtLeast(1)).coerceIn(0.0, 1.0)
            onSalvarCalibracao(pkg, app, relX, relY, displayId)
            Toast.makeText(contexto, "X do ${app ?: "app"} guardado", Toast.LENGTH_SHORT).show()
            fecharCalibracao()
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 0
        }
        if (runCatching { gerenciador.addView(raiz, lp) }.isFailure) {
            onCalibracaoFechada()
            return
        }

        calibracao = raiz
        calibracaoMira = mira
        calibracaoPkg = pkg
        calibracaoApp = app
        calibracaoJanela = janelaDoApp
    }

    fun estaCalibrando(): Boolean = calibracao != null

    fun cancelarCalibracao() {
        fecharCalibracao()
    }

    private fun fecharCalibracao() {
        val estavaAberta = calibracao != null
        calibracao?.let { runCatching { wm?.removeView(it) } }
        calibracao = null
        calibracaoMira = null
        calibracaoPkg = null
        calibracaoApp = null
        calibracaoJanela = null
        if (estavaAberta) onCalibracaoFechada()
    }

    /** Mira desenhada à mão: círculos + cruz, sem depender de imagem no APK. */
    private inner class MiraView(context: android.content.Context) : View(context) {
        private val tinta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 196, 0)
            style = Paint.Style.STROKE
            strokeWidth = dp(3f).toFloat()
        }
        private val centro = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val raio = min(width, height) / 2f - tinta.strokeWidth
            // Sombra escura por baixo, para a mira aparecer em tela clara.
            tinta.color = Color.argb(160, 0, 0, 0)
            tinta.strokeWidth = dp(7f).toFloat()
            canvas.drawCircle(cx, cy, raio, tinta)
            tinta.color = Color.rgb(255, 196, 0)
            tinta.strokeWidth = dp(3f).toFloat()
            canvas.drawCircle(cx, cy, raio, tinta)
            canvas.drawCircle(cx, cy, raio * 0.45f, tinta)
            canvas.drawLine(cx - raio, cy, cx + raio, cy, tinta)
            canvas.drawLine(cx, cy - raio, cx, cy + raio, tinta)
            canvas.drawCircle(cx, cy, dp(4f).toFloat(), centro)
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
        fecharCalibracao()
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
