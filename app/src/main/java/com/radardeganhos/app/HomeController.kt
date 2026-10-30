package com.radardeganhos.app

import android.app.Activity
import android.app.TimePickerDialog
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.util.Locale

/**
 * Aba Home: a calculadora do plantão.
 *
 * Tudo o que o motorista digita é do dia e fica no aparelho (SharedPreferences), sem
 * servidor. A conta segue a mesma ordem da tela: entra o bruto por app, saem
 * combustível, reserva de manutenção e outros gastos; o que sobra é o saldo líquido,
 * e o R$/km e o R$/h vêm dele.
 */
class HomeController(private val act: Activity) {

    private var p: Plantao = Plantao()

    /** Ligado enquanto a tela escreve nos campos, para o watcher não recarregar tudo. */
    private var silencioso = false

    private val handler = Handler(Looper.getMainLooper())
    private val relogio = object : Runnable {
        override fun run() {
            atualizarRelogio()
            if (p.aoVivo) {
                lerCampos()
                render()
            }
            handler.postDelayed(this, 30_000)
        }
    }

    // ---- campos -----------------------------------------------------------

    private val etGanho99 by lazy { act.findViewById<EditText>(R.id.etGanho99) }
    private val etGanhoUber by lazy { act.findViewById<EditText>(R.id.etGanhoUber) }
    private val etGanhoInDrive by lazy { act.findViewById<EditText>(R.id.etGanhoInDrive) }
    private val etGanhoGorjetas by lazy { act.findViewById<EditText>(R.id.etGanhoGorjetas) }
    private val etKm by lazy { act.findViewById<EditText>(R.id.etKm) }
    private val etPrecoLitro by lazy { act.findViewById<EditText>(R.id.etPrecoLitro) }
    private val etConsumo by lazy { act.findViewById<EditText>(R.id.etConsumo) }
    private val etManut by lazy { act.findViewById<EditText>(R.id.etManut) }
    private val etOutros by lazy { act.findViewById<EditText>(R.id.etOutros) }

    private val tvSaldo by lazy { act.findViewById<TextView>(R.id.tvSaldo) }
    private val tvBruto by lazy { act.findViewById<TextView>(R.id.tvBruto) }
    private val tvDespesas by lazy { act.findViewById<TextView>(R.id.tvDespesas) }
    private val tvBrutoApps by lazy { act.findViewById<TextView>(R.id.tvBrutoApps) }
    private val tvGanhoKm by lazy { act.findViewById<TextView>(R.id.tvGanhoKm) }
    private val tvGanhoHora by lazy { act.findViewById<TextView>(R.id.tvGanhoHora) }
    private val tvTotalCustos by lazy { act.findViewById<TextView>(R.id.tvTotalCustos) }
    private val tvDetalheCustos by lazy { act.findViewById<TextView>(R.id.tvDetalheCustos) }
    private val tvMeta by lazy { act.findViewById<TextView>(R.id.tvMeta) }
    private val tvFaltaMeta by lazy { act.findViewById<TextView>(R.id.tvFaltaMeta) }
    private val tvInfoHoras by lazy { act.findViewById<TextView>(R.id.tvInfoHoras) }
    private val tvHoraBrasilia by lazy { act.findViewById<TextView>(R.id.tvHoraBrasilia) }
    private val tvInicio by lazy { act.findViewById<TextView>(R.id.tvInicio) }
    private val tvFim by lazy { act.findViewById<TextView>(R.id.tvFim) }
    private val tvSemPlantoes by lazy { act.findViewById<TextView>(R.id.tvSemPlantoes) }
    private val listaPlantoes by lazy { act.findViewById<LinearLayout>(R.id.listaPlantoes) }
    private val pbMeta by lazy { act.findViewById<ProgressBar>(R.id.pbMeta) }
    private val swAoVivo by lazy { act.findViewById<Switch>(R.id.swAoVivo) }
    private val btnGasolina by lazy { act.findViewById<TextView>(R.id.btnGasolina) }
    private val btnAlcool by lazy { act.findViewById<TextView>(R.id.btnAlcool) }
    private val btnGnv by lazy { act.findViewById<TextView>(R.id.btnGnv) }

    // ---- ciclo ------------------------------------------------------------

    fun bind() {
        p = PlantaoStore.carregar(act)
        preencher()
        ligar()
        render()
        atualizarRelogio()
        handler.postDelayed(relogio, 30_000)
    }

    /** Chamado no onResume: o dia pode ter virado com o app aberto. */
    fun refresh() {
        val novo = PlantaoStore.carregar(act)
        if (novo.dia != p.dia) {
            p = novo
            preencher()
        }
        atualizarRelogio()
        render()
    }

