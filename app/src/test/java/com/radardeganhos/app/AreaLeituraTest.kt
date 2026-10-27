package com.radardeganhos.app

import android.content.Context
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A leitura de tela começa abaixo do card. É isso que impede o OCR de ler o próprio
 * card do Radar e o topo do app (saldo, menu, notificação): o saldo, sendo o maior R$
 * da tela, era lido como se fosse o valor da corrida.
 *
 * Também cobre os padrões novos — leitura de 2 em 2 segundos e card no topo — e a
 * troca desses padrões em quem já tem o app instalado.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w411dp-h891dp-xxhdpi")
class AreaLeituraTest {

    // Tela de 2400 px de altura, card de 600 px preso no topo começando em 100 px.
    @Test
    fun fimDoCardNoTopoSomaDeslocamentoEAltura() {
        assertEquals(700, AreaLeitura.fimDoCard(2400, 100, 600, ancoradoNoTopo = true))
    }

    @Test
    fun fimDoCardCentradoContaDoMeioDaTela() {
        // 2400/2 + 0 + 600/2 = 1500
        assertEquals(1500, AreaLeitura.fimDoCard(2400, 0, 600, ancoradoNoTopo = false))
        // Arrastado 200 px abaixo do centro: 1200 + 200 + 300 = 1700.
        assertEquals(1700, AreaLeitura.fimDoCard(2400, 200, 600, ancoradoNoTopo = false))
    }

    @Test
    fun leituraComecaNaLinhaEmQueOCardTermina() {
        assertEquals(700, AreaLeitura.inicioNaCaptura(2400, 2400, 700))
    }

    @Test
    fun inicioAcompanhaAResolucaoDaCaptura() {
        // Captura com metade da resolução da tela: a linha cai pela metade.
        assertEquals(350, AreaLeitura.inicioNaCaptura(1200, 2400, 700))
    }

    @Test
    fun semCardNaTelaLeATelaInteira() {
        assertEquals(0, AreaLeitura.inicioNaCaptura(2400, 2400, 0))
    }

    /**
     * A auto-análise lê depois que o card some — nesse instante não há card na tela,
     * e mesmo assim a leitura tem que começar abaixo da área dele, senão o topo do
     * app (saldo) volta para dentro da conta.
     */
    @Test
    fun semCardNaTelaAReservaUsaAAlturaDoUltimoCard() {
        // Tela de 2400 px, barra de status de 100, último card com 600 px, no topo.
        assertEquals(700, AreaLeitura.fimReservado(2400, true, 100, 600, 520))
    }

    @Test
    fun primeiraLeituraReservaAAlturaDeFabrica() {
        // Antes de desenhar qualquer card: reserva 520 px (220 dp), em vez de zero.
        assertEquals(620, AreaLeitura.fimReservado(2400, true, 100, 0, 520))
    }

    @Test
    fun cardNoCentroReservaAPartirDoMeio() {
        // 2400/2 + 0 + 600/2 = 1500.
        assertEquals(1500, AreaLeitura.fimReservado(2400, false, 100, 600, 520))
    }

    @Test
    fun cardQueOcupaQuaseTudoNaoDeixaFaixaInutil() {
        // Card até 2300 px de uma tela de 2400: sobrariam 100 px (4%), e aí é melhor
        // ler a tela inteira do que uma tirinha de 100 px.
        assertEquals(0, AreaLeitura.inicioNaCaptura(2400, 2400, 2300))
        // Sobrando 40% da tela já vale o corte.
        assertEquals(1400, AreaLeitura.inicioNaCaptura(2400, 2400, 1400))
    }

    @Test
    fun fimDoCardNuncaPassaDaTela() {
        assertEquals(2400, AreaLeitura.fimDoCard(2400, 2000, 900, ancoradoNoTopo = true))
        assertEquals(0, AreaLeitura.fimDoCard(0, 0, 600, ancoradoNoTopo = true))
    }

    /** Instalação nova já vem com os padrões pedidos: 2 s e card no topo. */
    @Test
    fun padroesNovosDeFabrica() {
        assertEquals(2, Settings().autoIntervaloSeg)
        assertEquals("topo", Settings().cardGravity)

        val ctx: Context = ApplicationProvider.getApplicationContext()
        assertEquals(2, Prefs.load(ctx).autoIntervaloSeg)
        assertEquals("topo", Prefs.load(ctx).cardGravity)
    }

    /**
     * Quem já tem o app instalado recebe os padrões novos uma vez, senão continuaria
     * com o 6 e o "centro" gravados — mudar o padrão no código não muda valor salvo.
     */
    @Test
    fun migracaoTrazOsPadroesNovosParaQuemJaTinhaOApp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("radar_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("autoIntervaloSeg", 6)
            .putString("cardGravity", "centro")
            .apply()

        val lido = Prefs.load(ctx)
        assertEquals(2, lido.autoIntervaloSeg)
        assertEquals("topo", lido.cardGravity)
    }

    @Test
    fun migracaoRespeitaOQueOMotoristaEscolheu() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("radar_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("autoIntervaloSeg", 15)
            .putString("cardGravity", "topo-direita")
            .apply()

        val lido = Prefs.load(ctx)
        assertEquals(15, lido.autoIntervaloSeg)
        assertEquals("topo-direita", lido.cardGravity)
    }

    /**
     * O intervalo salva enquanto o motorista digita. Antes ele só era gravado ao sair
     * do campo, então escrever o valor e fechar o teclado podia não salvar nada — era
     * o "não consigo mudar o valor".
     */
    @Test
    fun digitarOIntervaloJaSalva() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val activity = Robolectric.buildActivity(CardConfigActivity::class.java).setup().get()
        val campo = activity.findViewById<EditText>(R.id.etAutoIntervalo)

        campo.setText("3")
        assertEquals(3, Prefs.load(ctx).autoIntervaloSeg)
    }

    @Test
    fun intervaloAbaixoDoMinimoViraOMinimo() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val activity = Robolectric.buildActivity(CardConfigActivity::class.java).setup().get()
        val campo = activity.findViewById<EditText>(R.id.etAutoIntervalo)

        campo.setText("4")
        assertEquals(4, Prefs.load(ctx).autoIntervaloSeg)
        // "1" está abaixo da faixa (mínimo 2): o que fica gravado é o mínimo, e é o
        // valor que a tela passa a mostrar quando o campo perde o foco.
        campo.setText("1")
        assertEquals(2, Prefs.load(ctx).autoIntervaloSeg)
    }
}
