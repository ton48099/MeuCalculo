package com.radardeganhos.app

import android.content.Context
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Recusa automática: quais corridas são recusadas e qual botão o app aperta.
 *
 * Os cards usados aqui são os das capturas reais do motorista — o Uber X de R$ 11,65
 * (que passa nos mínimos, mas o veredito padrão dá RUIM), a Uber a R$ 6,89 por 6,2 km
 * (que reprova) e a 99 de R$ 6,90 (que passa mesmo sendo barata, porque por km e por
 * hora ela vale a pena). O critério principal da recusa é o veredito RUIM que o card
 * mostra; os mínimos numéricos continuam como ajuste fino.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w411dp-h891dp-xxhdpi")
class RecusaTest {

    // Uber X da captura: R$ 11,65 · 2 min (0,3 km) + 12 min (4,9 km) → 5,2 km e 14 min.
    private val uberBom = RideCard(
        app = "Uber", valor = 11.65,
        kmDeslocamento = 0.3, minDeslocamento = 2.0,
        kmViagem = 4.9, minViagem = 12.0,
        precoPorKmApp = 2.24, taxaDeslocamento = null,
        avisos = emptyList(), textoOriginal = ""
    )

    /** O mesmo trajeto pagando R$ 20,00: aí o veredito é ÓTIMA e a corrida passa. */
    private val uberBoa = uberBom.copy(valor = 20.0)

    // Uber da captura do modo desktop: R$ 6,89 · 10 min (4,6 km) + 4 min (1,6 km).
    private val uberRuim = RideCard(
        app = "Uber", valor = 6.89,
        kmDeslocamento = 4.6, minDeslocamento = 10.0,
        kmViagem = 1.6, minViagem = 4.0,
        precoPorKmApp = 1.11, taxaDeslocamento = null,
        avisos = emptyList(), textoOriginal = ""
    )

    // 99 da captura: R$ 6,90 · 4 min (692 m) + 4 min (2 km) → 2,69 km e 8 min.
    private val noveBarata = RideCard(
        app = "99", valor = 6.90,
        kmDeslocamento = 0.692, minDeslocamento = 4.0,
        kmViagem = 2.0, minViagem = 4.0,
        precoPorKmApp = null, taxaDeslocamento = 1.95,
        avisos = emptyList(), textoOriginal = ""
    )

    private fun ligada(vararg mudancas: Pair<String, Any>): Settings {
        var s = Settings().copy(recusaAtiva = true, recusaSoAviso = false)
        mudancas.forEach { (campo, valor) ->
            s = when (campo) {
                "km" -> s.copy(recusaMinKm = valor as Double)
                "min" -> s.copy(recusaMinMin = valor as Double)
                "hora" -> s.copy(recusaMinHora = valor as Double)
                "porKm" -> s.copy(recusaMinPorKm = valor as Double)
                else -> s
            }
        }
        return s
    }

    // ---------------------------------------------------------------- critérios

    @Test
    fun desligadaNaoRecusaNada() {
        val s = Settings().copy(recusaAtiva = false)
        assertTrue(Recusa.motivos(Calculator.analyze(uberRuim, s), s).isEmpty())
    }

    @Test
    fun corridaBoaPassa() {
        val s = ligada()
        assertTrue(Recusa.motivos(Calculator.analyze(uberBoa, s), s).isEmpty())
    }

    /** R$ 6,89 por 6,2 km e 14 min: R$ 1,11/km e R$ 29,53/h — reprova no veredito e nos dois mínimos. */
    @Test
    fun corridaBarataERecusadaPeloVereditoEPelosDoisCriterios() {
        val s = ligada()
        val motivos = Recusa.motivos(Calculator.analyze(uberRuim, s), s)
        assertEquals(3, motivos.size)
        assertTrue(motivos.any { it.contains("RUIM") })
        assertTrue(motivos.any { it.contains("1,11") && it.contains("/km") })
        assertTrue(motivos.any { it.contains("29,53") && it.contains("/h") })
    }

