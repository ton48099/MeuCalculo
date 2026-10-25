package com.radardeganhos.app

import android.graphics.Color
import kotlin.math.round

/** Semáforo de uma métrica: bom (verde), neutro (branco) ou ruim (vermelho). */
enum class Tone(val label: String) {
    BOM("bom"),
    NEUTRO("neutro"),
    RUIM("ruim")
}

/**
 * Regras de cor do card para R$/hora e R$/km.
 *
 * Padrões (ajustáveis nas configurações):
 *   R$/hora: até 35,00 vermelho · 35,01–44,99 branco · 45,00 ou mais verde
 *   R$/km:   até 1,30 vermelho · 1,31–1,79 branco · 1,80 ou mais verde
 *
 * A comparação usa o valor com duas casas (o mesmo que aparece na tela), para que
 * algo como 44,997 não fique branco mostrando "R$ 45,00".
 */
object ColorRules {

    const val VERDE = "#00D26A"
    const val BRANCO = "#FFFFFF"
    const val VERMELHO = "#FF4D5E"

    fun toneHora(valorHora: Double, s: Settings): Tone {
        val v = centavos(valorHora)
        val bom = centavos(s.horaBoaDe)
        val ruim = centavos(s.horaRuimAte)
        return when {
            v >= bom -> Tone.BOM
            v <= ruim -> Tone.RUIM
            else -> Tone.NEUTRO
        }
    }

    fun toneKm(valorKm: Double, s: Settings): Tone {
        val v = centavos(valorKm)
        val bom = centavos(s.kmBoaDe)
        val ruim = centavos(s.kmRuimAte)
        return when {
            v >= bom -> Tone.BOM
            v <= ruim -> Tone.RUIM
            else -> Tone.NEUTRO
        }
    }

    fun corHora(valorHora: Double, s: Settings): Int = cor(toneHora(valorHora, s))

    fun corKm(valorKm: Double, s: Settings): Int = cor(toneKm(valorKm, s))

    fun cor(t: Tone): Int = Color.parseColor(
        when (t) {
            Tone.BOM -> VERDE
            Tone.NEUTRO -> BRANCO
            Tone.RUIM -> VERMELHO
        }
    )

    /** Valor com duas casas, igual ao exibido no card. */
    private fun centavos(v: Double): Double = round(v * 100.0) / 100.0
}
