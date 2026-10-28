package com.radardeganhos.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.provider.Settings.Secure as SecureSettings
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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

        // Recusa automática: liga/desliga sem entrar nas configurações.
        findViewById<TextView>(R.id.btnRecusa).setOnClickListener {
            val s = Prefs.load(this)
            val novo = s.copy(recusaAtiva = !s.recusaAtiva)
            Prefs.save(this, novo)
            RideAccessibilityService.instance?.applySettings(novo)
            atualizarRecusa()
        }

        // Mira para ensinar onde fica o X de cada app (celular e 2ª tela).
        findViewById<TextView>(R.id.btnEnsinaX).setOnClickListener { ensinarX() }

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
        atualizarRecusa()
    }

    /** Mostra no botão da tela principal se a recusa automática está ligada. */
    private fun atualizarRecusa() {
        val btn = findViewById<TextView>(R.id.btnRecusa)
        val s = Prefs.load(this)
        btn.text = if (s.recusaAtiva) {
            if (s.recusaSoAviso) "🚫 RECUSA SOZINHO: LIGADA (só avisando)" else "🚫 RECUSA SOZINHO: LIGADA"
        } else {
            "🚫 RECUSA SOZINHO: DESLIGADA"
        }
    }

    /** Mira para ensinar o X: escolhe o app e a tela, depois o serviço abre a mira. */
    private fun ensinarX() {
        val apps = arrayOf("Uber", "99")
        AlertDialog.Builder(this)
            .setTitle("Ensinar o X de qual app?")
            .setItems(apps) { _, qual -> escolherTelaDoX(apps[qual]) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun escolherTelaDoX(app: String) {
        val opcoes = arrayOf("Neste celular (a mira aparece em 5 s)", "Na 2ª tela / modo desktop")
        AlertDialog.Builder(this)
            .setTitle("Onde está o $app?")
            .setItems(opcoes) { _, escolha ->
                val inst = RideAccessibilityService.instance
                if (inst == null || !inst.ativo) {
                    tvStatus.text = "Ative a leitura de tela antes de ensinar o X."
                    return@setItems
                }
                val externo = escolha == 1
                if (!inst.iniciarCalibracaoDaTela(app, externo)) {
                    tvStatus.text = if (externo) {
                        "A 2ª tela não está disponível: ligue o modo desktop e conecte a tela."
                    } else {
                        "Não consegui abrir a mira. Ative a leitura de tela e tente de novo."
                    }
                } else {
                    tvStatus.text = if (externo) {
                        "Arraste a mira até o X do $app e toque em SALVAR."
                    } else {
                        "Abra o $app: a mira aparece em 5 segundos."
                    }
                }
            }
            .setNegativeButton("Voltar", null)
            .show()
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
