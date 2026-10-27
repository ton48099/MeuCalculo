package com.radardeganhos.app

import android.graphics.Rect
import kotlin.math.abs

/**
 * Botão de recusar (o X) do card de ofertas.
 *
 * O X do Uber fica no canto de cima do card de oferta; o da 99, no alto da janela —
 * lugares diferentes, e cada app desenha o botão do seu jeito. Por isso a escolha
 * acontece em três degraus, do mais confiável para o mais improvisado:
 *
 *  1. **o X que o motorista ensinou**: quando ele recusa uma oferta à mão, o app guarda
 *     a impressão daquele botão (id, descrição e posição relativa) e passa a usar isso;
 *  2. **descrição/texto/id** que dizem "fechar", "recusar", "decline", "close"…;
 *  3. **geometria**: botão clicável pequeno colado na borda de cima da janela.
 *
 * Sem botão encontrado, nada é clicado — errar o alvo é pior do que não recusar.
 * Tudo aqui é puro (sem Android além de Rect) para dar para testar sem aparelho.
 */

/** Um botão visto na árvore de acessibilidade do app. */
data class BotaoCandidato(
    val id: String,
    val descricao: String,
    val texto: String,
    val esquerda: Int,
    val topo: Int,
    val direita: Int,
    val base: Int,
    /** O próprio nó é clicável? Quando não é, o app clica no pai clicável mais próximo. */
    val clicavel: Boolean
) {
    val largura: Int get() = direita - esquerda
    val altura: Int get() = base - topo
    val centroX: Int get() = (esquerda + direita) / 2
    val centroY: Int get() = (topo + base) / 2
}

/** Impressão do X aprendida com o motorista, guardada por pacote do app. */
data class XAprendido(
    val id: String,
    val descricao: String,
    val relX: Double,
    val relY: Double
) {
    fun encode(): String = "$id\u0001$descricao\u0001$relX\u0001$relY"

    companion object {
        fun decode(texto: String?): XAprendido? {
            val partes = texto?.split('\u0001') ?: return null
            if (partes.size != 4) return null
            val x = partes[2].toDoubleOrNull() ?: return null
            val y = partes[3].toDoubleOrNull() ?: return null
            if (partes[0].isBlank() && partes[1].isBlank()) return null
            return XAprendido(partes[0], partes[1], x, y)
        }

        /** Posição do botão dentro da janela do app, de 0 a 1. */
        fun de(c: BotaoCandidato, janela: Rect): XAprendido {
            val largura = janela.width().coerceAtLeast(1).toDouble()
            val altura = janela.height().coerceAtLeast(1).toDouble()
            return XAprendido(
                id = c.id,
                descricao = c.descricao,
                relX = (c.centroX - janela.left) / largura,
                relY = (c.centroY - janela.top) / altura
            )
        }
    }
}

object Recusa {

    /** Palavras que aparecem em descrição/texto de botão de recusar/dispensar. */
    private val PALAVRAS = listOf(
        "fechar", "close", "encerrar", "recusar", "recusa", "declinar", "decline",
        "reject", "dispensar", "ignorar", "cancelar", "nao aceitar", "não aceitar",
        "x", "✕", "✖", "×", "✗"
    )

    /** Pedaços de id de view usados pelos apps para esse botão. */
    private val IDS = listOf(
        "close", "decline", "reject", "recusar", "dismiss", "cancel", "btnx", "buttonx"
    )

    /** Botão pequeno o bastante para ser o X, em fração da janela. */
    private const val FRACAO_MAXIMA = 0.15f

    /** Quanto o centro pode estar da borda direita/esquerda para valer como X. */
    private const val FRACAO_BORDA = 0.18f

    // ---------------------------------------------------------------- critérios

    /**
     * Por que esta corrida deve ser recusada (vazio = aceita).
     *
     * Regras de segurança, antes de qualquer critério:
     *  - recusa desligada nas configurações → nunca recusa;
     *  - leitura incompleta (sem valor, km ou tempo) → nunca recusa: o app prefere
     *    deixar passar a arriscar recusar uma oferta boa por erro de leitura;
     *  - mínimo igual a 0 significa "não usa este critério".
     */
    fun motivos(a: RideAnalysis, s: Settings): List<String> {
        if (!s.recusaAtiva) return emptyList()
        val card = a.card
        if (card.valor <= 0.0 || card.kmTotal <= 0.05 || a.minTotal <= 0.0) return emptyList()

        val motivos = mutableListOf<String>()
        if (s.recusaMinKm > 0.0 && card.kmTotal < s.recusaMinKm) {
            motivos.add("${Report.km(card.kmTotal)} (mínimo ${Report.km(s.recusaMinKm)})")
        }
        if (s.recusaMinMin > 0.0 && a.minTotal < s.recusaMinMin) {
            motivos.add("${Report.tempo(a.minTotal)} (mínimo ${Report.tempo(s.recusaMinMin)})")
        }
        if (s.recusaMinHora > 0.0 && a.brutoHora < s.recusaMinHora) {
            motivos.add("${Report.money(a.brutoHora)}/h (mínimo ${Report.money(s.recusaMinHora)}/h)")
        }
        if (s.recusaMinPorKm > 0.0 && a.brutoKm < s.recusaMinPorKm) {
            motivos.add("${Report.money(a.brutoKm)}/km (mínimo ${Report.money(s.recusaMinPorKm)}/km)")
        }
        return motivos
    }

    fun deveRecusar(a: RideAnalysis, s: Settings): Boolean = motivos(a, s).isNotEmpty()

