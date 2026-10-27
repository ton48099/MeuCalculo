package com.radardeganhos.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cobre as duas funções novas:
 *  - a escolha da tela no modo desktop (Smart Connect / DeX);
 *  - o print da corrida salvo e exibido no histórico.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w411dp-h891dp-xxhdpi")
class MultiTelaPrintTest {

    private val ctx: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun record(at: Long = 1_700_000_000_000L) = RideRecord(
        at = at,
        app = "com.ubercab.driver",
        valor = 11.65,
        km = 5.2,
        brutoKm = 2.24,
        brutoHora = 49.93,
        liquido = 7.29,
        origem = "Av. República, 123",
        destino = "Alameda da República, 321"
    )

    private fun printTeste(): Bitmap =
        Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(20, 40, 60))
        }

    @After
    fun limpa() {
        RideHistory.clear(ctx)
    }

    // ---- modo desktop: de qual tela a leitura acontece -------------------

    @Test
    fun modoDesktopDesligadoSempreLeNaTelaPrincipal() {
        val externa = "com.ubercab.driver" to 4
        assertEquals(
            "com.ubercab.driver" to MultiTela.TELA_PRINCIPAL,
            MultiTela.alvo(modoDesktop = false, corridaNaTelaExterna = externa, principal = "com.ubercab.driver")
        )
    }

    @Test
    fun modoDesktopLigadoPrefereATelaOndeOAppDeCorridaEsta() {
        val externa = "com.taxis99.driver" to 7
        assertEquals(
            externa,
            MultiTela.alvo(modoDesktop = true, corridaNaTelaExterna = externa, principal = "com.ubercab.driver")
        )
    }

    @Test
    fun modoDesktopSemCorridaNaTelaExternaCaiNaTelaPrincipal() {
        assertEquals(
            "com.ubercab.driver" to MultiTela.TELA_PRINCIPAL,
            MultiTela.alvo(modoDesktop = true, corridaNaTelaExterna = null, principal = "com.ubercab.driver")
        )
        assertNull(MultiTela.alvo(modoDesktop = true, corridaNaTelaExterna = null, principal = null))
        assertNull(MultiTela.alvo(modoDesktop = false, corridaNaTelaExterna = null, principal = null))
    }

    // ---- print da corrida ----------------------------------------------

    @Test
    fun salvaOPrintEGuardaJuntoComACorrida() {
        RideHistory.clear(ctx)
        RideHistory.save(ctx, listOf(record()))

        val bmp = printTeste()
        val nome = Prints.salvar(ctx, bmp)
        bmp.recycle()
        assertNotNull(nome)
        assertTrue(RideHistory.attachPrint(ctx, nome!!))

        val lido = RideHistory.load(ctx)
        assertEquals(nome, lido[0].print)
        // Miniatura do histórico e imagem em tela cheia abrem do arquivo.
        assertNotNull(Prints.carregar(ctx, nome, 480))
        assertNotNull(Prints.carregar(ctx, nome, 1440))
        // Serialização mantém o print ida e volta.
        assertEquals(nome, RideHistory.decode(RideHistory.encode(lido))[0].print)
    }

    @Test
    fun historicoAntigoSemOPcaoDePrintContinuaValido() {
        // Linha gravada pela versão que não tinha o campo de print (9 campos).
        val linha = RideHistory.encode(listOf(record()))
            .split("|")
            .take(9)
            .joinToString("|")
        val lido = RideHistory.decode(linha)
        assertEquals(1, lido.size)
        assertNull(lido[0].print)
        assertEquals(11.65, lido[0].valor, 0.001)
    }

    @Test
    fun printAntigoEApagadoQuandoACorridaSaiDoHistorico() {
        RideHistory.clear(ctx)
        RideHistory.save(ctx, listOf(record(at = 1_000L), record(at = 2_000L)))

        val bmp = printTeste()
        val nome = Prints.salvar(ctx, bmp)!!
        bmp.recycle()
        assertTrue(RideHistory.attachPrint(ctx, nome))
        assertNotNull(Prints.carregar(ctx, nome, 480))

        // A corrida com print sai da lista (limite/limpeza): o arquivo some junto.
        val semPrint = RideHistory.load(ctx).map { it.copy(print = null) }
        RideHistory.save(ctx, semPrint)
        assertNull(Prints.carregar(ctx, nome, 480))
    }

    @Test
    fun limparHistoricoApagaOsPrintsDoAparelho() {
        RideHistory.clear(ctx)
        RideHistory.save(ctx, listOf(record()))
        val bmp = printTeste()
        val nome = Prints.salvar(ctx, bmp)!!
        bmp.recycle()
        assertTrue(RideHistory.attachPrint(ctx, nome))

        RideHistory.clear(ctx)
        assertTrue(RideHistory.load(ctx).isEmpty())
        assertNull(Prints.carregar(ctx, nome, 480))
    }

    @Test
    fun opcaoDePrintELigadaDeFabricaEDesligavel() {
        assertTrue(Settings().printCorrida)
        assertFalse(Settings(printCorrida = false).printCorrida)
        // Modo desktop vem desligado: quem usa Smart Connect liga quando quiser.
        assertFalse(Settings().modoDesktop)
    }
}
