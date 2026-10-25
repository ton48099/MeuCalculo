package com.radardeganhos.app

import android.content.Context
import android.content.SharedPreferences
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Uma corrida que passou pelo app (manual ou auto-análise).
 * Guarda o que o motorista precisa conferir depois: valor, R$/km, R$/hora e o trajeto.
 */
data class RideRecord(
    val at: Long,
    val app: String?,
    val valor: Double,
    val km: Double,
    val brutoKm: Double,
    val brutoHora: Double,
    val liquido: Double,
    val origem: String?,
    val destino: String?
)

/**
 * Histórico local das corridas analisadas (SharedPreferences, nada sai do aparelho).
 * A serialização é texto com campos escapados, para poder ser testada sem Android.
 */
object RideHistory {
    private const val FILE = "radar_historico"
    private const val KEY = "corridas"
    private const val CAMPO = "|"
    const val MAX = 300

    /** Duas leituras do mesmo card nesse intervalo contam como uma só. */
    private const val JANELA_DEDUPE_MS = 120_000L

    private fun sp(c: Context): SharedPreferences = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(c: Context): List<RideRecord> = decode(sp(c).getString(KEY, null).orEmpty())

    fun save(c: Context, lista: List<RideRecord>) {
        sp(c).edit().putString(KEY, encode(lista)).apply()
    }

    fun clear(c: Context) {
        sp(c).edit().remove(KEY).apply()
    }

    fun from(a: RideAnalysis, at: Long = System.currentTimeMillis()): RideRecord = RideRecord(
        at = at,
        app = a.card.app,
        valor = a.bruto,
        km = a.card.kmTotal,
        brutoKm = a.brutoKm,
        brutoHora = a.brutoHora,
        liquido = a.liquido,
        origem = a.card.origem,
        destino = a.card.destino
    )

    /**
     * Registra a corrida no topo do histórico. Devolve false quando é a mesma
     * leitura repetida (auto-análise + toque manual no mesmo card, por exemplo).
     */
    fun record(c: Context, a: RideAnalysis, at: Long = System.currentTimeMillis()): Boolean {
        val novo = from(a, at)
        val atual = load(c)
        val ultimo = atual.firstOrNull()
        if (ultimo != null && mesmoCard(ultimo, novo) && novo.at - ultimo.at in 0..JANELA_DEDUPE_MS) {
            return false
        }
        save(c, (listOf(novo) + atual).take(MAX))
        return true
    }

    private fun mesmoCard(a: RideRecord, b: RideRecord): Boolean =
        a.app == b.app &&
            Math.abs(a.valor - b.valor) < 0.005 &&
            Math.abs(a.km - b.km) < 0.005 &&
            a.origem == b.origem &&
            a.destino == b.destino

    // ---- serialização -----------------------------------------------------

    private fun esc(v: String?): String = if (v == null) "" else URLEncoder.encode(v, "UTF-8")

    private fun unesc(v: String): String? =
        if (v.isEmpty()) null else runCatching { URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)

    fun encode(lista: List<RideRecord>): String = lista.joinToString("\n") { r ->
        listOf(
            r.at.toString(),
            esc(r.app),
            r.valor.toString(),
            r.km.toString(),
            r.brutoKm.toString(),
            r.brutoHora.toString(),
            r.liquido.toString(),
            esc(r.origem),
            esc(r.destino)
        ).joinToString(CAMPO)
    }

    fun decode(texto: String): List<RideRecord> {
        if (texto.isBlank()) return emptyList()
        return texto.split('\n').mapNotNull { linha ->
            val f = linha.split(CAMPO)
            if (f.size < 7) return@mapNotNull null
            val at = f[0].toLongOrNull() ?: return@mapNotNull null
            val valor = f[2].toDoubleOrNull() ?: return@mapNotNull null
            RideRecord(
                at = at,
                app = unesc(f[1]),
                valor = valor,
                km = f[3].toDoubleOrNull() ?: 0.0,
                brutoKm = f[4].toDoubleOrNull() ?: 0.0,
                brutoHora = f[5].toDoubleOrNull() ?: 0.0,
                liquido = f[6].toDoubleOrNull() ?: 0.0,
                origem = unesc(f.getOrNull(7).orEmpty()),
                destino = unesc(f.getOrNull(8).orEmpty())
            )
        }
    }
}