    /**
     * O critério que o motorista vê no card é o veredito: uma corrida RUIM é recusada
     * mesmo com os mínimos numéricos zerados.
     */
    @Test
    fun vereditoRuimRecusaMesmoComOsMinimosZerados() {
        val s = ligada("km" to 0.0, "min" to 0.0, "hora" to 0.0, "porKm" to 0.0)
        val motivos = Recusa.motivos(Calculator.analyze(uberRuim, s), s)
        assertEquals(1, motivos.size)
        assertTrue(motivos.first().contains("RUIM"))
    }

    /** A 99 de R$ 6,90 vale R$ 2,56/km e R$ 51,75/h: é barata, mas passa nos mínimos. */
    @Test
    fun corridaCurtaMasBemPagaNaoERecusada() {
        val s = ligada()
        assertTrue(Recusa.motivos(Calculator.analyze(noveBarata, s), s).isEmpty())
    }

    @Test
    fun minimoDeKmRecusaCorridaCurta() {
        val s = ligada("km" to 6.0)
        val motivos = Recusa.motivos(Calculator.analyze(uberBoa, s), s)
        assertEquals(1, motivos.size)
        assertTrue(motivos.first().contains("5,2 km"))
        // Sem o critério (0 = desligado), a mesma corrida passa.
        assertTrue(Recusa.motivos(Calculator.analyze(uberBoa, ligada()), ligada()).isEmpty())
    }

    @Test
    fun minimoDeMinutosRecusaCorridaCurta() {
        val s = ligada("min" to 20.0)
        val motivos = Recusa.motivos(Calculator.analyze(uberBoa, s), s)
        assertEquals(1, motivos.size)
        assertTrue(motivos.first().contains("14 min"))
    }

    @Test
    fun minimosZeradosDesligamCadaCriterio() {
        // R$ 20 por 20 km e 20 min: R$ 1,00/km bruto (o mínimo de 1,30 reprova), mas
        // o card pinta RUIM — então a recusa continua pelo veredito.
        val card = RideCard(
            app = "Uber", valor = 20.0,
            kmDeslocamento = 0.0, minDeslocamento = 0.0,
            kmViagem = 20.0, minViagem = 20.0,
            precoPorKmApp = null, taxaDeslocamento = null,
            avisos = emptyList(), textoOriginal = ""
        )
        val comKm = ligada("hora" to 0.0)
        val motivos = Recusa.motivos(Calculator.analyze(card, comKm), comKm)
        assertEquals(2, motivos.size)
        assertTrue(motivos.any { it.contains("1,00") && it.contains("/km") })
        assertTrue(motivos.any { it.contains("RUIM") })

        val semKm = ligada("hora" to 0.0, "porKm" to 0.0)
        val soVeredito = Recusa.motivos(Calculator.analyze(card, semKm), semKm)
        assertEquals(1, soVeredito.size)
        assertTrue(soVeredito.first().contains("RUIM"))
    }

    /** Leitura incompleta não vira recusa: o app prefere deixar passar a errar. */
    @Test
    fun leituraIncompletaNuncaRecusa() {
        val s = ligada()
        val semValor = uberRuim.copy(valor = 0.0)
        assertTrue(Recusa.motivos(Calculator.analyze(semValor, s), s).isEmpty())
        val semKm = uberRuim.copy(kmViagem = 0.0, kmDeslocamento = 0.0)
        assertTrue(Recusa.motivos(Calculator.analyze(semKm, s), s).isEmpty())
    }

    // ------------------------------------------------------------ escolha do X

    private val janela = Rect(0, 0, 800, 1000)

    private fun botao(
        id: String = "",
        desc: String = "",
        texto: String = "",
        x: Int,
        y: Int,
        lado: Int = 40,
        clicavel: Boolean = true
    ) = BotaoCandidato(id, desc, texto, x, y, x + lado, y + lado, clicavel)

