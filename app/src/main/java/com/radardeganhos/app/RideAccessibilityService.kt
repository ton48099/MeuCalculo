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

        val UBER_PKGS = setOf("com.ubercab.driver", "com.ubercab", "com.ubercab.driver.lite")
        val NINE_PKGS = setOf("com.taxis99", "com.taxis99.driver", "com.taxis99.passenger")
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private var screenReader: ScreenReader? = null
    private var overlay: OverlayController? = null

    private var scanning = false
    private var lastScanAt = 0L
    private var lastPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        if (screenReader == null) screenReader = ScreenReader()
        if (overlay == null) overlay = OverlayController(this) { analyzeScreen(manual = true) }
        overlay?.showBubble()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return
        lastPackage = pkg

        val settings = Prefs.load(this)
        if (!settings.autoScan) return
        if (pkg !in UBER_PKGS && pkg !in NINE_PKGS) return
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            e.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val now = SystemClock.elapsedRealtime()
        if (scanning || now - lastScanAt < 4000L) return
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
                    return@launch
                }

                val reader = screenReader ?: ScreenReader().also { screenReader = it }
                val text = try {
                    reader.read(bmp)
                } catch (t: Throwable) {
                    if (manual) overlay?.toast("Falha no OCR: ${t.message}")
                    return@launch
                }

                val settings = Prefs.load(this@RideAccessibilityService)
                val card = RideParser.parse(text, lastPackage)
                if (card == null) {
                    if (manual) overlay?.toast("Nenhum card de corrida nesta tela")
                    return@launch
                }

                val analysis = Calculator.analyze(card, settings)
                overlay?.showResult(analysis, settings)
            } catch (t: Throwable) {
                overlay?.restoreAfterCapture()
                if (manual) overlay?.toast("Erro: ${t.message}")
            } finally {
                bmp?.recycle()
                scanning = false
            }
        }
    }

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
        screenReader?.close()
        screenReader = null
        scope.cancel()
        super.onDestroy()
    }
}
