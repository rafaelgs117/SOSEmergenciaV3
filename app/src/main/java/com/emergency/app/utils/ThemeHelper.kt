package com.emergency.app.utils

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import com.emergency.app.R
import com.emergency.app.model.AppTheme

object ThemeHelper {

    fun applyTheme(activity: Activity, theme: AppTheme) {
        when (theme) {
            AppTheme.DARK -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                activity.setTheme(R.style.Theme_SOSEmergencia_Dark)
            }
            AppTheme.DALTONISM -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                activity.setTheme(R.style.Theme_SOSEmergencia_Daltonism)
            }
            AppTheme.NORMAL -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                activity.setTheme(R.style.Theme_SOSEmergencia)
            }
        }
    }
}
