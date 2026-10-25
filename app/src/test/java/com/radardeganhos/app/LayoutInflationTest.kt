package com.radardeganhos.app

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
        assertNotNull(v.findViewById<View>(R.id.etPreco))
        assertNotNull(v.findViewById<View>(R.id.etConsumo))
        assertNotNull(v.findViewById<View>(R.id.etManut))
        assertNotNull(v.findViewById<View>(R.id.etMetaKm))
        assertNotNull(v.findViewById<View>(R.id.etMetaHora))
        assertNotNull(v.findViewById<View>(R.id.swAuto))
        assertNotNull(v.findViewById<View>(R.id.btnSalvar))
        assertNotNull(v.findViewById<View>(R.id.btnGaleria))
        assertNotNull(v.findViewById<View>(R.id.tvResultado))
        // Limites de cor e histórico
        assertNotNull(v.findViewById<View>(R.id.etHoraRuim))
        assertNotNull(v.findViewById<View>(R.id.etHoraBoa))
        assertNotNull(v.findViewById<View>(R.id.etKmRuim))
        assertNotNull(v.findViewById<View>(R.id.etKmBoa))
        assertNotNull(v.findViewById<View>(R.id.btnSalvarCores))
        assertNotNull(v.findViewById<View>(R.id.tvHistoricoContagem))
        assertNotNull(v.findViewById<View>(R.id.btnAbrirHistorico))
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
        assertNotNull(v.findViewById<View>(R.id.btnClose))
        assertNotNull(v.findViewById<View>(R.id.btnRepeat))
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
    }
}
