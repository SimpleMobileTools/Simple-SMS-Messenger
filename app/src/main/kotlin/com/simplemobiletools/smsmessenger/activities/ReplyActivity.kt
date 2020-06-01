package com.simplemobiletools.smsmessenger.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import com.simplemobiletools.commons.extensions.notificationManager
import com.simplemobiletools.commons.extensions.onTextChangeListener
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.extensions.getMessages
import com.simplemobiletools.smsmessenger.extensions.getThreadParticipants
import com.simplemobiletools.smsmessenger.extensions.markMessageRead
import com.simplemobiletools.smsmessenger.helpers.MESSAGE_ID
import com.simplemobiletools.smsmessenger.helpers.MESSAGE_IS_MMS
import com.simplemobiletools.smsmessenger.helpers.THREAD_ID
import com.simplemobiletools.smsmessenger.models.Message
import kotlinx.android.synthetic.main.activity_reply.*
import kotlinx.android.synthetic.main.activity_thread.thread_send_message
import kotlinx.android.synthetic.main.activity_thread.thread_type_message
import org.greenrobot.eventbus.EventBus

class ReplyActivity : AppCompatActivity() {

    private var threadId = 0
    private var bus: EventBus? = null
    private var participants = ArrayList<SimpleContact>()
    private var messages = ArrayList<Message>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reply)

        val extras = intent.extras
        if (extras == null) {
            toast(R.string.unknown_error_occurred)
            finish()
            return
        }

        val messageId = intent.getIntExtra(MESSAGE_ID, 0)
        val isMMS = intent.getBooleanExtra(MESSAGE_IS_MMS, false)

        if (isMMS) {
            val intent = Intent(this, ThreadActivity::class.java).apply {
                putExtra(THREAD_ID, threadId)
            }
            startActivity(intent)
            finish()
            return
        }

        markMessageRead(messageId, isMMS)
        notificationManager.cancel(messageId)

        threadId = intent.getIntExtra(THREAD_ID, 0)

        runOnUiThread {
            messages = getMessages(threadId)
            participants = if (messages.isEmpty()) {
                getThreadParticipants(threadId, null)
            } else {
                messages.first().participants
            }

            header.text = participants.first().name

            messageBox.text = messages[messages.lastIndex].body
            println(messages)
        }

        thread_type_message.onTextChangeListener {
            checkSendMessageAvailability()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    private fun checkSendMessageAvailability() {
        if (thread_type_message.text.isNotEmpty()) {
            thread_send_message.isClickable = true
            thread_send_message.alpha = 0.9f
        } else {
            thread_send_message.isClickable = false
            thread_send_message.alpha = 0.4f
        }
    }

    fun sendMessage(view: View) {
        val msg = thread_type_message.text.toString()
        if (msg.isEmpty()) {
            return
        }

        val number = participants.map { it.phoneNumber }.toTypedArray()
        val settings = Settings()
        settings.useSystemSending = true

        val transaction = Transaction(this, settings)
        val message = com.klinker.android.send_message.Message(msg, number)

        try {
            transaction.sendNewMessage(message, threadId.toLong())
            finish()
        } catch (e: Exception) {

        }
    }

    fun closePopUp(view: View) {
        finish()
    }
}
