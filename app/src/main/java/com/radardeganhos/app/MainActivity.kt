package com.radardeganhos.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.provider.Settings.Secure as SecureSettings
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var etPreco: EditText
    private lateinit var etConsumo: EditText
    private lateinit var etManut: EditText
    private lateinit var etComissao: EditText
    private lateinit var etFixoHora: EditText
    private lateinit var etEspera: EditText
    private lateinit var etRetorno: EditText
    private lateinit var etMetaKm: EditText
    private lateinit var etMetaHora: EditText
    private lateinit var etHoraRuim: EditText
    private lateinit var etHoraBoa: EditText
    private lateinit var etKmRuim: EditText
    private lateinit var etKmBoa: EditText
    private lateinit var swAuto: SwitchMaterial
    private lateinit var etCardDuracao: EditText
    private lateinit var etAutoIntervalo: EditText
    private lateinit var rgEscala: RadioGroup
    private lateinit var rbPequeno: RadioButton
    private lateinit var rbNormal: RadioButton
    private lateinit var rbGrande: RadioButton
    private lateinit var rbExtra: RadioButton
    private lateinit var swVeredito: SwitchMaterial
    private lateinit var swHora: SwitchMaterial
    private lateinit var swKm: SwitchMaterial
    private lateinit var swResumo: SwitchMaterial
    private lateinit var swLucro: SwitchMaterial
    private lateinit var swCustos: SwitchMaterial
    private lateinit var swTrajeto: SwitchMaterial
    private lateinit var swMeta: SwitchMaterial
    private lateinit var swReler: SwitchMaterial
    private lateinit var tvStatus: TextView
    private lateinit var tvHistoricoContagem: TextView
    private lateinit var tvResultado: TextView
    private lateinit var etTextoLivre: EditText

    private lateinit var screenReader: ScreenReader

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) analyzeImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        screenReader = ScreenReader()

        etPreco = findViewById(R.id.etPreco)
        etConsumo = findViewById(R.id.etConsumo)
        etManut = findViewById(R.id.etManut)
        etComissao = findViewById(R.id.etComissao)
        etFixoHora = findViewById(R.id.etFixoHora)
        etEspera = findViewById(R.id.etEspera)
        etRetorno = findViewById(R.id.etRetorno)
        etMetaKm = findViewById(R.id.etMetaKm)
        etMetaHora = findViewById(R.id.etMetaHora)
        etHoraRuim = findViewById(R.id.etHoraRuim)
        etHoraBoa = findViewById(R.id.etHoraBoa)
        etKmRuim = findViewById(R.id.etKmRuim)
        etKmBoa = findViewById(R.id.etKmBoa)
        swAuto = findViewById(R.id.swAuto)
        etCardDuracao = findViewById(R.id.etCardDuracao)
        etAutoIntervalo = findViewById(R.id.etAutoIntervalo)
        rgEscala = findViewById(R.id.rgEscala)
        rbPequeno = findViewById(R.id.rbPequeno)
        rbNormal = findViewById(R.id.rbNormal)
        rbGrande = findViewById(R.id.rbGrande)
        rbExtra = findViewById(R.id.rbExtra)
        swVeredito = findViewById(R.id.swVeredito)
        swHora = findViewById(R.id.swHora)
        swKm = findViewById(R.id.swKm)
        swResumo = findViewById(R.id.swResumo)
        swLucro = findViewById(R.id.swLucro)
        swCustos = findViewById(R.id.swCustos)
        swTrajeto = findViewById(R.id.swTrajeto)
        swMeta = findViewById(R.id.swMeta)
        swReler = findViewById(R.id.swReler)
        tvStatus = findViewById(R.id.tvStatus)
        tvHistoricoContagem = findViewById(R.id.tvHistoricoContagem)
        tvResultado = findViewById(R.id.tvResultado)
        etTextoLivre = findViewById(R.id.etTextoLivre)

        fillForm(Prefs.load(this))

        findViewById<TextView>(R.id.btnSalvar).setOnClickListener { salvar() }
        findViewById<TextView>(R.id.btnSalvarCard).setOnClickListener { salvar() }
        findViewById<TextView>(R.id.btnSalvarCores).setOnClickListener { salvar() }

        findViewById<TextView>(R.id.btnAbrirHistorico).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        findViewById<TextView>(R.id.btnAcessibilidade).setOnClickListener {
            runCatching { startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) }
                .onFailure { tvStatus.text = "Abra Ajustes › Acessibilidade manualmente e ative o Radar de Ganhos." }
        }

        findViewById<TextView>(R.id.btnGaleria).setOnClickListener { pickImage.launch("image/*") }

        findViewById<TextView>(R.id.btnCalcularTexto).setOnClickListener {
            val texto = etTextoLivre.text?.toString().orEmpty()
            if (texto.isBlank()) {
                tvResultado.text = "Cole o texto do card primeiro."
                return@setOnClickListener
            }
            showAnalysis(texto)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        atualizarHistorico()
    }

    private fun atualizarHistorico() {
        val n = RideHistory.load(this).size
        tvHistoricoContagem.text = if (n == 0) {
            "Nenhuma corrida registrada ainda. Cada card analisado entra aqui com valor, R$/km, R$/hora e trajeto."
        } else {
            "$n corrida(s) registrada(s) neste aparelho. Cada uma com valor, R$/km, R$/hora, busca e desembarque."
        }
    }

    private fun updateStatus() {
        val ativo = isAccessibilityEnabled()
        tvStatus.text = if (ativo) {
            "Leitura de tela ATIVA ✓  ·  toque no botão R$ por cima do Uber/99, de uma foto ou de um post."
        } else {
            "Serviço desativado. Ative para o botão flutuante R$ funcionar em qualquer tela."
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        if (RideAccessibilityService.instance != null) return true
        val enabled = SecureSettings.getString(
            contentResolver,
            SecureSettings.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val alvo = "$packageName/${RideAccessibilityService::class.java.name}"
        return enabled.split(':').any { it.equals(alvo, ignoreCase = true) }
    }

    /** Salva tudo e já aplica no card que estiver na tela. */
    private fun salvar() {
        val s = readForm()
        Prefs.save(this, s)
        RideAccessibilityService.instance?.applySettings(s)
        tvResultado.text = "Configurações salvas. Custo por km: ${Report.money(s.custoKm)}." +
            if (s.cardDuracaoSeg == 0) " Card fica na tela até você fechar."
            else " Card fecha sozinho em ${s.cardDuracaoSeg}s."
    }

    private fun fillForm(s: Settings) {
        etPreco.setText(plain(s.precoLitro))
        etConsumo.setText(plain(s.consumo))
        etManut.setText(plain(s.manutencaoKm))
        etComissao.setText(plain(s.comissaoPct))
        etFixoHora.setText(plain(s.custoFixoHora))
        etEspera.setText(plain(s.esperaMin))
        etRetorno.setText(plain(s.retornoPct))
        etMetaKm.setText(plain(s.metaKm))
        etMetaHora.setText(plain(s.metaHora))
        etHoraRuim.setText(plain(s.horaRuimAte))
        etHoraBoa.setText(plain(s.horaBoaDe))
        etKmRuim.setText(plain(s.kmRuimAte))
        etKmBoa.setText(plain(s.kmBoaDe))
        swAuto.isChecked = s.autoScan
        etCardDuracao.setText(s.cardDuracaoSeg.toString())
        etAutoIntervalo.setText(s.autoIntervaloSeg.toString())
        rgEscala.check(
            when {
                s.cardEscala <= 0.9f -> R.id.rbPequeno
                s.cardEscala >= 1.25f -> R.id.rbExtra
                s.cardEscala >= 1.1f -> R.id.rbGrande
                else -> R.id.rbNormal
            }
        )
        swVeredito.isChecked = s.cardVeredito
        swHora.isChecked = s.cardHora
        swKm.isChecked = s.cardKm
        swResumo.isChecked = s.cardResumo
        swLucro.isChecked = s.cardLucro
        swCustos.isChecked = s.cardCustos
        swTrajeto.isChecked = s.cardTrajeto
        swMeta.isChecked = s.cardMeta
        swReler.isChecked = s.cardReler
    }

    private fun escalaSelecionada(): Float = when (rgEscala.checkedRadioButtonId) {
        R.id.rbPequeno -> 0.85f
        R.id.rbGrande -> 1.15f
        R.id.rbExtra -> 1.3f
        else -> 1.0f
    }

    private fun plain(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else String.format(java.util.Locale.US, "%.2f", v)

    private fun readForm(): Settings {
        val d = Settings()
        fun v(et: EditText, def: Double): Double = NumberParse.toDouble(et.text?.toString()) ?: def
        return Settings(
            precoLitro = v(etPreco, d.precoLitro),
            consumo = v(etConsumo, d.consumo),
            manutencaoKm = v(etManut, d.manutencaoKm),
            comissaoPct = v(etComissao, d.comissaoPct),
            custoFixoHora = v(etFixoHora, d.custoFixoHora),
            metaKm = v(etMetaKm, d.metaKm),
            metaHora = v(etMetaHora, d.metaHora),
            horaRuimAte = v(etHoraRuim, d.horaRuimAte),
            horaBoaDe = v(etHoraBoa, d.horaBoaDe),
            kmRuimAte = v(etKmRuim, d.kmRuimAte),
            kmBoaDe = v(etKmBoa, d.kmBoaDe),
            esperaMin = v(etEspera, d.esperaMin),
            retornoPct = v(etRetorno, d.retornoPct),
            autoScan = swAuto.isChecked,
            autoIntervaloSeg = v(etAutoIntervalo, d.autoIntervaloSeg.toDouble()).toInt().coerceIn(2, 120),
            cardDuracaoSeg = v(etCardDuracao, d.cardDuracaoSeg.toDouble()).toInt().coerceIn(0, 120),
            cardEscala = escalaSelecionada(),
            cardVeredito = swVeredito.isChecked,
            cardHora = swHora.isChecked,
            cardKm = swKm.isChecked,
            cardResumo = swResumo.isChecked,
            cardLucro = swLucro.isChecked,
            cardCustos = swCustos.isChecked,
            cardTrajeto = swTrajeto.isChecked,
            cardMeta = swMeta.isChecked,
            cardReler = swReler.isChecked
        )
    }

    /** Analisa um texto de card (colado ou vindo do OCR). */
    private fun showAnalysis(texto: String) {
        val card = RideParser.parse(texto)
        if (card == null) {
            tvResultado.text = "Não identifiquei um card de corrida nesse texto.\n\nTexto lido:\n" + texto.take(500)
            return
        }
        val settings = readForm()
        val analysis = Calculator.analyze(card, settings)
        RideHistory.record(this, analysis)
        atualizarHistorico()
        tvResultado.text = Report.detalhado(analysis, settings)
    }

    private fun analyzeImage(uri: Uri) {
        tvResultado.text = "Lendo imagem..."
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { loadBitmap(uri) }
            if (bitmap == null) {
                tvResultado.text = "Não consegui abrir essa imagem."
                return@launch
            }
            val texto = try {
                screenReader.read(bitmap)
            } catch (t: Throwable) {
                tvResultado.text = "Falha no OCR: ${t.message}"
                bitmap.recycle()
                return@launch
            }
            bitmap.recycle()
            showAnalysis(texto)
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? = try {
        val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
        var bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (bmp != null && bmp.height > 2400) {
            bmp = bmp.scaledForOcr(2400)
        }
        bmp
    } catch (t: Throwable) {
        null
    }

    override fun onDestroy() {
        screenReader.close()
        super.onDestroy()
    }
}
