package com.radardeganhos.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Configurações do motorista. Tudo fica local (SharedPreferences), sem backup em nuvem.
 */
data class Settings(
    /** Preço do litro do combustível em R$. */
    val precoLitro: Double = 5.89,
    /** Consumo médio em km por litro. */
    val consumo: Double = 10.0,
    /** Manutenção/desgaste em R$ por km (óleo, pneu, freio, revisão). */
    val manutencaoKm: Double = 0.25,
    /** Comissão do app em %, caso o valor do card já não venha líquido. */
    val comissaoPct: Double = 0.0,
    /** Custo fixo por hora (aluguel do carro, diária, seguro por hora). */
    val custoFixoHora: Double = 0.0,
    /** Meta de R$ líquido por km. */
    val metaKm: Double = 1.80,
    /** Meta de R$ líquido por hora. */
    val metaHora: Double = 30.0,
    /** Espera/parada extra lançada à mão, em minutos. */
    val esperaMin: Double = 0.0,
    /** Km de retorno extra em % do total (para voltar ao ponto forte). */
    val retornoPct: Double = 0.0,
    /** Analisa sozinho quando um card aparece no Uber/99. */
    val autoScan: Boolean = true
) {
    /** Custo variável por km (combustível + manutenção). */
    val custoKm: Double
        get() = (if (consumo > 0) precoLitro / consumo else 0.0) + manutencaoKm
}

object Prefs {
    private const val FILE = "radar_prefs"

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(c: Context): Settings {
        val p = sp(c)
        val d = Settings()
        fun dbl(key: String, def: Double): Double =
            p.getString(key, null)?.let { NumberParse.toDouble(it) } ?: def
        return Settings(
            precoLitro = dbl("precoLitro", d.precoLitro),
            consumo = dbl("consumo", d.consumo),
            manutencaoKm = dbl("manutencaoKm", d.manutencaoKm),
            comissaoPct = dbl("comissaoPct", d.comissaoPct),
            custoFixoHora = dbl("custoFixoHora", d.custoFixoHora),
            metaKm = dbl("metaKm", d.metaKm),
            metaHora = dbl("metaHora", d.metaHora),
            esperaMin = dbl("esperaMin", d.esperaMin),
            retornoPct = dbl("retornoPct", d.retornoPct),
            autoScan = p.getBoolean("autoScan", d.autoScan)
        )
    }

    fun save(c: Context, s: Settings) {
        sp(c).edit()
            .putString("precoLitro", s.precoLitro.toString())
            .putString("consumo", s.consumo.toString())
            .putString("manutencaoKm", s.manutencaoKm.toString())
            .putString("comissaoPct", s.comissaoPct.toString())
            .putString("custoFixoHora", s.custoFixoHora.toString())
            .putString("metaKm", s.metaKm.toString())
            .putString("metaHora", s.metaHora.toString())
            .putString("esperaMin", s.esperaMin.toString())
            .putString("retornoPct", s.retornoPct.toString())
            .putBoolean("autoScan", s.autoScan)
            .apply()
    }
}
