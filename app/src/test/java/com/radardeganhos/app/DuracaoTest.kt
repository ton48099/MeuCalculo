package com.radardeganhos.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * O bug da corrida de 1 h e 19 min lida como 9/19 min. Cada caso aqui é uma forma
 * que o card já apareceu no aparelho — em foto, com o OCR comendo dígito e letra.
 */
class DuracaoTest {

    @Test
    fun horaEMinutoValeOHoraInteira() {
        assertEquals(79.0, Duracao.minutos("1 h e 19 min")!!, 0.001)
        assertEquals(79.0, Duracao.minutos("1h19min")!!, 0.001)
        assertEquals(79.0, Duracao.minutos("1h 19min")!!, 0.001)
        assertEquals(79.0, Duracao.minutos("1 h e 19min")!!, 0.001)
        assertEquals(79.0, Duracao.minutos("1h19")!!, 0.001)
        assertEquals(79.0, Duracao.minutos("1 hora e 19 minutos")!!, 0.001)
        assertEquals(103.0, Duracao.minutos("1 h e 43 min")!!, 0.001)
    }

    @Test
    fun leOQueOcrFazComOsDigitos() {
        // "1" lido como "l", "19" com o um lido como "l" e dígitos separados.
        assertEquals(79.0, Duracao.minutos("lh e 19 min")!!, 0.001)
        assertEquals(79.0, Duracao.minutos("1 h e l9 min")!!, 0.001)
        assertEquals(79.0, Duracao.minutos("1 h e 1 9 min")!!, 0.001)
        // zero lido como a letra "o"
        assertEquals(30.0, Duracao.minutos("3o min")!!, 0.001)
    }

    @Test
    fun tempoSimplesContinuaIgual() {
        assertEquals(4.0, Duracao.minutos("4 min")!!, 0.001)
        assertEquals(5.0, Duracao.minutos("5Min")!!, 0.001)
        assertEquals(12.0, Duracao.minutos("12 minutos")!!, 0.001)
        assertEquals(120.0, Duracao.minutos("2 horas")!!, 0.001)
        assertEquals(60.0, Duracao.minutos("1h")!!, 0.001)
    }

    @Test
    fun antesDoKmPegaAFraseInteira() {
        val texto = "4 min (1.3 km) rua jose rodrigues, cachoeirinha 1 h e 19 min (71.7 km) rua luiz"
        val kmGrande = texto.indexOf("71.7")
        assertEquals(79.0, Duracao.minutosAntesDe(texto, kmGrande)!!, 0.001)
        val kmPequeno = texto.indexOf("1.3")
        assertEquals(4.0, Duracao.minutosAntesDe(texto, kmPequeno)!!, 0.001)
    }

    @Test
    fun antesDoKmIgnoraOParentesesEODistancia() {
        val t = "viagem de 12 min (4.9 km)"
        assertEquals(12.0, Duracao.minutosAntesDe(t, t.indexOf("4.9"))!!, 0.001)
    }

    @Test
    fun tempoDepoisDoKmTambemConta() {
        val t = "12 km • 25 min"
        assertEquals(25.0, Duracao.minutosDepoisDe(t, t.indexOf("km") + 2)!!, 0.001)
    }

    @Test
    fun textoSemTempoDevolveNulo() {
        assertNull(Duracao.minutos("Rua Luiz Volkart, 478, Centro, Três Coroas"))
        assertNull(Duracao.minutos(""))
        assertNull(Duracao.minutosAntesDe("Rua A, 10", 5))
    }
}
