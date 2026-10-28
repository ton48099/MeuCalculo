package com.radardeganhos.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Configurações do motorista. Tudo fica local (SharedPreferences), sem backup em nuvem.
 *
 * Regra importante: toda tela que mexe aqui precisa montar o Settings a partir de
 * [Prefs.load] — nunca a partir de [Settings] (os padrões), senão salvar numa tela
 * apaga o que o motorista configurou nas outras.
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
    val autoIntervaloSeg: Int = 2,
    /** Lê o card na tela externa do modo desktop (Smart Connect / DeX / espelhamento). */
    val modoDesktop: Boolean = false,
    /** Salva um print da tela toda vez que uma corrida é identificada (para o histórico). */
    val printCorrida: Boolean = true,
    /** Quantos segundos o card flutuante fica na tela. 0 = só fecha tocando nele. */
    val cardDuracaoSeg: Int = 12,
    /** Nível de opacidade do card (0-100). */
    val cardOpacidade: Int = 100,
    /** Tamanho da fonte do card: "pequena", "media" (padrão), "grande". */
    val cardTamanhoFonte: String = "media",
    /** Empilhar novos cards (deslize para ver). */
    val cardEmpilhar: Boolean = false,
    /**
     * Posição do card flutuante na tela: "topo" (padrão), "topo-esquerda",
     * "topo-direita" ou "centro".
     *
     * O topo é o padrão porque a leitura de tela começa logo abaixo do card: com ele
     * em cima sobram a oferta do app e o resto da tela na área lida, e ficam de fora
     * o próprio card do Radar e o topo do app (saldo, menu, notificação).
     */
    val cardGravity: String = "topo",
    /** Itens visíveis no card flutuante. */
    val cardVeredito: Boolean = true,
    val cardHora: Boolean = true,
    val cardKm: Boolean = true,
    /** R$/minuto (terceira coluna, ligada pelo "Valor/Minuto"). */
    val cardMinuto: Boolean = false,
    val cardResumo: Boolean = true,
    val cardLucro: Boolean = true,
    val cardCustos: Boolean = true,
    val cardTrajeto: Boolean = true,
    /** Linha "Busca: ... / Desembarque: ...". */
    val cardEnderecos: Boolean = true,
    val cardMeta: Boolean = true,
    val cardReler: Boolean = true,

    // --- Recusa automática (o X do card de ofertas) ---

    /**
     * Aperta o X sozinho quando a corrida está RUIM (abaixo da meta), depois da
     * contagem regressiva que o motorista pode cancelar tocando no card. Vem ligada;
     * o botão da tela principal e a opção do card desligam na hora.
     */
    val recusaAtiva: Boolean = true,
    /** Modo aviso: mostra a contagem e o motivo, mas NÃO clica no X. */
    val recusaSoAviso: Boolean = false,
    /** Segundos entre o card aparecer e o X ser apertado (tempo para você ler). */
    val recusaSegundos: Int = 5,
    /** Corrida mais curta que isto (km no total) é recusada. 0 = não usa o critério. */
    val recusaMinKm: Double = 0.0,
    /** Corrida mais curta que isto (minutos no total) é recusada. 0 = não usa. */
    val recusaMinMin: Double = 0.0,
    /** R$/hora abaixo disto é recusado. 0 = não usa. */
    val recusaMinHora: Double = 35.0,
    /** R$/km abaixo disto é recusado. 0 = não usa. */
    val recusaMinPorKm: Double = 1.30,
    /** Semáforo do R$/minuto (por minuto, igual à escala do R$/hora). */
    val semaforoMinutoBom: Double = 0.75,
    val semaforoMinutoRuim: Double = 0.58
) {
    /** Custo variável por km (combustível + manutenção). */
    val custoKm: Double
        get() = (if (consumo > 0) precoLitro / consumo else 0.0) + manutencaoKm
}

object Prefs {
    private const val FILE = "radar_prefs"

    /** Versão do arquivo de configurações: serve para trocar padrões já gravados. */
    private const val VERSAO = 3
    private const val CHAVE_VERSAO = "prefsVersao"

    /** Padrões antigos, só para saber se o motorista nunca mexeu no campo. */
    private const val INTERVALO_ANTIGO = 6
    private const val POSICAO_ANTIGA = "centro"

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Traz os padrões novos para quem já tem o app instalado: mudar o padrão no código
     * não muda nada em quem já tem valor gravado. Só troca o que ainda está no valor
     * antigo de fábrica, para não apagar uma escolha do motorista — e roda uma vez só.
     */
    private fun migrar(p: SharedPreferences) {
        val versao = p.getInt(CHAVE_VERSAO, 1)
        if (versao >= VERSAO) return
        val padrao = Settings()
        val ed = p.edit()
        if (versao < 2) {
            if (p.getInt("autoIntervaloSeg", INTERVALO_ANTIGO) == INTERVALO_ANTIGO) {
                ed.putInt("autoIntervaloSeg", padrao.autoIntervaloSeg)
            }
            if ((p.getString("cardGravity", POSICAO_ANTIGA) ?: POSICAO_ANTIGA) == POSICAO_ANTIGA) {
                ed.putString("cardGravity", padrao.cardGravity)
            }
        }
        if (versao < 3) {
            // 1.14: a recusa passa a clicar no X (o "só avisar" era o padrão antigo,
            // e era por isso que a contagem chegava ao fim sem recusar) e vem ligada.
            // A contagem regressiva é sempre mostrada, para dar tempo de cancelar.
            ed.putBoolean("recusaAtiva", true)
            ed.putBoolean("recusaSoAviso", false)
        }
        ed.putInt(CHAVE_VERSAO, VERSAO).apply()
    }

