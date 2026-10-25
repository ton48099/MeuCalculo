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
    /** Até este R$/hora o valor fica vermelho (ruim). */
    val horaRuimAte: Double = 35.0,
    /** A partir deste R$/hora o valor fica verde (bom). */
    val horaBoaDe: Double = 45.0,
    /** Até este R$/km o valor fica vermelho (ruim). */
    val kmRuimAte: Double = 1.30,
    /** A partir deste R$/km o valor fica verde (bom). */
    val kmBoaDe: Double = 1.80,
    /** Espera/parada extra lançada à mão, em minutos. */
    val esperaMin: Double = 0.0,
    /** Km de retorno extra em % do total (para voltar ao ponto forte). */
    val retornoPct: Double = 0.0,
    /** Analisa sozinho quando um card aparece no Uber/99. */
    val autoScan: Boolean = true,
    /** Intervalo mínimo, em segundos, entre duas leituras automáticas. */
    val autoIntervaloSeg: Int = 6,
    /** Quantos segundos o card flutuante fica na tela. 0 = só fecha no X. */
    val cardDuracaoSeg: Int = 12,
    /** Escala do card: 0.85 pequeno, 1.0 normal, 1.15 grande, 1.3 extra. */
    val cardEscala: Float = 1.0f,
    /** Itens visíveis no card flutuante. */
    val cardVeredito: Boolean = true,
    val cardHora: Boolean = true,
    val cardKm: Boolean = true,
    val cardResumo: Boolean = true,
    val cardLucro: Boolean = true,
    val cardCustos: Boolean = true,
    val cardTrajeto: Boolean = true,
    val cardMeta: Boolean = true,
    val cardReler: Boolean = true
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
            horaRuimAte = dbl("horaRuimAte", d.horaRuimAte),
            horaBoaDe = dbl("horaBoaDe", d.horaBoaDe),
            kmRuimAte = dbl("kmRuimAte", d.kmRuimAte),
            kmBoaDe = dbl("kmBoaDe", d.kmBoaDe),
            esperaMin = dbl("esperaMin", d.esperaMin),
            retornoPct = dbl("retornoPct", d.retornoPct),
            autoScan = p.getBoolean("autoScan", d.autoScan),
            autoIntervaloSeg = p.getInt("autoIntervaloSeg", d.autoIntervaloSeg),
            cardDuracaoSeg = p.getInt("cardDuracaoSeg", d.cardDuracaoSeg),
            cardEscala = p.getFloat("cardEscala", d.cardEscala),
            cardVeredito = p.getBoolean("cardVeredito", d.cardVeredito),
            cardHora = p.getBoolean("cardHora", d.cardHora),
            cardKm = p.getBoolean("cardKm", d.cardKm),
            cardResumo = p.getBoolean("cardResumo", d.cardResumo),
            cardLucro = p.getBoolean("cardLucro", d.cardLucro),
            cardCustos = p.getBoolean("cardCustos", d.cardCustos),
            cardTrajeto = p.getBoolean("cardTrajeto", d.cardTrajeto),
            cardMeta = p.getBoolean("cardMeta", d.cardMeta),
            cardReler = p.getBoolean("cardReler", d.cardReler)
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
            .putString("horaRuimAte", s.horaRuimAte.toString())
            .putString("horaBoaDe", s.horaBoaDe.toString())
            .putString("kmRuimAte", s.kmRuimAte.toString())
            .putString("kmBoaDe", s.kmBoaDe.toString())
            .putString("esperaMin", s.esperaMin.toString())
            .putString("retornoPct", s.retornoPct.toString())
            .putBoolean("autoScan", s.autoScan)
            .putInt("autoIntervaloSeg", s.autoIntervaloSeg)
            .putInt("cardDuracaoSeg", s.cardDuracaoSeg)
            .putFloat("cardEscala", s.cardEscala)
            .putBoolean("cardVeredito", s.cardVeredito)
            .putBoolean("cardHora", s.cardHora)
            .putBoolean("cardKm", s.cardKm)
            .putBoolean("cardResumo", s.cardResumo)
            .putBoolean("cardLucro", s.cardLucro)
            .putBoolean("cardCustos", s.cardCustos)
            .putBoolean("cardTrajeto", s.cardTrajeto)
            .putBoolean("cardMeta", s.cardMeta)
            .putBoolean("cardReler", s.cardReler)
            .apply()
    }
}
