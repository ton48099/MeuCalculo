package com.radardeganhos.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Serviço que permite ler a tela (API 30+) e desenhar o botão flutuante.
 * Nenhum dado sai do aparelho: a captura é analisada na memória e descartada.
 */
class RideAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: RideAccessibilityService? = null
            private set
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private var screenReader: ScreenReader? = null
    private var overlay: OverlayController? = null

    private var scanning = false
    private var lastScanAt = 0L
    private var lastPackage: String? = null
    /** Frequência do relógio automático (não é o intervalo entre leituras). */
    private val pollMs = 1200L
    /** Assinatura do último card analisado, para não repetir a mesma leitura. */
    private var lastCardSignature: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        if (screenReader == null) screenReader = ScreenReader()
        if (overlay == null) {
            overlay = OverlayController(
                service = this,
                onAnalyze = { analyzeScreen(manual = true) },
                // Card sumiu: o intervalo entre leituras volta a contar daqui.
                onPanelHide = { lastScanAt = SystemClock.elapsedRealtime() }
            )
        }
        overlay?.showBubble()
        // O relógio da leitura automática. Roda mesmo sem evento do sistema,
        // porque o Uber/99 nem sempre emite mudança de janela quando o card aparece.
        handler.removeCallbacks(poller)
        handler.postDelayed(poller, pollMs)
    }

    /** Tique do relógio automático: 1,2 em 1,2 s decide se está na hora de ler. */
    private val poller = object : Runnable {
        override fun run() {
            tickAutomatico()
            handler.postDelayed(this, pollMs)
        }
    }

    private fun tickAutomatico() {
        val pkg = pacoteAtivo() ?: lastPackage ?: return
        if (pkg == packageName) return
        lastPackage = pkg

        val settings = Prefs.load(this)
        val now = SystemClock.elapsedRealtime()
        val motivo = AutoScanPolicy.motivoParaNaoLer(
            s = settings,
            pkg = pkg,
            now = now,
            lastScanAt = lastScanAt,
            scanning = scanning,
            panelVisible = overlay?.isPanelVisible == true,
            panelAbertoHaMs = overlay?.panelAbertoHa(now) ?: Long.MAX_VALUE
        )
        when {
            motivo == null -> {
                // O intervalo venceu: vale a pena olhar a tela antes de capturar.
                lastScanAt = now
                if (!telaPareceCard()) {
                    AutoDiag.anotar(this, "$pkg · nada de card na tela agora")
                } else {
                    handler.removeCallbacks(autoScan)
                    handler.postDelayed(autoScan, 150L)
                }
            }
            // O ritmo ("esperando") e o estado normal (desligada/fora da lista) não
            // poluem o diagnóstico; o que trava de verdade sim.
            motivo != "auto-análise desligada" && motivo != "app fora da lista" &&
                !motivo.startsWith("esperando") ->
                AutoDiag.anotar(this, "$pkg · $motivo")
        }
    }

    /** Pacote em primeiro plano, lido da árvore de acessibilidade (sem depender de evento). */
    private fun pacoteAtivo(): String? = try {
        rootInActiveWindow?.packageName?.toString()
    } catch (t: Throwable) {
        null
    }

    /**
     * Gasto barato antes de capturar: se o texto da tela não tem nenhum sinal de card
     * (R$, km, min), não vale tirar screenshot e rodar OCR. Se a árvore vier vazia ou
     * bloqueada, devolve true e a leitura acontece do jeito normal.
     */
    private fun telaPareceCard(): Boolean {
        val texto = try {
            val root = rootInActiveWindow ?: return true
            val sb = StringBuilder()
            fun andar(n: android.view.accessibility.AccessibilityNodeInfo?, d: Int) {
                if (n == null || d > 12 || sb.length > 4000) return
                n.text?.let { if (it.isNotEmpty()) sb.append(it).append('\n') }
                for (i in 0 until n.childCount) andar(try { n.getChild(i) } catch (t: Throwable) { null }, d + 1)
            }
            andar(root, 0)
            sb.toString()
        } catch (t: Throwable) {
            return true
        }
        if (texto.length < 80) return true
        val b = texto.lowercase()
        return b.contains("r\$") || b.contains("rs ") ||
            (b.contains("km") && (b.contains("min") || Regex("""\d\s*h\b""").containsMatchIn(b)))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return
        lastPackage = pkg

        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            e.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val settings = Prefs.load(this)
        val now = SystemClock.elapsedRealtime()
        val motivo = AutoScanPolicy.motivoParaNaoLer(
            s = settings,
            pkg = pkg,
            now = now,
            lastScanAt = lastScanAt,
            scanning = scanning,
            panelVisible = overlay?.isPanelVisible == true,
            panelAbertoHaMs = overlay?.panelAbertoHa(now) ?: Long.MAX_VALUE
        )
        if (motivo != null) {
            // "esperando Xs" é o ritmo normal; o que trava é anotado para o diagnóstico.
            if (!motivo.startsWith("esperando")) AutoDiag.anotar(this, "$pkg · $motivo")
            return
        }
        AutoDiag.anotar(this, "tentando ler em $pkg", forcar = true)
        lastScanAt = now
        handler.removeCallbacks(autoScan)
        handler.postDelayed(autoScan, 900L)
    }

    private val autoScan = Runnable { analyzeScreen(manual = false) }

    override fun onInterrupt() {
        scanning = false
    }

    /** Captura a tela atual, lê com OCR e mostra o painel de resultado. */
    fun analyzeScreen(manual: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (manual) overlay?.toast("A leitura de tela requer Android 11 ou superior")
            return
        }
        val capacidade = serviceInfo?.capabilities ?: 0
        if (capacidade and AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT == 0) {
            if (manual) overlay?.toast("Reative o serviço de acessibilidade para liberar a captura de tela")
            else AutoDiag.anotar(this, "captura de tela bloqueada", forcar = true)
            return
        }
        if (scanning) return
        scanning = true

        scope.launch {
            var bmp: Bitmap? = null
            try {
                overlay?.hideForCapture()
                delay(160L)
                bmp = captureScreen()
                overlay?.restoreAfterCapture()

                if (bmp == null) {
                    if (manual) overlay?.toast("Não consegui capturar a tela. Verifique a permissão de acessibilidade.")
                    else AutoDiag.anotar(this@RideAccessibilityService, "captura de tela falhou", forcar = true)
                    return@launch
                }

                val reader = screenReader ?: ScreenReader().also { screenReader = it }
                val text = try {
                    reader.read(bmp)
                } catch (t: Throwable) {
                    if (manual) overlay?.toast("Falha no OCR: ${t.message}")
                    else AutoDiag.anotar(this@RideAccessibilityService, "falha no OCR: ${t.message ?: "?"}", forcar = true)
                    return@launch
                }

                val settings = Prefs.load(this@RideAccessibilityService)
                val card = RideParser.parse(text, lastPackage)
                if (card == null) {
                    if (manual) overlay?.toast("Nenhum card de corrida nesta tela")
                    else AutoDiag.anotar(
                        this@RideAccessibilityService,
                        "sem card em ${lastPackage ?: "?"}",
                        forcar = true
                    )
                    return@launch
                }

                // Auto-análise não repete o mesmo card; a leitura manual sempre responde.
                val signature = cardSignature(card)
                if (!manual && signature == lastCardSignature) {
                    AutoDiag.anotar(this@RideAccessibilityService, "mesmo card de ${lastPackage ?: "?"}")
                    return@launch
                }
                lastCardSignature = signature

                val analysis = Calculator.analyze(card, settings)
                // Guarda no histórico (o mesmo card lido duas vezes não duplica).
                RideHistory.record(this@RideAccessibilityService, analysis)
                if (!manual) {
                    AutoDiag.anotar(
                        this@RideAccessibilityService,
                        "card lido em ${lastPackage ?: "?"}",
                        forcar = true
                    )
                }
                overlay?.showResult(analysis, settings)
            } catch (t: Throwable) {
                overlay?.restoreAfterCapture()
                if (manual) overlay?.toast("Erro: ${t.message}")
                else AutoDiag.anotar(this@RideAccessibilityService, "erro: ${t.message ?: "?"}", forcar = true)
            } finally {
                bmp?.recycle()
                scanning = false
            }
        }
    }

    /** Repassa às configurações salvas para o card que já está na tela. */
    fun applySettings(s: Settings) {
        overlay?.applySettings(s)
    }

    private fun cardSignature(card: RideCard): String =
        "${card.app}|${card.valor}|${card.kmTotal}|${card.minTotal}"

    private suspend fun captureScreen(): Bitmap? = suspendCancellableCoroutine { cont ->
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
                if (cont.isActive) cont.resume(bitmap?.scaledForOcr())
            }

            override fun onFailure(errorCode: Int) {
                if (cont.isActive) cont.resume(null)
            }
        }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)
        } catch (t: Throwable) {
            if (cont.isActive) cont.resume(null)
        }
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        overlay?.removeAll()
        overlay = null
        lastCardSignature = null
        screenReader?.close()
        screenReader = null
        scope.cancel()
        super.onDestroy()
    }
}
