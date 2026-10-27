package com.radardeganhos.app

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * O que acontece quando o Uber e a 99 estão abertos lado a lado na 2ª tela:
 * a captura é dividida pelas janelas de cada app e cada pedaço é lido separado,
 * para não virar um card só com os dois valores misturados.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DesktopLadoALadoTest {

    // Tela externa em paisagem: 1080 de altura, 2400 de largura.
    private val telaL = 2400
    private val telaA = 1080

    @Test
    fun janelasLadoALadoViramDoisRecortes() {
        val esquerda = Rect(0, 0, 1200, 1080)
        val direita = Rect(1200, 0, 2400, 1080)

        val recortes = Recortes.dividir(telaL, telaA, telaL, telaA, listOf(esquerda, direita))

        assertEquals(2, recortes.size)
        assertEquals(Rect(0, 0, 1200, 1080), recortes[0])
        assertEquals(Rect(1200, 0, 2400, 1080), recortes[1])
    }

    @Test
    fun recorteEscalaQuandoACapturaTemOutraResolucao() {
        // Captura menor que a tela: as coordenadas das janelas são reduzidas junto.
        val metade = Rect(0, 0, 1200, 1080)
        val recortes = Recortes.dividir(1200, 540, telaL, telaA, listOf(metade, Rect(1200, 0, 2400, 1080)))
        assertEquals(2, recortes.size)
        assertEquals(Rect(0, 0, 600, 540), recortes[0])
        assertEquals(Rect(600, 0, 1200, 540), recortes[1])
    }

    @Test
    fun umaJanelaSoNaoDivide() {
        // Um app só (ou uma janela escondendo a outra): lê a tela inteira como antes.
        assertTrue(
            Recortes.dividir(telaL, telaA, telaL, telaA, listOf(Rect(0, 0, 2400, 1080))).isEmpty()
        )
        assertTrue(Recortes.dividir(telaL, telaA, telaL, telaA, emptyList()).isEmpty())
    }

    @Test
    fun janelaVaziaOuForaDaCapturaAbortaACDivisao() {
        assertTrue(
            Recortes.dividir(telaL, telaA, telaL, telaA, listOf(Rect(0, 0, 1200, 1080), Rect()))
                .isEmpty()
        )
        // Janela com altura negativa: em vez de recorte torto, cai na tela inteira.
        assertTrue(
            Recortes.dividir(telaL, telaA, telaL, telaA, listOf(Rect(0, 0, 1200, 1080), Rect(1200, 1080, 2400, 900)))
                .isEmpty()
        )
    }

    @Test
    fun dimensoesImpossiveisDevolvemVazio() {
        assertNull(Recortes.paraCaptura(Rect(0, 0, 100, 100), 0, 100, 100, 100))
        assertNull(Recortes.paraCaptura(Rect(0, 0, 100, 100), 100, 100, 0, 100))
        assertTrue(Recortes.dividir(0, 0, telaL, telaA, listOf(Rect(0, 0, 10, 10), Rect(10, 0, 20, 10))).isEmpty())
    }

    @Test
    fun osDoisAppsLadoALadoViramDoisCards() {
        // O que o OCR devolve em cada metade da tela.
        val textoUber = "R\$ 24,90 · 5 min (1,2 km) de distância · Viagem de 18 min (7,4 km)"
        val texto99 = "R\$ 11,65 · 2 min (0,3 km) de distância · Viagem de 12 min (4,9 km)"

        val uber = RideParser.parse(textoUber, "com.ubercab.driver")
        val noveNove = RideParser.parse(texto99, "com.taxis99.driver")

        assertTrue(uber != null)
        assertTrue(noveNove != null)
        assertEquals(24.90, uber!!.valor, 0.01)
        assertEquals(11.65, noveNove!!.valor, 0.01)
        // Valores diferentes: sem dividir, os dois misturados dariam um card só.
        assertTrue(Math.abs(uber.valor - noveNove.valor) > 10)

        val assinaturas = listOf(uber, noveNove)
            .map { "${it.app}|${it.valor}|${it.kmTotal}|${it.minTotal}" }
            .toSet()
        assertEquals(2, assinaturas.size)
    }
}
