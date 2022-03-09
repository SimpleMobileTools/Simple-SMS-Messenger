package com.simplemobiletools.smsmessenger.helpers

import com.simplemobiletools.smsmessenger.models.Events
import org.greenrobot.eventbus.EventBus

const val THREAD_ID = "thread_id"
const val THREAD_TITLE = "thread_title"
const val THREAD_TEXT = "thread_text"
const val THREAD_NUMBER = "thread_number"
const val THREAD_ATTACHMENT_URI = "thread_attachment_uri"
const val THREAD_ATTACHMENT_URIS = "thread_attachment_uris"
const val SEARCHED_MESSAGE_ID = "searched_message_id"
const val USE_SIM_ID_PREFIX = "use_sim_id_"
const val NOTIFICATION_CHANNEL = "simple_sms_messenger"
const val SHOW_CHARACTER_COUNTER = "show_character_counter"
const val USE_SIMPLE_CHARACTERS = "use_simple_characters"
const val LOCK_SCREEN_VISIBILITY = "lock_screen_visibility"
const val ENABLE_DELIVERY_REPORTS = "enable_delivery_reports"
const val SEND_LONG_MESSAGE_MMS = "send_long_message_mms"
const val MMS_FILE_SIZE_LIMIT = "mms_file_size_limit"
const val PINNED_CONVERSATIONS = "pinned_conversations"
const val LAST_EXPORT_PATH = "last_export_path"
const val EXPORT_BACKUP_PASSWORD = "export_backup_password"
const val EXPORT_SMS = "export_sms"
const val EXPORT_MMS = "export_mms"
const val EXPORT_MIME_TYPE = "application/json"
const val EXPORT_SECURE_MIME_TYPE = "application/sec"
const val EXPORT_FILE_EXT = ".json"
const val EXPORT_SECURE_FILE_EXT = ".sec"
const val IMPORT_BACKUP_PASSWORD = "import_backup_password"
const val IMPORT_SMS = "import_sms"
const val IMPORT_MMS = "import_mms"
const val WAS_DB_CLEARED = "was_db_cleared"

//Secure Backup Cipher Parameters
const val KEY_ITERATIONS = 65536
const val KEY_LENGTH = 256

private const val PATH = "com.simplemobiletools.smsmessenger.action."
const val MARK_AS_READ = PATH + "mark_as_read"
const val REPLY = PATH + "reply"

// view types for the thread list view
const val THREAD_DATE_TIME = 1
const val THREAD_RECEIVED_MESSAGE = 2
const val THREAD_SENT_MESSAGE = 3
const val THREAD_SENT_MESSAGE_ERROR = 4
const val THREAD_SENT_MESSAGE_SENT = 5
const val THREAD_SENT_MESSAGE_SENDING = 6

// lock screen visibility constants
const val LOCK_SCREEN_SENDER_MESSAGE = 1
const val LOCK_SCREEN_SENDER = 2
const val LOCK_SCREEN_NOTHING = 3

//Notifications
const val EXPORT_IMPORT_NOTIFICATION_ID = 33

const val FILE_SIZE_NONE = -1L
const val FILE_SIZE_100_KB = 102_400L
const val FILE_SIZE_200_KB = 204_800L
const val FILE_SIZE_300_KB = 307_200L
const val FILE_SIZE_600_KB = 614_400L
const val FILE_SIZE_1_MB = 1_048_576L
const val FILE_SIZE_2_MB = 2_097_152L

fun refreshMessages() {
    EventBus.getDefault().post(Events.RefreshMessages())
}
