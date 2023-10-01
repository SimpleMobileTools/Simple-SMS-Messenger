package com.simplemobiletools.smsmessenger.helpers

import android.net.Uri
import android.util.Xml
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.dialogs.ImportMessagesDialog
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.models.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream


class MessagesImporter(private val activity: SimpleActivity) {

    private val messageWriter = MessagesWriter(activity)
    private val config = activity.config
    private var messagesImported = 0
    private var messagesFailed = 0

    fun importMessages(uri: Uri) {
        try {
            val fileType = activity.contentResolver.getType(uri).orEmpty()
            val isXml = isXmlMimeType(fileType) || (uri.path?.endsWith("txt") == true && isFileXml(uri))
            if (isXml) {
                activity.toast(com.simplemobiletools.commons.R.string.importing)
                getInputStreamFromUri(uri)!!.importXml()
            } else {
                importJson(uri)
            }
        } catch (e: Exception) {
            activity.showErrorToast(e)
        }
    }

    private fun importJson(uri: Uri) {
        try {
            val jsonString = activity.contentResolver.openInputStream(uri)!!.use { inputStream ->
                inputStream.bufferedReader().readText()
            }

            val deserializedList = Json.decodeFromString<List<MessagesBackup>>(jsonString)
            if (deserializedList.isEmpty()) {
                activity.toast(com.simplemobiletools.commons.R.string.no_entries_for_importing)
                return
            }
            ImportMessagesDialog(activity, deserializedList)
        } catch (e: SerializationException) {
            activity.toast(com.simplemobiletools.commons.R.string.invalid_file_format)
        } catch (e: IllegalArgumentException) {
            activity.toast(com.simplemobiletools.commons.R.string.invalid_file_format)
        } catch (e: Exception) {
            activity.showErrorToast(e)
        }
    }

    fun restoreMessages(messagesBackup: List<MessagesBackup>, callback: (ImportResult) -> Unit) {
        ensureBackgroundThread {
            try {
                messagesBackup.forEach { message ->
                    try {
                        if (message.backupType == BackupType.SMS && config.importSms) {
                            messageWriter.writeSmsMessage(message as SmsBackup)
                            messagesImported++
                        } else if (message.backupType == BackupType.MMS && config.importMms) {
                            messageWriter.writeMmsMessage(message as MmsBackup)
                            messagesImported++
                        }
                    } catch (e: Exception) {
                        activity.showErrorToast(e)
                        messagesFailed++
                    }
                }
                refreshMessages()
            } catch (e: Exception) {
                activity.showErrorToast(e)
            }

            callback.invoke(
                when {
                    messagesImported == 0 && messagesFailed == 0 -> ImportResult.IMPORT_NOTHING_NEW
                    messagesFailed > 0 && messagesImported > 0 -> ImportResult.IMPORT_PARTIAL
                    messagesFailed > 0 -> ImportResult.IMPORT_FAIL
                    else -> ImportResult.IMPORT_OK
                }
            )
        }
    }

    private fun InputStream.importXml() {
        try {
            bufferedReader().use { reader ->
                val xmlParser = Xml.newPullParser().apply {
                    setInput(reader)
                }

                xmlParser.nextTag()
                xmlParser.require(XmlPullParser.START_TAG, null, "smses")

                var depth = 1
                while (depth != 0) {
                    when (xmlParser.next()) {
                        XmlPullParser.END_TAG -> depth--
                        XmlPullParser.START_TAG -> depth++
                    }

                    if (xmlParser.eventType != XmlPullParser.START_TAG) {
                        continue
                    }

                    try {
                        if (xmlParser.name == "sms") {
                            if (config.importSms) {
                                val message = xmlParser.readSms()
                                messageWriter.writeSmsMessage(message)
                                messagesImported++
                            } else {
                                xmlParser.skip()
                            }
                        } else {
                            xmlParser.skip()
                        }
                    } catch (e: Exception) {
                        activity.showErrorToast(e)
                        messagesFailed++
                    }
                }
                refreshMessages()
            }
            when {
                messagesFailed > 0 && messagesImported > 0 -> activity.toast(com.simplemobiletools.commons.R.string.importing_some_entries_failed)
                messagesFailed > 0 -> activity.toast(com.simplemobiletools.commons.R.string.importing_failed)
                else -> activity.toast(com.simplemobiletools.commons.R.string.importing_successful)
            }
        } catch (_: Exception) {
            activity.toast(com.simplemobiletools.commons.R.string.invalid_file_format)
        }
    }

    private fun XmlPullParser.readSms(): SmsBackup {
        require(XmlPullParser.START_TAG, null, "sms")

        return SmsBackup(
            subscriptionId = 0,
            address = getAttributeValue(null, "address"),
            body = getAttributeValue(null, "body"),
            date = getAttributeValue(null, "date").toLong(),
            dateSent = getAttributeValue(null, "date").toLong(),
            locked = getAttributeValue(null, "locked").toInt(),
            protocol = getAttributeValue(null, "protocol"),
            read = getAttributeValue(null, "read").toInt(),
            status = getAttributeValue(null, "status").toInt(),
            type = getAttributeValue(null, "type").toInt(),
            serviceCenter = getAttributeValue(null, "service_center")
        )
    }

    private fun XmlPullParser.skip() {
        if (eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }

    private fun getInputStreamFromUri(uri: Uri): InputStream? {
        return try {
            activity.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            null
        }
    }

    private fun isFileXml(uri: Uri): Boolean {
        val inputStream = getInputStreamFromUri(uri)
        return inputStream?.bufferedReader()?.use { reader ->
            reader.readLine()?.startsWith("<?xml") ?: false
        } ?: false
    }

    private fun isXmlMimeType(mimeType: String): Boolean {
        return mimeType.equals("application/xml", ignoreCase = true) || mimeType.equals("text/xml", ignoreCase = true)
    }

    private fun isJsonMimeType(mimeType: String): Boolean {
        return mimeType.equals("application/json", ignoreCase = true)
    }
}
