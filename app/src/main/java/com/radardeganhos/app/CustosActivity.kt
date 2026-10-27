package com.radardeganhos.app

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class CustosActivity : AppCompatActivity() {

    private lateinit var etPreco: EditText
    private lateinit var etConsumo: EditText
    private lateinit var etManut: EditText
    private lateinit var etComissao: EditText
    private lateinit var etFixoHora: EditText
    private lateinit var etEspera: EditText
    private lateinit var etRetorno: EditText
    private lateinit var etMetaKm: EditText
    private lateinit var etMetaHora: EditText
    private lateinit var tvResumo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custos)

        etPreco = findViewById(R.id.etPreco)
        etConsumo = findViewById(R.id.etConsumo)
        etManut = findViewById(R.id.etManut)
        etComissao = findViewById(R.id.etComissao)
        etFixoHora = findViewById(R.id.etFixoHora)
        etEspera = findViewById(R.id.etEspera)
        etRetorno = findViewById(R.id.etRetorno)
        etMetaKm = findViewById(R.id.etMetaKm)
        etMetaHora = findViewById(R.id.etMetaHora)
        tvResumo = findViewById(R.id.tvResumoCusto)

        val s = Prefs.load(this)
        fillForm(s)
        atualizarResumo()

        // Mexer no que define o custo já recalcula o resumo no topo.
        listOf(etPreco, etConsumo, etManut, etMetaKm, etMetaHora).forEach { et ->
            et.setOnFocusChangeListener { _, temFoco -> if (!temFoco) atualizarResumo() }
        }

        findViewById<android.widget.TextView>(R.id.btnSalvar).setOnClickListener {
            val novo = readForm()
            Prefs.save(this, novo)
            RideAccessibilityService.instance?.applySettings(novo)
            setResult(RESULT_OK)
            finish()
        }

        findViewById<android.widget.TextView>(R.id.btnVoltar).setOnClickListener { finish() }
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
    }

    /**
     * Parte das configurações já salvas e mexe só no que esta tela cuida.
     * Montar a partir de [Settings] (padrões) aqui apagava tudo que o motorista
     * configurou no card flutuante toda vez que ele salvava os custos.
     */
    private fun readForm(): Settings {
        fun v(et: EditText, def: Double): Double = NumberParse.toDouble(et.text?.toString()) ?: def
        val salvo = Prefs.load(this)
        return salvo.copy(
            precoLitro = v(etPreco, salvo.precoLitro),
            consumo = v(etConsumo, salvo.consumo),
            manutencaoKm = v(etManut, salvo.manutencaoKm),
            comissaoPct = v(etComissao, salvo.comissaoPct),
            custoFixoHora = v(etFixoHora, salvo.custoFixoHora),
            metaKm = v(etMetaKm, salvo.metaKm),
            metaHora = v(etMetaHora, salvo.metaHora),
            esperaMin = v(etEspera, salvo.esperaMin),
            retornoPct = v(etRetorno, salvo.retornoPct)
            // A leitura automática agora é configurada em "Card e Cores".
        )
    }

    /**
     * O número que interessa nesta tela, calculado com o que está sendo digitado:
     * quanto cada quilômetro do seu carro custa, desdobrado.
     */
    private fun atualizarResumo() {
        val s = readForm()
        val combustivel = if (s.consumo > 0) s.precoLitro / s.consumo else 0.0
        tvResumo.text = "R$ ${br(s.custoKm)} por km rodado\n" +
            "combustível R$ ${br(combustivel)}/km · manutenção R$ ${br(s.manutencaoKm)}/km\n" +
            "sua meta: R$ ${br(s.metaKm)}/km líquido e R$ ${br(s.metaHora)}/h líquido"
    }

    private fun br(v: Double): String =
        String.format(java.util.Locale.US, "%.2f", v).replace('.', ',')

    private fun plain(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else String.format(java.util.Locale.US, "%.2f", v)
}