    private fun escolher(vararg candidatos: BotaoCandidato, aprendido: XAprendido? = null): BotaoCandidato? {
        val idx = Recusa.escolherX(candidatos.toList(), janela, aprendido)
        return idx?.let { candidatos[it] }
    }

    @Test
    fun oXEhAchadoPelaDescricao() {
        // O botão do meio da tela com descrição "Fechar" vence o canto sem pista... e
        // também vence o canto genérico, que é o que a geometria acharia sozinha.
        val fechar = botao(desc = "Fechar", x = 400, y = 500)
        val canto = botao(x = 740, y = 20)
        assertEquals(fechar, escolher(canto, fechar))
    }

    @Test
    fun oXEhAchadoPeloId() {
        val porId = botao(id = "close_button", x = 400, y = 620)
        val canto = botao(x = 740, y = 20)
        assertEquals(porId, escolher(canto, porId))
    }

    @Test
    fun oXEhAchadoPelaGeometriaQuandoNaoTemPista() {
        val canto = botao(x = 748, y = 24)
        val mapa = botao(x = 300, y = 700)
        assertEquals(canto, escolher(mapa, canto))
    }

    @Test
    fun oXDoCardDoUberEhAchadoForaDaBordaDaJanela() {
        // O X do Uber fica no canto do card de oferta, e o card não encosta na borda
        // da janela: sem olhar a faixa de cima à direita ele nunca era achado.
        val xDoCard = botao(x = 620, y = 430)
        val mapa = botao(x = 300, y = 700)
        assertEquals(xDoCard, escolher(mapa, xDoCard))
    }

    @Test
    fun oMenuDoCabecalhoNaoGanhaDoXDoCard() {
        // O "..." do cabeçalho está mais perto do canto, mas quem recusa a oferta é
        // o X do card, logo abaixo do cabeçalho.
        val menu = botao(x = 742, y = 18)
        val xDoCard = botao(x = 700, y = 430)
        assertEquals(xDoCard, escolher(menu, xDoCard))
    }

    @Test
    fun oBotaoAprendidoGanhaDeTudo() {
        val aprendido = XAprendido(id = "reject_btn", descricao = "", relX = 0.95, relY = 0.66)
        val aprendidoCerto = botao(id = "reject_btn", x = 740, y = 640)
        val comDescricao = botao(desc = "Fechar", x = 400, y = 300)
        assertEquals(aprendidoCerto, escolher(comDescricao, aprendidoCerto, aprendido = aprendido))
    }

    @Test
    fun botaoGiganteNaoEhConfundidoComOX() {
        // A oferta inteira é clicável; sem descrição de fechar, ela não serve.
        val oferta = botao(x = 40, y = 600, lado = 760)
        assertNull(escolher(oferta))
    }

    @Test
    fun textoDoCardNaoEhBotao() {
        val texto = botao(texto = "R$ 6,89", x = 60, y = 700, clicavel = false)
        assertNull(escolher(texto))
    }

    @Test
    fun iconeSemCliqueEhAceitoEPassaPeloPai() {
        // O X do Uber costuma ser um ícone dentro de um FrameLayout clicável: o ícone
        // não é clicável, mas tem descrição — e o pai é quem recebe o clique.
        val icone = botao(desc = "Fechar oferta", x = 700, y = 640, clicavel = false)
        assertEquals(icone, escolher(icone))
    }

    // -------------------------------------------------------- botão aprendido

    @Test
    fun impressaoDoBotaoVaiEVolta() {
        // Botão de 760..800 por 660..700 numa janela de 800x1000: centro 780,680.
        val b = botao(id = "close_button", desc = "Fechar", x = 760, y = 660)
        val x = XAprendido.de(b, janela)
        assertEquals(0.975, x.relX, 0.001)
        assertEquals(0.68, x.relY, 0.001)
        assertEquals(x, XAprendido.decode(x.encode()))
        assertNull(XAprendido.decode(null))
        assertNull(XAprendido.decode("vazio"))
    }

