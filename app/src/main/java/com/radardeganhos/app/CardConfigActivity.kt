package com.radardeganhos.app

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Tela "Card e Cores".
 *
 * Duas regras que faltavam antes e faziam a config não funcionar na prática:
 *  1. o que é salto começa sempre em [Prefs.load] (nunca em [Settings], os padrões),
 *     para não apagar custos/metas configurados na outra tela;
 *  2. cada alteração salva e aplica na hora no card que está na tela — o botão
 *     "Salvar" virou só um "concluir e voltar".
 */
class CardConfigActivity : AppCompatActivity() {

    // Leitura automática
    private lateinit var chkAutoScan: CheckBox
    private lateinit var etAutoIntervalo: EditText
    private lateinit var chkModoDesktop: CheckBox
    private lateinit var chkPrintCorrida: CheckBox
    private lateinit var tvDiag: TextView

    // Posição do card
    private lateinit var btnPosTopoEsquerda: android.widget.LinearLayout
    private lateinit var btnPosTopoCentro: android.widget.LinearLayout
    private lateinit var btnPosTopoDireita: android.widget.LinearLayout
    private lateinit var btnPosCentro: android.widget.LinearLayout
    private var posicaoSelecionada = "centro"

    private lateinit var seekDuracao: SeekBar
    private lateinit var seekOpacidade: SeekBar
    private lateinit var txtDuracaoValor: TextView
    private lateinit var btnFontePequena: android.widget.LinearLayout
    private lateinit var btnFonteMedia: android.widget.LinearLayout
    private lateinit var btnFonteGrande: android.widget.LinearLayout
    private lateinit var chkEmpilhar: CheckBox
    private lateinit var chkValorKm: CheckBox
    private lateinit var chkValorHora: CheckBox
    private lateinit var chkValorMinuto: CheckBox
    private lateinit var chkValoresTotais: CheckBox
    private lateinit var chkVeredito: CheckBox
    private lateinit var chkEnderecos: CheckBox
    private lateinit var chkCustoTotal: CheckBox
    private lateinit var chkLucro: CheckBox
    private lateinit var chkMeta: CheckBox
    private lateinit var chkTrajeto: CheckBox
    private lateinit var chkReler: CheckBox

    // Semáforos KM
    private lateinit var btnKmBomMenos: ImageButton
    private lateinit var txtKmBom: TextView
    private lateinit var btnKmBomMais: ImageButton
    private lateinit var btnKmRuimMenos: ImageButton
    private lateinit var txtKmRuim: TextView
    private lateinit var btnKmRuimMais: ImageButton

    // Semáforos Hora
    private lateinit var btnHoraBomMenos: ImageButton
    private lateinit var txtHoraBom: TextView
    private lateinit var btnHoraBomMais: ImageButton
    private lateinit var btnHoraRuimMenos: ImageButton
    private lateinit var txtHoraRuim: TextView
    private lateinit var btnHoraRuimMais: ImageButton

    // Semáforos Minuto
    private lateinit var btnMinutoBomMenos: ImageButton
    private lateinit var txtMinutoBom: TextView
    private lateinit var btnMinutoBomMais: ImageButton
    private lateinit var btnMinutoRuimMenos: ImageButton
    private lateinit var txtMinutoRuim: TextView
    private lateinit var btnMinutoRuimMais: ImageButton

    private var tamanhoFonteSelecionado = "media"

