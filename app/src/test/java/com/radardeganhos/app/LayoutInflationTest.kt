package com.radardeganhos.app

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Garante que as telas e o overlay inflam de verdade no aparelho
 * (atributos obrigatórios, estilos, drawables e ids).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w411dp-h891dp-xxhdpi")
class LayoutInflationTest {

    private fun themed(): Context {
        val base: Context = ApplicationProvider.getApplicationContext()
        return ContextThemeWrapper(base, R.style.Theme_Radar)
    }

    @Test
    fun inflaTelaPrincipalComTodosOsCampos() {
        val v = LayoutInflater.from(themed()).inflate(R.layout.activity_main, null)
        assertNotNull(v.findViewById<View>(R.id.btnToggleLeitura))
        assertNotNull(v.findViewById<View>(R.id.btnCustos))
        assertNotNull(v.findViewById<View>(R.id.btnCardConfig))
        assertNotNull(v.findViewById<View>(R.id.tvStatus))
        // A leitura automática agora mora em "Card e Cores" e a seção 4 (conferir uma
        // leitura) foi removida da tela principal — nem os ids existem mais.
        val textos = mutableListOf<String>()
        fun coletar(view: View) {
            if (view is TextView) view.text?.toString()?.let { textos.add(it) }
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) coletar(view.getChildAt(i))
            }
        }
        coletar(v)
        assertTrue(textos.none { it.contains("CONFERIR UMA LEITURA") })
        assertTrue(textos.none { it.contains("LEITURA AUTOMÁTICA") })
        assertTrue(textos.any { it.contains("1 · CUSTOS") })
        assertTrue(textos.any { it.contains("2 · CARD E CORES") })
    }

    @Test
    fun inflaTelaDeHistoricoEUmItem() {
        val v = LayoutInflater.from(themed()).inflate(R.layout.activity_history, null)
        assertNotNull(v.findViewById<View>(R.id.listaHistorico))
        assertNotNull(v.findViewById<View>(R.id.tvHistoricoResumo))
        assertNotNull(v.findViewById<View>(R.id.btnLimparHistorico))
        assertNotNull(v.findViewById<View>(R.id.btnVoltar))

        val item = LayoutInflater.from(themed()).inflate(R.layout.item_history, null)
        assertNotNull(item.findViewById<View>(R.id.tvItemValor))
        assertNotNull(item.findViewById<View>(R.id.tvItemHora))
        assertNotNull(item.findViewById<View>(R.id.tvItemKm))
        assertNotNull(item.findViewById<View>(R.id.tvItemOrigem))
        assertNotNull(item.findViewById<View>(R.id.tvItemDestino))
        // Miniatura do print (some quando a corrida não tem imagem).
        assertNotNull(item.findViewById<View>(R.id.imgPrint))
    }

    @Test
    fun limitesDeCorFicamSalvos() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        Prefs.save(ctx, Settings(horaRuimAte = 38.0, horaBoaDe = 50.0, kmRuimAte = 1.4, kmBoaDe = 2.0))
        val lido = Prefs.load(ctx)
        assertEquals(38.0, lido.horaRuimAte, 0.001)
        assertEquals(50.0, lido.horaBoaDe, 0.001)
        assertEquals(1.4, lido.kmRuimAte, 0.001)
        assertEquals(2.0, lido.kmBoaDe, 0.001)
        // Valores de fábrica (o que vem sem o motorista mexer).
        val padrao = Settings()
        assertEquals(35.0, padrao.horaRuimAte, 0.001)
        assertEquals(45.0, padrao.horaBoaDe, 0.001)
        assertEquals(1.30, padrao.kmRuimAte, 0.001)
        assertEquals(1.80, padrao.kmBoaDe, 0.001)
    }

    @Test
    fun inflaBotaoFlutuante() {
        val v = LayoutInflater.from(themed()).inflate(R.layout.overlay_bubble, null)
        val texto = v.findViewById<TextView>(R.id.bubbleText)
        assertEquals("R$", texto.text.toString())
        assertTrue(texto.layoutParams.width > 0)
        assertTrue(texto.layoutParams.height > 0)
    }

    @Test
    fun inflaPainelDeResultado() {
        val v = LayoutInflater.from(themed()).inflate(R.layout.overlay_panel, null)
        assertNotNull(v.findViewById<View>(R.id.tvVerdict))
        assertNotNull(v.findViewById<View>(R.id.tvHora))
        assertNotNull(v.findViewById<View>(R.id.tvKm))
        assertNotNull(v.findViewById<View>(R.id.tvCustos))
        assertNotNull(v.findViewById<View>(R.id.btnRepeat))
        // Itens novos: R$/minuto e a linha de busca/desembarque.
        assertNotNull(v.findViewById<View>(R.id.tvMinuto))
        assertNotNull(v.findViewById<View>(R.id.tvEnderecos))
    }

    @Test
    fun salvaECarregaConfiguracoes() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val s = Settings(precoLitro = 6.19, consumo = 9.5, metaKm = 2.1, autoScan = false)
        Prefs.save(ctx, s)
        val lido = Prefs.load(ctx)
        assertEquals(6.19, lido.precoLitro, 0.001)
        assertEquals(9.5, lido.consumo, 0.001)
        assertEquals(2.1, lido.metaKm, 0.001)
        assertEquals(false, lido.autoScan)
        assertEquals(6.19 / 9.5 + 0.25, lido.custoKm, 0.001)

        // Leitura multi-tela e print da corrida também sobrevivem ao reinício.
        Prefs.save(
            ctx,
            Settings(modoDesktop = true, printCorrida = false, cardGravity = "topo-direita")
        )
        val novo = Prefs.load(ctx)
        assertTrue(novo.modoDesktop)
        assertFalse(novo.printCorrida)
        assertEquals("topo-direita", novo.cardGravity)
        // Valores de fábrica.
        val padrao = Settings()
        assertFalse(padrao.modoDesktop)
        assertTrue(padrao.printCorrida)
    }

    /** A tela "Card e Cores" infla com todos os itens que ela controla. */
    @Test
    fun inflaTelaDeConfiguracaoDoCard() {
        val v = LayoutInflater.from(themed()).inflate(R.layout.activity_card_config, null)
        listOf(
            R.id.chkVeredito, R.id.chkEnderecos, R.id.chkMeta, R.id.chkTrajeto,
            R.id.chkReler, R.id.chkValorKm, R.id.chkValorHora, R.id.chkValorMinuto,
            R.id.chkValoresTotais, R.id.chkCustoTotal, R.id.chkLucro, R.id.chkEmpilhar,
            R.id.seekDuracao, R.id.seekOpacidade, R.id.txtDuracaoValor,
            R.id.txtKmBom, R.id.txtKmRuim, R.id.txtHoraBom, R.id.txtHoraRuim,
            R.id.txtMinutoBom, R.id.txtMinutoRuim,
            R.id.chkAutoScan, R.id.etAutoIntervalo, R.id.chkModoDesktop,
            R.id.chkPrintCorrida, R.id.tvDiag,
            R.id.btnPosTopoEsquerda, R.id.btnPosTopoCentro,
            R.id.btnPosTopoDireita, R.id.btnPosCentro
        ).forEach { id -> assertNotNull(v.findViewById<View>(id)) }
    }

    /** A tela de custos redesenhada mantém todos os campos + o resumo do topo. */
    @Test
    fun inflaTelaDeCustosComResumoECampos() {
        val v = LayoutInflater.from(themed()).inflate(R.layout.activity_custos, null)
        listOf(
            R.id.etPreco, R.id.etConsumo, R.id.etManut, R.id.etComissao,
            R.id.etFixoHora, R.id.etEspera, R.id.etRetorno,
            R.id.etMetaKm, R.id.etMetaHora,
            R.id.tvResumoCusto, R.id.btnSalvar, R.id.btnVoltar
        ).forEach { id -> assertNotNull(v.findViewById<View>(id)) }
    }
}
