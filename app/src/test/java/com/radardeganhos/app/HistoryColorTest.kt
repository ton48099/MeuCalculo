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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Semáforo de R$/hora e R$/km, endereços do card e histórico de corridas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w411dp-h891dp-xxhdpi")
class HistoryColorTest {

    private fun themed(): Context {
        val base: Context = ApplicationProvider.getApplicationContext()
        return ContextThemeWrapper(base, R.style.Theme_Radar)
    }

    private val verde = Color.parseColor("#00D26A")
    private val branco = Color.parseColor("#FFFFFF")
    private val vermelho = Color.parseColor("#FF4D5E")

    // ---- 1. regras de cor ------------------------------------------------

    @Test
    fun horaUsaOsLimitesPedidos() {
        val s = Settings()
        assertEquals(Tone.RUIM, ColorRules.toneHora(20.0, s))
        assertEquals(Tone.RUIM, ColorRules.toneHora(35.0, s))     // 35,00 inclusive = vermelho
        assertEquals(Tone.NEUTRO, ColorRules.toneHora(35.01, s))
        assertEquals(Tone.NEUTRO, ColorRules.toneHora(44.99, s))
        assertEquals(Tone.BOM, ColorRules.toneHora(45.0, s))      // 45,00 = verde
        assertEquals(Tone.BOM, ColorRules.toneHora(92.4, s))
    }

    @Test
    fun kmUsaOsLimitesPedidos() {
        val s = Settings()
        assertEquals(Tone.RUIM, ColorRules.toneKm(0.9, s))
        assertEquals(Tone.RUIM, ColorRules.toneKm(1.30, s))       // 1,30 inclusive = vermelho
        assertEquals(Tone.NEUTRO, ColorRules.toneKm(1.31, s))
        assertEquals(Tone.NEUTRO, ColorRules.toneKm(1.79, s))
        assertEquals(Tone.BOM, ColorRules.toneKm(1.80, s))        // 1,80 = verde
        assertEquals(Tone.BOM, ColorRules.toneKm(2.24, s))
    }

    /** O terceiro valor do card (R$/minuto) tem o próprio semáforo, ajustável. */
    @Test
    fun minutoUsaOsLimitesConfigurados() {
        val s = Settings()
        assertEquals(Tone.RUIM, ColorRules.toneMinuto(0.40, s))
        assertEquals(Tone.RUIM, ColorRules.toneMinuto(0.58, s))   // inclusive = vermelho
        assertEquals(Tone.NEUTRO, ColorRules.toneMinuto(0.65, s))
        assertEquals(Tone.BOM, ColorRules.toneMinuto(0.75, s))    // inclusive = verde

        val outro = Settings(semaforoMinutoRuim = 0.5, semaforoMinutoBom = 0.9)
        assertEquals(Tone.NEUTRO, ColorRules.toneMinuto(0.7, outro))
        assertEquals(Tone.BOM, ColorRules.toneMinuto(0.9, outro))
    }

    @Test
    fun limitesSaoAjustaveisNasConfiguracoes() {
        val s = Settings(horaRuimAte = 50.0, horaBoaDe = 70.0, kmRuimAte = 2.0, kmBoaDe = 3.0)
        assertEquals(Tone.RUIM, ColorRules.toneHora(45.0, s))
        assertEquals(Tone.NEUTRO, ColorRules.toneHora(60.0, s))
        assertEquals(Tone.BOM, ColorRules.toneHora(70.0, s))
        assertEquals(Tone.RUIM, ColorRules.toneKm(1.9, s))
        assertEquals(Tone.NEUTRO, ColorRules.toneKm(2.5, s))
        assertEquals(Tone.BOM, ColorRules.toneKm(3.0, s))
    }

    @Test
    fun coresSaoVerdeBrancoEVermelho() {
        assertEquals(verde, ColorRules.cor(Tone.BOM))
        assertEquals(branco, ColorRules.cor(Tone.NEUTRO))
        assertEquals(vermelho, ColorRules.cor(Tone.RUIM))
    }

