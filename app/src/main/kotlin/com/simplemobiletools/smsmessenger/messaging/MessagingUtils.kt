package com.simplemobiletools.smsmessenger.messaging

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony.Sms
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.widget.Toast
import com.klinker.android.send_message.Message
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.extensions.getThreadId
import com.simplemobiletools.smsmessenger.extensions.isPlainTextMimeType
import com.simplemobiletools.smsmessenger.extensions.smsSender
import com.simplemobiletools.smsmessenger.messaging.SmsException.Companion.ERROR_PERSISTING_MESSAGE
import com.simplemobiletools.smsmessenger.models.Attachment
import com.simplemobiletools.smsmessenger.receivers.MmsSentReceiver
import com.simplemobiletools.smsmessenger.receivers.SendStatusReceiver

class MessagingUtils(val context: Context) {

    /**
     * Insert an SMS to the given URI with thread_id specified.
     */
    private fun insertSmsMessage(
        subId: Int, dest: String, text: String, timestamp: Long, threadId: Long,
        status: Int = Sms.STATUS_NONE, type: Int = Sms.MESSAGE_TYPE_OUTBOX
    ): Uri {
        val response: Uri?
        val values = ContentValues().apply {
            put(Sms.ADDRESS, dest)
            put(Sms.DATE, timestamp)
            put(Sms.READ, 1)
            put(Sms.SEEN, 1)
            put(Sms.BODY, text)

            // insert subscription id only if it is a valid one.
            if (subId != Settings.DEFAULT_SUBSCRIPTION_ID) {
                put(Sms.SUBSCRIPTION_ID, subId)
            }

            if (status != Sms.STATUS_NONE) {
                put(Sms.STATUS, status)
            }
            if (type != Sms.MESSAGE_TYPE_ALL) {
                put(Sms.TYPE, type)
            }
            if (threadId != -1L) {
                put(Sms.THREAD_ID, threadId)
            }
        }

        try {
            response = context.contentResolver.insert(Sms.CONTENT_URI, values)
        } catch (e: Exception) {
            throw SmsException(ERROR_PERSISTING_MESSAGE, e)
        }
        return response ?: throw SmsException(ERROR_PERSISTING_MESSAGE)
    }

    /** Send an SMS message given [text] and [addresses]. A [SmsException] is thrown in case any errors occur. */
    fun sendSmsMessage(
        text: String, addresses: Set<String>, subId: Int, requireDeliveryReport: Boolean
    ) {
        if (addresses.size > 1) {
            // insert a dummy message for this thread if it is a group message
            val broadCastThreadId = context.getThreadId(addresses.toSet())
            val mergedAddresses = addresses.joinToString(ADDRESS_SEPARATOR)
            insertSmsMessage(
                subId = subId, dest = mergedAddresses, text = text,
                timestamp = System.currentTimeMillis(), threadId = broadCastThreadId,
                status = Sms.Sent.STATUS_COMPLETE, type = Sms.Sent.MESSAGE_TYPE_SENT
            )
        }

        for (address in addresses) {
            val threadId = context.getThreadId(address)
            val messageUri = insertSmsMessage(
                subId = subId, dest = address, text = text,
                timestamp = System.currentTimeMillis(), threadId = threadId
            )
            try {
                context.smsSender.sendMessage(
                    subId = subId, destination = address, body = text, serviceCenter = null,
                    requireDeliveryReport = requireDeliveryReport, messageUri = messageUri
                )
            } catch (e: Exception) {
                updateSmsMessageSendingStatus(messageUri, Sms.Outbox.MESSAGE_TYPE_FAILED)
                throw e // propagate error to caller
            }
        }
    }

    fun updateSmsMessageSendingStatus(messageUri: Uri?, type: Int) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(Sms.Outbox.TYPE, type)
        }

        try {
            if (messageUri != null) {
                resolver.update(messageUri, values, null, null)
            } else {
                // mark latest sms as sent, need to check if this is still necessary (or reliable)
                // as this was taken from android-smsmms. The messageUri shouldn't be null anyway
                val cursor = resolver.query(Sms.Outbox.CONTENT_URI, null, null, null, null)
                cursor?.use {
                    if (cursor.moveToFirst()) {
                        @SuppressLint("Range")
                        val id = cursor.getString(cursor.getColumnIndex(Sms.Outbox._ID))
                        val selection = "${Sms._ID} = ?"
                        val selectionArgs = arrayOf(id.toString())
                        resolver.update(Sms.Outbox.CONTENT_URI, values, selection, selectionArgs)
                    }
                }
            }
        } catch (e: Exception) {
            context.showErrorToast(e)
        }
    }

    fun getSmsMessageFromDeliveryReport(intent: Intent): SmsMessage? {
        val pdu = intent.getByteArrayExtra("pdu")
        val format = intent.getStringExtra("format")
        return SmsMessage.createFromPdu(pdu, format)
    }

    @Deprecated("TODO: Move/rewrite MMS code into the app.")
    fun sendMmsMessage(text: String, addresses: List<String>, attachments: List<Attachment>, settings: Settings) {
        val transaction = Transaction(context, settings)
        val message = Message(text, addresses.toTypedArray())

        if (attachments.isNotEmpty()) {
            for (attachment in attachments) {
                try {
                    val uri = attachment.getUri()
                    context.contentResolver.openInputStream(uri)?.use {
                        val bytes = it.readBytes()
                        val mimeType = if (attachment.mimetype.isPlainTextMimeType()) {
                            "application/txt"
                        } else {
                            attachment.mimetype
                        }
                        val name = attachment.filename
                        message.addMedia(bytes, mimeType, name, name)
                    }
                } catch (e: Exception) {
                    context.showErrorToast(e)
                } catch (e: Error) {
                    context.showErrorToast(e.localizedMessage ?: context.getString(R.string.unknown_error_occurred))
                }
            }
        }

        val mmsSentIntent = Intent(context, MmsSentReceiver::class.java)
        transaction.setExplicitBroadcastForSentMms(mmsSentIntent)

        try {
            transaction.sendNewMessage(message)
        } catch (e: Exception) {
            context.showErrorToast(e)
        }
    }

    fun maybeShowErrorToast(resultCode: Int, errorCode: Int) {
        if (resultCode != Activity.RESULT_OK) {
            val msg = if (errorCode != SendStatusReceiver.NO_ERROR_CODE) {
                context.getString(R.string.carrier_send_error)
            } else {
                when (resultCode) {
                    SmsManager.RESULT_ERROR_NO_SERVICE -> context.getString(R.string.error_service_is_unavailable)
                    SmsManager.RESULT_ERROR_RADIO_OFF -> context.getString(R.string.error_radio_turned_off)
                    else -> context.getString(R.string.unknown_error_occurred_sending_message, resultCode)
                }
            }
            context.toast(msg = msg, length = Toast.LENGTH_LONG)
        } else {
            // no-op
        }
    }

    companion object {
        const val ADDRESS_SEPARATOR = "|"
    }
}
