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

    /** Texto completo exibido na tela do app (e no teste de texto colado). */
    fun detalhado(a: RideAnalysis, s: Settings): String {
        val card = a.card
        val sb = StringBuilder()
        sb.append("${a.veredito.label}  ·  ${if (card.app != null) "card ${card.app}" else "leitura de tela"}\n\n")
        sb.append("Gera nesta corrida: ${money(a.brutoHora)}/h  ·  ${money(a.brutoKm)}/km\n")
        sb.append("Lucro líquido: ${money(a.liquido)}  (${money(a.liquidoHora)}/h  ·  ${money(a.liquidoKm)}/km)\n\n")
        sb.append("Corrida: ${money(a.bruto)}\n")
        sb.append("Distância: ${km(card.kmTotal)} (${km(card.kmDeslocamento)} até o cliente + ${km(card.kmViagem)} de viagem)\n")
        if (card.kmDeslocamento > 0 || s.retornoPct > 0) {
            sb.append("Km cobrados no custo: ${km(a.kmCobrado)}\n")
        }
        sb.append("Tempo: ${tempo(a.minTotal)} (${tempo(card.minDeslocamento)} + ${tempo(card.minViagem)}")
        if (s.esperaMin > 0) sb.append(" + ${tempo(s.esperaMin)} de espera")
        sb.append(")\n\n")

        sb.append("Custos\n")
        sb.append("• Combustível: ${money(a.combustivel)}\n")
        sb.append("• Manutenção: ${money(a.manutencao)}\n")
        if (a.comissao > 0) sb.append("• Comissão: ${money(a.comissao)}\n")
        if (a.fixo > 0) sb.append("• Custo fixo: ${money(a.fixo)}\n")
        sb.append("• Custo total: ${money(a.custoTotal)}\n")
        sb.append("• Sobra: ${money(a.liquido)}\n\n")

        sb.append("Meta: ${money(s.metaKm)}/km e ${money(s.metaHora)}/h  ·  nota ${Math.round(a.score)}%\n")
        if (card.valor < a.valorMinimo) {
            sb.append("Para bater a meta esta corrida precisaria pagar ${money(a.valorMinimo)} (faltam ${money(a.valorMinimo - card.valor)}).\n")
        } else {
            sb.append("Acima do mínimo de ${money(a.valorMinimo)} para bater a meta.\n")
        }
        if (card.precoPorKmApp != null) {
            sb.append("App informa ${money(card.precoPorKmApp!!)}/km bruto (o cálculo usa o total).\n")
        }
        if (card.taxaDeslocamento != null) {
            sb.append("Taxa de deslocamento detectada: ${money(card.taxaDeslocamento!!)} — não somada ao valor.\n")
        }
        if (card.avisos.isNotEmpty()) {
            sb.append("\nAvisos: ${card.avisos.joinToString("; ")}")
        }
        return sb.toString()
    }
}
