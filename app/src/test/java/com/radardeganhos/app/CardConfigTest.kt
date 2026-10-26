package com.radardeganhos.app

import android.content.Context
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * O card precisa mostrar, em primeiro lugar, o que a corrida gera por hora e por km,
 * e logo abaixo o lucro líquido já com os custos. Também testa os toggles e o
 * respeito da auto-análise às configurações.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w411dp-h891dp-xxhdpi")
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
                cardEscala = 1.3f,
                autoIntervaloSeg = 15,
                autoQualquerApp = true,
                cardKm = false,
                cardLucro = false,
                cardReler = false
            )
        )
        val lido = Prefs.load(ctx)
        assertEquals(0, lido.cardDuracaoSeg)
        assertTrue(lido.autoQualquerApp)
        assertEquals(1.3f, lido.cardEscala, 0.001f)
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
        // Fora dos apps de corrida a auto-análise nunca dispara (o botão manual continua).
        assertFalse(AutoScanPolicy.shouldScan(s, "com.facebook.katana", agora, 0L, false, false))
        // Desligada nas configurações.
        assertFalse(AutoScanPolicy.shouldScan(s.copy(autoScan = false), pkg, agora, 0L, false, false))
    }

    @Test
    fun reconheceOsAppsDeCorridaPorPrefixo() {
        assertTrue(AutoScanPolicy.isRideApp("com.ubercab.driver"))
        assertTrue(AutoScanPolicy.isRideApp("com.ubercab"))
        assertTrue(AutoScanPolicy.isRideApp("com.taxis99"))
        assertTrue(AutoScanPolicy.isRideApp("com.taxis99.pro"))     // 99 Pro
        assertTrue(AutoScanPolicy.isRideApp("com.taxis99.driver"))  // 99 motorista
        assertFalse(AutoScanPolicy.isRideApp("com.whatsapp"))
        assertFalse(AutoScanPolicy.isRideApp("com.ubercabx"))
    }

    @Test
    fun cardFixoNaoTravaALeituraAutomatica() {
        val s = Settings(autoScan = true, autoIntervaloSeg = 6, cardDuracaoSeg = 0)
        val pkg = "com.ubercab.driver"
        val agora = 100_000L

        // Card recém-aberto: segura um pouco para o motorista olhar os números.
        assertFalse(
            AutoScanPolicy.shouldScan(
                s, pkg, agora, 0L, scanning = false, panelVisible = true, panelAbertoHaMs = 3_000L
            )
        )
        // Passou da graça: card fixo (duração 0) deixa de travar a leitura automática.
        assertTrue(
            AutoScanPolicy.shouldScan(
                s, pkg, agora, 0L, scanning = false, panelVisible = true, panelAbertoHaMs = 11_000L
            )
        )
        // Com duração de 12s a graça também é de 12s.
        val comDuracao = s.copy(cardDuracaoSeg = 12)
        assertFalse(
            AutoScanPolicy.shouldScan(
                comDuracao, pkg, agora, 0L, scanning = false, panelVisible = true, panelAbertoHaMs = 11_000L
            )
        )
        assertTrue(
            AutoScanPolicy.shouldScan(
                comDuracao, pkg, agora, 0L, scanning = false, panelVisible = true, panelAbertoHaMs = 13_000L
            )
        )
        // Sem card na tela não há graça nenhuma.
        assertTrue(AutoScanPolicy.shouldScan(s, pkg, agora, 0L, scanning = false, panelVisible = false))
    }

    @Test
    fun cardMostraBuscaEDesembarqueEPodeOcultar() {
        val s = Settings()
        val comEndereco = card.copy(origem = "Av. Paulista, 1000", destino = "Rua Augusta, 250")

        val v = LayoutInflater.from(themed()).inflate(R.layout.overlay_panel, null)
        PanelBinder.bind(v, Calculator.analyze(comEndereco, s), s)
        val tv = v.findViewById<TextView>(R.id.tvEnderecos)
        assertEquals("Busca: Av. Paulista, 1000\nDesembarque: Rua Augusta, 250", tv.text.toString())
        assertEquals(View.VISIBLE, tv.visibility)

        // Desligado na configuração, some do card.
        val oculto = LayoutInflater.from(themed()).inflate(R.layout.overlay_panel, null)
        PanelBinder.bind(oculto, Calculator.analyze(comEndereco, s), s.copy(cardEnderecos = false))
        assertEquals(View.GONE, oculto.findViewById<View>(R.id.tvEnderecos).visibility)
    }

    @Test
    fun semEnderecoIdentificadoALinhaNaoAparece() {
        val s = Settings()
        val v = LayoutInflater.from(themed()).inflate(R.layout.overlay_panel, null)
        // Card sem origem/destino (ex.: print só de preço): nada de linha vazia.
        PanelBinder.bind(v, Calculator.analyze(card, s), s)
        assertEquals(View.GONE, v.findViewById<View>(R.id.tvEnderecos).visibility)
    }

    @Test
    fun opcaoDeEnderecoFicaSalva() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        Prefs.save(ctx, Settings(cardEnderecos = false))
        assertFalse(Prefs.load(ctx).cardEnderecos)
        Prefs.save(ctx, Settings(cardEnderecos = true))
        assertTrue(Prefs.load(ctx).cardEnderecos)
    }

    @Test
    fun podeLerEmQualquerAppQuandoLiberado() {
        val agora = 100_000L
        assertTrue(
            AutoScanPolicy.shouldScan(
                Settings(autoQualquerApp = true), "com.whatsapp", agora, 0L, false, false
            )
        )
        val padrao = Settings()
        assertEquals(
            "app fora da lista",
            AutoScanPolicy.motivoParaNaoLer(padrao, "com.whatsapp", agora, 0L, false, false)
        )
        assertEquals(
            "auto-análise desligada",
            AutoScanPolicy.motivoParaNaoLer(padrao.copy(autoScan = false), "com.ubercab.driver", agora, 0L, false, false)
        )
        assertEquals(
            "card aberto na tela",
            AutoScanPolicy.motivoParaNaoLer(padrao, "com.ubercab.driver", agora, 0L, false, true)
        )
        assertNull(AutoScanPolicy.motivoParaNaoLer(padrao, "com.ubercab.driver", agora, 0L, false, false))
    }
}
