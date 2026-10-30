package com.radardeganhos.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Conta da aba Home: bruto, despesas, saldo, R$/km, R$/hora e meta. */
class PlantaoTest {

    @Test
    fun somaOGanhoDosAppsNoBruto() {
        val p = Plantao(ganho99 = 120.0, ganhoUber = 200.0, ganhoInDrive = 50.0, ganhoGorjetas = 30.0)
        assertEquals(400.0, p.bruto, 0.001)
    }

    @Test
    fun descontaCombustivelManutencaoEOutros() {
        val p = Plantao(
            ganhoUber = 300.0,
            km = 150.0,
            precoGasolina = 6.0,
            consumo = 10.0,
            manutencaoKm = 0.25,
            outros = 20.0
        )
        // 150 km / 10 km por litro = 15 litros × R$ 6,00
        assertEquals(90.0, p.gastoCombustivel, 0.001)
        // 150 km × R$ 0,25
        assertEquals(37.5, p.gastoManutencao, 0.001)
        assertEquals(147.5, p.despesas, 0.001)
        assertEquals(152.5, p.liquido, 0.001)
        assertEquals(152.5 / 150.0, p.porKm!!, 0.001)
    }

    @Test
    fun semKmNaoCalculaPorKmNemCombustivel() {
        val p = Plantao(ganho99 = 100.0, km = 0.0)
        assertEquals(0.0, p.gastoCombustivel, 0.001)
        assertEquals(0.0, p.gastoManutencao, 0.001)
        assertNull(p.porKm)
        assertEquals(100.0, p.liquido, 0.001)
    }

    @Test
    fun ganhoPorHoraUsaOTempoRodado() {
        val p = Plantao(ganhoUber = 240.0, inicioMin = 8 * 60, fimMin = 12 * 60)
        assertEquals(240.0, p.minutosRodados(agoraMin = 20 * 60)!!, 0.001)
        assertEquals(60.0, p.porHora(agoraMin = 20 * 60)!!, 0.001)
    }

    /** Plantão que virou a noite: 22:00 → 02:30 são 4 h 30, não tempo negativo. */
    @Test
    fun plantaoDaMadrugadaNaoDaTempoNegativo() {
        val p = Plantao(ganhoUber = 180.0, inicioMin = 22 * 60, fimMin = 2 * 60 + 30)
        assertEquals(270.0, p.minutosRodados(agoraMin = 12 * 60)!!, 0.001)
        assertEquals(40.0, p.porHora(agoraMin = 12 * 60)!!, 0.001)
    }

    @Test
    fun modoAoVivoAcompanhaORelogio() {
        val p = Plantao(ganhoUber = 100.0, inicioMin = 10 * 60, aoVivo = true)
        assertEquals(120.0, p.minutosRodados(agoraMin = 12 * 60)!!, 0.001)
        assertEquals(50.0, p.porHora(agoraMin = 12 * 60)!!, 0.001)
    }

    @Test
    fun semHorarioNaoDaGanhoPorHora() {
        val p = Plantao(ganhoUber = 100.0)
        assertNull(p.porHora())
        assertNull(p.minutosRodados())
    }

    @Test
    fun metaMostraQuantoFalta() {
        val p = Plantao(ganhoUber = 120.0, km = 0.0, meta = 300.0)
        assertEquals(180.0, p.faltaMeta, 0.001)
        assertEquals(40.0, p.progressoMeta, 0.001)

        val batida = Plantao(ganhoUber = 350.0, meta = 300.0)
        assertEquals(0.0, batida.faltaMeta, 0.001)
        assertTrue(batida.progressoMeta > 100.0)
    }

    @Test
    fun cadaCombustivelTemSeuPreco() {
        val p = Plantao(precoGasolina = 6.0, precoAlcool = 4.2, precoGnv = 4.5, combustivel = Combustivel.ALCOOL)
        assertEquals(4.2, p.precoLitro, 0.001)
        assertEquals(4.2, p.copy(combustivel = Combustivel.ALCOOL).precoLitro, 0.001)
        assertEquals(6.0, p.copy(combustivel = Combustivel.GASOLINA).precoLitro, 0.001)
        assertEquals(4.5, p.copy(combustivel = Combustivel.GNV).precoLitro, 0.001)
    }

    @Test
    fun despesaMaiorQueOGanhoDahSaldoNegativo() {
        val p = Plantao(ganhoUber = 50.0, km = 200.0, precoGasolina = 6.0, consumo = 8.0)
        assertTrue(p.liquido < 0)
    }
}
