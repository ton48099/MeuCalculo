package com.radardeganhos.app

/**
 * Decide se a auto-análise pode rodar. Fica separado do serviço para poder ser
 * testado e para deixar explícito que ela obedece às configurações:
 * só age nos apps de corrida, respeita o intervalo escolhido, não roda em cima
 * de outra leitura e nunca fecha/atropela um card que o motorista está lendo.
 */
object AutoScanPolicy {

    val UBER_PKGS = setOf("com.ubercab.driver", "com.ubercab", "com.ubercab.driver.lite")
    val NINE_PKGS = setOf("com.taxis99", "com.taxis99.driver", "com.taxis99.passenger")

    fun isRideApp(pkg: String): Boolean = pkg in UBER_PKGS || pkg in NINE_PKGS

    fun intervaloMs(s: Settings): Long = s.autoIntervaloSeg.coerceIn(2, 120) * 1000L

    fun shouldScan(
        s: Settings,
        pkg: String,
        now: Long,
        lastScanAt: Long,
        scanning: Boolean,
        panelVisible: Boolean
    ): Boolean {
        if (!s.autoScan) return false
        if (!isRideApp(pkg)) return false
        if (scanning) return false
        if (panelVisible) return false
        return now - lastScanAt >= intervaloMs(s)
    }
}
