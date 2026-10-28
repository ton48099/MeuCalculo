package com.radardeganhos.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideParserTest {

    private val uberX = """
        UberX Exclusiva
        R${'$'}11.65
        R${'$'}2.24/km est.
        4.94
        2 min (0.3 km) de distância
        Av. República, 123 - Brasil
        Viagem de 12 min (4.9 km)
        Alameda da República, 321 - Brasil
        Aceitar
    """.trimIndent()

    private val uberBlack = """
        Black
        R${'$'} 256,15
        4,77
        4 minutos (0.8 km) de distância
        Alameda Joaquim Eugênio de Lima, Bela Vista
        Viagem de 1 h e 43 min (95,7 km)
        Av. Sao Joao, 2200 - Jardim das Colinas
        Viagem longa (mais de 45 min)
        Aceitar
    """.trimIndent()

    private val ninetyNine = """
        Corrida longa
        R${'$'}54,89
        R${'$'}2,20 por km
        x2.0 R${'$'}15,50 inclusos
        4,8 · 12 corridas
        5Min (2,5km)
        Shopping Penha, R. Dr. João Ribeiro, 304
        32Min (6,3km) Área de risco
        Estação Prefeito Saladino
    """.trimIndent()

    private val ninetyNineDinheiro = """
        Corrida longa
        Dinheiro
        R${'$'}16,20
        Taxa de deslocamento R${'$'}1,11
        4,93 419 corridas
        Perfil Premium
        8min (3,3km)
        Terceira Rua, Rio do Ouro, Rio Bonito - RJ
        1 parada(s)
        22min (9,2km)
        Rua Maj. Bezerra Cavalcante, 651, Serra do
    """.trimIndent()

    @Test
    fun leCardDoUberX() {
        val c = RideParser.parse(uberX, "com.ubercab.driver")
        assertNotNull(c)
        c!!
        assertEquals("Uber", c.app)
        assertEquals(11.65, c.valor, 0.001)
        assertEquals(0.3, c.kmDeslocamento, 0.001)
        assertEquals(2.0, c.minDeslocamento, 0.001)
        assertEquals(4.9, c.kmViagem, 0.001)
        assertEquals(12.0, c.minViagem, 0.001)
        assertEquals(5.2, c.kmTotal, 0.001)
        assertEquals(2.24, c.precoPorKmApp ?: 0.0, 0.001)
    }

    @Test
    fun leCardDoUberBlackComHoraEMinuto() {
        val c = RideParser.parse(uberBlack, "com.ubercab.driver")
        assertNotNull(c)
        c!!
        assertEquals(256.15, c.valor, 0.001)
        assertEquals(0.8, c.kmDeslocamento, 0.001)
        assertEquals(4.0, c.minDeslocamento, 0.001)
        assertEquals(95.7, c.kmViagem, 0.001)
        assertEquals(103.0, c.minViagem, 0.001)
        assertEquals(96.5, c.kmTotal, 0.001)
    }

    @Test
    fun leCardDa99() {
        val c = RideParser.parse(ninetyNine, "com.taxis99")
        assertNotNull(c)
        c!!
        assertEquals(54.89, c.valor, 0.001)
        assertEquals(2.5, c.kmDeslocamento, 0.001)
        assertEquals(5.0, c.minDeslocamento, 0.001)
        assertEquals(6.3, c.kmViagem, 0.001)
        assertEquals(32.0, c.minViagem, 0.001)
        assertEquals(2.20, c.precoPorKmApp ?: 0.0, 0.001)
    }

    @Test
    fun leCardDa99ComTaxaDeDeslocamento() {
        val c = RideParser.parse(ninetyNineDinheiro, "com.taxis99")
        assertNotNull(c)
        c!!
        assertEquals(16.20, c.valor, 0.001)
        assertEquals(1.11, c.taxaDeslocamento ?: 0.0, 0.001)
        assertEquals(3.3, c.kmDeslocamento, 0.001)
        assertEquals(8.0, c.minDeslocamento, 0.001)
        assertEquals(9.2, c.kmViagem, 0.001)
        assertEquals(22.0, c.minViagem, 0.001)
    }

    @Test
    fun textoComumNaoViraCorrida() {
        assertNull(RideParser.parse("Bom dia, tudo bem? Vamos almoçar às 12h30."))
        assertNull(RideParser.parse(""))
    }

    @Test
    fun calculaGanhoLiquidoDoUberX() {
        val card = RideParser.parse(uberX, "com.ubercab.driver")!!
        val a = Calculator.analyze(card, Settings())
        // 5,2 km / 14 min, gasolina 5,89 a 10 km/l, manutenção 0,25/km
        assertEquals(4.3628, a.custoTotal, 0.01)
        assertEquals(7.2872, a.liquido, 0.01)
        assertEquals(31.23, a.liquidoHora, 0.05)
        assertEquals(1.40, a.liquidoKm, 0.01)
        // R$/km fica 22% abaixo da meta de 1,80 -> reprovado
        assertEquals(0.778, a.score / 100, 0.01)
        assertEquals(Veredito.RUIM, a.veredito)
        assertTrue(a.valorMinimo > card.valor)
    }

    @Test
    fun corridaLongaDoUberBlackValeMais() {
        val card = RideParser.parse(uberBlack, "com.ubercab.driver")!!
        val a = Calculator.analyze(card, Settings())
        assertEquals(96.5, a.kmCobrado, 0.001)
        assertEquals(1.82, a.liquidoKm, 0.01)
        assertTrue(a.liquidoHora > 90.0)
        assertTrue(a.veredito == Veredito.BOA || a.veredito == Veredito.OTIMA)
    }

    @Test
    fun corridaLongaDa99PagaBem() {
        val card = RideParser.parse(ninetyNine, "com.taxis99")!!
        val a = Calculator.analyze(card, Settings())
        assertEquals(8.8, a.kmCobrado, 0.001)
        assertTrue(a.liquidoKm > 4.0)
        assertEquals(Veredito.OTIMA, a.veredito)
    }

    @Test
    fun corridaDe1699NaoCompensa() {
        val card = RideParser.parse(ninetyNineDinheiro, "com.taxis99")!!
        val a = Calculator.analyze(card, Settings())
        assertEquals(12.5, a.kmCobrado, 0.001)
        assertEquals(30.0, a.minTotal, 0.001)
        assertTrue(a.liquidoHora < 15.0)
        assertEquals(Veredito.RUIM, a.veredito)
    }

    @Test
    fun custoExtraMudaOVeredito() {
        val card = RideParser.parse(uberX, "com.ubercab.driver")!!
        val base = Calculator.analyze(card, Settings())
        val caro = Calculator.analyze(
            card,
            Settings(precoLitro = 7.0, consumo = 7.0, manutencaoKm = 0.5, custoFixoHora = 10.0)
        )
        assertTrue(caro.liquidoKm < base.liquidoKm)
        assertTrue(caro.liquidoHora < base.liquidoHora)
        assertEquals(Veredito.RUIM, caro.veredito)
    }

    @Test
    fun corridaLongaDe1hE19minNaoVira9Min() {
        // Card real do Uber: "1 h e 19 min (71,7 km)". Quando o OCR perde o "1h" e
        // sobra "9 min (71,7 km)", 478 km/h denuncia o erro e o tempo é recuperado.
        val certo = RideParser.parse(
            "R\$ 118,89 Est. de R\$1,63/km 4 min (1.3 km) 1 h e 19 min (71.7 km)", "com.ubercab.driver"
        )!!
        assertEquals(79.0, certo.minViagem, 0.01)
        assertEquals(71.7, certo.kmViagem, 0.01)

        val ocrPerdeuAHora = RideParser.parse(
            "R\$ 118,89 4 min (1.3 km) 1 h e 19 min 9 min (71.7 km)", "com.ubercab.driver"
        )!!
        assertEquals(79.0, ocrPerdeuAHora.minViagem, 0.01)

        // O OCR separou os dígitos do "19": "1 h e 1 9 min". Antes o parser achava
        // "9 min (71,7 km)" e o card mostrava 9 min numa viagem de 1 h e 19 min.
        val digitosSeparados = RideParser.parse(
            "R\$ 118,89 4 min (1.3 km) 1 h e 1 9 min (71.7 km)", "com.ubercab.driver"
        )!!
        assertEquals(79.0, digitosSeparados.minViagem, 0.01)
        assertEquals(71.7, digitosSeparados.kmViagem, 0.01)

        // Mesmo com um km pequeno (9,2 km), onde a velocidade não denuncia o erro.
        val kmPequeno = RideParser.parse(
            "R\$ 118,89 4 min (1.3 km) 1 h e 1 9 min (9.2 km)", "com.ubercab.driver"
        )!!
        assertEquals(79.0, kmPequeno.minViagem, 0.01)

        // Por extenso, também com o número quebrado pelo OCR.
        val porExtenso = RideParser.parse(
            "R\$ 118,89 4 min (1.3 km) 1 hora e 1 9 minutos (71,7 km)", "com.ubercab.driver"
        )!!
        assertEquals(79.0, porExtenso.minViagem, 0.01)
    }

    @Test
    fun numerosBrasileiros() {
        assertEquals(1234.56, NumberParse.toDouble("1.234,56") ?: 0.0, 0.001)
        assertEquals(11.65, NumberParse.toDouble("11.65") ?: 0.0, 0.001)
        assertEquals(256.15, NumberParse.toDouble("256,15") ?: 0.0, 0.001)
        assertEquals(5.89, NumberParse.toDouble("5.89") ?: 0.0, 0.001)
        assertNull(NumberParse.toDouble(""))
    }
}