    @Test
    fun impressaoFicaGuardadaPorApp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val x = XAprendido(id = "close_button", descricao = "Fechar", relX = 0.9, relY = 0.6)
        Prefs.salvarXAprendido(ctx, "com.ubercab.driver", x)

        assertEquals(x, Prefs.xAprendido(ctx, "com.ubercab.driver"))
        // O X da 99 é outro: o que foi aprendido de um app não vale para o outro.
        assertNull(Prefs.xAprendido(ctx, "com.app99.driver"))
    }

    // ------------------------------------------- a oferta ainda está na tela?

    @Test
    fun ofertaNaTelaEhConfirmadaPeloValor() {
        // O texto da tela pode trazer o valor com vírgula ou com ponto.
        assertEquals(true, Recusa.ofertaNaTela(6.89, listOf("Aceitar", "R$ 6,89", "4 min (1,6 km)")))
        assertEquals(true, Recusa.ofertaNaTela(6.89, listOf("R$6.89")))
        // Outra oferta, outro valor: a que foi analisada saiu da tela.
        assertEquals(false, Recusa.ofertaNaTela(6.89, listOf("Aceitar", "R$ 7,10")))
        // Tela sem texto nenhum (app desenhado em canvas): não dá para conferir.
        assertNull(Recusa.ofertaNaTela(6.89, emptyList()))
        assertNull(Recusa.ofertaNaTela(6.89, listOf("", "  ")))
    }

    // -------------------------------------------------------------- na tela

    @Test
    fun contagemNasceEscondidaNoCard() {
        val ctx: Context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(), R.style.Theme_Radar
        )
        val v = LayoutInflater.from(ctx).inflate(R.layout.overlay_panel, null)
        PanelBinder.bind(v, Calculator.analyze(uberRuim, Settings()), Settings())
        // Quem escreve nela é o serviço, quando a recusa está agendada.
        assertEquals(View.GONE, v.findViewById<View>(R.id.tvContagem).visibility)
    }

    @Test
    fun recusaFicaGuardadaNasConfiguracoes() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val activity = Robolectric.buildActivity(CardConfigActivity::class.java).setup().get()

        activity.findViewById<CheckBox>(R.id.chkRecusa).isChecked = true
        activity.findViewById<CheckBox>(R.id.chkRecusaAviso).isChecked = false
        activity.findViewById<EditText>(R.id.etRecusaSegundos).setText("8")
        activity.findViewById<EditText>(R.id.etRecusaMinKm).setText("6,5")
        activity.findViewById<EditText>(R.id.etRecusaMinHora).setText("42")

        val s = Prefs.load(ctx)
        assertTrue(s.recusaAtiva)
        assertFalse(s.recusaSoAviso)
        assertEquals(8, s.recusaSegundos)
        assertEquals(6.5, s.recusaMinKm, 0.001)
        assertEquals(42.0, s.recusaMinHora, 0.001)
        // O que não foi mexido continua no padrão.
        assertEquals(1.30, s.recusaMinPorKm, 0.001)
    }

    @Test
    fun segundosDaRecusaFicamNaFaixa() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val activity = Robolectric.buildActivity(CardConfigActivity::class.java).setup().get()
        activity.findViewById<EditText>(R.id.etRecusaSegundos).setText("99")
        assertEquals(30, Prefs.load(ctx).recusaSegundos)
    }

    @Test
    fun padroesDaRecusaDeFabrica() {
        val s = Settings()
        // A recusa vem ligada e clicando: a contagem no card é a chance de cancelar.
        assertTrue(s.recusaAtiva)
        assertFalse(s.recusaSoAviso)
        assertEquals(5, s.recusaSegundos)
        assertEquals(0.0, s.recusaMinKm, 0.001)
        assertEquals(0.0, s.recusaMinMin, 0.001)
        assertEquals(35.0, s.recusaMinHora, 0.001)
        assertEquals(1.30, s.recusaMinPorKm, 0.001)
    }
}
