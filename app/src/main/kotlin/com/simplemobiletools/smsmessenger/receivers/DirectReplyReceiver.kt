package com.simplemobiletools.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.klinker.android.send_message.Transaction
import com.simplemobiletools.commons.extensions.notificationManager
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.REPLY
import com.simplemobiletools.smsmessenger.helpers.THREAD_ID
import com.simplemobiletools.smsmessenger.helpers.THREAD_NUMBER

class DirectReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra(THREAD_NUMBER)
        val threadId = intent.getLongExtra(THREAD_ID, 0L)
        var msg = RemoteInput.getResultsFromIntent(intent).getCharSequence(REPLY)?.toString() ?: return

        msg = context.removeDiacriticsIfNeeded(msg)

        val settings = context.getSendMessageSettings()
        if (address != null) {
            val SIMId = context.config.getUseSIMIdAtNumber(address)
            if (SIMId != 0){
                settings.subscriptionId = SIMId
            }
        }

        val transaction = Transaction(context, settings)
        val message = com.klinker.android.send_message.Message(msg, address)

        try {
            val smsSentIntent = Intent(context, SmsStatusSentReceiver::class.java)
            val deliveredIntent = Intent(context, SmsStatusDeliveredReceiver::class.java)

            transaction.setExplicitBroadcastForSentSms(smsSentIntent)
            transaction.setExplicitBroadcastForDeliveredSms(deliveredIntent)

            transaction.sendNewMessage(message)
        } catch (e: Exception) {
            context.showErrorToast(e)
        }

        context.notificationManager.cancel(threadId.hashCode())

        ensureBackgroundThread {
            context.markThreadMessagesRead(threadId)
            context.conversationsDB.markRead(threadId)
        }
    }
}
