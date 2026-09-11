package com.emergency.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.emergency.app.databinding.ActivitySettingsBinding
import com.emergency.app.model.AppTheme
import com.emergency.app.utils.PreferencesManager
import com.emergency.app.utils.ThemeHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = PreferencesManager(this)
        ThemeHelper.applyTheme(this, prefs.getTheme())
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadCurrentSettings()

        binding.btnSaveSettings.setOnClickListener { saveSettings() }
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun loadCurrentSettings() {
        when (prefs.getTheme()) {
            AppTheme.NORMAL    -> binding.rbNormal.isChecked    = true
            AppTheme.DARK      -> binding.rbDark.isChecked      = true
            AppTheme.DALTONISM -> binding.rbDaltonism.isChecked = true
        }
        binding.etVoiceKeyword.setText(prefs.getVoiceKeyword())
    }

    private fun saveSettings() {
        val newTheme = when {
            binding.rbDark.isChecked      -> AppTheme.DARK
            binding.rbDaltonism.isChecked -> AppTheme.DALTONISM
            else                          -> AppTheme.NORMAL
        }

        // Salva tema e palavra-chave
        prefs.saveTheme(newTheme)
        val keyword = binding.etVoiceKeyword.text.toString().trim()
        if (keyword.isNotEmpty()) prefs.saveVoiceKeyword(keyword)

        Toast.makeText(this, "✅ Configurações salvas!", Toast.LENGTH_SHORT).show()

        // Reinicia a pilha inteira de Activities para o tema novo ser aplicado em tudo
        // FLAG_ACTIVITY_NEW_TASK + CLEAR_TASK recria MainActivity do zero com o novo tema
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
