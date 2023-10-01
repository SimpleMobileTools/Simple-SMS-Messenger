package com.simplemobiletools.smsmessenger.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import android.util.Base64
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.smsmessenger.extensions.getConversationIds
import com.simplemobiletools.smsmessenger.models.*
import java.io.IOException
import java.io.InputStream

class MessagesReader(private val context: Context) {

    fun getMessagesToExport(
        getSms: Boolean, getMms: Boolean, callback: (messages: List<MessagesBackup>) -> Unit
    ) {
        val conversationIds = context.getConversationIds()
        var smsMessages = listOf<SmsBackup>()
        var mmsMessages = listOf<MmsBackup>()

        if (getSms) {
            smsMessages = getSmsMessages(conversationIds)
        }
        if (getMms) {
            mmsMessages = getMmsMessages(conversationIds)
        }
        callback(smsMessages + mmsMessages)
    }

    private fun getSmsMessages(threadIds: List<Long>): List<SmsBackup> {
        val projection = arrayOf(
            Sms.SUBSCRIPTION_ID,
            Sms.ADDRESS,
            Sms.BODY,
            Sms.DATE,
            Sms.DATE_SENT,
            Sms.LOCKED,
            Sms.PROTOCOL,
            Sms.READ,
            Sms.STATUS,
            Sms.TYPE,
            Sms.SERVICE_CENTER
        )

        val selection = "${Sms.THREAD_ID} = ?"
        val smsList = mutableListOf<SmsBackup>()

        threadIds.map { it.toString() }.forEach { threadId ->
            context.queryCursor(Sms.CONTENT_URI, projection, selection, arrayOf(threadId)) { cursor ->
                val subscriptionId = cursor.getLongValue(Sms.SUBSCRIPTION_ID)
                val address = cursor.getStringValue(Sms.ADDRESS)
                val body = cursor.getStringValueOrNull(Sms.BODY)
                val date = cursor.getLongValue(Sms.DATE)
                val dateSent = cursor.getLongValue(Sms.DATE_SENT)
                val locked = cursor.getIntValue(Sms.DATE_SENT)
                val protocol = cursor.getStringValueOrNull(Sms.PROTOCOL)
                val read = cursor.getIntValue(Sms.READ)
                val status = cursor.getIntValue(Sms.STATUS)
                val type = cursor.getIntValue(Sms.TYPE)
                val serviceCenter = cursor.getStringValueOrNull(Sms.SERVICE_CENTER)
                smsList.add(SmsBackup(subscriptionId, address, body, date, dateSent, locked, protocol, read, status, type, serviceCenter))
            }
        }
        return smsList
    }

    private fun getMmsMessages(threadIds: List<Long>, includeTextOnlyAttachment: Boolean = false): List<MmsBackup> {
        val projection = arrayOf(
            Mms._ID,
            Mms.CREATOR,
            Mms.CONTENT_TYPE,
            Mms.DELIVERY_REPORT,
            Mms.DATE,
            Mms.DATE_SENT,
            Mms.LOCKED,
            Mms.MESSAGE_TYPE,
            Mms.MESSAGE_BOX,
            Mms.READ,
            Mms.READ_REPORT,
            Mms.SEEN,
            Mms.TEXT_ONLY,
            Mms.STATUS,
            Mms.SUBJECT_CHARSET,
            Mms.SUBSCRIPTION_ID,
            Mms.TRANSACTION_ID
        )
        val selection = if (includeTextOnlyAttachment) {
            "${Mms.THREAD_ID} = ? AND ${Mms.TEXT_ONLY} = ?"
        } else {
            "${Mms.THREAD_ID} = ?"
        }
        val mmsList = mutableListOf<MmsBackup>()

        threadIds.map { it.toString() }.forEach { threadId ->
            val selectionArgs = if (includeTextOnlyAttachment) {
                arrayOf(threadId, "1")
            } else {
                arrayOf(threadId)
            }
            context.queryCursor(Mms.CONTENT_URI, projection, selection, selectionArgs) { cursor ->
                val mmsId = cursor.getLongValue(Mms._ID)
                val creator = cursor.getStringValueOrNull(Mms.CREATOR)
                val contentType = cursor.getStringValueOrNull(Mms.CONTENT_TYPE)
                val deliveryReport = cursor.getIntValue(Mms.DELIVERY_REPORT)
                val date = cursor.getLongValue(Mms.DATE)
                val dateSent = cursor.getLongValue(Mms.DATE_SENT)
                val locked = cursor.getIntValue(Mms.LOCKED)
                val messageType = cursor.getIntValue(Mms.MESSAGE_TYPE)
                val messageBox = cursor.getIntValue(Mms.MESSAGE_BOX)
                val read = cursor.getIntValue(Mms.READ)
                val readReport = cursor.getIntValue(Mms.READ_REPORT)
                val seen = cursor.getIntValue(Mms.SEEN)
                val textOnly = cursor.getIntValue(Mms.TEXT_ONLY)
                val status = cursor.getStringValueOrNull(Mms.STATUS)
                val subject = cursor.getStringValueOrNull(Mms.SUBJECT)
                val subjectCharSet = cursor.getStringValueOrNull(Mms.SUBJECT_CHARSET)
                val subscriptionId = cursor.getLongValue(Mms.SUBSCRIPTION_ID)
                val transactionId = cursor.getStringValueOrNull(Mms.TRANSACTION_ID)

                val parts = getParts(mmsId)
                val addresses = getMmsAddresses(mmsId)
                mmsList.add(
                    MmsBackup(
                        creator,
                        contentType,
                        deliveryReport,
                        date,
                        dateSent,
                        locked,
                        messageType,
                        messageBox,
                        read,
                        readReport,
                        seen,
                        textOnly,
                        status,
                        subject,
                        subjectCharSet,
                        subscriptionId,
                        transactionId,
                        addresses,
                        parts
                    )
                )
            }
        }
        return mmsList
    }