    /**
     * Impressão do botão X que o próprio motorista ensinou, por pacote do app
     * ("com.ubercab.driver", "com.app99.driver"…). O X do Uber e o da 99 ficam em
     * lugares diferentes, então cada app tem o seu.
     */
    fun xAprendido(c: Context, pkg: String): XAprendido? =
        XAprendido.decode(sp(c).getString("xBotao_$pkg", null))

    fun salvarXAprendido(c: Context, pkg: String, x: XAprendido) {
        sp(c).edit().putString("xBotao_$pkg", x.encode()).apply()
    }

    fun load(c: Context): Settings {
        val p = sp(c)
        migrar(p)
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
            autoIntervaloSeg = p.getInt("autoIntervaloSeg", d.autoIntervaloSeg).coerceIn(2, 120),
            modoDesktop = p.getBoolean("modoDesktop", d.modoDesktop),
            printCorrida = p.getBoolean("printCorrida", d.printCorrida),
            cardDuracaoSeg = p.getInt("cardDuracaoSeg", d.cardDuracaoSeg).coerceIn(0, 120),
            cardOpacidade = p.getInt("cardOpacidade", d.cardOpacidade).coerceIn(0, 100),
            cardTamanhoFonte = p.getString("cardTamanhoFonte", d.cardTamanhoFonte) ?: d.cardTamanhoFonte,
            cardEmpilhar = p.getBoolean("cardEmpilhar", d.cardEmpilhar),
            cardGravity = p.getString("cardGravity", d.cardGravity) ?: d.cardGravity,
            cardVeredito = p.getBoolean("cardVeredito", d.cardVeredito),
            cardHora = p.getBoolean("cardHora", d.cardHora),
            cardKm = p.getBoolean("cardKm", d.cardKm),
            cardMinuto = p.getBoolean("cardMinuto", d.cardMinuto),
            cardResumo = p.getBoolean("cardResumo", d.cardResumo),
            cardLucro = p.getBoolean("cardLucro", d.cardLucro),
            cardCustos = p.getBoolean("cardCustos", d.cardCustos),
            cardTrajeto = p.getBoolean("cardTrajeto", d.cardTrajeto),
            cardEnderecos = p.getBoolean("cardEnderecos", d.cardEnderecos),
            cardMeta = p.getBoolean("cardMeta", d.cardMeta),
            cardReler = p.getBoolean("cardReler", d.cardReler),
            recusaAtiva = p.getBoolean("recusaAtiva", d.recusaAtiva),
            recusaSoAviso = p.getBoolean("recusaSoAviso", d.recusaSoAviso),
            recusaSegundos = p.getInt("recusaSegundos", d.recusaSegundos).coerceIn(1, 30),
            recusaMinKm = dbl("recusaMinKm", d.recusaMinKm),
            recusaMinMin = dbl("recusaMinMin", d.recusaMinMin),
            recusaMinHora = dbl("recusaMinHora", d.recusaMinHora),
            recusaMinPorKm = dbl("recusaMinPorKm", d.recusaMinPorKm),
            semaforoMinutoBom = dbl("semaforoMinutoBom", d.semaforoMinutoBom),
            semaforoMinutoRuim = dbl("semaforoMinutoRuim", d.semaforoMinutoRuim)
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
            .putBoolean("modoDesktop", s.modoDesktop)
            .putBoolean("printCorrida", s.printCorrida)
            .putInt("cardDuracaoSeg", s.cardDuracaoSeg)
            .putInt("cardOpacidade", s.cardOpacidade)
            .putString("cardTamanhoFonte", s.cardTamanhoFonte)
            .putBoolean("cardEmpilhar", s.cardEmpilhar)
            .putString("cardGravity", s.cardGravity)
            .putBoolean("cardVeredito", s.cardVeredito)
            .putBoolean("cardHora", s.cardHora)
            .putBoolean("cardKm", s.cardKm)
            .putBoolean("cardMinuto", s.cardMinuto)
            .putBoolean("cardResumo", s.cardResumo)
            .putBoolean("cardLucro", s.cardLucro)
            .putBoolean("cardCustos", s.cardCustos)
            .putBoolean("cardTrajeto", s.cardTrajeto)
            .putBoolean("cardEnderecos", s.cardEnderecos)
            .putBoolean("cardMeta", s.cardMeta)
            .putBoolean("cardReler", s.cardReler)
            .putBoolean("recusaAtiva", s.recusaAtiva)
            .putBoolean("recusaSoAviso", s.recusaSoAviso)
            .putInt("recusaSegundos", s.recusaSegundos)
            .putString("recusaMinKm", s.recusaMinKm.toString())
            .putString("recusaMinMin", s.recusaMinMin.toString())
            .putString("recusaMinHora", s.recusaMinHora.toString())
            .putString("recusaMinPorKm", s.recusaMinPorKm.toString())
            .putString("semaforoMinutoBom", s.semaforoMinutoBom.toString())
            .putString("semaforoMinutoRuim", s.semaforoMinutoRuim.toString())
            .putInt(CHAVE_VERSAO, VERSAO)
            .apply()
    }
}