    /** true enquanto a tela está sendo preenchida: nesse meio tempo nada salva. */
    private var carregando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_config)

        chkAutoScan = findViewById(R.id.chkAutoScan)
        etAutoIntervalo = findViewById(R.id.etAutoIntervalo)
        chkModoDesktop = findViewById(R.id.chkModoDesktop)
        chkPrintCorrida = findViewById(R.id.chkPrintCorrida)
        tvDiag = findViewById(R.id.tvDiag)
        btnPosTopoEsquerda = findViewById(R.id.btnPosTopoEsquerda)
        btnPosTopoCentro = findViewById(R.id.btnPosTopoCentro)
        btnPosTopoDireita = findViewById(R.id.btnPosTopoDireita)
        btnPosCentro = findViewById(R.id.btnPosCentro)

        seekDuracao = findViewById(R.id.seekDuracao)
        seekOpacidade = findViewById(R.id.seekOpacidade)
        txtDuracaoValor = findViewById(R.id.txtDuracaoValor)
        btnFontePequena = findViewById(R.id.btnFontePequena)
        btnFonteMedia = findViewById(R.id.btnFonteMedia)
        btnFonteGrande = findViewById(R.id.btnFonteGrande)
        chkEmpilhar = findViewById(R.id.chkEmpilhar)
        chkValorKm = findViewById(R.id.chkValorKm)
        chkValorHora = findViewById(R.id.chkValorHora)
        chkValorMinuto = findViewById(R.id.chkValorMinuto)
        chkValoresTotais = findViewById(R.id.chkValoresTotais)
        chkVeredito = findViewById(R.id.chkVeredito)
        chkEnderecos = findViewById(R.id.chkEnderecos)
        chkCustoTotal = findViewById(R.id.chkCustoTotal)
        chkLucro = findViewById(R.id.chkLucro)
        chkMeta = findViewById(R.id.chkMeta)
        chkTrajeto = findViewById(R.id.chkTrajeto)
        chkReler = findViewById(R.id.chkReler)

        btnKmBomMenos = findViewById(R.id.btnKmBomMenos)
        txtKmBom = findViewById(R.id.txtKmBom)
        btnKmBomMais = findViewById(R.id.btnKmBomMais)
        btnKmRuimMenos = findViewById(R.id.btnKmRuimMenos)
        txtKmRuim = findViewById(R.id.txtKmRuim)
        btnKmRuimMais = findViewById(R.id.btnKmRuimMais)

        btnHoraBomMenos = findViewById(R.id.btnHoraBomMenos)
        txtHoraBom = findViewById(R.id.txtHoraBom)
        btnHoraBomMais = findViewById(R.id.btnHoraBomMais)
        btnHoraRuimMenos = findViewById(R.id.btnHoraRuimMenos)
        txtHoraRuim = findViewById(R.id.txtHoraRuim)
        btnHoraRuimMais = findViewById(R.id.btnHoraRuimMais)

        btnMinutoBomMenos = findViewById(R.id.btnMinutoBomMenos)
        txtMinutoBom = findViewById(R.id.txtMinutoBom)
        btnMinutoBomMais = findViewById(R.id.btnMinutoBomMais)
        btnMinutoRuimMenos = findViewById(R.id.btnMinutoRuimMenos)
        txtMinutoRuim = findViewById(R.id.txtMinutoRuim)
        btnMinutoRuimMais = findViewById(R.id.btnMinutoRuimMais)

        fillForm(Prefs.load(this))

        // Tamanho da fonte
        btnFontePequena.setOnClickListener { selecionarFonte("pequena") }
        btnFonteMedia.setOnClickListener { selecionarFonte("media") }
        btnFonteGrande.setOnClickListener { selecionarFonte("grande") }

        // Posição do card na tela
        btnPosTopoEsquerda.setOnClickListener { selecionarPosicao("topo-esquerda") }
        btnPosTopoCentro.setOnClickListener { selecionarPosicao("topo") }
        btnPosTopoDireita.setOnClickListener { selecionarPosicao("topo-direita") }
        btnPosCentro.setOnClickListener { selecionarPosicao("centro") }

        // Leitura automática: cada item salva ao toque, sem depender do Salvar.
        listOf(chkAutoScan, chkModoDesktop, chkPrintCorrida).forEach { chk ->
            chk.setOnCheckedChangeListener { _, _ -> salvar() }
        }
        // O intervalo salva ENQUANTO o motorista digita. Antes ele só era gravado ao
        // sair do campo: escrever "2" e fechar o teclado podia não gravar nada, e a
        // tela voltava mostrando 6 — era o "não consigo mudar o valor".
        etAutoIntervalo.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (carregando) return
                val valor = NumberParse.toDouble(s?.toString())?.toInt() ?: return
                // Número ainda incompleto ("1" de "12") não vale: só a faixa aceita.
                if (valor in 2..120) salvar()
            }
        })
        etAutoIntervalo.setOnFocusChangeListener { _, temFoco ->
            if (!temFoco) {
                salvar()
                // Mostra o que ficou gravado de verdade (digitou 1 → vira 2).
                etAutoIntervalo.setText(Prefs.load(this).autoIntervaloSeg.toString())
            }
        }

        // Semáforo R$/km: limites em que a cor muda (mesmos da seção de cores).
        btnKmBomMenos.setOnClickListener { ajustarValor(txtKmBom, -0.05) }
        btnKmBomMais.setOnClickListener { ajustarValor(txtKmBom, 0.05) }
        btnKmRuimMenos.setOnClickListener { ajustarValor(txtKmRuim, -0.05) }
        btnKmRuimMais.setOnClickListener { ajustarValor(txtKmRuim, 0.05) }

        // Semáforo R$/hora
        btnHoraBomMenos.setOnClickListener { ajustarValor(txtHoraBom, -1.0) }
        btnHoraBomMais.setOnClickListener { ajustarValor(txtHoraBom, 1.0) }
        btnHoraRuimMenos.setOnClickListener { ajustarValor(txtHoraRuim, -1.0) }
        btnHoraRuimMais.setOnClickListener { ajustarValor(txtHoraRuim, 1.0) }

        // Semáforo R$/minuto (0,58 ≈ R$ 35/h · 0,75 ≈ R$ 45/h)
        btnMinutoBomMenos.setOnClickListener { ajustarValor(txtMinutoBom, -0.05) }
        btnMinutoBomMais.setOnClickListener { ajustarValor(txtMinutoBom, 0.05) }
        btnMinutoRuimMenos.setOnClickListener { ajustarValor(txtMinutoRuim, -0.05) }
        btnMinutoRuimMais.setOnClickListener { ajustarValor(txtMinutoRuim, 0.05) }

        // Duração na tela: mostra o valor escolhido e já vale para o próximo card.
        seekDuracao.min = 1000
        seekDuracao.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                txtDuracaoValor.text = "${progress / 1000}s na tela"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) = salvar()
        })

        // Opacidade do card
        seekOpacidade.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) = salvar()
        })

        // Tudo o que é item do card salva e já aparece aplicado.
        listOf(
            chkEmpilhar, chkValorKm, chkValorHora, chkValorMinuto, chkValoresTotais,
            chkVeredito, chkEnderecos, chkCustoTotal, chkLucro, chkMeta, chkTrajeto, chkReler
        ).forEach { chk -> chk.setOnCheckedChangeListener { _, _ -> salvar() } }

        findViewById<TextView>(R.id.btnSalvar).setOnClickListener {
            salvar()
            setResult(RESULT_OK)
            finish()
        }

        findViewById<TextView>(R.id.btnVoltar).setOnClickListener { finish() }
    }

    /**
     * O diagnóstico atualiza sozinho enquanto a tela está aberta: além da última
     * linha gravada, mostra o que a leitura automática está fazendo AGORA (esperando
     * o intervalo, card na tela, app fora da lista…). É a linha que diz o que travou.
     */
    private val handlerDiag = android.os.Handler(android.os.Looper.getMainLooper())
    private var tickDiag: Runnable? = null

    override fun onResume() {
        super.onResume()
        atualizarDiagnostico()
        val tick = object : Runnable {
            override fun run() {
                atualizarDiagnostico()
                handlerDiag.postDelayed(this, 1500L)
            }
        }
        tickDiag = tick
        handlerDiag.postDelayed(tick, 1500L)
    }

    override fun onPause() {
        super.onPause()
        tickDiag?.let { handlerDiag.removeCallbacks(it) }
        tickDiag = null
    }

    private fun atualizarDiagnostico() {
        val s = Prefs.load(this)
        val inst = RideAccessibilityService.instance
        val agora = inst?.estadoAoVivo(s)
            ?: "serviço não conectado — ative em Ajustes › Acessibilidade"
        tvDiag.text = "Última leitura: ${AutoDiag.resumo(this)}\n\nAgora: $agora"
    }

    /** Salva tudo (preservando as outras seções) e aplica no card que está na tela. */
    private fun salvar() {
        if (carregando) return
        val novo = readForm()
        Prefs.save(this, novo)
        RideAccessibilityService.instance?.applySettings(novo)
    }

    private fun selecionarFonte(tipo: String) {
        tamanhoFonteSelecionado = tipo
        aplicarCorFonte(btnFontePequena, tipo == "pequena")
        aplicarCorFonte(btnFonteMedia, tipo == "media")
        aplicarCorFonte(btnFonteGrande, tipo == "grande")
        salvar()
    }

    /** Posição em que o card abre: topo nas três colunas ou no centro da tela. */
    private fun selecionarPosicao(opcao: String) {
        posicaoSelecionada = opcao
        aplicarCorFonte(btnPosTopoEsquerda, opcao == "topo-esquerda")
        aplicarCorFonte(btnPosTopoCentro, opcao == "topo")
        aplicarCorFonte(btnPosTopoDireita, opcao == "topo-direita")
        aplicarCorFonte(btnPosCentro, opcao == "centro")
        salvar()
    }

    private fun aplicarCorFonte(container: android.view.ViewGroup, selecionado: Boolean) {
        for (i in 0 until container.childCount) {
            val v = container.getChildAt(i)
            if (v is TextView) {
                v.setTextColor(resources.getColor(if (selecionado) R.color.orange else R.color.textDim, theme))
            }
        }
    }

    private fun ajustarValor(txt: TextView, delta: Double) {
        val atual = NumberParse.toDouble(txt.text.toString()) ?: 0.0
        val novo = (atual + delta).coerceAtLeast(0.0)
        txt.text = formatarNumero(novo)
        salvar()
    }

    private fun fillForm(s: Settings) {
        // Preencher a tela dispara os próprios listeners; sem esta trava o que já
        // estava salvo seria sobrescrito pelos valores do layout.
        carregando = true
        seekDuracao.progress = s.cardDuracaoSeg.coerceIn(1, 30) * 1000
        txtDuracaoValor.text = "${s.cardDuracaoSeg}s na tela"
        seekOpacidade.progress = s.cardOpacidade.coerceIn(0, 100)

        tamanhoFonteSelecionado = when (s.cardTamanhoFonte) {
            "pequena", "media", "grande" -> s.cardTamanhoFonte
            else -> "media"
        }
        selecionarFonte(tamanhoFonteSelecionado)

        selecionarPosicao(
            when (s.cardGravity) {
                "topo-esquerda", "topo", "topo-direita", "centro" -> s.cardGravity
                else -> "centro"
            }
        )

        chkAutoScan.isChecked = s.autoScan
        etAutoIntervalo.setText(s.autoIntervaloSeg.toString())
        chkModoDesktop.isChecked = s.modoDesktop
        chkPrintCorrida.isChecked = s.printCorrida

        chkEmpilhar.isChecked = s.cardEmpilhar
        chkValorKm.isChecked = s.cardKm
        chkValorHora.isChecked = s.cardHora
        chkValorMinuto.isChecked = s.cardMinuto
        chkValoresTotais.isChecked = s.cardResumo
        chkVeredito.isChecked = s.cardVeredito
        chkEnderecos.isChecked = s.cardEnderecos
        chkCustoTotal.isChecked = s.cardCustos
        chkLucro.isChecked = s.cardLucro
        chkMeta.isChecked = s.cardMeta
        chkTrajeto.isChecked = s.cardTrajeto
        chkReler.isChecked = s.cardReler

        // Limites do semáforo: são os mesmos que pintam o card (ColorRules).
        txtKmBom.text = formatarNumero(s.kmBoaDe)
        txtKmRuim.text = formatarNumero(s.kmRuimAte)
        txtHoraBom.text = formatarNumero(s.horaBoaDe)
        txtHoraRuim.text = formatarNumero(s.horaRuimAte)
        txtMinutoBom.text = formatarNumero(s.semaforoMinutoBom)
        txtMinutoRuim.text = formatarNumero(s.semaforoMinutoRuim)
        carregando = false
    }

    private fun formatarNumero(v: Double): String {
        val s = if (v == v.toInt().toDouble()) v.toInt().toString()
        else String.format(java.util.Locale.US, "%.2f", v)
        return s.replace('.', ',')
    }

    private fun num(txt: TextView, padrao: Double): Double =
        NumberParse.toDouble(txt.text.toString()) ?: padrao

    /**
     * Monta a partir das configurações já salvas: só o que esta tela cuida muda.
     * Começar de [Settings] aqui era o que apagava preço do litro, consumo, metas e
     * o intervalo da leitura automática toda vez que o motorista salvava o card.
     */
    private fun readForm(): Settings {
        val base = Prefs.load(this)
        val digitado = NumberParse.toDouble(etAutoIntervalo.text?.toString())?.toInt()
            ?: base.autoIntervaloSeg
        return base.copy(
        cardDuracaoSeg = (seekDuracao.progress / 1000).coerceIn(1, 30),
        autoScan = chkAutoScan.isChecked,
        autoIntervaloSeg = digitado.coerceIn(2, 120),
        modoDesktop = chkModoDesktop.isChecked,
        printCorrida = chkPrintCorrida.isChecked,
        cardGravity = posicaoSelecionada,
        cardOpacidade = seekOpacidade.progress.coerceIn(0, 100),
        cardTamanhoFonte = tamanhoFonteSelecionado,
        cardEmpilhar = chkEmpilhar.isChecked,
        cardKm = chkValorKm.isChecked,
        cardHora = chkValorHora.isChecked,
        cardMinuto = chkValorMinuto.isChecked,
        cardResumo = chkValoresTotais.isChecked,
        cardVeredito = chkVeredito.isChecked,
        cardEnderecos = chkEnderecos.isChecked,
        cardCustos = chkCustoTotal.isChecked,
        cardLucro = chkLucro.isChecked,
        cardMeta = chkMeta.isChecked,
        cardTrajeto = chkTrajeto.isChecked,
        cardReler = chkReler.isChecked,
        kmBoaDe = num(txtKmBom, Settings().kmBoaDe),
        kmRuimAte = num(txtKmRuim, Settings().kmRuimAte),
        horaBoaDe = num(txtHoraBom, Settings().horaBoaDe),
        horaRuimAte = num(txtHoraRuim, Settings().horaRuimAte),
        semaforoMinutoBom = num(txtMinutoBom, Settings().semaforoMinutoBom),
        semaforoMinutoRuim = num(txtMinutoRuim, Settings().semaforoMinutoRuim)
        )
    }
}
