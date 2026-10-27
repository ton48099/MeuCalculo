package com.radardeganhos.app

/**
 * De onde a leitura de tela começa: logo abaixo do card do Radar.
 *
 * O card fica no topo e o OCR lia a tela inteira — então ele lia o próprio card e o
 * topo do app (saldo, menu, notificação). Como o parser pega o maior R$ do texto, o
 * saldo virava o valor da corrida e a conta saía errada.
 *
 * Agora a área lida é o que sobra da tela embaixo do card. As contas ficam aqui,
 * fora do serviço, porque é o que dá para conferir sem aparelho.
 */
object AreaLeitura {

    /** Folga entre o fim do card e o começo da leitura, em pixels da tela. */
    const val FOLGA_DP = 8

    /**
     * Área que o card ocupa, em dp, antes de o app saber a altura real dele (primeira
     * leitura depois de instalar). Cobre a barra de status e a faixa de cima do app
     * (onde ficam o saldo e o menu), sem chegar perto da oferta.
     */
    const val ALTURA_RESERVADA_DP = 220

    /**
     * Pedaço mínimo que ainda vale ler, em fração da altura da tela. Com o card
     * ocupando quase tudo (fonte grande, todos os itens) não sobra área útil: nesse
     * caso a leitura volta a ser a tela inteira, em vez de ler uma faixa de 20 px.
     */
    const val FRACAO_MINIMA = 0.25

    /**
     * Linha em que o card termina, em pixels da tela (0 quando não dá para saber).
     *
     * @param ancoradoNoTopo true nas posições presas no topo ("topo", "topo-esquerda",
     *        "topo-direita"): aí o deslocamento conta a partir do alto da tela. False
     *        quando o card é centrado na vertical, e o deslocamento conta do meio.
     */
    fun fimDoCard(
        alturaTela: Int,
        deslocamentoY: Int,
        alturaCard: Int,
        ancoradoNoTopo: Boolean
    ): Int {
        if (alturaTela <= 0 || alturaCard <= 0) return 0
        val fim = if (ancoradoNoTopo) {
            deslocamentoY + alturaCard
        } else {
            alturaTela / 2 + deslocamentoY + alturaCard / 2
        }
        return fim.coerceIn(0, alturaTela)
    }

    /**
     * Fim da área do card quando **nenhum** card está na tela. É o caso da leitura
     * automática, que dispara justamente depois que o card some: sem isto ela voltaria
     * a ler a tela inteira, com o saldo do app no meio.
     *
     * Usa a altura que o card teve da última vez e a posição configurada; na primeira
     * leitura, quando não há altura medida, reserva [ALTURA_RESERVADA_DP].
     */
    fun fimReservado(
        alturaTela: Int,
        ancoradoNoTopo: Boolean,
        deslocamentoY: Int,
        alturaUltimoCard: Int,
        alturaReservada: Int
    ): Int {
        val altura = if (alturaUltimoCard > 0) alturaUltimoCard else alturaReservada
        return fimDoCard(
            alturaTela = alturaTela,
            deslocamentoY = if (ancoradoNoTopo) deslocamentoY else 0,
            alturaCard = altura,
            ancoradoNoTopo = ancoradoNoTopo
        )
    }

    /**
     * Linha da captura em que a leitura começa: [inicioNaTela] (fim do card + folga)
     * convertida para os pixels da captura, que pode ter outra resolução.
     *
     * Devolve 0 — "lê a tela inteira", como antes — quando não há card na tela ou
     * quando não sobra área útil embaixo dele.
     */
    fun inicioNaCaptura(alturaCaptura: Int, alturaTela: Int, inicioNaTela: Int): Int {
        if (alturaCaptura <= 0 || alturaTela <= 0) return 0
        if (inicioNaTela <= 0) return 0
        val inicio = ((inicioNaTela.toLong() * alturaCaptura) / alturaTela).toInt()
        if (inicio <= 0) return 0
        val minimo = (alturaCaptura * FRACAO_MINIMA).toInt()
        if (alturaCaptura - inicio < minimo) return 0
        return inicio.coerceIn(0, alturaCaptura - 1)
    }
}
