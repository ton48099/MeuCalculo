package com.radardeganhos.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

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

    @SuppressLint("SetTextI18n")
    private fun bind(item: View, r: RideRecord, s: Settings) {
        item.findViewById<TextView>(R.id.tvItemValor).text = Report.money(r.valor)

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

        // Botões embarque/desembarque abrem no Google Maps
        val btnOrigem = item.findViewById<MaterialButton>(R.id.btnMapOrigem)
        val btnDestino = item.findViewById<MaterialButton>(R.id.btnMapDestino)

        val temOrigem = r.origem?.isNotBlank() == true
        val temDestino = r.destino?.isNotBlank() == true
        btnOrigem.visibility = if (temOrigem) View.VISIBLE else View.GONE
        btnDestino.visibility = if (temDestino) View.VISIBLE else View.GONE

        btnOrigem.setOnClickListener { abrirMapa(r.origem ?: "") }
        btnDestino.setOnClickListener { abrirMapa(r.destino ?: "") }

        item.findViewById<TextView>(R.id.tvItemLucro).text =
            "Lucro líquido estimado: ${Report.money(r.liquido)}"

        // Print da tela: miniatura no item, tela cheia ao tocar.
        val img = item.findViewById<ImageView>(R.id.imgPrint)
        val thumb = r.print?.let { Prints.carregar(this, it, 480) }
        if (thumb != null) {
            img.setImageBitmap(thumb)
            img.visibility = View.VISIBLE
            img.setOnClickListener { r.print?.let { mostrarPrint(it) } }
        } else {
            img.setImageDrawable(null)
            img.visibility = View.GONE
        }
    }

    /** Print em tela cheia. */
    private fun mostrarPrint(nome: String) {
        val bmp = Prints.carregar(this, nome, 1440) ?: return
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(resources.getColor(R.color.bg, theme))
            setPadding(0, 0, 0, 0)
            setImageBitmap(bmp)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Print da corrida")
            .setView(img)
            .setPositiveButton("Fechar", null)
            .setOnDismissListener { bmp.recycle() }
            .create()
        dialog.show()
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private fun abrirMapa(endereco: String) {
        val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(endereco)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")
        if (intent.resolveActivity(packageManager) == null) {
            intent.setPackage(null) // fallback: abre no navegador
        }
        startActivity(intent)
    }
}