package com.radardeganhos.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lista das corridas já analisadas: valor, R$/km, R$/hora e o trajeto
 * (endereço de busca e de desembarque). Tudo local, sem servidor.
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var lista: LinearLayout
    private lateinit var resumo: TextView
    private lateinit var vazio: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        lista = findViewById(R.id.listaHistorico)
        resumo = findViewById(R.id.tvHistoricoResumo)
        vazio = findViewById(R.id.tvHistoricoVazio)

        findViewById<TextView>(R.id.btnVoltar).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnLimparHistorico).setOnClickListener { confirmarLimpeza() }

        carregar()
    }

    override fun onResume() {
        super.onResume()
        carregar()
    }

    private fun carregar() {
        val corridas = RideHistory.load(this)
        val settings = Prefs.load(this)
        lista.removeAllViews()
        vazio.visibility = if (corridas.isEmpty()) View.VISIBLE else View.GONE
        resumo.text = resumoDe(corridas)

        val inflater = LayoutInflater.from(this)
        corridas.forEach { r ->
            val item = inflater.inflate(R.layout.item_history, lista, false)
            bind(item, r, settings)
            lista.addView(item)
        }
    }

    private fun confirmarLimpeza() {
        AlertDialog.Builder(this)
            .setTitle("Limpar histórico?")
            .setMessage("As corridas registradas neste aparelho serão apagadas. Isso não cancela nada nos apps.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Limpar") { _, _ ->
                RideHistory.clear(this)
                carregar()
            }
            .show()
    }

    private fun resumoDe(corridas: List<RideRecord>): String {
        if (corridas.isEmpty()) return "Nenhuma corrida registrada ainda."
        val total = corridas.sumOf { it.valor }
        val hora = corridas.sumOf { it.brutoHora } / corridas.size
        val km = corridas.sumOf { it.brutoKm } / corridas.size
        return "${corridas.size} corrida(s) · ${Report.money(total)} somados\n" +
            "média ${Report.money(hora)}/h · ${Report.money(km)}/km"
    }

    companion object {

        private val formato = SimpleDateFormat("dd/MM · HH:mm", Locale.getDefault())

        /** Preenche um item do histórico (separado da tela para poder ser testado). */
        @SuppressLint("SetTextI18n")
        fun bind(item: View, r: RideRecord, s: Settings) {
            item.findViewById<TextView>(R.id.tvItemValor).text = Report.money(r.valor)

            val app = r.app ?: "leitura"
            item.findViewById<TextView>(R.id.tvItemMeta).text =
                "$app · ${formato.format(Date(r.at))} · ${Report.km(r.km)}"

            item.findViewById<TextView>(R.id.tvItemHora).apply {
                text = "${Report.money(r.brutoHora)}/h"
                setTextColor(ColorRules.corHora(r.brutoHora, s))
            }
            item.findViewById<TextView>(R.id.tvItemKm).apply {
                text = "${Report.money(r.brutoKm)}/km"
                setTextColor(ColorRules.corKm(r.brutoKm, s))
            }

            item.findViewById<TextView>(R.id.tvItemOrigem).text =
                "Busca: ${r.origem ?: "não identificada"}"
            item.findViewById<TextView>(R.id.tvItemDestino).text =
                "Desembarque: ${r.destino ?: "não identificado"}"
            item.findViewById<TextView>(R.id.tvItemLucro).text =
                "Lucro líquido estimado: ${Report.money(r.liquido)}"
        }
    }
}
