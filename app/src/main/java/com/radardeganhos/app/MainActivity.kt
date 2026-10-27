package com.radardeganhos.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.provider.Settings.Secure as SecureSettings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Tela principal: liga/desliga a leitura de tela e abre as configurações.
 *
 * Tudo o que é configuração (custos, card, cores e leitura automática) mora nas
 * telas próprias; daqui só saem o botão único de leitura, os atalhos e o status.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)

        // Botão único: liga e desliga a leitura sem sair do app.
        findViewById<TextView>(R.id.btnToggleLeitura).setOnClickListener {
            val inst = RideAccessibilityService.instance
            when {
                inst != null && inst.ativo -> inst.desativar()
                inst != null -> inst.ativar()
                else -> runCatching {
                    startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
                }.onFailure {
                    tvStatus.text = "Abra Ajustes › Acessibilidade › Apps instalados › Radar de Ganhos › Ativar."
                }
            }
            atualizarBotao()
        }

        // Custos do carro
        findViewById<TextView>(R.id.btnCustos).setOnClickListener {
            startActivity(Intent(this, CustosActivity::class.java))
        }

        // Card, cores e leitura automática
        findViewById<TextView>(R.id.btnCardConfig).setOnClickListener {
            startActivity(Intent(this, CardConfigActivity::class.java))
        }

        // Bottom Navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navHistorico -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    true
                }
                else -> true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        atualizarBotao()
    }

    private fun atualizarBotao() {
        val inst = RideAccessibilityService.instance
        val ligado = inst != null && inst.ativo
        val btn = findViewById<TextView>(R.id.btnToggleLeitura)

        when {
            ligado -> {
                btn.text = "⏻ DESLIGAR LEITURA E REMOVER BOTÃO FLUTUANTE"
                btn.setBackgroundResource(R.drawable.bg_btn_danger)
                tvStatus.text = "Leitura de tela ATIVA ✓ · o botão R$ fica por cima de qualquer app."
            }
            inst != null -> {
                btn.text = "▶ ATIVAR LEITURA DE TELA"
                btn.setBackgroundResource(R.drawable.bg_btn_primary)
                tvStatus.text = "Serviço ligado, mas a leitura está desligada. Toque em ATIVAR."
            }
            else -> {
                btn.text = "▶ ATIVAR LEITURA DE TELA"
                btn.setBackgroundResource(R.drawable.bg_btn_primary)
                tvStatus.text = if (servicoAtivoNoSistema()) {
                    "Acessibilidade ligada: o botão R$ aparece em instantes. Se não aparecer, toque em ATIVAR."
                } else {
                    "Serviço desativado. Toque em ATIVAR LEITURA DE TELA."
                }
            }
        }
    }

    /** O serviço já está habilitado nos ajustes do Android (mesmo sem estar conectado). */
    private fun servicoAtivoNoSistema(): Boolean {
        val habilitados = SecureSettings.getString(
            contentResolver,
            SecureSettings.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val alvo = "$packageName/${RideAccessibilityService::class.java.name}"
        return habilitados.split(':').any { it.equals(alvo, ignoreCase = true) }
    }
}
