package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.MessagesImporter.ImportResult.*
import com.simplemobiletools.smsmessenger.models.ExportedMessage
import java.io.*
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import kotlin.text.StringBuilder


class MessagesImporter(private val context: Context) {
    enum class ImportResult {
        IMPORT_FAIL, IMPORT_OK, IMPORT_PARTIAL, IMPORT_NOTHING_NEW, NO_PASSWORD_PROVIDED
    }

    private val gson = Gson()
    private val messageWriter = MessagesWriter(context)
    private val config = context.config
    private var messagesImported = 0
    private var messagesFailed = 0
    private var errorPassword : Boolean = false

    private fun readBuffer(inputStream: InputStream, buffer: ByteArray)
    {
        var left = buffer.size

        while(left > 0)
        {
            val count = inputStream.read(buffer, buffer.size - left, left)
            if (count < 0)
                throw Exception("Bad file encryption")
            left -= count
        }
    }

    private fun stringBuilder(inputStream: InputStream): StringBuilder
    {
        var data = StringBuilder()

        inputStream.bufferedReader().use { reader ->
            var line = reader.readLine()
            while( line != null)
            {
                data.append(line)
                line = reader.readLine()
            }
        }
        return data
    }

    fun importMessages(path: String, onProgress: (total: Int, current: Int) -> Unit = { _, _ -> }, callback: (result: ImportResult) -> Unit) {
        ensureBackgroundThread {
            try {
                val inputStream: InputStream
                inputStream = if (path.contains("/")) {
                    File(path).inputStream()
                } else {
                    context.assets.open(path)
                }

                val password = config.importBackupPassword
                val data: StringBuilder
                if (password != "")
                {
                    try {
                        BufferedInputStream(inputStream).use { raw ->
                            val salt = ByteArray(16)
                            val prefix = ByteArray(16)
                            readBuffer(raw, salt)
                            readBuffer(raw, prefix)
                            val keyFactory: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                            val keySpec: KeySpec = PBEKeySpec(password.toCharArray(), salt, KEY_ITERATIONS, KEY_LENGTH)
                            val secret: SecretKey = keyFactory.generateSecret(keySpec)
                            val cipher: Cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                            val iv = IvParameterSpec(prefix)
                            cipher.init(Cipher.DECRYPT_MODE, secret, iv)
                            data = stringBuilder(CipherInputStream(raw, cipher))
                        }
                    } catch (e: Exception) {
                        throw Exception("Error while decrypting backup, please check your password.")
                        }
                }
                else
                {
                    data = stringBuilder(inputStream)
                }

                val json = data.toString()
                val type = object : TypeToken<List<ExportedMessage>>() {}.type
                val messages = gson.fromJson<List<ExportedMessage>>(json, type)
                val totalMessages = messages.flatMap { it.sms ?: emptyList() }.size + messages.flatMap { it.mms ?: emptyList() }.size
                if (totalMessages <= 0) {
                    callback.invoke(IMPORT_NOTHING_NEW)
                    return@ensureBackgroundThread
                }

                onProgress.invoke(totalMessages, messagesImported)
                for (message in messages) {
                    if (config.importSms) {
                        message.sms?.forEach { backup ->
                            messageWriter.writeSmsMessage(backup)
                            messagesImported++
                            onProgress.invoke(totalMessages, messagesImported)
                        }
                    }
                    if (config.importMms) {
                        message.mms?.forEach { backup ->
                            messageWriter.writeMmsMessage(backup)
                            messagesImported++
                            onProgress.invoke(totalMessages, messagesImported)
                        }
                    }
                    refreshMessages()
                }

            } catch (e: Exception) {
                context.showErrorToast(e)
                messagesFailed++
            }

            callback.invoke(
                when {
                    messagesImported == 0 -> IMPORT_FAIL
                    messagesFailed > 0 -> IMPORT_PARTIAL
                    errorPassword -> NO_PASSWORD_PROVIDED
                    else -> IMPORT_OK
                }
            )
        }
    }
}
