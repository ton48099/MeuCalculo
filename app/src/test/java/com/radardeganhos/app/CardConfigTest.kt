package com.radardeganhos.app

import android.content.Context
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * O card precisa mostrar, em primeiro lugar, o que a corrida gera por hora e por km,
 * e logo abaixo o lucro líquido já com os custos. Também testa os toggles e o
 * respeito da auto-análise às configurações.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h891dp-xxhdpi")
class CardConfigTest {

    private fun themed(): Context {
        val base: Context = ApplicationProvider.getApplicationContext()
        return ContextThemeWrapper(base, R.style.Theme_Radar)
    }

    // Card real da captura do Uber X: R$ 11,65 · 2 min (0,3 km) · viagem 12 min (4,9 km)
    private val card = RideCard(
        app = "Uber",
        valor = 11.65,
        kmDeslocamento = 0.3,
        minDeslocamento = 2.0,
        kmViagem = 4.9,
        minViagem = 12.0,
        precoPorKmApp = 2.24,
        taxaDeslocamento = null,
        avisos = emptyList(),
        textoOriginal = ""
    )

    private fun bind(settings: Settings): View {
        val v = LayoutInflater.from(themed()).inflate(R.layout.overlay_panel, null)
        PanelBinder.bind(v, Calculator.analyze(card, settings), settings)
        return v
    }

    @Test
    fun cardMostraHoraEDistanciaDaCorridaAtual() {
        val s = Settings()
        val a = Calculator.analyze(card, s)
        val v = bind(s)

        val hora = v.findViewById<TextView>(R.id.tvHora)
        val km = v.findViewById<TextView>(R.id.tvKm)

        // O destaque é o que ESTA corrida gera: bruto da oferta por hora e por km.
        assertEquals(Report.money(a.brutoHora), hora.text.toString())
        assertEquals("R$ 49,93", hora.text.toString())
        assertEquals("R$ 2,24/km", km.text.toString())
        assertEquals(Report.money(a.bruto), "R$ 11,65")
    }

    @Test
    fun lucroLiquidoDescontaOsCustosConfigurados() {
        val s = Settings()
        val a = Calculator.analyze(card, s)
        val v = bind(s)

        val lucro = v.findViewById<TextView>(R.id.tvLucroValor).text.toString()
        val sub = v.findViewById<TextView>(R.id.tvLucroSub).text.toString()

        assertEquals(Report.money(a.liquido), lucro)
        // 11,65 − 4,36 de custos (combustível + manutenção) = 7,29
        assertEquals("R$ 7,29", lucro)
        assertTrue(sub.contains("R$ 11,65"))
        assertTrue(sub.contains("de custos"))
        assertTrue(sub.contains("${Report.money(a.liquidoHora)}/h"))
    }

    @Test
    fun lucroNegativoFicaVermelho() {
        val s = Settings(metaKm = 2.5, metaHora = 45.0)
        val ruim = card.copy(valor = 4.0, kmViagem = 30.0, minViagem = 80.0)
        val v = LayoutInflater.from(themed()).inflate(R.layout.overlay_panel, null)
        PanelBinder.bind(v, Calculator.analyze(ruim, s), s)

        assertTrue(Calculator.analyze(ruim, s).liquido < 0)
        assertEquals(Color.parseColor("#FF4D5E"), v.findViewById<TextView>(R.id.tvLucroValor).currentTextColor)
    }

    @Test
    fun togglesOcultamCadaItemDoCard() {
        val v = bind(
            Settings(
                cardKm = false,
                cardLucro = false,
                cardVeredito = false,
                cardCustos = false
            )
        )

        assertEquals(View.GONE, v.findViewById<View>(R.id.colKm).visibility)
        assertEquals(View.GONE, v.findViewById<View>(R.id.boxLucro).visibility)
        assertEquals(View.GONE, v.findViewById<View>(R.id.tvVerdict).visibility)
        assertEquals(View.GONE, v.findViewById<View>(R.id.tvCustos).visibility)

        // O que ficou ligado continua na tela.
        assertEquals(View.VISIBLE, v.findViewById<View>(R.id.colHora).visibility)
        assertEquals(View.VISIBLE, v.findViewById<View>(R.id.rowMetricas).visibility)
        assertEquals(View.VISIBLE, v.findViewById<View>(R.id.tvResumo).visibility)
        assertEquals(View.VISIBLE, v.findViewById<View>(R.id.tvMeta).visibility)
    }