    @SuppressLint("NewApi")
    private fun getParts(mmsId: Long): List<MmsPart> {
        val parts = mutableListOf<MmsPart>()
        val uri = if (isQPlus()) Mms.Part.CONTENT_URI else Uri.parse("content://mms/part")
        val projection = arrayOf(
            Mms.Part._ID,
            Mms.Part.CONTENT_DISPOSITION,
            Mms.Part.CHARSET,
            Mms.Part.CONTENT_ID,
            Mms.Part.CONTENT_LOCATION,
            Mms.Part.CONTENT_TYPE,
            Mms.Part.CT_START,
            Mms.Part.CT_TYPE,
            Mms.Part.FILENAME,
            Mms.Part.NAME,
            Mms.Part.SEQ,
            Mms.Part.TEXT
        )

        val selection = "${Mms.Part.MSG_ID} = ?"
        val selectionArgs = arrayOf(mmsId.toString())
        context.queryCursor(uri, projection, selection, selectionArgs) { cursor ->
            val partId = cursor.getLongValue(Mms.Part._ID)
            val contentDisposition = cursor.getStringValueOrNull(Mms.Part.CONTENT_DISPOSITION)
            val charset = cursor.getStringValueOrNull(Mms.Part.CHARSET)
            val contentId = cursor.getStringValueOrNull(Mms.Part.CONTENT_ID)
            val contentLocation = cursor.getStringValueOrNull(Mms.Part.CONTENT_LOCATION)
            val contentType = cursor.getStringValue(Mms.Part.CONTENT_TYPE)
            val ctStart = cursor.getStringValueOrNull(Mms.Part.CT_START)
            val ctType = cursor.getStringValueOrNull(Mms.Part.CT_TYPE)
            val filename = cursor.getStringValueOrNull(Mms.Part.FILENAME)
            val name = cursor.getStringValueOrNull(Mms.Part.NAME)
            val sequenceOrder = cursor.getIntValue(Mms.Part.SEQ)
            val text = cursor.getStringValueOrNull(Mms.Part.TEXT)
            val data = when {
                contentType.startsWith("text/") -> {
                    usePart(partId) { stream ->
                        stream.readBytes().toString(Charsets.UTF_8)
                    }
                }

                else -> {
                    usePart(partId) { stream ->
                        Base64.encodeToString(stream.readBytes(), Base64.DEFAULT)
                    }
                }
            }
            parts.add(MmsPart(contentDisposition, charset, contentId, contentLocation, contentType, ctStart, ctType, filename, name, sequenceOrder, text, data))
        }
        return parts
    }

    @SuppressLint("NewApi")
    private fun usePart(partId: Long, block: (InputStream) -> String): String {
        val partUri = if (isQPlus()) Mms.Part.CONTENT_URI.buildUpon().appendPath(partId.toString()).build() else Uri.parse("content://mms/part/$partId")
        try {
            val stream = context.contentResolver.openInputStream(partUri) ?: return ""
            stream.use {
                return block(stream)
            }
        } catch (e: IOException) {
            return ""
        }
    }

    @SuppressLint("NewApi")
    private fun getMmsAddresses(messageId: Long): List<MmsAddress> {
        val addresses = mutableListOf<MmsAddress>()
        val uri = if (isRPlus()) Mms.Addr.getAddrUriForMessage(messageId.toString()) else Uri.parse("content://mms/$messageId/addr")
        val projection = arrayOf(Mms.Addr.ADDRESS, Mms.Addr.TYPE, Mms.Addr.CHARSET)
        val selection = "${Mms.Addr.MSG_ID}= ?"
        val selectionArgs = arrayOf(messageId.toString())
        context.queryCursor(uri, projection, selection, selectionArgs) { cursor ->
            val address = cursor.getStringValue(Mms.Addr.ADDRESS)
            val type = cursor.getIntValue(Mms.Addr.TYPE)
            val charset = cursor.getIntValue(Mms.Addr.CHARSET)
            addresses.add(MmsAddress(address, type, charset))
        }
        return addresses
    }

    fun getMessagesCount(): Int {
        return getSmsCount() + getMmsCount()
    }

    fun getMmsCount(): Int {
        return countRows(Mms.CONTENT_URI)
    }

    fun getSmsCount(): Int {
        return countRows(Sms.CONTENT_URI)
    }

    private fun countRows(uri: Uri): Int {
        val cursor = context.contentResolver.query(
            uri, null, null, null, null
        ) ?: return 0
        cursor.use {
            return cursor.count
        }
    }
}
