package com.simplemobiletools.smsmessenger.dialogs

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.extensions.config
import kotlinx.android.synthetic.main.dialog_notifications.view.*

@RequiresApi(Build.VERSION_CODES.O)
class NotificationsDialog(val activity: BaseSimpleActivity, val threadId: Long, val name: String) {
    init {
        val view = activity.layoutInflater.inflate(R.layout.dialog_notifications, null).apply {
            custom_notifications.isChecked = activity.config.customNotifications.contains(threadId.toString())
            custom_notifications_button.beVisibleIf(custom_notifications.isChecked)

            custom_notifications.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    custom_notifications_button.beVisible()
                    activity.config.addCustomNotificationsByThreadId(threadId)
                    createNotificationChannel()
                } else {
                    custom_notifications_button.beGone()
                    activity.config.removeCustomNotificationsByThreadId(threadId)
                    removeNotificationChannel()
                }
            }

            custom_notifications_button.setOnClickListener {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, threadId.hashCode().toString())
                    activity.startActivity(this)
                }
            }
        }

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok) { _, _ -> { } }
            .create().apply {
                activity.setupDialogStuff(view, this)
            }
    }

    private fun getNotificationManager() = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createNotificationChannel() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
            .build()

        NotificationChannel(threadId.hashCode().toString(), name, NotificationManager.IMPORTANCE_HIGH).apply {
            setBypassDnd(false)
            enableLights(true)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes)
            enableVibration(true)
            getNotificationManager().createNotificationChannel(this)
        }
    }

    private fun removeNotificationChannel() {
        getNotificationManager().deleteNotificationChannel(threadId.hashCode().toString())
    }
}
