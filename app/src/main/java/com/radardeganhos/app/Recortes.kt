package com.radardeganhos.app

import android.graphics.Rect

/**
 * Divide a captura da 2ª tela nas janelas dos apps de corrida.
 *
 * Com o Uber e a 99 abertos lado a lado no modo desktop, um OCR da tela inteira
 * lê os dois cards como um texto só — e o parser acha um valor só. Recortando a
 * janela de cada app e lendo separado, saem as duas ofertas.
 *
 * As janelas vêm em coordenadas da tela; a captura pode ter outra resolução, então
 * o recorte é escalado. Tudo em pixels inteiros para o cálculo ser testável sem
 * aparelho: quando alguma conta não fecha, devolve vazio e a tela inteira é lida
 * do jeito antigo.
 */
object Recortes {

    /** Converte uma janela (coordenadas de tela) em retângulo da captura. */
    fun paraCaptura(
        janela: Rect,
        larguraCaptura: Int,
        alturaCaptura: Int,
        larguraTela: Int,
        alturaTela: Int
    ): Rect? {
        if (larguraCaptura <= 0 || alturaCaptura <= 0 || larguraTela <= 0 || alturaTela <= 0) return null
        if (janela.isEmpty) return null
        val kx = larguraCaptura.toDouble() / larguraTela
        val ky = alturaCaptura.toDouble() / alturaTela
        val x = (janela.left * kx).toInt().coerceIn(0, larguraCaptura - 1)
        val y = (janela.top * ky).toInt().coerceIn(0, alturaCaptura - 1)
        val direita = (janela.right * kx).toInt().coerceIn(0, larguraCaptura)
        val baixo = (janela.bottom * ky).toInt().coerceIn(0, alturaCaptura)
        // A janela pode passar 1 px da borda por arredondamento (ou pela barra do
        // sistema). Cortar o excedente é melhor do que desistir da divisão inteira.
        if (direita <= x || baixo <= y) return null
        return Rect(x, y, direita, baixo)
    }

    /**
     * Retângulos de recorte, um por janela. Vazio quando não dá para dividir de forma
     * segura (menos de duas janelas, coordenadas fora da captura, janela vazia).
     */
    fun dividir(
        larguraCaptura: Int,
        alturaCaptura: Int,
        larguraTela: Int,
        alturaTela: Int,
        janelas: List<Rect>
    ): List<Rect> {
        if (janelas.size < 2) return emptyList()
        val recortes = mutableListOf<Rect>()
        for (janela in janelas) {
            val r = paraCaptura(janela, larguraCaptura, alturaCaptura, larguraTela, alturaTela)
                ?: return emptyList()
            recortes.add(r)
        }
        return recortes
    }
}
