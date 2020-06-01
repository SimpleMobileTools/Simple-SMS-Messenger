package com.simplemobiletools.smsmessenger.receivers

import android.content.Context
import android.net.Uri
import com.bumptech.glide.Glide
import com.simplemobiletools.commons.extensions.isNumberBlocked
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.extensions.conversationsDB
import com.simplemobiletools.smsmessenger.extensions.getConversations
import com.simplemobiletools.smsmessenger.extensions.getLatestMMS
import com.simplemobiletools.smsmessenger.extensions.showReceivedMessageNotification

// more info at https://github.com/klinker41/android-smsmms
class MmsReceiver : com.klinker.android.send_message.MmsReceivedReceiver() {
    override fun onMessageReceived(context: Context, messageUri: Uri) {
        val mms = context.getLatestMMS() ?: return
        val address = mms.participants.firstOrNull()?.phoneNumber ?: ""
        if (context.isNumberBlocked(address)) {
            return
        }

        val size = context.resources.getDimension(R.dimen.notification_large_icon_size).toInt()
        ensureBackgroundThread {
            val glideBitmap = try {
                Glide.with(context)
                    .asBitmap()
                    .load(mms.attachment!!.attachments.first().uri)
                    .centerCrop()
                    .into(size, size)
                    .get()
            } catch (e: Exception) {
                null
            }

            context.showReceivedMessageNotification(address, mms.body, mms.thread, glideBitmap, mms.id, true)
            val conversation = context.getConversations(mms.thread.toLong()).firstOrNull() ?: return@ensureBackgroundThread
            context.conversationsDB.insertOrUpdate(conversation)
        }
    }

    override fun onError(context: Context, error: String) {}
}
