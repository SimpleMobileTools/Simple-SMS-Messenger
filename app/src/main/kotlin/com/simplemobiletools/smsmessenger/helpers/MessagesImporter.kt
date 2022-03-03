package com.simplemobiletools.smsmessenger.helpers

import android.R.attr.password
import android.content.Context
import android.provider.Telephony.*
import android.text.TextUtils
import androidx.appcompat.widget.ThemedSpinnerAdapter.Helper
import androidx.documentfile.provider.DocumentFile
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

    fun readBuffer(inputStream: InputStream, buffer: ByteArray)
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

    fun importMessages(path: String?, file: DocumentFile?, onProgress: (total: Int, current: Int) -> Unit = { _, _ -> }, callback: (result: ImportResult) -> Unit) {
        ensureBackgroundThread {
            try {
                //if extension is not "sec" then the file must be not encrypted
                var inputStream: InputStream

                if(path != null)
                {
                    val extension = path.substringAfterLast(".","")
                    inputStream = if (path.contains("/")) {
                        File(path).inputStream()
                    } else {
                        context.assets.open(path)
                    }
                }
                else if (file != null)
                {
                    inputStream = File(file.uri)
                }

                if (extension == "sec")
                {
                    val password = config.importBackupPassword
                    if(password == "")
                    {
                        errorPassword = true
                        throw Exception("No password provided")
                    }

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
                                inputStream = CipherInputStream(raw, cipher)
                            }
                    }
                    catch (e: Exception)
                    {
                        throw Exception("Error while decrypting backup, please check your password.")
                    }
                }



                inputStream.bufferedReader().use { reader ->
                    val json = reader.readText()
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
