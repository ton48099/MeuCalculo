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
    fun minutosQuebradosPeloOcrNaoPerdemODigito() {
        // Card real do Uber de R$ 10,08: "18 min (4,2 km)" saiu do OCR como
        // "1 8 min" e o card mostrava 8 min. O dígito separado tem de voltar a 18.
        val dezoito = RideParser.parse(
            "R\$ 10,08 2 min (0.5 km) 1 8 min (4.2 km)", "com.ubercab.driver"
        )!!
        assertEquals(18.0, dezoito.minViagem, 0.01)
        assertEquals(4.2, dezoito.kmViagem, 0.01)
        assertEquals(20.0, dezoito.minTotal, 0.01)

        // Por extenso: "18 minutos" lido como "1 8 minutos".
        val porExtenso = RideParser.parse(
            "R\$ 10,08 2 min (0.5 km) 1 8 minutos (4.2 km)", "com.ubercab.driver"
        )!!
        assertEquals(18.0, porExtenso.minViagem, 0.01)

        // "22 min" quebrado em "2 2 min" também não pode virar 2 min.
        val vinteDois = RideParser.parse(
            "R\$ 10,08 2 min (0.5 km) 2 2 min (7.4 km)", "com.ubercab.driver"
        )!!
        assertEquals(22.0, vinteDois.minViagem, 0.01)
        assertEquals(7.4, vinteDois.kmViagem, 0.01)
    }

    @Test
    fun numerosBrasileiros() {
        assertEquals(1234.56, NumberParse.toDouble("1.234,56") ?: 0.0, 0.001)
        assertEquals(11.65, NumberParse.toDouble("11.65") ?: 0.0, 0.001)
        assertEquals(256.15, NumberParse.toDouble("256,15") ?: 0.0, 0.001)
        assertEquals(5.89, NumberParse.toDouble("5.89") ?: 0.0, 0.001)
        assertNull(NumberParse.toDouble(""))
    }

    // ---- cards das capturas de 28/10 (Uber e 99 Motorista atuais) ----------

    /**
     * Card da 99 com ofertas alternativas embaixo ("R$10,73  R$11,24  R$11,75").
     * O valor da corrida é o do topo, R$ 10,22 — antes o parser pegava o maior R$ da
     * tela e lia R$ 11,75, o que estragava a conta e o veredito.
     */
    @Test
    fun valorEhODoTopoDoCardENaoDaOfertaAlternativa() {
        val c = RideParser.parse(
            """
            Negocia Pgto. no app
            R${'$'}10,22
            R${'$'}1,29/km
            4,83 · 209 corridas
            CPF e Cartão verif.
            6 min (1.2 km)
            Avenida Venâncio Aires, 449, Cidade Baixa
            15 min (6.8 km)
            Clínica Iguatemi, Avenida Dr. Nilo Peçanha, 2811 - Conj 1304
            Aceitar por R${'$'}10,22
            R${'$'}10,73
            R${'$'}11,24
            R${'$'}11,75
            """.trimIndent(),
            "com.taxis99"
        )!!
        assertEquals(10.22, c.valor, 0.001)
        assertEquals(1.2, c.kmDeslocamento, 0.001)
        assertEquals(6.0, c.minDeslocamento, 0.001)
        assertEquals(6.8, c.kmViagem, 0.001)
        assertEquals(15.0, c.minViagem, 0.001)
        assertEquals(1.29, c.precoPorKmApp ?: 0.0, 0.001)
        assertEquals("99", c.app)
    }

    /**
     * 99 Motorista (com.app99.driver): o card não tem a palavra "99" em lugar nenhum,
     * então quem identifica o app é o pacote — antes saía "app não identificado" e a
     * recusa não sabia de quem era a oferta na 2ª tela.
     */
    @Test
    fun identifica99PeloPacoteDoMotorista() {
        val c = RideParser.parse(
            """
            Pgto. no app
            R${'$'}9,20 1,2x
            R${'$'}1,89/km
            R${'$'}1,42 por embarque distante incl.
            ★ 4,84 · +999 corridas
            Perfil Premium
            6 min (1,5 km)
            Rua Múcio Teixeira, 1392, Menino Deus
            7 min (3,3 km)
            Rua Eng. Luiz Englert, 333, Farroupilha
            Aceitar (2s)
            """.trimIndent(),
            "com.app99.driver"
        )!!
        assertEquals("99", c.app)
        assertEquals(9.20, c.valor, 0.001)
        assertEquals(4.8, c.kmTotal, 0.001)
        assertEquals(13.0, c.minTotal, 0.001)
        assertEquals(1.89, c.precoPorKmApp ?: 0.0, 0.001)
    }

    /**
     * Uber atual: "Est. de R$1,42/km" e "18 minutos (5.4 km)". A conta do app tem de
     * bater com a estimativa que o próprio Uber mostra no card (10,08 / 7,1 km ≈ 1,42).
     */
    @Test
    fun leCardAtualDoUberComEstimativaPorKm() {
        val c = RideParser.parse(
            """
            UberX Exclusivo
            R${'$'} 10,08
            Est. de R${'$'}1,42/km
            ★ 4,91 (303)
            4 min (1.7 km)
            Rua Gonçalves de Magalhães, Alvorada, Alvorada
            18 minutos (5.4 km)
            Rua Dois Mil e Trinta e Nove, 60, Mário Quintana, Porto Alegre
            Aceitar
            """.trimIndent(),
            "com.ubercab.driver"
        )!!
        assertEquals("Uber", c.app)
        assertEquals(10.08, c.valor, 0.001)
        assertEquals(7.1, c.kmTotal, 0.001)
        assertEquals(22.0, c.minTotal, 0.001)
        assertEquals(1.42, c.precoPorKmApp ?: 0.0, 0.001)
        assertEquals(1.42, c.valor / c.kmTotal, 0.01)
    }

    // ---- corrida longa (1 h e 19 min): as capturas de 28/10 --------------

    /** O card completo da foto: R$ 118,89, 4 min (1.3 km) e 1 h e 19 min (71.7 km). */
    @Test
    fun leCorridaLongaDeUmaHoraDaCaptura() {
        val c = RideParser.parse(
            """
            UberX Exclusivo
            R${'$'} 118,89
            Est. de R${'$'}1,63/km
            ★ 4,92 (229)
            +10% de ganhos Uber Pro
            4 min (1.3 km)
            Rua José Rodrigues, Cachoeirinha, Cachoeirinha
            1 h e 19 min (71.7 km)
            Rua Luiz Volkart, 478, Centro, Três Coroas
            Viagem longa (mais de 30 min)
            Aceitar
            """.trimIndent(),
            "com.ubercab.driver"
        )!!
        assertEquals("Uber", c.app)
        assertEquals(118.89, c.valor, 0.001)
        assertEquals(1.3, c.kmDeslocamento, 0.001)
        assertEquals(4.0, c.minDeslocamento, 0.001)
        assertEquals(71.7, c.kmViagem, 0.001)
        assertEquals(79.0, c.minViagem, 0.001)
        assertEquals(73.0, c.kmTotal, 0.001)
        assertEquals(83.0, c.minTotal, 0.001)
    }

    /** OCR troca o "1" por "l" ("lh e 19 min", "7l,7 km") — a hora não pode sumir. */
    @Test
    fun leHoraComLNoLugarDoUm() {
        val c = RideParser.parse(
            "R\$ 118,89 4 min (1.3 km) lh e 19 min (7l,7 km)", "com.ubercab.driver"
        )!!
        assertEquals(79.0, c.minViagem, 0.01)
        assertEquals(71.7, c.kmViagem, 0.01)
    }

    /** "1h19 (71,7 km)": o OCR comeu o "min" depois do número. */
    @Test
    fun leHoraSemAMinhaSeparada() {
        val c = RideParser.parse(
            "R\$ 118,89 4 min (1.3 km) 1h19 (71,7 km)", "com.ubercab.driver"
        )!!
        assertEquals(79.0, c.minViagem, 0.01)
        assertEquals(71.7, c.kmViagem, 0.01)
    }

    // ---- categorias (UberX/Confort · 99Pop/99Negocia) e capturas de 29/10 --

    /**
     * Card do Uber (foto nova): badge "UberX", linha "2 paradas" e trecho escrito
     * por extenso — "58 minutos (28.1 km)". A conta tem de fechar com o "Est. de
     * R${'$'}1,25/km" que o próprio card mostra.
     */
    @Test
    fun leCategoriaEBa58MinutosNoCardDoUber() {
        val c = RideParser.parse(
            """
            UberX
            R${'$'} 37,03
            Est. de R${'$'}1,25/km
            4,88 (1039)
            4 min (1.6 km)
            Av. João Corrêa, Bairro Centro, São Leopoldo
            2 paradas
            58 minutos (28.1 km)
            R. Prof. Stumpf, 183, Vicentina, São Leopoldo
            Viagem longa (mais de 30 min)
            Tenho interesse
            """.trimIndent(),
            "com.ubercab.driver"
        )!!
        assertEquals("Uber", c.app)
        assertEquals("UberX", c.categoria)
        assertEquals(37.03, c.valor, 0.001)
        assertEquals(1.6, c.kmDeslocamento, 0.001)
        assertEquals(4.0, c.minDeslocamento, 0.001)
        assertEquals(28.1, c.kmViagem, 0.001)
        assertEquals(58.0, c.minViagem, 0.001)
        assertEquals(1.25, c.valor / c.kmTotal, 0.01)
    }

    /**
     * Card da 99 Negocia (foto nova): o tempo e a distância vêm separados por um
     * ponto — "5min • 2,3km" e "235min • 283,5km". Sem aceitar o ponto, o par não
     * casava e o km do cabeçalho ("283,5 km") acabava virando o deslocamento.
     */
    @Test
    fun leCardDa99NegociaComPontoEntreTempoEKm() {
        val c = RideParser.parse(
            """
            5,00 ★ • 37 corridas • 99Negocia
            Perfil Premium
            R${'$'} 1.032,48
            Longa
            283,5 km
            5min • 2,3km
            Residencial Vivendas do Lago, Estr. do Ipatinga, 401 - Sorocaba - SP
            235min • 283,5km
            Terminal Rodoviário de Caraguatatuba, Avenida Brasília, 50 - Caraguatatuba - SP
            Aceitar por 1.032,48
            """.trimIndent(),
            "com.taxis99"
        )!!
        assertEquals("99", c.app)
        assertEquals("Negocia", c.categoria)
        assertEquals(1032.48, c.valor, 0.001)
        assertEquals(2.3, c.kmDeslocamento, 0.001)
        assertEquals(5.0, c.minDeslocamento, 0.001)
        assertEquals(283.5, c.kmViagem, 0.001)
        assertEquals(235.0, c.minViagem, 0.001)
    }

    /**
     * "99Pop" colado: o 99 seguido de letra não tem borda de palavra, então o nome
     * da categoria é que identifica o app quando a leitura não traz o pacote.
     */
    @Test
    fun identifica99PeloNomeDaCategoriaSemPacote() {
        val c = RideParser.parse(
            """
            99Pop Pgto. no app
            R${'$'} 12,60
            R${'$'}1,72/km
            4,90 · 150 corridas
            6 min • 1,8km
            Rua Mário Quintana, 100, Centro
            14 min • 7,2km
            Avenida Ipiranga, 2000, República
            Aceitar
            """.trimIndent()
        )!!
        assertEquals("99", c.app)
        assertEquals("99Pop", c.categoria)
        assertEquals(12.60, c.valor, 0.001)
        assertEquals(1.8, c.kmDeslocamento, 0.001)
        assertEquals(6.0, c.minDeslocamento, 0.001)
        assertEquals(7.2, c.kmViagem, 0.001)
        assertEquals(14.0, c.minViagem, 0.001)
    }

    /** Categoria "Confort" do Uber, lida no badge do card. */
    @Test
    fun leCategoriaConfortNoCardDoUber() {
        val c = RideParser.parse(
            """
            Confort
            R${'$'} 24,50
            Est. de R${'$'}1,90/km
            5 min (2.1 km)
            Rua Carlos Gomes, 500, Centro
            21 min (10,8 km)
            Avenida Bibiano Vidal, 1200, Floresta
            Aceitar
            """.trimIndent(),
            "com.ubercab.driver"
        )!!
        assertEquals("Uber", c.app)
        assertEquals("Confort", c.categoria)
        assertEquals(24.50, c.valor, 0.001)
        assertEquals(12.9, c.kmTotal, 0.001)
        assertEquals(26.0, c.minTotal, 0.001)
    }

    /**
     * Card do Uber (foto nova) com dois trechos de 9 min — um escrito "min", outro
     * "minutos": os dois têm de entrar na conta, um como busca e outro como viagem.
     */
    @Test
    fun leDoisNoveMinutosNoCardDoUber() {
        val c = RideParser.parse(
            """
            UberX
            R${'$'} 8,75
            Est. de R${'$'}1,05/km
            4,88 (3832)
            Verificado
            9 min (4.4 km)
            Avenida Dorival Cândido Luz de Oliveira, Barnabé, Gravataí
            9 minutos (3.9 km)
            Rua Polivalente, 36, Gravataí, Gravataí
            Tenho interesse
            """.trimIndent(),
            "com.ubercab.driver"
        )!!
        assertEquals("UberX", c.categoria)
        assertEquals(8.75, c.valor, 0.001)
        assertEquals(8.3, c.kmTotal, 0.001)
        assertEquals(18.0, c.minTotal, 0.001)
        assertEquals(1.05, c.valor / c.kmTotal, 0.01)
    }

    // ---- fotos de 30/10: 99 Envíos Carro, UberX com paradas, Uber dinâmico e 99 Prioritário ----

    private val enviosCarro99 = """
        Envios Carro Exclusivo
        R${'$'} 10,03
        4,99 (103)
        17 min (11.1 km)
        Rua Treze de Maio, Esmeralda, Viamão
        12 minutos (5.9 km)
        Rua Arueira, 58, São Tomé, Viamão
        Aceitar
    """.trimIndent()

    /**
     * Foto 1 — 99 Envíos Carro: a busca é mais longa (17 min / 11,1 km) que a entrega
     * (12 min / 5,9 km). Cada trecho tem de ficar no seu lugar e o badge "Envios Carro"
     * tem de aparecer como categoria do card.
     */
    @Test
    fun leEnviosCarroDa99ComBuscaMaiorQueAEntrega() {
        val c = RideParser.parse(enviosCarro99, "com.app99.driver", aplicarMinimos = false)!!
        assertEquals("99", c.app)
        assertEquals("Envíos Carro", c.categoria)
        assertEquals(10.03, c.valor, 0.001)
        assertEquals(11.1, c.kmDeslocamento, 0.001)
        assertEquals(17.0, c.minDeslocamento, 0.001)
        assertEquals(5.9, c.kmViagem, 0.001)
        assertEquals(12.0, c.minViagem, 0.001)
        assertEquals(17.0, c.kmTotal, 0.001)
        assertEquals("Rua Treze de Maio, Esmeralda, Viamão", c.origem)
        assertEquals("Rua Arueira, 58, São Tomé, Viamão", c.destino)
    }

    /**
     * Este card é o piso de leitura: R$ 10,03 por 17,0 km e 29 min dão R$ 0,59/km e
     * R$ 20,75/h. Pagando exatamente o piso, o card ainda aparece.
     */
    @Test
    fun enviosCarroNoPisoDeLeituraAindaViraCard() {
        val c = RideParser.parse(enviosCarro99, "com.app99.driver")!!
        assertEquals(10.03, c.valor, 0.001)
        assertEquals(0.59, c.valor / c.kmTotal, 0.005)
    }

    /** Abaixo do piso (R$ 0,20/km e R$ 8,00/h) o card não aparece. */
    @Test
    fun ofertaAbaixoDoPisoDeLeituraNaoViraCard() {
        assertNull(
            RideParser.parse(
                """
                Corrida
                R${'$'} 8,00
                4,9 · 50 corridas
                40 min (30 km)
                Rua A, 10, Centro
                60 min (10 km)
                Rua B, 20, Centro
                Aceitar
                """.trimIndent(),
                "com.app99.driver"
            )
        )
    }

    /** Foto 2 — UberX: "2 paradas" no meio do trajeto não pode entrar no endereço. */
    @Test
    fun paradasNoMeioDoTrajetoNaoEntramNoEndereco() {
        val c = RideParser.parse(
            """
            UberX
            R${'$'} 37,03
            Est. de R${'$'}1,25/km
            4,88 (1039)
            4 min (1.6 km)
            Av. João Corrêa, Bairro Centro, São Leopoldo
            2 paradas
            58 minutos (28.1 km)
            R. Prof. Stumpf, 183, Vicentina, São Leopoldo
            Viagem longa (mais de 30 min)
            Tenho interesse
            """.trimIndent(),
            "com.ubercab.driver"
        )!!
        assertEquals("Av. João Corrêa, Bairro Centro, São Leopoldo", c.origem)
        assertEquals("R. Prof. Stumpf, 183, Vicentina, São Leopoldo", c.destino)
    }

    /**
     * Foto 3 — Uber no tema escuro: a tarifa dinâmica ("R$3,40 … incl.") fica logo
     * abaixo do valor e já está dentro dele. O botão "Tentando encontrar (2s)" fecha o
     * card — o texto dele não é endereço.
     */
    @Test
    fun tarifaDinamicaNaoViraValorENaoEntraNoEndereco() {
        val c = RideParser.parse(
            """
            R${'$'}10,20
            R${'$'}3,40 Tarifa base dinâmica incl.
            4,88 · 108 corridas
            CPF verif.
            9 min (2,5 km)
            Rua Passo da Pátria, 515 , Bela Vista
            15 min (5,5 km)
            Estação de Metrô Mercado, Avenida Mauá, Centro Histórico, Porto Alegre
            Tentando encontrar (2s)
            """.trimIndent(),
            "com.ubercab.driver"
        )!!
        assertEquals(10.20, c.valor, 0.001)
        assertEquals(2.5, c.kmDeslocamento, 0.001)
        assertEquals(9.0, c.minDeslocamento, 0.001)
        assertEquals(5.5, c.kmViagem, 0.001)
        assertEquals(15.0, c.minViagem, 0.001)
        assertEquals("Estação de Metrô Mercado, Avenida Mauá, Centro Histórico, Porto Alegre", c.destino)
    }

    /** Sem a linha do valor, o R$ do bônus não pode virar a oferta. */
    @Test
    fun bonusSozinhoNaoViraValorDaCorrida() {
        assertNull(
            RideParser.parse(
                "R${'$'}3,40 Tarifa base dinâmica incl. 9 min (2,5 km) Rua Passo da Pátria, 515, Bela Vista",
                "com.ubercab.driver",
                aplicarMinimos = false
            )
        )
    }

    /** Foto 4 — 99 Prioritário: "R$1,70 por corrida longa incl." é acréscimo já incluso. */
    @Test
    fun leCardPrioritarioDa99SemLerOBonusComoValor() {
        val c = RideParser.parse(
            """
            Pgto. no app Prioritário
            R${'$'}22,00
            R${'$'}1,70 por corrida longa incl.
            4,82 · 358 corridas
            Perfil Essencial
            7 min (1,6 km)
            Rua Dr. Sebastião Leão, 151, Azenha
            31 min (15,7 km)
            Rua Timbaúva, 36 , Restinga
            Aceitar
            """.trimIndent(),
            "com.app99.driver"
        )!!
        assertEquals("99", c.app)
        assertEquals("Prioritário", c.categoria)
        assertEquals(22.00, c.valor, 0.001)
        assertEquals(1.6, c.kmDeslocamento, 0.001)
        assertEquals(7.0, c.minDeslocamento, 0.001)
        assertEquals(15.7, c.kmViagem, 0.001)
        assertEquals(31.0, c.minViagem, 0.001)
        assertEquals("Rua Dr. Sebastião Leão, 151, Azenha", c.origem)
        assertEquals("Rua Timbaúva, 36, Restinga", c.destino)
    }

    /**
     * Foto 5 — UberX Exclusivo de R$ 118,89 com "1 h e 19 min (71.7 km)".
     *
     * O bug: a corrida entrava como 9 min (ou 19 min) e o R$/hora saía absurdo
     * (R$ 793/h numa corrida que paga R$ 86/h). O tempo agora é lido inteiro.
     */
    private fun cardDeUmaHoraE19(tempoDaViagem: String) = """
        UberX Exclusivo
        R${'$'} 118,89
        Est. de R${'$'}1,63/km
        4,92 (229)
        +10% de ganhos Uber Pro
        4 min (1.3 km)
        Rua José Rodrigues, Cachoeirinha, Cachoeirinha
        $tempoDaViagem
        Rua Luiz Volkart, 478, Centro, Três Coroas
        Viagem longa (mais de 30 min)
        Aceitar
    """.trimIndent()

    @Test
    fun leCorridaDeUmaHoraE19Minutos() {
        val c = RideParser.parse(cardDeUmaHoraE19("1 h e 19 min (71.7 km)"), "com.ubercab.driver")!!
        assertEquals("Uber", c.app)
        assertEquals("UberX", c.categoria)
        assertEquals(118.89, c.valor, 0.001)
        assertEquals(1.3, c.kmDeslocamento, 0.001)
        assertEquals(4.0, c.minDeslocamento, 0.001)
        assertEquals(71.7, c.kmViagem, 0.001)
        assertEquals(79.0, c.minViagem, 0.001)
        assertEquals(83.0, c.minTotal, 0.001)
        assertNotNull(c.origem)
        assertNotNull(c.destino)
    }

    /** O mesmo card com o que o OCR devolve de verdade, variação por variação. */
    @Test
    fun umaHoraE19SobreviveAoOcr() {
        listOf(
            "1 h e 19 min (71.7 km)",
            "1h19min (71.7 km)",
            "1h 19min (71,7 km)",
            "lh e 19 min (71.7 km)",
            "1 h e l9 min (71.7 km)",
            "1 h e 1 9 min (71.7 km)",
            "1 hora e 19 minutos (71.7 km)",
            "1h19 (71.7 km)"
        ).forEach { linha ->
            val c = RideParser.parse(cardDeUmaHoraE19(linha), "com.ubercab.driver")
            assertNotNull("não leu o card com \"$linha\"", c)
            assertEquals("tempo da viagem com \"$linha\"", 79.0, c!!.minViagem, 0.001)
            assertEquals("deslocamento com \"$linha\"", 4.0, c.minDeslocamento, 0.001)
        }
    }
}