    @Test
    fun cardPintaHoraEKmComOsLimites() {
        val s = Settings()

        // Card real do Uber X: R$ 11,65 · 14 min · 5,2 km → R$ 49,93/h e R$ 2,24/km
        val bom = uberCard(valor = 11.65, kmViagem = 4.9, minViagem = 12.0)
        val vBom = bindPanel(bom, s)
        assertEquals("R$ 49,93", vBom.findViewById<TextView>(R.id.tvHora).text.toString())
        assertEquals(verde, vBom.findViewById<TextView>(R.id.tvHora).currentTextColor)
        assertEquals(verde, vBom.findViewById<TextView>(R.id.tvKm).currentTextColor)

        // R$ 12,00 · 20 min · 7,8 km → R$ 36,00/h (branco) e R$ 1,54/km (branco)
        val neutro = bindPanel(uberCard(valor = 12.0, kmViagem = 7.5, minViagem = 18.0), s)
        assertEquals(Tone.NEUTRO, ColorRules.toneHora(36.0, s))
        assertEquals(Tone.NEUTRO, ColorRules.toneKm(12.0 / 7.8, s))
        assertEquals(branco, neutro.findViewById<TextView>(R.id.tvHora).currentTextColor)
        assertEquals(branco, neutro.findViewById<TextView>(R.id.tvKm).currentTextColor)

        // R$ 8,00 · 40 min · 10 km → R$ 12,00/h e R$ 0,80/km: tudo vermelho.
        val ruim = bindPanel(uberCard(valor = 8.0, kmViagem = 10.0, minViagem = 40.0), s)
        assertEquals(vermelho, ruim.findViewById<TextView>(R.id.tvHora).currentTextColor)
        assertEquals(vermelho, ruim.findViewById<TextView>(R.id.tvKm).currentTextColor)
    }

    private fun bindPanel(card: RideCard, s: Settings): View {
        val v = LayoutInflater.from(themed()).inflate(R.layout.overlay_panel, null)
        PanelBinder.bind(v, Calculator.analyze(card, s), s)
        return v
    }

    private fun uberCard(valor: Double, kmViagem: Double, minViagem: Double) = RideCard(
        app = "Uber",
        valor = valor,
        kmDeslocamento = 0.3,
        minDeslocamento = 2.0,
        kmViagem = kmViagem,
        minViagem = minViagem,
        precoPorKmApp = null,
        taxaDeslocamento = null,
        avisos = emptyList(),
        textoOriginal = ""
    )

    // ---- 2. endereços de origem e destino --------------------------------

    @Test
    fun leEnderecosDoCardDoUber() {
        val card = RideParser.parse(
            """
            UberX  Exclusiva
            R$11.65
            R$2.24/km est.
            ★ 4.94
            2 min (0.3 km) de distância
            Av. República, 123 - Brasil
            Viagem de 12 min (4.9 km)
            Alameda da República, 321 - Brasil
            Aceitar
            """.trimIndent()
        )!!
        assertEquals("Av. República, 123 - Brasil", card.origem)
        assertEquals("Alameda da República, 321 - Brasil", card.destino)
        assertEquals(11.65, card.valor, 0.001)
    }

    @Test
    fun leEnderecosComQuebraDeLinhaNoMeio() {
        val card = RideParser.parse(
            """
            Black
            R$ 256,15
            ★ 4,77
            4 minutos (0.8 km) de distância
            Alameda Joaquim Eugênio de Lima,
            Bela Vista
            Viagem de 1 h e 43 min (95,7 km)
            Av. Sao Joao, 2200 - Jardim das Colinas
            - São José dos Campos - SP, 12242-970
            Viagem longa (mais de 45 min)
            Aceitar
            """.trimIndent()
        )!!
        assertEquals("Alameda Joaquim Eugênio de Lima, Bela Vista", card.origem)
        assertEquals(
            "Av. Sao Joao, 2200 - Jardim das Colinas, São José dos Campos - SP, 12242-970",
            card.destino
        )
    }

    @Test
    fun leEnderecosDoCardDo99ComParadaNoMeio() {
        val card = RideParser.parse(
            """
            Corrida longa
            Dinheiro
            R$16,20
            Taxa de deslocamento R$1,11
            4,93 419 corridas
            Perfil Premium
            8min (3,3km)
            Terceira Rua, Rio do Ouro,
            Rio Bonito - RJ
            1 parada(s)
            22min (9,2km)
            Rua Maj. Bezerra
            Cavalcante, 651, Serra do...
            """.trimIndent()
        )!!
        assertEquals("Terceira Rua, Rio do Ouro, Rio Bonito - RJ", card.origem)
        assertEquals("Rua Maj. Bezerra, Cavalcante, 651, Serra do...", card.destino)
    }

    @Test
    fun cardSemEnderecoNaoInventaTrajeto() {
        val card = RideParser.parse("R$ 24,90 · 5 min (1,2 km) de distância · Viagem de 18 min (7,4 km)")!!
        assertNull(card.origem)
        assertNull(card.destino)
    }

