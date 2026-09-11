package com.emergency.app.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.emergency.app.databinding.ActivityRegisterContactsBinding
import com.emergency.app.model.EmergencyContact
import com.emergency.app.utils.PreferencesManager
import com.emergency.app.utils.ThemeHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class RegisterContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterContactsBinding
    private lateinit var prefs: PreferencesManager

    // Lista dinâmica de grupos de campos (nome, parentesco, telefone)
    data class ContactFields(
        val tilName: TextInputLayout,
        val tilRelationship: TextInputLayout,
        val tilPhone: TextInputLayout,
        val etName: TextInputEditText,
        val etRelationship: TextInputEditText,
        val etPhone: TextInputEditText,
    )

    private val contactFields = mutableListOf<ContactFields>()

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = PreferencesManager(this)
        ThemeHelper.applyTheme(this, prefs.getTheme())
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Pré-carrega contatos já cadastrados ou cria o primeiro bloco
        val savedContacts = prefs.getAllContacts()
        if (savedContacts.isEmpty()) {
            addContactBlock()
        } else {
            savedContacts.forEach { addContactBlock(it) }
        }

        binding.btnAddContact.setOnClickListener {
            if (contactFields.size < 5) {
                addContactBlock()
                updateAddButtonState()
            } else {
                Toast.makeText(this, "Máximo de 5 contatos atingido.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSave.setOnClickListener { validateAndSave() }
    }

    private fun addContactBlock(prefill: EmergencyContact? = null) {
        val index = contactFields.size + 1
        val container = binding.contactsContainer

        // Cria os campos programaticamente para suportar quantidade dinâmica
        val tilName = TextInputLayout(this, null,
            com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = "Nome completo"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = 12.dp }
        }
        val etName = TextInputEditText(this).apply {
            inputType = android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS or
                        android.text.InputType.TYPE_CLASS_TEXT
        }
        tilName.addView(etName)

        val tilRel = TextInputLayout(this, null,
            com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = "Parentesco (Mãe, Pai, Amigo...)"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = 10.dp }
        }
        val etRel = TextInputEditText(this).apply {
            inputType = android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                        android.text.InputType.TYPE_CLASS_TEXT
        }
        tilRel.addView(etRel)

        val tilPhone = TextInputLayout(this, null,
            com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = "WhatsApp com DDD"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = 10.dp }
        }
        val etPhone = TextInputEditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        setupPhoneMask(etPhone)
        tilPhone.addView(etPhone)

        // Card de agrupamento
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            radius = 16.dp.toFloat()
            cardElevation = 4f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = 14.dp }
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 16.dp, 16.dp, 16.dp)
        }

        // Header do card
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val tvTitle = android.widget.TextView(this).apply {
            text = "Contato $index"
            setTextColor(resources.getColor(com.emergency.app.R.color.sos_red, theme))
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Botão remover (só aparece se não for o único contato)
        val btnRemove = MaterialButton(this,null,
            com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Remover"
            setTextColor(resources.getColor(android.R.color.holo_red_light, theme))
            visibility = if (contactFields.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            setOnClickListener {
                binding.contactsContainer.removeView(card)
                contactFields.removeAt(index - 1)
                // Re-numera os títulos
                renumberCards()
                updateAddButtonState()
            }
        }

        header.addView(tvTitle)
        header.addView(btnRemove)
        inner.addView(header)
        inner.addView(tilName)
        inner.addView(tilRel)
        inner.addView(tilPhone)
        card.addView(inner)
        container.addView(card)

        val fields = ContactFields(tilName, tilRel, tilPhone, etName, etRel, etPhone)
        contactFields.add(fields)

        // Preenche se vier de dados salvos
        prefill?.let {
            etName.setText(it.name)
            etRel.setText(it.relationship)
            etPhone.setText(it.phone)
        }

        updateAddButtonState()
    }

    private fun renumberCards() {
        val container = binding.contactsContainer
        for (i in 0 until container.childCount) {
            val card = container.getChildAt(i) as? com.google.android.material.card.MaterialCardView ?: continue
            val inner = card.getChildAt(0) as? LinearLayout ?: continue
            val header = inner.getChildAt(0) as? LinearLayout ?: continue
            val tv = header.getChildAt(0) as? android.widget.TextView ?: continue
            tv.text = "Contato ${i + 1}"
        }
    }

    private fun updateAddButtonState() {
        binding.btnAddContact.isEnabled = contactFields.size < 5
        binding.tvContactCount.text = "${contactFields.size}/5 contato(s) cadastrado(s)"
    }

    private fun setupPhoneMask(et: TextInputEditText) {
        et.addTextChangedListener(object : TextWatcher {
            private var fmt = false
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (fmt || s == null) return
                fmt = true
                val d = s.toString().filter { it.isDigit() }.take(11)
                val r = buildString {
                    d.forEachIndexed { i, c ->
                        when (i) { 0 -> append("("); 2 -> append(") "); 7 -> append("-") }
                        append(c)
                    }
                }
                s.replace(0, s.length, r)
                fmt = false
            }
        })
    }

    private fun validateAndSave() {
        if (contactFields.isEmpty()) {
            Toast.makeText(this, "Adicione ao menos 1 contato.", Toast.LENGTH_SHORT).show()
            return
        }
        val contacts = mutableListOf<EmergencyContact>()
        for ((i, f) in contactFields.withIndex()) {
            val n = f.etName.text.toString().trim()
            val r = f.etRelationship.text.toString().trim()
            val p = f.etPhone.text.toString().trim()
            when {
                n.isEmpty() -> { Toast.makeText(this, "Nome do contato ${i+1} obrigatório.", Toast.LENGTH_SHORT).show(); return }
                r.isEmpty() -> { Toast.makeText(this, "Parentesco do contato ${i+1} obrigatório.", Toast.LENGTH_SHORT).show(); return }
                p.length < 14 -> { Toast.makeText(this, "Telefone do contato ${i+1} incompleto.", Toast.LENGTH_SHORT).show(); return }
            }
            contacts.add(EmergencyContact(n, r, p))
        }

        // Limpa contatos antigos e salva os novos
        for (i in 1..5) prefs.deleteContact(i)
        contacts.forEachIndexed { i, c -> prefs.saveContact(i + 1, c) }
        prefs.saveContactCount(contacts.size)
        prefs.setRegistrationComplete()

        Toast.makeText(this, "✅ ${contacts.size} contato(s) salvo(s)!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java))
        finishAffinity()
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}
