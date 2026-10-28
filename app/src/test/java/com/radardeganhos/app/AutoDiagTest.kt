package com.radardeganhos.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * O botão "copiar log" é a linha de vida entre o aparelho e a correção do parser:
 * ele tem de trazer o texto BRUTO do OCR (como o card saiu na tela) e o que o app
 * entendeu dele, lado a lado — é comparando os dois que o erro aparece.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoDiagTest {

    private val ctx: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun logTrazOTextoBrutoEOQueOAppEntendeu() {
        val bruto = """
            UberX
            R$ 10,08
            Est. de R$1,42/km
            4 min (1.7 km)
            18 minutos (5.4 km)
        """.trimIndent()
        AutoDiag.salvarLeitura(
            ctx, bruto, "com.ubercab.driver",
            RideParser.parse(bruto, "com.ubercab.driver")
        )

        val log = AutoDiag.montarLog(ctx)
        assertTrue(log.contains("pacote: com.ubercab.driver"))
        assertTrue(log.contains("--- OCR LEU (texto bruto) ---"))
        // O texto como saiu, com a pontuação original — é ela que importa para consertar.
        assertTrue(log.contains("18 minutos (5.4 km)"))
        assertTrue(log.contains("--- APP ENTENDEU ---"))
        assertTrue(log.contains("app=Uber"))
        assertTrue(log.contains("categoria=UberX"))
        assertTrue(log.contains("R$ 10,08"))
    }

    /** Quando o parser não acha card, o log ainda tem de trazer o texto que falhou. */
    @Test
    fun logDizQuandoNaoAchouCard() {
        AutoDiag.salvarLeitura(ctx, "não sei o que é isso na tela", null, null)
        val log = AutoDiag.montarLog(ctx)
        assertTrue(log.contains("NENHUM CARD"))
        assertTrue(log.contains("não sei o que é isso na tela"))
        assertTrue(log.contains("pacote: não visto"))
    }

    /** Antes da primeira leitura, o botão explica o que o motorista precisa fazer. */
    @Test
    fun logSemLeituraAindaExplicaOQueFazer() {
        assertTrue(AutoDiag.montarLog(ctx).contains("Ainda não houve leitura"))
    }

    /** Texto gigante não estoura o log: corta no limite salvo. */
    @Test
    fun textoBrutoTemLimite() {
        AutoDiag.salvarLeitura(ctx, "x".repeat(20000), null, null)
        val log = AutoDiag.montarLog(ctx)
        assertTrue(log.length < 9000)
    }
}