    // ---- 3. histórico ----------------------------------------------------

    private fun record(valor: Double, km: Double, hora: Double, origem: String?, destino: String?, at: Long) =
        RideRecord(at, "Uber", valor, km, valor / km, hora, valor / 2, origem, destino)

    @Test
    fun historicoGuardaELeDeVoltaComTextoReal() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        RideHistory.clear(ctx)
        val lista = listOf(
            record(11.65, 5.2, 49.93, "Av. República, 123 - Brasil", "Alameda da República, 321 - Brasil", 1_700_000_000_000),
            record(256.15, 96.5, 148.2, "Rua A|B, 10", "Av. C, 20\napto 3", 1_700_000_100_000)
        )
        RideHistory.save(ctx, lista)
        val lido = RideHistory.load(ctx)

        assertEquals(2, lido.size)
        assertEquals(11.65, lido[0].valor, 0.001)
        assertEquals("Av. República, 123 - Brasil", lido[0].origem)
        assertEquals("Alameda da República, 321 - Brasil", lido[0].destino)
        // Caracteres que quebrariam um formato ingênuo continuam intactos.
        assertEquals("Rua A|B, 10", lido[1].origem)
        assertEquals("Av. C, 20\napto 3", lido[1].destino)
    }

    @Test
    fun registroPulaALeituraRepetidaDoMesmoCard() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        RideHistory.clear(ctx)

        val card = uberCard(valor = 11.65, kmViagem = 4.9, minViagem = 12.0)
        val analysis = Calculator.analyze(card, Settings())

        assertTrue(RideHistory.record(ctx, analysis, at = 1_000_000))
        // Auto-análise + toque manual no mesmo card em segundos: não duplica.
        assertFalse(RideHistory.record(ctx, analysis, at = 1_030_000))
        assertEquals(1, RideHistory.load(ctx).size)

        // Depois da janela, é outra oferta (ou outra corrida) e entra de novo.
        assertTrue(RideHistory.record(ctx, analysis, at = 1_000_000 + 300_000))
        val lido = RideHistory.load(ctx)
        assertEquals(2, lido.size)
        assertEquals(1_000_000 + 300_000, lido[0].at)   // mais novo primeiro
    }

    @Test
    fun historicoRespeitaOLimiteDeItens() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        RideHistory.clear(ctx)
        val cheio = (1..RideHistory.MAX).map { record(10.0 + it, 5.0, 40.0, null, null, it.toLong()) }
        RideHistory.save(ctx, cheio)
        assertEquals(RideHistory.MAX, RideHistory.load(ctx).size)

        RideHistory.record(ctx, Calculator.analyze(uberCard(20.0, 6.0, 15.0), Settings()), at = 9_999_999)
        val lido = RideHistory.load(ctx)
        assertEquals(RideHistory.MAX, lido.size)
        assertEquals(9_999_999, lido[0].at)
    }

    @Test
    fun telaDeHistoricoListaValorHoraKmETrajeto() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        RideHistory.save(
            ctx,
            listOf(record(11.65, 5.2, 49.93, "Av. República, 123 - Brasil", "Alameda da República, 321 - Brasil", 1_700_000_000_000))
        )

        val activity = Robolectric.buildActivity(HistoryActivity::class.java).setup().get()
        val lista = activity.findViewById<android.widget.LinearLayout>(R.id.listaHistorico)
        assertEquals(1, lista.childCount)

        val item = lista.getChildAt(0)
        assertEquals("R$ 11,65", item.findViewById<TextView>(R.id.tvItemValor).text.toString())
        assertEquals("R$ 49,93/h", item.findViewById<TextView>(R.id.tvItemHora).text.toString())
        assertEquals("R$ 2,24/km", item.findViewById<TextView>(R.id.tvItemKm).text.toString())
        assertTrue(item.findViewById<TextView>(R.id.tvItemOrigem).text.toString().contains("Av. República, 123"))
        assertTrue(item.findViewById<TextView>(R.id.tvItemDestino).text.toString().contains("Alameda da República, 321"))
        assertEquals(verde, item.findViewById<TextView>(R.id.tvItemHora).currentTextColor)
        assertEquals(verde, item.findViewById<TextView>(R.id.tvItemKm).currentTextColor)

        val resumo = activity.findViewById<TextView>(R.id.tvHistoricoResumo).text.toString()
        assertTrue(resumo.contains("1 corrida"))
        assertEquals(View.GONE, activity.findViewById<View>(R.id.tvHistoricoVazio).visibility)
    }
}
