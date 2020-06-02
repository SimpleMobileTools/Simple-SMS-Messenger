package com.simplemobiletools.smsmessenger.helpers

import com.simplemobiletools.smsmessenger.models.Events
import org.greenrobot.eventbus.EventBus

const val THREAD_ID = "thread_id"
const val THREAD_TITLE = "thread_title"
const val THREAD_TEXT = "thread_text"
const val THREAD_NUMBER = "thread_number"
const val THREAD_ATTACHMENT_URI = "thread_attachment_uri"
const val THREAD_ATTACHMENT_URIS = "thread_attachment_uris"
const val USE_SIM_ID_PREFIX = "use_sim_id_"

private const val PATH = "com.simplemobiletools.smsmessenger.action."
const val MARK_AS_READ = PATH + "mark_as_read"
const val MESSAGE_ID = "message_id"
const val MESSAGE_IS_MMS = "message_is_mms"

// view types for the thread list view
const val THREAD_DATE_TIME = 1
const val THREAD_RECEIVED_MESSAGE = 2
const val THREAD_SENT_MESSAGE = 3
const val THREAD_SENT_MESSAGE_ERROR = 4

fun refreshMessages() {
    EventBus.getDefault().post(Events.RefreshMessages())
}
