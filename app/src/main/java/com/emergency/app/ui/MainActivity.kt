package com.emergency.app.ui

import android.Manifest
import java.time.LocalDate
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telephony.SmsManager
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.emergency.app.databinding.ActivityMainBinding
import com.emergency.app.utils.PreferencesManager
import com.emergency.app.utils.ThemeHelper
import com.google.android.gms.location.*
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    // ── Permissões ────────────────────────────────────────────────────────────
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> checkSmsPermissionAndSend() }

    private val smsPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(this, "Sem permissão SMS. Só WhatsApp será aberto.", Toast.LENGTH_LONG).show()
        getLocationAndSendSOS()
    }

    private val audioPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceListening()
        else Toast.makeText(this, "Permissão de microfone negada.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = PreferencesManager(this)
        ThemeHelper.applyTheme(this, prefs.getTheme())
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        loadUserData()

        binding.btnSOS.setOnClickListener { confirmSendSOS() }

        binding.btnMic.setOnClickListener { toggleVoiceListening() }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnEditProfile.setOnClickListener {
            startActivity(Intent(this, RegisterContactsActivity::class.java))
        }

        binding.btnEditUser.setOnClickListener {
            // Abre edição SEM apagar dados — passa flag EDIT_MODE
            val intent = Intent(this, RegisterUserActivity::class.java).apply {
                putExtra("EDIT_MODE", true)
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        loadUserData() // Atualiza se voltou das configurações/contatos
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }

    // ── Dados ─────────────────────────────────────────────────────────────────
    private fun loadUserData() {
        prefs.getUserProfile()?.let {
            binding.tvUserName.text  = it.fullName
            binding.tvBloodType.text = "🩸 Tipo sanguíneo: ${it.bloodType}"
            binding.tvAge.text       = "🎂 Idade: ${it.ageLabel}"
            binding.tvBirthDate.text = "📅 Nascimento: ${it.birthDate}"
            binding.tvAddress.text   = "📍 ${it.address}"

            // Verifica se hoje é aniversário e exibe parabéns
            checkBirthday(it.birthDate, it.ageLabel)
        }
        val contacts = prefs.getAllContacts()
        binding.tvContactsInfo.text = if (contacts.isEmpty()) {
            "Nenhum contato cadastrado."
        } else {
            contacts.mapIndexed { i, c -> "${i+1}. ${c.name} (${c.relationship})  ${c.phone}" }
                    .joinToString("\n")
        }
        binding.tvVoiceKeyword.text = "🎙️ Palavra-chave: \"${prefs.getVoiceKeyword()}\""
    }

    // ── SOS ───────────────────────────────────────────────────────────────────
    private fun confirmSendSOS() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Enviar SOS?")
            .setMessage("Será enviado:\n\n📱 SMS automático\n💬 WhatsApp com localização")
            .setPositiveButton("ENVIAR AGORA") { _, _ -> checkLocationPermissionAndSend() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun checkLocationPermissionAndSend() {
        val fine   = Manifest.permission.ACCESS_FINE_LOCATION
        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
        val has = ContextCompat.checkSelfPermission(this, fine)   == PackageManager.PERMISSION_GRANTED ||
                  ContextCompat.checkSelfPermission(this, coarse) == PackageManager.PERMISSION_GRANTED
        if (has) checkSmsPermissionAndSend() else locationPermissionRequest.launch(arrayOf(fine, coarse))
    }

    private fun checkSmsPermissionAndSend() {
        val has = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        if (has) getLocationAndSendSOS() else smsPermissionRequest.launch(Manifest.permission.SEND_SMS)
    }

    private fun getLocationAndSendSOS() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSOS.isEnabled = false
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) sendSOSWithLocation(loc.latitude, loc.longitude) else requestFreshLocation()
                }
                .addOnFailureListener { sendSOSWithoutLocation() }
        } catch (e: SecurityException) { sendSOSWithoutLocation() }
    }

    private fun requestFreshLocation() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).setMaxUpdates(1).build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) {
                fusedLocationClient.removeLocationUpdates(this)
                val loc = r.lastLocation
                if (loc != null) sendSOSWithLocation(loc.latitude, loc.longitude) else sendSOSWithoutLocation()
            }
        }
        try { fusedLocationClient.requestLocationUpdates(req, cb, Looper.getMainLooper()) }
        catch (e: SecurityException) { sendSOSWithoutLocation() }
    }

    private fun sendSOSWithLocation(lat: Double, lon: Double) {
        resetUI()
        dispatchSOS("https://maps.google.com/?q=$lat,$lon")
    }

    private fun sendSOSWithoutLocation() { resetUI(); dispatchSOS("(localizacao indisponivel)") }

    private fun dispatchSOS(locationText: String) {
        val user     = prefs.getUserProfile() ?: return
        val contacts = prefs.getAllContacts()
        if (contacts.isEmpty()) { Toast.makeText(this, "Nenhum contato cadastrado!", Toast.LENGTH_LONG).show(); return }

        val age    = user.ageLabel
        val smsMsg = buildSmsMessage(user.fullName, user.bloodType, age, locationText)
        val waMsg  = buildWhatsAppMessage(user.fullName, user.bloodType, age, locationText)

        var smsSent = 0
        contacts.forEach { c -> if (sendSms(c.phone, smsMsg)) smsSent++ }

        Toast.makeText(this,
            if (smsSent > 0) "✅ SMS enviado para $smsSent contato(s)!" else "⚠️ SMS falhou.",
            Toast.LENGTH_LONG).show()

        // Abre WhatsApp para cada contato com delay escalonado
        contacts.forEachIndexed { i, c ->
            binding.root.postDelayed({ openWhatsApp(c.phone, waMsg) }, (600 + i * 3000).toLong())
        }
    }

    // ── SMS ───────────────────────────────────────────────────────────────────
    private fun sendSms(phoneRaw: String, message: String): Boolean {
        return try {
            val digits = phoneRaw.filter { it.isDigit() }
            val phone  = if (digits.startsWith("55") && digits.length > 11) digits.substring(2) else digits
            val sm = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                applicationContext.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            sm.sendMultipartTextMessage(phone, null, sm.divideMessage(message), null, null)
            true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    // ── WhatsApp ──────────────────────────────────────────────────────────────
    private fun openWhatsApp(phoneRaw: String, message: String) {
        val digits = phoneRaw.filter { it.isDigit() }
        val phone  = if (digits.startsWith("55")) digits else "55$digits"
        try {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/$phone?text=${URLEncoder.encode(message, "UTF-8")}")))
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp não encontrado.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildSmsMessage(name: String, bt: String, age: String, loc: String) =
        "** EMERGENCIA SOS **\n$name precisa de ajuda URGENTE!\nIdade: $age | Tipo sanguineo: $bt\nLocalizacao: $loc\nLigue imediatamente!"

    private fun buildWhatsAppMessage(name: String, bt: String, age: String, loc: String) =
        "🆘 *EMERGÊNCIA — SOS* 🆘\n\n*$name* precisa de ajuda URGENTE!\n\n🩸 *Tipo sanguíneo:* $bt\n🎂 *Idade:* $age\n📍 *Localização:* $loc\n\nPor favor, ligue imediatamente!"

    // ── Voz ───────────────────────────────────────────────────────────────────
    private fun toggleVoiceListening() {
        if (isListening) stopVoiceListening()
        else {
            val hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (hasAudio) startVoiceListening() else audioPermissionRequest.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Reconhecimento de voz não disponível.", Toast.LENGTH_SHORT).show()
            return
        }

        isListening = true
        binding.btnMic.text = "🔴 Ouvindo..."
        binding.tvVoiceStatus.text = "Aguardando palavra-chave: \"${prefs.getVoiceKeyword()}\""
        binding.tvVoiceStatus.visibility = View.VISIBLE

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onPartialResults(r: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val keyword = prefs.getVoiceKeyword()
                val detected = matches?.any { it.lowercase().contains(keyword) } ?: false

                if (detected) {
                    binding.tvVoiceStatus.text = "✅ Palavra-chave detectada! Enviando SOS..."
                    stopVoiceListening()
                    // Dispara SOS direto sem confirmação (emergência por voz)
                    checkLocationPermissionAndSend()
                } else {
                    binding.tvVoiceStatus.text = "Não detectei \"$keyword\". Tentando de novo..."
                    // Reinicia automaticamente para continuar ouvindo
                    startVoiceListeningIntent()
                }
            }

            override fun onError(error: Int) {
                if (isListening) {
                    // Erros de timeout/silêncio: reinicia automaticamente
                    if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        startVoiceListeningIntent()
                    } else {
                        stopVoiceListening()
                        binding.tvVoiceStatus.text = "Erro no microfone. Toque para tentar novamente."
                    }
                }
            }

            override fun onEndOfSpeech() {}
        })

        startVoiceListeningIntent()
    }

    private fun startVoiceListeningIntent() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun stopVoiceListening() {
        isListening = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        binding.btnMic.text = "🎙️ Ativar por Voz"
        binding.tvVoiceStatus.visibility = View.GONE
    }

    // ── Aniversário ───────────────────────────────────────────────────────────
    /**
     * Verifica se hoje é o aniversário do usuário e exibe uma mensagem de parabéns.
     * A idade já foi recalculada automaticamente pelo Models.kt.
     */
    private fun checkBirthday(birthDate: String, ageLabel: String) {
        try {
            val parts = birthDate.split("/")
            if (parts.size < 3) return
            val birthDay   = parts[0].toInt()
            val birthMonth = parts[1].toInt()
            val today = java.time.LocalDate.now()
            if (today.dayOfMonth == birthDay && today.monthValue == birthMonth) {
                android.widget.Toast.makeText(
                    this,
                    "🎂 Feliz aniversário! Idade atualizada para $ageLabel",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            // data inválida — ignora silenciosamente
        }
    }

    private fun resetUI() {
        binding.progressBar.visibility = View.GONE
        binding.btnSOS.isEnabled = true
    }
}
