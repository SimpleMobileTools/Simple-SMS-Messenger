package com.simplemobiletools.smsmessenger.services

import android.app.Service
import android.content.Intent
import android.net.Uri
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import com.simplemobiletools.smsmessenger.extensions.getThreadId

class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (intent == null) {
                return START_NOT_STICKY
            }

            val number = Uri.decode(intent.dataString!!.removePrefix("sms:").removePrefix("smsto:").removePrefix("mms").removePrefix("mmsto:").trim())
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val settings = Settings()
            settings.useSystemSending = true
            val transaction = Transaction(this, settings)
            val message = com.klinker.android.send_message.Message(text, number)
            transaction.sendNewMessage(message, getThreadId(number))
        } catch (ignored: Exception) {
        }

        return super.onStartCommand(intent, flags, startId)
    }
}
