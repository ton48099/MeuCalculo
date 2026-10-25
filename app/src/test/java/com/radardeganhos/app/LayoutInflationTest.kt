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
