package com.radardeganhos.app

import android.content.Context
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Combustível do carro: cada um guarda o seu preço por litro. */
enum class Combustivel(val chave: String, val label: String, val precoPadrao: Double) {
    GASOLINA("gasolina", "Gasolina", 5.89),
    ALCOOL("alcool", "Álcool", 4.19),
    GNV("gnv", "GNV", 4.29);

    companion object {
        fun de(chave: String?): Combustivel =
            values().firstOrNull { it.chave == chave } ?: GASOLINA
    }
}

/** Fuso do horário oficial: o motorista conta o dia por Brasília, não por UTC. */
object Fuso {
    val BRASILIA: ZoneId = ZoneId.of("America/Sao_Paulo")

    fun agoraMin(): Int = LocalTime.now(BRASILIA).let { it.hour * 60 + it.minute }

    fun hoje(): String = LocalDate.now(BRASILIA).toString()
}

/**
 * O plantão do dia: o que entrou por app, quantos km, que horas começou e acabou, e
 * quanto custou rodar. É a calculadora da aba Home — nada disso vai para a nuvem,
 * tudo fica nas preferências do aparelho.
 */
data class Plantao(
    val dia: String = Fuso.hoje(),
    val ganho99: Double = 0.0,
    val ganhoUber: Double = 0.0,
    val ganhoInDrive: Double = 0.0,
    val ganhoGorjetas: Double = 0.0,
    val km: Double = 0.0,
    /** Minutos desde a meia-noite, no fuso de Brasília. */
    val inicioMin: Int? = null,
    val fimMin: Int? = null,
    /** "Ainda estou rodando": o fim é o relógio andando. */
    val aoVivo: Boolean = false,
    val combustivel: Combustivel = Combustivel.GASOLINA,
    val precoGasolina: Double = Combustivel.GASOLINA.precoPadrao,
    val precoAlcool: Double = Combustivel.ALCOOL.precoPadrao,
    val precoGnv: Double = Combustivel.GNV.precoPadrao,
    val consumo: Double = 10.0,
    val manutencaoKm: Double = PADRAO_MANUTENCAO,
    val outros: Double = 0.0,
    val meta: Double = PADRAO_META
) {

    /** Soma do que os apps pagaram (inclui gorjetas e corrida particular). */
    val bruto: Double get() = ganho99 + ganhoUber + ganhoInDrive + ganhoGorjetas

    val precoLitro: Double
        get() = when (combustivel) {
            Combustivel.GASOLINA -> precoGasolina
            Combustivel.ALCOOL -> precoAlcool
            Combustivel.GNV -> precoGnv
        }

    /**
     * Combustível do dia. Sem km ou sem consumo não há o que calcular — é o mesmo
     * aviso que a tela mostra embaixo do campo de km.
     */
    val gastoCombustivel: Double
        get() = if (km > 0.0 && consumo > 0.0) km / consumo * precoLitro else 0.0

    /** Reserva de manutenção: R$ por km rodado (óleo, pneu, freio, revisão). */
    val gastoManutencao: Double get() = if (km > 0.0) km * manutencaoKm else 0.0

    /** Tudo o que saiu do bolso para rodar hoje. */
    val despesas: Double get() = gastoCombustivel + gastoManutencao + outros

    val liquido: Double get() = bruto - despesas

    val porKm: Double? get() = if (km > 0.0) liquido / km else null

    fun porHora(agoraMin: Int = Fuso.agoraMin()): Double? {
        val min = minutosRodados(agoraMin) ?: return null
        if (min <= 0.0) return null
        return liquido / (min / 60.0)
    }

    /**
     * Minutos trabalhados. Fim antes do início significa plantão que virou a noite
     * (22:00 → 02:30), então soma um dia em vez de dar tempo negativo.
     */
    fun minutosRodados(agoraMin: Int = Fuso.agoraMin()): Double? {
        val inicio = inicioMin ?: return null
        val fim = when {
            aoVivo -> agoraMin
            fimMin != null -> fimMin
            else -> return null
        }
        var delta = fim - inicio
        if (delta < 0) delta += 24 * 60
        return if (delta <= 0) null else delta.toDouble()
    }

    /** Quanto ainda falta para a meta de lucro líquido do dia. */
    val faltaMeta: Double get() = (meta - liquido).coerceAtLeast(0.0)

    /** 0–100+, em % da meta já cumprida. */
    val progressoMeta: Double get() = if (meta > 0.0) liquido / meta * 100.0 else 0.0

    companion object {
        /** Reserva de manutenção sugerida por km (óleo, pneu, freio, revisão). */
        const val PADRAO_MANUTENCAO = 0.25

        /** Meta de lucro líquido do dia. */
        const val PADRAO_META = 300.0
    }
}

/** Plantão fechado, guardado para consulta. */
data class PlantaoSalvo(
    val dia: String,
    val bruto: Double,
    val despesas: Double,
    val km: Double,
    val min: Double,
    val liquido: Double
)

/**
 * Leitura e gravação do plantão nas preferências.
 *
 * O plantão é do DIA: ao abrir num dia novo, os valores zeram (menos preço do litro,
 * consumo, reserva de manutenção e meta, que são configuração e continuam valendo).
 */
object PlantaoStore {

    private const val ARQ = "plantao"
    private const val HIST = "plantoes_salvos"