    /** Como o valor da corrida pode aparecer no texto da tela (com vírgula e com ponto). */
    fun formatosDoValor(valor: Double): List<String> =
        listOf(Report.num(valor), String.format(java.util.Locale.US, "%.2f", valor))

    /**
     * A oferta que foi analisada ainda está na tela? Sem isso, aceitar a corrida durante
     * a contagem faria o app apertar o X em outra coisa qualquer.
     *
     * null = a tela não expõe texto (app desenhado em canvas): aí não dá para conferir.
     * "Não sei" não pode virar "não clique", senão a recusa nunca funcionaria.
     */
    fun ofertaNaTela(valor: Double, textosDaTela: List<String>): Boolean? {
        val textos = textosDaTela.filter { it.isNotBlank() }
        if (textos.isEmpty()) return null
        val formatos = formatosDoValor(valor).filter { it.isNotBlank() }
        return textos.any { t -> formatos.any { f -> f in t } }
    }

    // ------------------------------------------------------------ escolha do X

    /**
     * Nota do candidato: maior = melhor; 0 = não serve.
     *
     * A nota é por degrau, e não uma soma de pontos: assim uma pista boa sempre ganha
     * de um palpite geométrico, por mais bem colocado que o palpite esteja.
     *
     *   4 = é o botão que o motorista ensinou (mesmo id) · 3 = mesma descrição ou mesmo
     *   lugar do aprendido · 2 = descrição/id de "fechar" · 1 = só geometria.
     */
    fun pontuar(c: BotaoCandidato, janela: Rect, aprendido: XAprendido?): Int {
        if (janela.isEmpty || c.largura <= 0 || c.altura <= 0) return 0

        val desc = normalizar(c.descricao)
        val texto = normalizar(c.texto)
        val id = c.id.lowercase()
        val casaDescricao = casaPalavra(desc) || casaPalavra(texto)
        val casaId = IDS.any { it in id }
        val mesmoId = aprendido != null && aprendido.id.isNotBlank() &&
            id == aprendido.id.lowercase()
        val mesmaDescricao = aprendido != null && aprendido.descricao.isNotBlank() &&
            desc.isNotEmpty() && desc == normalizar(aprendido.descricao)
        val pista = casaDescricao || casaId || mesmoId || mesmaDescricao

        // Sem pista nenhuma do que o botão é, só entra se ele for clicável.
        if (!c.clicavel && !pista) return 0

        val cabeNaJanela = c.largura <= janela.width() * FRACAO_MAXIMA &&
            c.altura <= janela.height() * FRACAO_MAXIMA
        // Botão grande demais (a oferta inteira, por exemplo) só passa com pista.
        if (!cabeNaJanela && !pista) return 0

        val pertoDaBorda = minOf(
            abs(c.centroX - janela.right),
            abs(c.centroX - janela.left)
        ) < janela.width() * FRACAO_BORDA
        val alturaRelativa = (c.centroY - janela.top).toDouble() / janela.height()
        val mesmoLugar = aprendido != null &&
            abs(relX(c, janela) - aprendido.relX) < 0.04 &&
            abs(relY(c, janela) - aprendido.relY) < 0.04

        val degrau = when {
            mesmoId -> 4
            mesmaDescricao || mesmoLugar -> 3
            casaDescricao || casaId -> 2
            pertoDaBorda && alturaRelativa < 0.85 -> 1
            else -> 0
        }
        if (degrau == 0) return 0

        // Desempate dentro do degrau: mais colado na borda e mais alto é melhor.
        var extra = 0
        if (pertoDaBorda) extra += 4
        extra += ((1.0 - alturaRelativa) * 4).toInt().coerceIn(0, 4)
        if (c.clicavel) extra += 1
        return degrau * 100 + extra
    }

    /** Índice do melhor candidato a X (null = nenhum serve). */
    fun escolherX(
        candidatos: List<BotaoCandidato>,
        janela: Rect,
        aprendido: XAprendido? = null
    ): Int? {
        var melhor = -1
        var melhorNota = 0
        candidatos.forEachIndexed { i, c ->
            val nota = pontuar(c, janela, aprendido)
            if (nota > melhorNota) {
                melhorNota = nota
                melhor = i
            }
        }
        return if (melhor >= 0) melhor else null
    }

    /** Texto de diagnóstico: o que foi achado, para o motorista conferir no celular. */
    fun descrever(c: BotaoCandidato): String {
        val onde = "x=${c.centroX} y=${c.centroY}"
        val id = if (c.id.isNotBlank()) " id=${c.id}" else ""
        val desc = if (c.descricao.isNotBlank()) " \"${c.descricao}\"" else ""
        return "$onde$id$desc${if (c.clicavel) "" else " (pai clicável)"}"
    }

    // ------------------------------------------------------------------ apoio

    /** minúsculas e sem acento, para comparar "Fechar" com "fechar". */
    fun normalizar(t: String): String {
        val semAcento = java.text.Normalizer.normalize(t.trim(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return semAcento.lowercase()
    }

    private fun casaPalavra(t: String): Boolean {
        if (t.isBlank()) return false
        return PALAVRAS.any { p ->
            t == p || t == "$p corrida" || t.startsWith("$p ") || t.endsWith(" $p")
        }
    }

    private fun relX(c: BotaoCandidato, janela: Rect): Double =
        (c.centroX - janela.left) / janela.width().coerceAtLeast(1).toDouble()

    private fun relY(c: BotaoCandidato, janela: Rect): Double =
        (c.centroY - janela.top) / janela.height().coerceAtLeast(1).toDouble()
}
