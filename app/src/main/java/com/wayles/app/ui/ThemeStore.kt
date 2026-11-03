package com.wayles.app.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { System, Light, Dark }

object ThemeStore {
    private var prefs: SharedPreferences? = null
    var mode by mutableStateOf(ThemeMode.System)
        private set

    fun init(ctx: Context) {
        if (prefs == null) {
            prefs = ctx.getSharedPreferences("wayles", Context.MODE_PRIVATE)
        }
        mode = ThemeMode.valueOf(
            prefs?.getString("theme", ThemeMode.System.name) ?: ThemeMode.System.name
        )
    }

    fun set(m: ThemeMode) {
        mode = m
        prefs?.edit()?.putString("theme", m.name)?.apply()
    }
}
