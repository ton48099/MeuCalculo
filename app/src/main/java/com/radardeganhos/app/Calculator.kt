package com.radardeganhos.app

enum class Veredito(val label: String) {
    OTIMA("ÓTIMA"),
    BOA("BOA"),
    MEDIA("MÉDIA"),
    RUIM("RUIM")
}

/**
 * Resultado completo do cálculo de uma corrida.
 * Todos os valores monetários são em R$; os tempos em minutos.
 */
data class RideAnalysis(
    val card: RideCard,
    /** Km considerados no custo (inclui o retorno extra configurado). */
    val kmCobrado: Double,
    val minTotal: Double,
    val bruto: Double,
    val comissao: Double,
    val combustivel: Double,
    val manutencao: Double,
    val fixo: Double,
    val custoTotal: Double,
    val liquido: Double,
    val liquidoHora: Double,
    val liquidoKm: Double,
    val brutoHora: Double,
    val brutoKm: Double,
    /** Quanto esta corrida gera por minuto (bruto), para o "Valor/Minuto". */
    val brutoMinuto: Double,
    /** Valor bruto mínimo para atingir a meta de R$/km. */
    val minimoKm: Double,
    /** Valor bruto mínimo para atingir a meta de R$/hora. */
    val minimoHora: Double,
    /** O maior dos dois: o mínimo que a corrida precisaria pagar. */
    val valorMinimo: Double,
    /** 100 = exatamente na meta. Abaixo de 100 = abaixo da meta. */
    val score: Double,
    val veredito: Veredito
)

object Calculator {

    fun analyze(card: RideCard, s: Settings): RideAnalysis {
        val kmCobrado = (card.kmTotal * (1.0 + s.retornoPct / 100.0)).coerceAtLeast(0.1)
        val minTotal = (card.minTotal + s.esperaMin).coerceAtLeast(1.0)
        val horas = minTotal / 60.0

        val comissao = card.valor * (s.comissaoPct / 100.0)
        val combustivel = if (s.consumo > 0) kmCobrado * s.precoLitro / s.consumo else 0.0
        val manutencao = kmCobrado * s.manutencaoKm
        val fixo = s.custoFixoHora * horas
        val custoTotal = comissao + combustivel + manutencao + fixo

        val liquido = card.valor - custoTotal
        val liquidoHora = liquido / horas
        val liquidoKm = liquido / kmCobrado

        val metaKm = s.metaKm.coerceAtLeast(0.01)
        val metaHora = s.metaHora.coerceAtLeast(0.01)
        val ratio = minOf(liquidoKm / metaKm, liquidoHora / metaHora)

        val veredito = when {
            ratio >= 1.2 -> Veredito.OTIMA
            ratio >= 1.0 -> Veredito.BOA
            ratio >= 0.8 -> Veredito.MEDIA
            else -> Veredito.RUIM
        }

        val minimoKm = metaKm * kmCobrado + custoTotal
        val minimoHora = metaHora * horas + custoTotal

        return RideAnalysis(
            card = card,
            kmCobrado = kmCobrado,
            minTotal = minTotal,
            bruto = card.valor,
            comissao = comissao,
            combustivel = combustivel,
            manutencao = manutencao,
            fixo = fixo,
            custoTotal = custoTotal,
            liquido = liquido,
            liquidoHora = liquidoHora,
            liquidoKm = liquidoKm,
            brutoHora = card.valor / horas,
            brutoKm = card.valor / kmCobrado,
            brutoMinuto = card.valor / minTotal,
            minimoKm = minimoKm,
            minimoHora = minimoHora,
            valorMinimo = maxOf(minimoKm, minimoHora),
            score = ratio * 100.0,
            veredito = veredito
        )
    }
}

/** Formatação em português para a tela principal e para o painel flutuante. */
object Report {

    fun money(v: Double): String =
        "R$ " + String.format(java.util.Locale.US, "%.2f", v).replace('.', ',')

    fun num(v: Double, dec: Int = 2): String =
        String.format(java.util.Locale.US, "%.${dec}f", v).replace('.', ',')

    fun km(v: Double): String = num(v, if (v < 10) 1 else 1) + " km"

    fun tempo(min: Double): String {
        val m = Math.round(min).toInt()
        return if (m < 60) "$m min" else "${m / 60}h ${String.format(java.util.Locale.US, "%02d", m % 60)}min"
    }
}
