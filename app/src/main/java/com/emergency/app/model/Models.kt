package com.emergency.app.model

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

data class UserProfile(
    val fullName:  String,
    val address:   String,
    val bloodType: String,
    val birthDate: String  // formato DD/MM/AAAA — idade é sempre calculada
) {
    /**
     * Calcula a idade exata com base na data de nascimento e hoje.
     * Retorna null se a data for inválida.
     */
    val age: Int?
        get() = try {
            val fmt  = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            val born = LocalDate.parse(birthDate, fmt)
            Period.between(born, LocalDate.now()).years
        } catch (e: Exception) {
            null
        }

    /** Texto formatado pronto para exibição, ex: "32 anos" */
    val ageLabel: String
        get() = age?.let { "$it anos" } ?: "—"
}

data class EmergencyContact(
    val name:         String,
    val relationship: String,
    val phone:        String
)

enum class AppTheme { NORMAL, DARK, DALTONISM }
