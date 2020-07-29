package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import com.simplemobiletools.commons.helpers.BaseConfig

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    fun saveUseSIMIdAtNumber(number: String, SIMId: Int) {
        prefs.edit().putInt(USE_SIM_ID_PREFIX + number, SIMId).apply()
    }

    fun getUseSIMIdAtNumber(number: String) = prefs.getInt(USE_SIM_ID_PREFIX + number, 0)

    var invertShortcuts: Boolean
        get() = prefs.getBoolean(INVERT_NOTIFICATION_SHORTCUTS, false)
        set(invertShortcuts) = prefs.edit().putBoolean(INVERT_NOTIFICATION_SHORTCUTS, invertShortcuts).apply()
}
