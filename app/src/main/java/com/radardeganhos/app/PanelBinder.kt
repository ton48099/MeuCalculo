package com.radardeganhos.app

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView

/**
 * Preenche o card flutuante. Fica separado do OverlayController para poder ser
 * testado sem uma janela do sistema (a janela só existe no aparelho).
 *
 * Foco do card: quanto a corrida gera por hora e por km (valores brutos, diretos do
 * card) e, logo abaixo, o lucro líquido já descontando o que está configurado.
 */
object PanelBinder {

    fun corDoVeredito(v: Veredito): Int = when (v) {
        Veredito.OTIMA -> Color.parseColor("#00E676")
        Veredito.BOA -> Color.parseColor("#00D26A")
        Veredito.MEDIA -> Color.parseColor("#FFC531")
        Veredito.RUIM -> Color.parseColor("#FF4D5E")
    }

    fun bind(v: View, a: RideAnalysis, s: Settings) {
        val cor = corDoVeredito(a.veredito)
        val density = v.resources.displayMetrics.density
        fun dp(x: Float): Int = (x * density).toInt()

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

        // Quanto ESTA corrida gera por hora e por km (bruto, como o motorista vê no card),
        // com o semáforo configurado: vermelho = ruim, branco = neutro, verde = bom.
        v.findViewById<TextView>(R.id.tvHora).apply {
            text = Report.money(a.brutoHora)
            setTextColor(ColorRules.corHora(a.brutoHora, s))
        }
        v.findViewById<TextView>(R.id.tvKm).apply {
            text = Report.money(a.brutoKm) + "/km"
            setTextColor(ColorRules.corKm(a.brutoKm, s))
        }
        v.findViewById<TextView>(R.id.tvBruto).text = buildString {
            append("corrida ${Report.money(a.bruto)}")
            append(" · ${Report.km(a.card.kmTotal)}")
            append(" · ${Report.tempo(a.minTotal)}")
            if (a.card.deslocamentoOuViagemResumo().isNotBlank()) {
                append(" (${a.card.deslocamentoOuViagemResumo()})")
            }
        }

        // Lucro líquido da corrida = valor − custos configurados.
        val lucro = v.findViewById<TextView>(R.id.tvLucroValor)
        lucro.text = Report.money(a.liquido)
        lucro.setTextColor(if (a.liquido >= 0) cor else Color.parseColor("#FF4D5E"))
        v.findViewById<TextView>(R.id.tvLucroSub).text = buildString {
            append(Report.money(a.bruto))
            append(" − ")
            append(Report.money(a.custoTotal))
            append(" de custos")
            append("\nsobra ${Report.money(a.liquidoHora)}/h · ${Report.money(a.liquidoKm)}/km")
        }

        v.findViewById<TextView>(R.id.tvResumo).text = buildString {
            append("Deslocamento ${Report.km(a.card.kmDeslocamento)} / ${Report.tempo(a.card.minDeslocamento)}")
            append(" · Viagem ${Report.km(a.card.kmViagem)} / ${Report.tempo(a.card.minViagem)}")
            append("\nKm no custo: ${Report.km(a.kmCobrado)} · tempo ${Report.tempo(a.minTotal)}")
        }

        v.findViewById<TextView>(R.id.tvCustos).text = buildString {
            append("Combustível ${Report.money(a.combustivel)}")
            append(" · Manutenção ${Report.money(a.manutencao)}")
            if (a.comissao > 0) append(" · Comissão ${Report.money(a.comissao)}")
            if (a.fixo > 0) append(" · Fixo ${Report.money(a.fixo)}")
            append("\nCusto total ${Report.money(a.custoTotal)}")
            if (a.card.taxaDeslocamento != null) {
                append(" · taxa ${Report.money(a.card.taxaDeslocamento!!)} (não somada)")
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
            setTextColor(
                if (a.card.valor < a.valorMinimo) Color.parseColor("#FFC531")
                else Color.parseColor("#00D26A")
            )
        }

        applyVisibility(v, s)
    }

    /** Mostra/oculta cada item do card conforme as configurações. */
    fun applyVisibility(v: View, s: Settings) {
        fun vis(b: Boolean) = if (b) View.VISIBLE else View.GONE

        v.findViewById<View>(R.id.tvVerdict).visibility = vis(s.cardVeredito)
        v.findViewById<View>(R.id.colHora).visibility = vis(s.cardHora)
        v.findViewById<View>(R.id.colKm).visibility = vis(s.cardKm)
        v.findViewById<View>(R.id.rowMetricas).visibility = vis(s.cardHora || s.cardKm)
        v.findViewById<View>(R.id.tvBruto).visibility = vis(s.cardResumo)
        v.findViewById<View>(R.id.boxLucro).visibility = vis(s.cardLucro)
        v.findViewById<View>(R.id.tvResumo).visibility = vis(s.cardTrajeto)
        v.findViewById<View>(R.id.tvCustos).visibility = vis(s.cardCustos)
        v.findViewById<View>(R.id.tvMeta).visibility = vis(s.cardMeta)
        v.findViewById<View>(R.id.btnRepeat).visibility = vis(s.cardReler)

        val temDetalhe = s.cardTrajeto || s.cardCustos || s.cardMeta || s.cardReler
        v.findViewById<View>(R.id.divider).visibility = vis(temDetalhe)
    }
}

/** "2 min até o cliente + 14 min de viagem", para o resumo compacto do card. */
private fun RideCard.deslocamentoOuViagemResumo(): String = buildString {
    if (minDeslocamento > 0) {
        append("${Report.tempo(minDeslocamento)} até o cliente")
    }
    if (minViagem > 0) {
        if (isNotEmpty()) append(" + ")
        append("${Report.tempo(minViagem)} de viagem")
    }
}
