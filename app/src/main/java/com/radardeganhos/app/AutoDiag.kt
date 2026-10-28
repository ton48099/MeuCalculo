package com.radardeganhos.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Uma linha de diagnóstico da leitura automática, mostrada na tela do app:
 * o que aconteceu por último, o pacote visto e a hora.
 *
 * Serve para responder sem adivinhação a pergunta "por que não leu sozinho?" —
 * o motorista manda a linha e já fica claro se o problema é app fora da lista,
 * card parado na tela, intervalo ou serviço desligado.
 *
 * Além da linha, guarda a ÚLTIMA LEITURA de verdade: o texto bruto do OCR e o que
 * o app entendeu dele. É isso que o botão "copiar log" leva para a conversa — com
 * o texto real na mão dá para consertar o parser sem chutar formato.
 */
object AutoDiag {
    private const val FILE = "radar_diag"
    private const val KEY = "resumo"
    private const val KEY_BRUTO = "leitura_bruta"
    private const val KEY_PACOTE = "leitura_pacote"
    private const val KEY_LIDO = "leitura_lida"

    /** Card de corrida cabe bem abaixo disso; o resto é proteção contra texto maluco. */
    private const val MAX_BRUTO = 6000

    private val hora = SimpleDateFormat("HH:mm", Locale.getDefault())

    @Volatile
    private var ultimaEscrita = 0L

    private fun prefs(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Anota um evento. Os motivos repetidos a cada janela de evento são agrupados
     * (máx. um por 3 s); [forcar] garante que uma leitura bem-sucedida apareça.
     */
    fun anotar(c: Context, texto: String, forcar: Boolean = false) {
        val agora = System.currentTimeMillis()
        if (!forcar && agora - ultimaEscrita < 3000L) return
        ultimaEscrita = agora
        val carimbo = hora.format(Date(agora))
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY, "$carimbo · $texto")
            .apply()
    }

    fun resumo(c: Context): String =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
            ?: "ainda sem leitura automática — abra o Uber/99 com o serviço ativo"

    /**
     * Guarda a última leitura: texto bruto do OCR, pacote de onde veio e o que o
     * parser tirou dele. Chamada em TODA leitura — inclusive quando não há card,
     * porque é justamente o texto que saiu torto que precisa aparecer no log.
     */
    fun salvarLeitura(c: Context, bruto: String, pacote: String?, card: RideCard?) {
        prefs(c).edit()
            .putString(KEY_BRUTO, bruto.take(MAX_BRUTO))
            .putString(KEY_PACOTE, pacote ?: "não visto")
            .putString(KEY_LIDO, if (card == null) NENHUM_CARD else resumir(card))
            .apply()
    }

    private const val NENHUM_CARD = "NENHUM CARD — sem valor+distância para calcular"

    /** Uma linha curta por campo: o que o parser decidiu, do jeito que a conta sai. */
    private fun resumir(card: RideCard): String = buildString {
        append("app=").append(card.app ?: "?")
        append(" · categoria=").append(card.categoria ?: "-")
        append(" · valor=").append(Report.money(card.valor))
        append("\ndeslocamento=").append(Report.km(card.kmDeslocamento))
        append(" / ").append(Report.tempo(card.minDeslocamento))
        append("\nviagem=").append(Report.km(card.kmViagem))
        append(" / ").append(Report.tempo(card.minViagem))
        if (card.precoPorKmApp != null) {
            append("\nR$/km que o app mostra: ").append(Report.money(card.precoPorKmApp))
        }
        if (card.avisos.isNotEmpty()) {
            append("\navisos: ").append(card.avisos.joinToString(" · "))
        }
    }

    /**
     * O log que o motorista cola na conversa junto com a foto do card: o texto bruto
     * do OCR de um lado, o que o app entendeu dele do outro. É olhando os dois lado
     * a lado que o erro aparece ("7l,7" no OCR virou 71,7 — ou virou 7,7).
     */
    fun montarLog(c: Context): String {
        val p = prefs(c)
        val bruto = p.getString(KEY_BRUTO, null)
            ?: return "Ainda não houve leitura — deixe o serviço ligado com um card do " +
                "Uber/99 na tela, espere ele ler e toque no botão de novo."
        return buildString {
            append("Radar · log da leitura\n")
            append("pacote: ").append(p.getString(KEY_PACOTE, null) ?: "não visto").append('\n')
            append("\n--- OCR LEU (texto bruto) ---\n")
            append(bruto)
            append("\n\n--- APP ENTENDEU ---\n")
            append(p.getString(KEY_LIDO, null) ?: "-")
            append("\n\n--- diagnóstico ---\n")
            append(resumo(c))
        }
    }
}
