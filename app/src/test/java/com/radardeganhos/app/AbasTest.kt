package com.radardeganhos.app

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * As três abas: Home abre por padrão com a calculadora, Config traz os comandos da
 * leitura e Histórico é a tela de corridas. Também garante que a barra inferior tem
 * os três itens e que a calculadora é recalculada ao digitar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h891dp-xxhdpi")
class AbasTest {

    @Test
    fun abreNaHomeComACalculadora() {
        val act = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        val home = act.findViewById<View>(R.id.tabHome)
        val config = act.findViewById<View>(R.id.tabConfig)
        assertEquals(View.VISIBLE, home.visibility)
        assertEquals(View.GONE, config.visibility)

        val nav = act.findViewById<BottomNavigationView>(R.id.bottomNav)
        assertEquals(3, nav.menu.size())
        assertEquals(R.id.navInicio, nav.selectedItemId)

        val saldo = act.findViewById<TextView>(R.id.tvSaldo)
        assertNotNull(saldo)
        assertTrue(saldo.text.toString().startsWith("R$"))
    }

    @Test
    fun calcularSaldoAoDigitarOsGanhos() {
        val act = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        act.findViewById<android.widget.EditText>(R.id.etGanho99).setText("100")
        act.findViewById<android.widget.EditText>(R.id.etGanhoUber).setText("250,50")

        val bruto = act.findViewById<TextView>(R.id.tvBruto)
        assertEquals("R$ 350,50", bruto.text.toString())
        val apps = act.findViewById<TextView>(R.id.tvBrutoApps)
        assertEquals("R$ 350,50", apps.text.toString())
    }

    /** O preço do litro não pode sumir quando o motorista digita em outro campo. */
    @Test
    fun precoDaLitroSobreviveAoDigitar() {
        val act = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val preco = act.findViewById<android.widget.EditText>(R.id.etPrecoLitro)
        assertEquals("5,89", preco.text.toString())

        act.findViewById<android.widget.EditText>(R.id.etGanhoUber).setText("120")
        assertEquals("5,89", preco.text.toString())
        assertEquals(5.89, PlantaoStore.carregar(act).precoGasolina, 0.001)
    }

    @Test
    fun chipDeKmSomaNoCampo() {
        val act = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        act.findViewById<View>(R.id.maisKm_50).performClick()
        val km = act.findViewById<TextView>(R.id.etKm)
        assertEquals("50", km.text.toString())

        act.findViewById<View>(R.id.maisKm_100).performClick()
        assertEquals("150", km.text.toString())
    }

    @Test
    fun configTemOsComandosDaLeitura() {
        val act = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val nav = act.findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.selectedItemId = R.id.navConfig

        assertEquals(View.VISIBLE, act.findViewById<View>(R.id.tabConfig).visibility)
        assertEquals(View.GONE, act.findViewById<View>(R.id.tabHome).visibility)
        assertNotNull(act.findViewById<View>(R.id.btnToggleLeitura))
        assertNotNull(act.findViewById<View>(R.id.btnRecusa))
        assertNotNull(act.findViewById<View>(R.id.btnEnsinaX))
        assertNotNull(act.findViewById<View>(R.id.btnCustos))
        assertNotNull(act.findViewById<View>(R.id.btnCardConfig))
    }
}
