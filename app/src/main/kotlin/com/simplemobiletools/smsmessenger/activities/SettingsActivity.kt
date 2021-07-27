package com.simplemobiletools.smsmessenger.activities

import android.annotation.TargetApi
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import com.simplemobiletools.commons.activities.ManageBlockedNumbersActivity
import com.simplemobiletools.commons.dialogs.ChangeDateTimeFormatDialog
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.extensions.getAllowLinksText
import com.simplemobiletools.smsmessenger.helpers.*
import kotlinx.android.synthetic.main.activity_settings.*
import java.util.*

class SettingsActivity : SimpleActivity() {
    private var blockedNumbersAtPause = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
    }

    override fun onResume() {
        super.onResume()

        setupPurchaseThankYou()
        setupCustomizeColors()
        setupCustomizeNotifications()
        setupUseEnglish()
        setupManageBlockedNumbers()
        setupChangeDateTimeFormat()
        setupFontSize()
        setupAllowLinks()
        setupShowCharacterCounter()
        updateTextColors(settings_scrollview)

        if (blockedNumbersAtPause != -1 && blockedNumbersAtPause != getBlockedNumbers().hashCode()) {
            refreshMessages()
        }
    }

    override fun onPause() {
        super.onPause()
        blockedNumbersAtPause = getBlockedNumbers().hashCode()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun setupPurchaseThankYou() {
        settings_purchase_thank_you_holder.beGoneIf(isOrWasThankYouInstalled())
        settings_purchase_thank_you_holder.setOnClickListener {
            launchPurchaseThankYouIntent()
        }
    }

    private fun setupCustomizeColors() {
        settings_customize_colors_label.text = getCustomizeColorsString()
        settings_customize_colors_holder.setOnClickListener {
            handleCustomizeColorsClick()
        }
    }

    private fun setupCustomizeNotifications() {
        settings_customize_notifications_holder.beVisibleIf(isOreoPlus())
        settings_customize_notifications_holder.setOnClickListener {
            launchCustomizeNotificationsIntent()
        }
    }

    private fun setupUseEnglish() {
        settings_use_english_holder.beVisibleIf(config.wasUseEnglishToggled || Locale.getDefault().language != "en")
        settings_use_english.isChecked = config.useEnglish
        settings_use_english_holder.setOnClickListener {
            settings_use_english.toggle()
            config.useEnglish = settings_use_english.isChecked
            System.exit(0)
        }
    }

    // support for device-wise blocking came on Android 7, rely only on that
    @TargetApi(Build.VERSION_CODES.N)
    private fun setupManageBlockedNumbers() {
        settings_manage_blocked_numbers_holder.beVisibleIf(isNougatPlus())
        settings_manage_blocked_numbers_holder.setOnClickListener {
            startActivity(Intent(this, ManageBlockedNumbersActivity::class.java))
        }
    }

    private fun setupChangeDateTimeFormat() {
        settings_change_date_time_format_holder.setOnClickListener {
            ChangeDateTimeFormatDialog(this) {
                refreshMessages()
            }
        }
    }

    private fun setupFontSize() {
        settings_font_size.text = getFontSizeText()
        settings_font_size_holder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(FONT_SIZE_SMALL, getString(R.string.small)),
                RadioItem(FONT_SIZE_MEDIUM, getString(R.string.medium)),
                RadioItem(FONT_SIZE_LARGE, getString(R.string.large)),
                RadioItem(FONT_SIZE_EXTRA_LARGE, getString(R.string.extra_large)))

            RadioGroupDialog(this@SettingsActivity, items, config.fontSize) {
                config.fontSize = it as Int
                settings_font_size.text = getFontSizeText()
            }
        }
    }
    
    private fun setupAllowLinks() {
        settings_allow_links.text = getAllowLinksText()
        settings_allow_links_holder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(ALLOW_LINKS_SENT, getString(R.string.allow_links_in_sent)),
                RadioItem(ALLOW_LINKS_RECEIVED, getString(R.string.allow_links_in_received)),
                RadioItem(ALLOW_LINKS_ALWAYS, getString(R.string.allow_links_always)),
                RadioItem(ALLOW_LINKS_NEVER, getString(R.string.allow_links_never)))

            RadioGroupDialog(this@SettingsActivity, items, config.allowLinks) {
                config.allowLinks = it as Int
                settings_allow_links.text = getAllowLinksText()
            }
        }
    }

    private fun setupShowCharacterCounter() {
        settings_show_character_counter.isChecked = config.showCharacterCounter
        settings_show_character_counter_holder.setOnClickListener {
            settings_show_character_counter.toggle()
            config.showCharacterCounter = settings_show_character_counter.isChecked
        }
    }
}