    @Test
    fun desligarTudoQueEstaAbaixoRemoveODivisor() {
        val v = bind(
            Settings(
                cardTrajeto = false,
                cardCustos = false,
                cardMeta = false,
                cardReler = false
            )
        )
        assertEquals(View.GONE, v.findViewById<View>(R.id.divider).visibility)
        assertEquals(View.GONE, v.findViewById<View>(R.id.btnRepeat).visibility)
    }

    @Test
    fun salvaECarregaAsNovasConfiguracoesDoCard() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        Prefs.save(
            ctx,
            Settings(
                cardDuracaoSeg = 0,
                cardTamanhoFonte = "grande",
                cardOpacidade = 70,
                autoIntervaloSeg = 15,
                cardKm = false,
                cardLucro = false,
                cardReler = false
            )
        )
        val lido = Prefs.load(ctx)
        assertEquals(0, lido.cardDuracaoSeg)
        assertEquals("grande", lido.cardTamanhoFonte)
        assertEquals(70, lido.cardOpacidade)
        assertEquals(15, lido.autoIntervaloSeg)
        assertFalse(lido.cardKm)
        assertFalse(lido.cardLucro)
        assertFalse(lido.cardReler)
        assertTrue(lido.cardHora)
        assertTrue(lido.cardMeta)
    }

    @Test
    fun autoAnaliseRespeitaConfiguracoes() {
        val s = Settings(autoScan = true, autoIntervaloSeg = 10)
        val agora = 100_000L
        val pkg = "com.ubercab.driver"

        assertTrue(AutoScanPolicy.shouldScan(s, pkg, agora, 0L, false, false))
        assertTrue(AutoScanPolicy.shouldScan(s, "com.taxis99", agora, 0L, false, false))
        // Intervalo escolhido ainda não passou.
        assertFalse(AutoScanPolicy.shouldScan(s, pkg, agora, agora - 9_000L, false, false))
        assertTrue(AutoScanPolicy.shouldScan(s, pkg, agora, agora - 10_000L, false, false))
        // Já está lendo, ou o motorista está com o card aberto.
        assertFalse(AutoScanPolicy.shouldScan(s, pkg, agora, 0L, scanning = true, panelVisible = false))
        assertFalse(AutoScanPolicy.shouldScan(s, pkg, agora, 0L, scanning = false, panelVisible = true))
        // A automática imita o botão: vale para QUALQUER tela (Uber, 99, print,
        // galeria, Facebook…). Quem decide se há card é o parser depois da leitura.
        assertTrue(AutoScanPolicy.shouldScan(s, "com.facebook.katana", agora, 0L, false, false))
        // Continua valendo para os apps de corrida de sempre (inclusive o 99 Motorista,
        // cujo pacote com.app99.driver faltava e trancava a leitura em silêncio).
        assertTrue(AutoScanPolicy.shouldScan(s, "com.app99.driver", agora, 0L, false, false))
        // Desligada nas configurações.
        assertFalse(AutoScanPolicy.shouldScan(s.copy(autoScan = false), pkg, agora, 0L, false, false))
    }

    /**
     * Card aberto na tela segura a auto-leitura só até passar a graça: o motorista
     * precisa de tempo para ler o card que apareceu, e depois a leitura volta.
     */
    @Test
    fun cardAbertoSeguraAtePassarAGraca() {
        val s = Settings(autoScan = true, autoIntervaloSeg = 5, cardDuracaoSeg = 12)
        val pkg = "com.taxis99"
        val agora = 50_000L

        assertFalse(
            AutoScanPolicy.shouldScan(s, pkg, agora, 0L, false, panelVisible = true, panelAbertoHaMs = 3_000L)
        )
        assertTrue(
            AutoScanPolicy.shouldScan(s, pkg, agora, 0L, false, panelVisible = true, panelAbertoHaMs = 13_000L)
        )
        assertTrue(
            AutoScanPolicy.shouldScan(s, pkg, agora, 0L, false, panelVisible = false, panelAbertoHaMs = Long.MAX_VALUE)
        )
    }

    /**
     * Abrir a tela de configurações não pode apagar o que já estava salvo. Antes o
     * preenchimento disparava o salvamento com os valores do layout, e o motorista
     * perdia os itens desligados (e, na tela de custos, até o preço do litro).
     */
    @Test
    fun abrirATelaNaoApagaAsConfiguracoesSalvas() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        Prefs.save(
            ctx,
            Settings(
                precoLitro = 6.5,
                autoIntervaloSeg = 9,
                cardKm = false,
                cardReler = false,
                cardEnderecos = false
            )
        )

        val activity = Robolectric.buildActivity(CardConfigActivity::class.java).setup().get()

        assertFalse(activity.findViewById<CheckBox>(R.id.chkValorKm).isChecked)
        assertFalse(activity.findViewById<CheckBox>(R.id.chkReler).isChecked)
        assertFalse(activity.findViewById<CheckBox>(R.id.chkEnderecos).isChecked)

        val depois = Prefs.load(ctx)
        assertFalse(depois.cardKm)
        assertFalse(depois.cardReler)
        assertFalse(depois.cardEnderecos)
        assertEquals(6.5, depois.precoLitro, 0.001)
        assertEquals(9, depois.autoIntervaloSeg)
    }

    /** R$/minuto e a linha de busca/desembarque obedecem aos próprios toggles. */
    @Test
    fun itensNovosDoCardObedecemOsToggles() {
        // Card sem endereço: a linha de trajeto some em vez de aparecer vazia.
        val semEndereco = bind(Settings(cardMinuto = true))
        assertEquals(View.VISIBLE, semEndereco.findViewById<View>(R.id.tvMinuto).visibility)
        assertEquals(View.GONE, semEndereco.findViewById<View>(R.id.tvEnderecos).visibility)
        assertEquals(View.GONE, bind(Settings(cardMinuto = false)).findViewById<View>(R.id.tvMinuto).visibility)

        val comTrajeto = card.copy(origem = "Rua A, 100", destino = "Av. B, 200")

        val v1 = LayoutInflater.from(themed()).inflate(R.layout.overlay_panel, null)
        PanelBinder.bind(v1, Calculator.analyze(comTrajeto, Settings()), Settings())
        assertEquals(View.VISIBLE, v1.findViewById<View>(R.id.tvEnderecos).visibility)
        assertEquals(
            "Busca: Rua A, 100\nDesembarque: Av. B, 200",
            v1.findViewById<TextView>(R.id.tvEnderecos).text.toString()
        )

        val desligado = Settings(cardEnderecos = false)
        val v2 = LayoutInflater.from(themed()).inflate(R.layout.overlay_panel, null)
        PanelBinder.bind(v2, Calculator.analyze(comTrajeto, desligado), desligado)
        assertEquals(View.GONE, v2.findViewById<View>(R.id.tvEnderecos).visibility)
    }

    /**
     * A leitura automática tem que disparar sozinha, de 2 em 2 segundos, e o 99
     * Motorista não pode ficar de fora da lista de apps de corrida: ele usa o
     * pacote com.app99.driver, diferente do 99 passageiro (com.taxis99) — quando
     * faltava aqui, a auto-leitura morria em silêncio e só o botão manual funcionava.
     */
    @Test
    fun autoAnaliseCobre99MotoristaEDisparaDe2Em2Segundos() {
        assertTrue(AutoScanPolicy.isRideApp("com.app99.driver"))
        assertTrue(AutoScanPolicy.isRideApp("com.taxis99"))
        assertTrue(AutoScanPolicy.isRideApp("com.ubercab.driver"))
        assertFalse(AutoScanPolicy.isRideApp("com.app99x"))
        assertFalse(AutoScanPolicy.isRideApp("com.whatsapp"))

        val s = Settings(autoScan = true, autoIntervaloSeg = 2)
        val pkg = "com.app99.driver"
        val agora = 60_000L

        // Ainda dentro do intervalo de 2 s: espera.
        assertFalse(AutoScanPolicy.shouldScan(s, pkg, agora, agora - 1_900L, false, false))
        // Venceu: dispara, sem nenhum outro bloqueio (é a leitura do botão manual).
        assertTrue(AutoScanPolicy.shouldScan(s, pkg, agora, agora - 2_000L, false, false))
        // Exceto uma pausa: o card aberto na tela (regra que o motorista pediu).
        assertFalse(
            AutoScanPolicy.shouldScan(s, pkg, agora, 0L, false, panelVisible = true, panelAbertoHaMs = 5_000L)
        )
        // Com o card sumido, volta a contar do zero e o próximo tique dispara.
        assertTrue(
            AutoScanPolicy.shouldScan(s, pkg, agora, agora - 2_000L, false, panelVisible = false)
        )
    }
}