    fun carregar(ctx: Context): Plantao {
        val p = ctx.getSharedPreferences(ARQ, Context.MODE_PRIVATE)
        val salvo = Plantao(
            dia = p.getString("dia", "") ?: "",
            ganho99 = p.getFloat("ganho99", 0f).toDouble(),
            ganhoUber = p.getFloat("ganhoUber", 0f).toDouble(),
            ganhoInDrive = p.getFloat("ganhoInDrive", 0f).toDouble(),
            ganhoGorjetas = p.getFloat("ganhoGorjetas", 0f).toDouble(),
            km = p.getFloat("km", 0f).toDouble(),
            inicioMin = if (p.contains("inicioMin")) p.getInt("inicioMin", 0) else null,
            fimMin = if (p.contains("fimMin")) p.getInt("fimMin", 0) else null,
            aoVivo = p.getBoolean("aoVivo", false),
            combustivel = Combustivel.de(p.getString("combustivel", null)),
            precoGasolina = p.getFloat("precoGasolina", Combustivel.GASOLINA.precoPadrao.toFloat()).toDouble(),
            precoAlcool = p.getFloat("precoAlcool", Combustivel.ALCOOL.precoPadrao.toFloat()).toDouble(),
            precoGnv = p.getFloat("precoGnv", Combustivel.GNV.precoPadrao.toFloat()).toDouble(),
            consumo = p.getFloat("consumo", 10f).toDouble(),
            manutencaoKm = p.getFloat("manutencaoKm", Plantao.PADRAO_MANUTENCAO.toFloat()).toDouble(),
            outros = p.getFloat("outros", 0f).toDouble(),
            meta = p.getFloat("meta", Plantao.PADRAO_META.toFloat()).toDouble()
        )
        // Virou o dia: zera o que é do dia, mantém o que é configuração do carro.
        return if (salvo.dia != Fuso.hoje()) {
            salvo.copy(
                dia = Fuso.hoje(),
                ganho99 = 0.0, ganhoUber = 0.0, ganhoInDrive = 0.0, ganhoGorjetas = 0.0,
                km = 0.0, inicioMin = null, fimMin = null, aoVivo = false, outros = 0.0
            ).also { salvar(ctx, it) }
        } else {
            salvo
        }
    }

    fun salvar(ctx: Context, p: Plantao) {
        val e = ctx.getSharedPreferences(ARQ, Context.MODE_PRIVATE).edit()
        e.putString("dia", p.dia)
        e.putFloat("ganho99", p.ganho99.toFloat())
        e.putFloat("ganhoUber", p.ganhoUber.toFloat())
        e.putFloat("ganhoInDrive", p.ganhoInDrive.toFloat())
        e.putFloat("ganhoGorjetas", p.ganhoGorjetas.toFloat())
        e.putFloat("km", p.km.toFloat())
        if (p.inicioMin != null) e.putInt("inicioMin", p.inicioMin) else e.remove("inicioMin")
        if (p.fimMin != null) e.putInt("fimMin", p.fimMin) else e.remove("fimMin")
        e.putBoolean("aoVivo", p.aoVivo)
        e.putString("combustivel", p.combustivel.chave)
        e.putFloat("precoGasolina", p.precoGasolina.toFloat())
        e.putFloat("precoAlcool", p.precoAlcool.toFloat())
        e.putFloat("precoGnv", p.precoGnv.toFloat())
        e.putFloat("consumo", p.consumo.toFloat())
        e.putFloat("manutencaoKm", p.manutencaoKm.toFloat())
        e.putFloat("outros", p.outros.toFloat())
        e.putFloat("meta", p.meta.toFloat())
        e.apply()
    }

    /** Guarda o plantão fechado e deixa o dia pronto para começar de novo. */
    fun salvarNoHistorico(ctx: Context, p: Plantao): PlantaoSalvo {
        val min = p.minutosRodados() ?: 0.0
        val reg = PlantaoSalvo(p.dia, p.bruto, p.despesas, p.km, min, p.liquido)
        val lista = carregarSalvos(ctx).toMutableList()
        lista.add(0, reg)
        val texto = lista.take(30).joinToString("\n") { r ->
            "${r.dia};${r.bruto};${r.despesas};${r.km};${r.min};${r.liquido}"
        }
        ctx.getSharedPreferences(ARQ, Context.MODE_PRIVATE).edit()
            .putString(HIST, texto).apply()
        return reg
    }

    fun carregarSalvos(ctx: Context): List<PlantaoSalvo> {
        val bruto = ctx.getSharedPreferences(ARQ, Context.MODE_PRIVATE)
            .getString(HIST, null) ?: return emptyList()
        return bruto.split('\n').mapNotNull { linha ->
            val c = linha.split(';')
            if (c.size != 6) return@mapNotNull null
            PlantaoSalvo(
                dia = c[0],
                bruto = c[1].toDoubleOrNull() ?: return@mapNotNull null,
                despesas = c[2].toDoubleOrNull() ?: return@mapNotNull null,
                km = c[3].toDoubleOrNull() ?: return@mapNotNull null,
                min = c[4].toDoubleOrNull() ?: return@mapNotNull null,
                liquido = c[5].toDoubleOrNull() ?: return@mapNotNull null
            )
        }
    }

    fun limparHistorico(ctx: Context) {
        ctx.getSharedPreferences(ARQ, Context.MODE_PRIVATE).edit().remove(HIST).apply()
    }
}
