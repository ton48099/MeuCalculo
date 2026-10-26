package com.radardeganhos.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
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
 * O painel respeita as configurações: duração na tela, escala e quais itens mostrar.
 */
class OverlayController(
    private val service: AccessibilityService,
    private val onAnalyze: () -> Unit,
    /** Chamado quando o card some da tela (X, tempo esgotado ou releitura). */
    private val onPanelHide: () -> Unit = {}
) {

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var bubble: View? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var panel: View? = null
    private var panelVisibleBeforeCapture = false
    private var panelScale = 0f
    private var panelShownAt = 0L
    private var hideRunnable: Runnable? = null

    /** O card está aberto na tela? A auto-análise usa isso para não atropelar a leitura. */
    val isPanelVisible: Boolean get() = panel?.visibility == View.VISIBLE

    /**
     * Há quanto tempo (ms) o card está na tela, ou Long.MAX_VALUE se não estiver.
     * A auto-análise segura a leitura só durante o período de graça — depois disso
     * um card novo pode substituir, senão um card fixo (duração 0) bloquearia a
     * leitura automática para sempre.
     */
    fun panelAbertoHa(now: Long): Long =
        if (isPanelVisible) (now - panelShownAt).coerceAtLeast(0L) else Long.MAX_VALUE

    private fun dp(v: Float): Int = (v * service.resources.displayMetrics.density).toInt()

    fun showBubble() {
        if (bubble != null) return
        val v = LayoutInflater.from(service).inflate(R.layout.overlay_bubble, null)
        val dm = service.resources.displayMetrics
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
                    runCatching { wm.updateViewLayout(v, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onAnalyze()
                    true
                }
                else -> false
            }
        }

        runCatching { wm.addView(v, lp) }
        bubble = v
        bubbleLp = lp
    }

    /** Esconde o overlay para que ele não apareça na captura da tela. */
    fun hideForCapture() {
        bubble?.visibility = View.INVISIBLE
        panel?.let {
            panelVisibleBeforeCapture = it.visibility == View.VISIBLE
            it.visibility = View.INVISIBLE
        }
    }

    fun restoreAfterCapture() {
        bubble?.visibility = View.VISIBLE
        if (panelVisibleBeforeCapture) panel?.visibility = View.VISIBLE
        panelVisibleBeforeCapture = false
    }

    fun toast(msg: String) {
        runCatching { Toast.makeText(service, msg, Toast.LENGTH_SHORT).show() }
    }

    fun showResult(a: RideAnalysis, s: Settings) {
        ensurePanel(s.cardEscala)
        val v = panel ?: return
        PanelBinder.bind(v, a, s)
        v.visibility = View.VISIBLE
        panelShownAt = SystemClock.elapsedRealtime()
        scheduleHide(s.cardDuracaoSeg)
    }

    fun hidePanel() {
        cancelHide()
        fecharPanel()
    }

    /** Fecha o card e avisa o serviço: o intervalo da auto-leitura recomeça agora. */
    private fun fecharPanel() {
        if (panel?.visibility == View.VISIBLE) {
            panel?.visibility = View.GONE
            onPanelHide()
        } else {
            panel?.visibility = View.GONE
        }
    }

    /** Aplica na hora as configurações do card (toggles e tamanho). */
    fun applySettings(s: Settings) {
        if (panel != null && panelScale != s.cardEscala) {
            cancelHide()
            panel?.let { runCatching { wm.removeView(it) } }
            panel = null
            panelScale = 0f
            return
        }
        panel?.let { PanelBinder.applyVisibility(it, s) }
    }

    private fun ensurePanel(scale: Float) {
        if (panel != null && panelScale == scale) return
        panel?.let { runCatching { wm.removeView(it) } }
        panel = inflatePanel(scale)
        panelScale = scale
    }

    /** Fecha o card sozinho depois do tempo configurado (0 = nunca). */
    private fun scheduleHide(segundos: Int) {
        cancelHide()
        if (segundos <= 0) return
        val runnable = Runnable {
            fecharPanel()
            hideRunnable = null
        }
        hideRunnable = runnable
        handler.postDelayed(runnable, segundos * 1000L)
    }

    private fun cancelHide() {
        hideRunnable?.let { handler.removeCallbacks(it) }
        hideRunnable = null
    }

    private fun inflatePanel(scale: Float): View {
        val v = LayoutInflater.from(service).inflate(R.layout.overlay_panel, null)
        scaleTree(v, scale)
        val dm = service.resources.displayMetrics
        val maxWidth = (dm.widthPixels * 0.94f).toInt()
        val width = minOf(maxWidth, dp(360f * scale))
        val lp = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        v.elevation = dp(10f).toFloat()
        runCatching { wm.addView(v, lp) }
        v.findViewById<View>(R.id.btnClose).setOnClickListener {
            cancelHide()
            fecharPanel()
        }
        v.findViewById<View>(R.id.btnRepeat).setOnClickListener {
            cancelHide()
            fecharPanel()
            onAnalyze()
        }
        // arrasta o painel pelo cabeçalho (e segurar ali pausa o fechamento automático)
        val header = v.findViewById<View>(R.id.tvVerdict).parent as View
        header.setOnTouchListener(object : View.OnTouchListener {
            var px = 0
            var py = 0
            var tx = 0f
            var ty = 0f
            override fun onTouch(view: View, ev: MotionEvent): Boolean {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        px = lp.x; py = lp.y; tx = ev.rawX; ty = ev.rawY
                        cancelHide()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = px + (ev.rawX - tx).toInt()
                        lp.y = py + (ev.rawY - ty).toInt()
                        runCatching { wm.updateViewLayout(v, lp) }
                        return true
                    }
                }
                return false
            }
        })
        return v
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

    fun removeAll() {
        cancelHide()
        handler.removeCallbacksAndMessages(null)
        bubble?.let { runCatching { wm.removeView(it) } }
        panel?.let { runCatching { wm.removeView(it) } }
        bubble = null
        panel = null
        panelScale = 0f
    }
}
