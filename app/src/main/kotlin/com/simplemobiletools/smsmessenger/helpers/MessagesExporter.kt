package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import com.google.gson.Gson
import com.google.gson.stream.JsonWriter
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.extensions.getConversationIds
import java.io.*
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class MessagesExporter(private val context: Context) {
    enum class ExportResult {
        EXPORT_FAIL, EXPORT_OK
    }
    enum class ExportState {
        EXPORT, ENCRYPT
    }

    private val config = context.config
    private val messageReader = MessagesReader(context)
    private val gson = Gson()

    private fun jsonWriter(workOutputStream: OutputStream, onProgress: (state: ExportState, total: Int, current: Int) -> Unit = {_, _, _ -> }, callback: (result: ExportResult) -> Unit) {
        val writer = JsonWriter(workOutputStream.bufferedWriter())
        writer.use {
            try {
                var written = 0
                writer.beginArray()
                val conversationIds = context.getConversationIds()
                val totalMessages = messageReader.getMessagesCount(config.exportSms, config.exportMms)
                for (threadId in conversationIds) {
                    writer.beginObject()

                    if (config.exportSms && messageReader.getSmsCount() > 0) {
                        writer.name("sms")
                        writer.beginArray()
                        messageReader.forEachSms(threadId) {
                            writer.jsonValue(gson.toJson(it))
                            written++
                            onProgress.invoke(ExportState.EXPORT, totalMessages, written)
                        }
                        writer.endArray()
                    }

                    if (config.exportMms && messageReader.getMmsCount() > 0) {
                        writer.name("mms")
                        writer.beginArray()
                        messageReader.forEachMms(threadId) {
                            writer.jsonValue(gson.toJson(it))
                            written++
                            onProgress.invoke(ExportState.EXPORT, totalMessages, written)
                        }

                        writer.endArray()
                    }

                    writer.endObject()
                }
                writer.endArray()
            } catch (e: Exception) {
                callback.invoke(ExportResult.EXPORT_FAIL)
            }
        }
    }

    fun exportMessages(outputStream: OutputStream?, onProgress: (state: ExportState, total: Int, current: Int) -> Unit = { _, _, _ -> }, callback: (result: ExportResult) -> Unit) {
        ensureBackgroundThread {
            if (outputStream == null) {
                callback.invoke(ExportResult.EXPORT_FAIL)
                return@ensureBackgroundThread
            }

            if (config.exportBackupPassword == "") {
                jsonWriter(outputStream, onProgress, callback)
            } else {
                val outputFile = File(context.cacheDir, "output.json")
                jsonWriter(outputFile.outputStream(), onProgress, callback)
                val salt = ByteArray(16)
                val random = SecureRandom()
                random.nextBytes(salt)

                //Taken from FairEmail project (setting export mechanism)
                // https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#Cipher
                val keyFactory: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                val keySpec: KeySpec = PBEKeySpec(config.exportBackupPassword.toCharArray(), salt, KEY_ITERATIONS, KEY_LENGTH)
                val secret: SecretKey = keyFactory.generateSecret(keySpec)
                val cipher: Cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.ENCRYPT_MODE, secret)

                outputStream.write(salt)
                outputStream.write(cipher.iv)

                val cout = CipherOutputStream(outputStream, cipher)
                val cin: InputStream = outputFile.inputStream()

                var left = cin.available()
                val total = left
                do {
                    onProgress.invoke(ExportState.ENCRYPT, total, total - left)
                    val byteArray = ByteArray(8192)
                    val readBytes = cin.read(byteArray, 0, 8192)
                    left -= readBytes
                    cout.write(byteArray, 0, readBytes)
                } while(left > 0)

                cout.flush()

                cin.close()
                outputFile.delete()

                outputStream.write(cipher.doFinal())
            }
            callback.invoke(ExportResult.EXPORT_OK)
        }
    }
}
