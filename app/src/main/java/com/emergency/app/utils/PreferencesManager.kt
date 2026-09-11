package com.emergency.app.utils

import android.content.Context
import com.emergency.app.model.AppTheme
import com.emergency.app.model.EmergencyContact
import com.emergency.app.model.UserProfile

class PreferencesManager(context: Context) {

    private val prefs = context.getSharedPreferences("emergency_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FULL_NAME     = "full_name"
        private const val KEY_ADDRESS       = "address"
        private const val KEY_BLOOD_TYPE    = "blood_type"
        private const val KEY_BIRTH_DATE    = "birth_date"   // DD/MM/AAAA completo
        private const val KEY_REGISTERED    = "is_registered"
        private const val KEY_THEME         = "app_theme"
        private const val KEY_VOICE_KW      = "voice_keyword"
        private const val KEY_CONTACT_COUNT = "contact_count"
    }

    // ── Usuário ───────────────────────────────────────────────────────────────

    fun saveUserProfile(user: UserProfile) {
        prefs.edit()
            .putString(KEY_FULL_NAME,  user.fullName)
            .putString(KEY_ADDRESS,    user.address)
            .putString(KEY_BLOOD_TYPE, user.bloodType)
            .putString(KEY_BIRTH_DATE, user.birthDate)
            .apply()
        // age NÃO é salvo — sempre calculado em tempo real
    }

    fun getUserProfile(): UserProfile? {
        val name = prefs.getString(KEY_FULL_NAME, null) ?: return null
        return UserProfile(
            fullName  = name,
            address   = prefs.getString(KEY_ADDRESS,    "") ?: "",
            bloodType = prefs.getString(KEY_BLOOD_TYPE, "") ?: "",
            birthDate = prefs.getString(KEY_BIRTH_DATE, "") ?: ""
        )
    }

    // ── Contatos (1 a 5) ──────────────────────────────────────────────────────

    fun saveContact(index: Int, contact: EmergencyContact) {
        prefs.edit()
            .putString("contact${index}_name",         contact.name)
            .putString("contact${index}_relationship", contact.relationship)
            .putString("contact${index}_phone",        contact.phone)
            .apply()
    }

    fun getContact(index: Int): EmergencyContact? {
        val name = prefs.getString("contact${index}_name", null) ?: return null
        return EmergencyContact(
            name         = name,
            relationship = prefs.getString("contact${index}_relationship", "") ?: "",
            phone        = prefs.getString("contact${index}_phone",        "") ?: ""
        )
    }

    fun deleteContact(index: Int) {
        val count = getContactCount()
        prefs.edit()
            .remove("contact${index}_name")
            .remove("contact${index}_relationship")
            .remove("contact${index}_phone")
            .apply()
        if (index < count) {
            for (i in index until count) {
                val next = getContact(i + 1)
                if (next != null) saveContact(i, next)
                else prefs.edit()
                    .remove("contact${i}_name")
                    .remove("contact${i}_relationship")
                    .remove("contact${i}_phone")
                    .apply()
            }
        }
        saveContactCount(maxOf(0, count - 1))
    }

    fun getAllContacts(): List<EmergencyContact> =
        (1..getContactCount()).mapNotNull { getContact(it) }

    fun saveContactCount(count: Int) =
        prefs.edit().putInt(KEY_CONTACT_COUNT, count).apply()

    fun getContactCount(): Int = prefs.getInt(KEY_CONTACT_COUNT, 0)

    // ── Configurações ─────────────────────────────────────────────────────────

    fun setRegistrationComplete() =
        prefs.edit().putBoolean(KEY_REGISTERED, true).apply()

    fun isRegistrationComplete(): Boolean =
        prefs.getBoolean(KEY_REGISTERED, false)

    fun saveTheme(theme: AppTheme) =
        prefs.edit().putString(KEY_THEME, theme.name).apply()

    fun getTheme(): AppTheme =
        AppTheme.valueOf(prefs.getString(KEY_THEME, AppTheme.NORMAL.name) ?: AppTheme.NORMAL.name)

    fun saveVoiceKeyword(keyword: String) =
        prefs.edit().putString(KEY_VOICE_KW, keyword.lowercase().trim()).apply()

    fun getVoiceKeyword(): String =
        prefs.getString(KEY_VOICE_KW, "socorro") ?: "socorro"

    fun clearAll() = prefs.edit().clear().apply()
}
