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

    var showCharacterCounter: Boolean
        get() = prefs.getBoolean(SHOW_CHARACTER_COUNTER, false)
        set(showCharacterCounter) = prefs.edit().putBoolean(SHOW_CHARACTER_COUNTER, showCharacterCounter).apply()
   
    // We set a default value of 1 which is ALLOW_LINKS_SENT 
    var allowLinks: Int
        get() = prefs.getInt(ALLOW_LINKS, ALLOW_LINKS_SENT)
        set(allowLinks) = prefs.edit().putInt(ALLOW_LINKS, allowLinks).apply()
}
