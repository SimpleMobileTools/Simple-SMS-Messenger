package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import com.google.gson.Gson
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.extensions.getConversationIds
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
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

    private val config = context.config
    private val messageReader = MessagesReader(context)
    private val gson = Gson()

    fun exportMessages(outputStream: OutputStream?, onProgress: (total: Int, current: Int) -> Unit = { _, _ -> }, callback: (result: ExportResult) -> Unit) {
        ensureBackgroundThread {
            if (outputStream == null) {
                callback.invoke(ExportResult.EXPORT_FAIL)
                return@ensureBackgroundThread
            }

            //To keep same layout as previous version and avoid rewriting importer
            val mainArray = JSONArray()

            //Main object containing Array of object jSMS & jMMS
            val mainObject = JSONObject()

            val jSMS = JSONArray()
            val jMMS = JSONArray()

            try {
                var written = 0
                val conversationIds = context.getConversationIds()
                val totalMessages = messageReader.getMessagesCount()
                for (threadId in conversationIds) {

                    if (config.exportSms && messageReader.getSmsCount() > 0) {
                        messageReader.forEachSms(threadId) {
                            jSMS.put(JSONObject(gson.toJson(it)))
                            written++
                            onProgress.invoke(totalMessages, written)
                        }
                    }

                    if (config.exportMms && messageReader.getMmsCount() > 0) {
                        messageReader.forEachMms(threadId) {
                            jMMS.put(JSONObject(gson.toJson(it)))
                            written++
                            onProgress.invoke(totalMessages, written)
                        }
                    }
                }
                if (jSMS.length() > 0) mainObject.put("sms", jSMS)
                if (jMMS.length() > 0) mainObject.put("mms", jMMS)

                mainArray.put(mainObject)
                if (config.exportBackupPassword == "")
                {
                    //If user didn't set a password we simply save the json in plain
                    outputStream.write(mainArray.toString().encodeToByteArray())
                }
                else
                {
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

                    val cout: OutputStream = CipherOutputStream(outputStream, cipher)
                    cout.bufferedWriter().use { writer ->
                        cout.write(mainArray.toString().toByteArray())
                    }
                    cout.flush()
                    outputStream.write(cipher.doFinal())

                }
                callback.invoke(ExportResult.EXPORT_OK)
            } catch (e: Exception) {
                callback.invoke(ExportResult.EXPORT_FAIL)
            }
        }
    }
}