    /** Volta a acompanhar o relógio (onResume). */
    fun retomar() {
        handler.removeCallbacks(relogio)
        handler.postDelayed(relogio, 30_000)
    }

    fun pausar() {
        handler.removeCallbacks(relogio)
    }

    // ---- leitura e gravação ----------------------------------------------

    /** Joga o que está nos campos para o modelo do dia e grava. */
    private fun lerCampos() {
        p = p.copy(
            ganho99 = numero(etGanho99),
            ganhoUber = numero(etGanhoUber),
            ganhoInDrive = numero(etGanhoInDrive),
            ganhoGorjetas = numero(etGanhoGorjetas),
            km = numero(etKm),
            outros = numero(etOutros),
            consumo = numero(etConsumo),
            manutencaoKm = numero(etManut)
        )
        val preco = numero(etPrecoLitro)
        p = when (p.combustivel) {
            Combustivel.GASOLINA -> p.copy(precoGasolina = preco)
            Combustivel.ALCOOL -> p.copy(precoAlcool = preco)
            Combustivel.GNV -> p.copy(precoGnv = preco)
        }
        PlantaoStore.salvar(act, p)
    }

    /** Escreve o modelo do dia nos campos (sem disparar o watcher). */
    private fun preencher() {
        silencioso = true
        etGanho99.setText(texto(p.ganho99))
        etGanhoUber.setText(texto(p.ganhoUber))
        etGanhoInDrive.setText(texto(p.ganhoInDrive))
        etGanhoGorjetas.setText(texto(p.ganhoGorjetas))
        etKm.setText(texto(p.km))
        etOutros.setText(texto(p.outros))
        etConsumo.setText(texto(p.consumo))
        etManut.setText(texto(p.manutencaoKm))
        // O preço do litro vem do combustível escolhido. Sem preencher aqui, o campo
        // ficava vazio e o primeiro dígito digitado em outro campo gravava preço 0.
        etPrecoLitro.setText(texto(p.precoLitro))
        silencioso = false
        pintarCombustivel()
        swAoVivo.setOnCheckedChangeListener(null)
        swAoVivo.isChecked = p.aoVivo
        swAoVivo.setOnCheckedChangeListener { _, ligado ->
            p = if (ligado) {
                p.copy(aoVivo = true, inicioMin = p.inicioMin ?: Fuso.agoraMin(), fimMin = null)
            } else {
                p.copy(aoVivo = false, fimMin = p.fimMin ?: Fuso.agoraMin())
            }
            PlantaoStore.salvar(act, p)
            render()
        }
    }

    private fun ligar() {
        listOf(etGanho99, etGanhoUber, etGanhoInDrive, etGanhoGorjetas, etKm, etPrecoLitro, etConsumo, etManut, etOutros)
            .forEach { et -> et.addTextChangedListener(objeto { aoDigitar() }) }

        // Ganhos por app: +10 / +20 / +50
        mapaDeChips(
            arrayOf(R.id.mais99_10 to 10.0, R.id.mais99_20 to 20.0, R.id.mais99_50 to 50.0),
            etGanho99
        )
        mapaDeChips(
            arrayOf(R.id.maisUber_10 to 10.0, R.id.maisUber_20 to 20.0, R.id.maisUber_50 to 50.0),
            etGanhoUber
        )
        mapaDeChips(
            arrayOf(R.id.maisInDrive_10 to 10.0, R.id.maisInDrive_20 to 20.0, R.id.maisInDrive_50 to 50.0),
            etGanhoInDrive
        )
        mapaDeChips(
            arrayOf(R.id.maisGorjetas_10 to 10.0, R.id.maisGorjetas_20 to 20.0, R.id.maisGorjetas_50 to 50.0),
            etGanhoGorjetas
        )
        // Km: +10 / +25 / +50 / +100
        mapaDeChips(
            arrayOf(R.id.maisKm_10 to 10.0, R.id.maisKm_25 to 25.0, R.id.maisKm_50 to 50.0, R.id.maisKm_100 to 100.0),
            etKm
        )

        // Horas
        tvInicio.setOnClickListener { escolherHora(p.inicioMin) { p = p.copy(inicioMin = it, fimMin = p.fimMin); salvar() } }
        tvFim.setOnClickListener { escolherHora(p.fimMin) { p = p.copy(fimMin = it, aoVivo = false); preencher(); salvar() } }
        act.findViewById<TextView>(R.id.btnComecarAgora).setOnClickListener {
            p = p.copy(inicioMin = Fuso.agoraMin())
            salvar()
        }
        act.findViewById<TextView>(R.id.btnAcabarAgora).setOnClickListener {
            p = p.copy(fimMin = Fuso.agoraMin(), aoVivo = false)
            preencher()
            salvar()
        }
        act.findViewById<TextView>(R.id.btnSincronizarFim).setOnClickListener {
            p = p.copy(fimMin = Fuso.agoraMin(), inicioMin = p.inicioMin ?: Fuso.agoraMin(), aoVivo = false)
            preencher()
            salvar()
        }

        // Combustível
        btnGasolina.setOnClickListener { trocarCombustivel(Combustivel.GASOLINA) }
        btnAlcool.setOnClickListener { trocarCombustivel(Combustivel.ALCOOL) }
        btnGnv.setOnClickListener { trocarCombustivel(Combustivel.GNV) }

        // Manutenção
        act.findViewById<TextView>(R.id.btnRestaurarManut).setOnClickListener {
            etManut.setText(texto(Plantao.PADRAO_MANUTENCAO))
        }
        act.findViewById<TextView>(R.id.btnZerarManut).setOnClickListener { etManut.setText("") }

        // Meta
        act.findViewById<TextView>(R.id.btnAlterarMeta).setOnClickListener { alterarMeta() }

        // Salvar plantão
        act.findViewById<TextView>(R.id.btnSalvarPlantao).setOnClickListener { salvarPlantao() }
    }

