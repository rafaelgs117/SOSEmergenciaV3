package com.emergency.app.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.emergency.app.databinding.ActivityRegisterUserBinding
import com.emergency.app.model.UserProfile
import com.emergency.app.utils.PreferencesManager
import com.emergency.app.utils.ThemeHelper
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class RegisterUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterUserBinding
    private lateinit var prefs: PreferencesManager

    // true = veio do botão "Editar meus dados" (já tem cadastro)
    // false = primeiro acesso (cadastro inicial)
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = PreferencesManager(this)
        ThemeHelper.applyTheme(this, prefs.getTheme())
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isEditMode = intent.getBooleanExtra("EDIT_MODE", false)

        // Cadastro inicial já completo e não está em edição → vai direto à tela principal
        if (prefs.isRegistrationComplete() && !isEditMode) {
            goToMain()
            return
        }

        setupBloodTypeDropdown()
        setupBirthDateMask()

        // Se está editando, pré-preenche os campos com os dados atuais
        if (isEditMode) {
            prefillFields()
            adjustUiForEditMode()
        }

        binding.btnNext.setOnClickListener { validateAndProceed() }
    }

    // ── Pré-preenchimento ─────────────────────────────────────────────────────

    private fun prefillFields() {
        val profile = prefs.getUserProfile() ?: return
        binding.etFullName.setText(profile.fullName)
        binding.etAddress.setText(profile.address)
        binding.actvBloodType.setText(profile.bloodType, false) // false = não filtra o dropdown
        binding.etBirthDate.setText(profile.birthDate)
    }

    private fun adjustUiForEditMode() {
        // Muda título e subtítulo para refletir modo de edição
        binding.tvTitle.text    = "Editar Dados Pessoais"
        binding.tvSubtitle.text = "Altere as informações e salve"
        // Muda texto do botão: não vai para contatos, volta para a tela principal
        binding.btnNext.text = "💾 Salvar alterações"
    }

    // ── Dropdown tipo sanguíneo ───────────────────────────────────────────────

    private fun setupBloodTypeDropdown() {
        val types = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
        binding.actvBloodType.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, types)
        )
    }

    // ── Máscara DD/MM/AAAA ────────────────────────────────────────────────────

    private fun setupBirthDateMask() {
        binding.etBirthDate.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return
                isFormatting = true
                val digits = s.toString().filter { it.isDigit() }.take(8)
                val formatted = buildString {
                    digits.forEachIndexed { i, c ->
                        if (i == 2 || i == 4) append('/')
                        append(c)
                    }
                }
                s.replace(0, s.length, formatted)
                isFormatting = false
            }
        })
    }

    // ── Validação e salvamento ────────────────────────────────────────────────

    private fun validateAndProceed() {
        val name      = binding.etFullName.text.toString().trim()
        val address   = binding.etAddress.text.toString().trim()
        val bloodType = binding.actvBloodType.text.toString().trim()
        val birthDate = binding.etBirthDate.text.toString().trim()

        when {
            name.isEmpty()          -> toast("Informe o nome completo")
            address.isEmpty()       -> toast("Informe o endereço")
            bloodType.isEmpty()     -> toast("Selecione o tipo sanguíneo")
            birthDate.length < 10   -> toast("Informe a data completa (DD/MM/AAAA)")
            !isValidDate(birthDate) -> toast("Data inválida. Use DD/MM/AAAA")
            !isPastDate(birthDate)  -> toast("A data de nascimento deve ser no passado")
            else -> {
                val profile = UserProfile(
                    fullName  = name,
                    address   = address,
                    bloodType = bloodType,
                    birthDate = birthDate
                )
                prefs.saveUserProfile(profile)

                if (isEditMode) {
                    // Edição: salva e volta para a tela principal sem tocar nos contatos
                    toast("✅ Dados atualizados! Idade: ${profile.ageLabel}")
                    goToMain()
                } else {
                    // Cadastro inicial: avança para cadastro de contatos
                    toast("Perfil salvo! Idade calculada: ${profile.ageLabel}")
                    startActivity(Intent(this, RegisterContactsActivity::class.java))
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isValidDate(date: String): Boolean = try {
        LocalDate.parse(date, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        true
    } catch (e: Exception) { false }

    private fun isPastDate(date: String): Boolean = try {
        val born = LocalDate.parse(date, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        born.isBefore(LocalDate.now())
    } catch (e: Exception) { false }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            // Evita empilhar MainActivity — volta para a existente
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }
}
