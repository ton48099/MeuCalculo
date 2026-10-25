package com.radardeganhos.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * Botão flutuante (arrastável) + painel de resultado desenhados por cima
 * de qualquer app, usando a janela do serviço de acessibilidade.
 */
class OverlayController(
    private val service: AccessibilityService,
    private val onAnalyze: () -> Unit
) {

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var bubble: View? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var panel: View? = null
    private var panelVisibleBeforeCapture = false

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
        val v = panel ?: inflatePanel().also { panel = it }
        bind(v, a, s)
        v.visibility = View.VISIBLE
    }

    private fun inflatePanel(): View {
        val v = LayoutInflater.from(service).inflate(R.layout.overlay_panel, null)
        val dm = service.resources.displayMetrics
        val width = minOf((dm.widthPixels * 0.92f).toInt(), dp(360f))
        val lp = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        v.elevation = dp(10f).toFloat()
        runCatching { wm.addView(v, lp) }
        v.findViewById<View>(R.id.btnClose).setOnClickListener { v.visibility = View.GONE }
        v.findViewById<View>(R.id.btnRepeat).setOnClickListener {
            v.visibility = View.GONE
            onAnalyze()
        }
        // arrasta o painel pelo cabeçalho
        val header = v.findViewById<View>(R.id.tvVerdict).parent as View
        header.setOnTouchListener(object : View.OnTouchListener {
            var px = 0
            var py = 0
            var tx = 0f
            var ty = 0f
            override fun onTouch(view: View, ev: MotionEvent): Boolean {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        px = lp.x; py = lp.y; tx = ev.rawX; ty = ev.rawY; return true
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

    private fun bind(v: View, a: RideAnalysis, s: Settings) {
        val cor = when (a.veredito) {
            Veredito.OTIMA -> Color.parseColor("#00E676")
            Veredito.BOA -> Color.parseColor("#00D26A")
            Veredito.MEDIA -> Color.parseColor("#FFC531")
            Veredito.RUIM -> Color.parseColor("#FF4D5E")
        }

        val verdict = v.findViewById<TextView>(R.id.tvVerdict)
        verdict.text = a.veredito.label
        verdict.setTextColor(cor)
        verdict.background = GradientDrawable().apply {
            cornerRadius = dp(999f).toFloat()
            setColor(Color.argb(34, Color.red(cor), Color.green(cor), Color.blue(cor)))
            setStroke(dp(1f), Color.argb(70, Color.red(cor), Color.green(cor), Color.blue(cor)))
        }

        v.findViewById<TextView>(R.id.tvApp).text = buildString {
            append(if (a.card.app != null) "card ${a.card.app}" else "leitura de tela")
            append(" · nota ")
            append(Math.round(a.score))
            append("%")
        }

        v.findViewById<TextView>(R.id.tvHora).apply {
            text = Report.money(a.liquidoHora)
            setTextColor(cor)
        }
        v.findViewById<TextView>(R.id.tvKm).text = Report.money(a.liquidoKm) + "/km"
        v.findViewById<TextView>(R.id.tvBruto).text =
            "bruto ${Report.money(a.brutoHora)}/h · ${Report.money(a.brutoKm)}/km"

        v.findViewById<TextView>(R.id.tvResumo).text = buildString {
            append("Corrida ${Report.money(a.bruto)} · ${Report.km(a.card.kmTotal)} · ${Report.tempo(a.minTotal)}\n")
            append("Deslocamento ${Report.km(a.card.kmDeslocamento)} / ${Report.tempo(a.card.minDeslocamento)}")
            append(" · Viagem ${Report.km(a.card.kmViagem)} / ${Report.tempo(a.card.minViagem)}")
        }

        v.findViewById<TextView>(R.id.tvCustos).text = buildString {
            append("Combustível ${Report.money(a.combustivel)} · Manutenção ${Report.money(a.manutencao)}")
            if (a.comissao > 0) append(" · Comissão ${Report.money(a.comissao)}")
            if (a.fixo > 0) append(" · Fixo ${Report.money(a.fixo)}")
            append("\nSobra ${Report.money(a.liquido)} · custo ${Report.money(a.custoTotal)}")
            if (a.card.taxaDeslocamento != null) {
                append("\nTaxa de deslocamento ${Report.money(a.card.taxaDeslocamento!!)} (não somada)")
            }
        }

        v.findViewById<TextView>(R.id.tvMeta).apply {
            text = if (a.card.valor < a.valorMinimo) {
                "Abaixo da meta (${Report.money(s.metaKm)}/km e ${Report.money(s.metaHora)}/h).\n" +
                    "Mínimo para valer a pena: ${Report.money(a.valorMinimo)}"
            } else {
                "Na meta ou acima (${Report.money(s.metaKm)}/km e ${Report.money(s.metaHora)}/h).\n" +
                    "Mínimo aceitável: ${Report.money(a.valorMinimo)}"
            }
            setTextColor(if (a.card.valor < a.valorMinimo) Color.parseColor("#FFC531") else Color.parseColor("#00D26A"))
        }
    }

    fun removeAll() {
        bubble?.let { runCatching { wm.removeView(it) } }
        panel?.let { runCatching { wm.removeView(it) } }
        bubble = null
        panel = null
    }
}