    private fun mapaDeChips(chips: Array<Pair<Int, Double>>, campo: EditText) {
        chips.forEach { (id, valor) ->
            act.findViewById<TextView>(id).setOnClickListener {
                campo.setText(texto(numero(campo) + valor))
            }
        }
    }

    private fun aoDigitar() {
        if (silencioso) return
        lerCampos()
        render()
    }

    private fun salvar() {
        PlantaoStore.salvar(act, p)
        render()
    }

    // ---- telas de escolha -------------------------------------------------

    private fun escolherHora(atual: Int?, depois: (Int) -> Unit) {
        val agora = Fuso.agoraMin()
        TimePickerDialog(
            act,
            { _, h, m -> depois(h * 60 + m) },
            (atual ?: agora) / 60,
            (atual ?: agora) % 60,
            true
        ).show()
    }

    private fun trocarCombustivel(c: Combustivel) {
        p = p.copy(combustivel = c)
        pintarCombustivel()
        silencioso = true
        etPrecoLitro.setText(texto(p.precoLitro))
        silencioso = false
        salvar()
    }

    private fun pintarCombustivel() {
        listOf(Combustivel.GASOLINA, Combustivel.ALCOOL, Combustivel.GNV).forEach { c ->
            val vista = when (c) {
                Combustivel.GASOLINA -> btnGasolina
                Combustivel.ALCOOL -> btnAlcool
                Combustivel.GNV -> btnGnv
            }
            val ativo = c == p.combustivel
            vista.setBackgroundResource(if (ativo) R.drawable.bg_pill else R.drawable.bg_field)
            vista.setTextColor(act.resources.getColor(if (ativo) R.color.orange else R.color.textDim, act.theme))
        }
    }

