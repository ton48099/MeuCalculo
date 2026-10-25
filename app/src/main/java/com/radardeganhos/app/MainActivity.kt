package com.radardeganhos.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.provider.Settings.Secure as SecureSettings
import android.widget.EditText
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
    private lateinit var swAuto: SwitchMaterial
    private lateinit var tvStatus: TextView
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
        swAuto = findViewById(R.id.swAuto)
        tvStatus = findViewById(R.id.tvStatus)
        tvResultado = findViewById(R.id.tvResultado)
        etTextoLivre = findViewById(R.id.etTextoLivre)

        fillForm(Prefs.load(this))

        findViewById<TextView>(R.id.btnSalvar).setOnClickListener {
            Prefs.save(this, readForm())
            tvResultado.text = "Configurações salvas. Custo por km: ${Report.money(readForm().custoKm)}."
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
        swAuto.isChecked = s.autoScan
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
            esperaMin = v(etEspera, d.esperaMin),
            retornoPct = v(etRetorno, d.retornoPct),
            autoScan = swAuto.isChecked
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
        tvResultado.text = Report.detalhado(Calculator.analyze(card, settings), settings)
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
