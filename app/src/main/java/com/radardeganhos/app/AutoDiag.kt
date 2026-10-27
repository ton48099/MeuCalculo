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
 */
object AutoDiag {
    private const val FILE = "radar_diag"
    private const val KEY = "resumo"

    private val hora = SimpleDateFormat("HH:mm", Locale.getDefault())

    @Volatile
    private var ultimaEscrita = 0L

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
}