    private fun alterarMeta() {
        val campo = EditText(act).apply {
            setText(texto(p.meta))
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(act.resources.getColor(R.color.text, act.theme))
            setHint("Ex.: 300")
            setPadding(30, 30, 30, 30)
        }
        AlertDialog.Builder(act)
            .setTitle("Meta de lucro líquido do dia")
            .setView(campo)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val valor = NumberParse.toDouble(campo.text.toString()) ?: 0.0
                p = p.copy(meta = if (valor > 0) valor else Plantao.PADRAO_META)
                salvar()
            }
            .show()
    }

    private fun salvarPlantao() {
        if (p.bruto <= 0.0 && p.km <= 0.0) {
            Toast.makeText(act, "Nada para salvar: informe o que entrou hoje.", Toast.LENGTH_SHORT).show()
            return
        }
        val salvo = PlantaoStore.salvarNoHistorico(act, p)
        Toast.makeText(
            act,
            "Plantão salvo: ${Report.money(salvo.liquido)} líquido em ${Report.tempo(salvo.min)}.",
            Toast.LENGTH_LONG
        ).show()
        // Fecha o dia: zera o que é do dia e mantém preço, consumo, reserva e meta.
        p = p.copy(
            ganho99 = 0.0, ganhoUber = 0.0, ganhoInDrive = 0.0, ganhoGorjetas = 0.0,
            km = 0.0, inicioMin = null, fimMin = null, aoVivo = false, outros = 0.0,
            dia = Fuso.hoje()
        )
        PlantaoStore.salvar(act, p)
        preencher()
        render()
    }

    // ---- desenho ----------------------------------------------------------

    private fun atualizarRelogio() {
        val agora = Fuso.agoraMin()
        tvHoraBrasilia.text = String.format(Locale.US, "%02d:%02d", agora / 60, agora % 60)
    }

    @android.annotation.SuppressLint("SetTextI18n")
    private fun render() {
        tvSaldo.text = Report.money(p.liquido)
        tvSaldo.setTextColor(act.resources.getColor(if (p.liquido < 0) R.color.red else R.color.orange, act.theme))
        tvBruto.text = Report.money(p.bruto)
        tvDespesas.text = "- ${Report.money(p.despesas)}"
        tvBrutoApps.text = Report.money(p.bruto)

        val porKm = p.porKm
        tvGanhoKm.text = if (porKm != null) "${Report.money(porKm)}/km" else "Não calculado"
        tvGanhoKm.setTextColor(act.resources.getColor(if (porKm != null) R.color.text else R.color.textDim, act.theme))

        val porHora = p.porHora()
        tvGanhoHora.text = if (porHora != null) "${Report.money(porHora)}/h" else "Não calculado"
        tvGanhoHora.setTextColor(act.resources.getColor(if (porHora != null) R.color.text else R.color.textDim, act.theme))

        tvTotalCustos.text = Report.money(p.despesas)
        tvDetalheCustos.text = "Combustível ${Report.money(p.gastoCombustivel)} · " +
            "manutenção ${Report.money(p.gastoManutencao)} · outros ${Report.money(p.outros)}"

        tvMeta.text = "Meta: ${Report.money(p.meta)}"
        tvFaltaMeta.text = if (p.faltaMeta <= 0.0) {
            "Meta batida! (${Report.num(p.progressoMeta, 0)}%)"
        } else {
            "Falta ${Report.money(p.faltaMeta)} (${Report.num(p.progressoMeta, 0)}%)"
        }
        pbMeta.progress = p.progressoMeta.coerceIn(0.0, 100.0).toInt()

        tvInicio.text = horaDe(p.inicioMin)
        tvFim.text = if (p.aoVivo) "rodando agora" else horaDe(p.fimMin)

        val min = p.minutosRodados()
        tvInfoHoras.text = when {
            min == null && p.aoVivo -> "Modo ao vivo ligado, mas sem hora de início: ninguém começou o plantão ainda."
            min == null -> "Sem horários definidos: o ganho por hora não é calculado. Defina quando começou " +
                "e terminou — ou ligue o ao vivo para ele acompanhar."
            p.aoVivo -> "Rodando há ${Report.tempo(min)}. O ritmo acompanha o relógio de Brasília."
            else -> "Rodou ${Report.tempo(min)} · ritmo de ${Report.money(porHora ?: 0.0)} por hora."
        }

        desenharPlantoes()
    }

    private fun horaDe(minuto: Int?): String =
        if (minuto == null) "--:--" else String.format(Locale.US, "%02d:%02d", minuto / 60, minuto % 60)

    @android.annotation.SuppressLint("SetTextI18n")
    private fun desenharPlantoes() {
        val salvos = PlantaoStore.carregarSalvos(act)
        listaPlantoes.removeAllViews()
        tvSemPlantoes.visibility = if (salvos.isEmpty()) View.VISIBLE else View.GONE
        salvos.take(3).forEach { s ->
            val linha = TextView(act).apply {
                text = "${diaCurto(s.dia)} · ${Report.money(s.liquido)} líquido · " +
                    "${Report.num(s.km, 0)} km · ${Report.tempo(s.min)}"
                setTextColor(act.resources.getColor(R.color.text, act.theme))
                textSize = 13f
                setPadding(0, 8, 0, 8)
            }
            listaPlantoes.addView(linha)
        }
    }

    private fun diaCurto(dia: String): String = runCatching {
        val d = java.time.LocalDate.parse(dia)
        String.format(Locale.US, "%02d/%02d", d.dayOfMonth, d.monthValue)
    }.getOrDefault(dia)

    // ---- utilidades -------------------------------------------------------

    private fun numero(et: EditText): Double =
        NumberParse.toDouble(et.text?.toString()) ?: 0.0

    /** Valor para campo: vazio em vez de "0,00", e vírgula no decimal. */
    private fun texto(v: Double): String {
        if (v <= 0.0) return ""
        val s = String.format(Locale.US, "%.2f", v)
        return if (s.endsWith(",00") || s.endsWith(".00")) s.dropLast(3) else s.replace('.', ',')
    }

    private fun objeto(aoMudar: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        override fun afterTextChanged(s: Editable?) = aoMudar()
    }
}
