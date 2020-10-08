package com.simplemobiletools.smsmessenger.extensions

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.PhoneLookup
import android.provider.Telephony.*
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.ThreadActivity
import com.simplemobiletools.smsmessenger.databases.MessagesDatabase
import com.simplemobiletools.smsmessenger.helpers.*
import com.simplemobiletools.smsmessenger.interfaces.ConversationsDao
import com.simplemobiletools.smsmessenger.models.*
import com.simplemobiletools.smsmessenger.receivers.DirectReplyReceiver
import com.simplemobiletools.smsmessenger.receivers.MarkAsReadReceiver
import me.leolin.shortcutbadger.ShortcutBadger
import java.util.*
import kotlin.collections.ArrayList

val Context.config: Config get() = Config.newInstance(applicationContext)

fun Context.getMessagessDB() = MessagesDatabase.getInstance(this)

val Context.conversationsDB: ConversationsDao get() = getMessagessDB().ConversationsDao()

fun Context.getMessages(threadId: Int): ArrayList<Message> {
    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms._ID,
        Sms.BODY,
        Sms.TYPE,
        Sms.ADDRESS,
        Sms.DATE,
        Sms.READ,
        Sms.THREAD_ID,
        Sms.SUBSCRIPTION_ID
    )

    val selection = "${Sms.THREAD_ID} = ?"
    val selectionArgs = arrayOf(threadId.toString())
    val sortOrder = "${Sms._ID} DESC LIMIT 100"

    val blockStatus = HashMap<String, Boolean>()
    val blockedNumbers = getBlockedNumbers()
    var messages = ArrayList<Message>()
    queryCursor(uri, projection, selection, selectionArgs, sortOrder, showErrors = true) { cursor ->
        val senderNumber = cursor.getStringValue(Sms.ADDRESS) ?: return@queryCursor

        val isNumberBlocked = if (blockStatus.containsKey(senderNumber)) {
            blockStatus[senderNumber]!!
        } else {
            val isBlocked = isNumberBlocked(senderNumber, blockedNumbers)
            blockStatus[senderNumber] = isBlocked
            isBlocked
        }

        if (isNumberBlocked) {
            return@queryCursor
        }

        val id = cursor.getIntValue(Sms._ID)
        val body = cursor.getStringValue(Sms.BODY)
        val type = cursor.getIntValue(Sms.TYPE)
        val namePhoto = getNameAndPhotoFromPhoneNumber(senderNumber)
        val senderName = namePhoto?.name ?: ""
        val photoUri = namePhoto?.photoUri ?: ""
        val date = (cursor.getLongValue(Sms.DATE) / 1000).toInt()
        val read = cursor.getIntValue(Sms.READ) == 1
        val thread = cursor.getIntValue(Sms.THREAD_ID)
        val subscriptionId = cursor.getIntValue(Sms.SUBSCRIPTION_ID)
        val participant = SimpleContact(0, 0, senderName, photoUri, arrayListOf(senderNumber))
        val isMMS = false
        val message = Message(id, body, type, arrayListOf(participant), date, read, thread, isMMS, null, senderName, photoUri, subscriptionId)
        messages.add(message)
    }

    messages.addAll(getMMS(threadId, sortOrder))
    messages = messages.filter { it.participants.isNotEmpty() }
        .sortedWith(compareBy<Message> { it.date }.thenBy { it.id }).toMutableList() as ArrayList<Message>

    return messages
}

// as soon as a message contains multiple recipients it counts as an MMS instead of SMS
fun Context.getMMS(threadId: Int? = null, sortOrder: String? = null): ArrayList<Message> {
    val uri = Mms.CONTENT_URI
    val projection = arrayOf(
        Mms._ID,
        Mms.DATE,
        Mms.READ,
        Mms.MESSAGE_BOX,
        Mms.THREAD_ID,
        Mms.SUBSCRIPTION_ID
    )

    val selection = if (threadId == null) {
        "1 == 1) GROUP BY (${Mms.THREAD_ID}"
    } else {
        "${Mms.THREAD_ID} = ?"
    }

    val selectionArgs = if (threadId == null) {
        null
    } else {
        arrayOf(threadId.toString())
    }

    val messages = ArrayList<Message>()
    val contactsMap = HashMap<Int, SimpleContact>()
    val threadParticipants = HashMap<Int, ArrayList<SimpleContact>>()
    queryCursor(uri, projection, selection, selectionArgs, sortOrder, showErrors = true) { cursor ->
        val mmsId = cursor.getIntValue(Mms._ID)
        val type = cursor.getIntValue(Mms.MESSAGE_BOX)
        val date = cursor.getLongValue(Mms.DATE).toInt()
        val read = cursor.getIntValue(Mms.READ) == 1
        val threadId = cursor.getIntValue(Mms.THREAD_ID)
        val subscriptionId = cursor.getIntValue(Mms.SUBSCRIPTION_ID)
        val participants = if (threadParticipants.containsKey(threadId)) {
            threadParticipants[threadId]!!
        } else {
            val parts = getThreadParticipants(threadId, contactsMap)
            threadParticipants[threadId] = parts
            parts
        }

        val isMMS = true
        val attachment = getMmsAttachment(mmsId)
        val body = attachment?.text ?: ""
        var senderName = ""
        var senderPhotoUri = ""

        if (type != Mms.MESSAGE_BOX_SENT && type != Mms.MESSAGE_BOX_FAILED) {
            val number = getMMSSender(mmsId)
            val namePhoto = getNameAndPhotoFromPhoneNumber(number)
            if (namePhoto != null) {
                senderName = namePhoto.name
                senderPhotoUri = namePhoto.photoUri ?: ""
            }
        }

        val message = Message(mmsId, body, type, participants, date, read, threadId, isMMS, attachment, senderName, senderPhotoUri, subscriptionId)
        messages.add(message)

        participants.forEach {
            contactsMap[it.rawId] = it
        }
    }

    return messages
}

fun Context.getMMSSender(msgId: Int): String {
    val uri = Uri.parse("${Mms.CONTENT_URI}/$msgId/addr")
    val projection = arrayOf(
        Mms.Addr.ADDRESS
    )

    try {
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getStringValue(Mms.Addr.ADDRESS)
            }
        }
    } catch (ignored: Exception) {
    }
    return ""
}

fun Context.getConversations(threadId: Long? = null): ArrayList<Conversation> {
    val uri = Uri.parse("${Threads.CONTENT_URI}?simple=true")
    val projection = arrayOf(
        Threads._ID,
        Threads.SNIPPET,
        Threads.DATE,
        Threads.READ,
        Threads.RECIPIENT_IDS
    )

    var selection = "${Threads.MESSAGE_COUNT} > ?"
    var selectionArgs = arrayOf("0")
    if (threadId != null) {
        selection += " AND ${Threads._ID} = ?"
        selectionArgs = arrayOf("0", threadId.toString())
    }

    val sortOrder = "${Threads.DATE} DESC"

    val conversations = ArrayList<Conversation>()
    val simpleContactHelper = SimpleContactsHelper(this)
    val blockedNumbers = getBlockedNumbers()
    queryCursor(uri, projection, selection, selectionArgs, sortOrder, true) { cursor ->
        val id = cursor.getIntValue(Threads._ID)
        var snippet = cursor.getStringValue(Threads.SNIPPET) ?: ""
        if (snippet.isEmpty()) {
            snippet = getThreadSnippet(id)
        }

        var date = cursor.getLongValue(Threads.DATE)
        if (date.toString().length > 10) {
            date /= 1000
        }

        val rawIds = cursor.getStringValue(Threads.RECIPIENT_IDS)
        val recipientIds = rawIds.split(" ").filter { it.areDigitsOnly() }.map { it.toInt() }.toMutableList()
        val phoneNumbers = getThreadPhoneNumbers(recipientIds)
        if (phoneNumbers.any { isNumberBlocked(it, blockedNumbers) }) {
            return@queryCursor
        }

        val names = getThreadContactNames(phoneNumbers)
        val title = TextUtils.join(", ", names.toTypedArray())
        val photoUri = if (phoneNumbers.size == 1) simpleContactHelper.getPhotoUriFromPhoneNumber(phoneNumbers.first()) else ""
        val isGroupConversation = phoneNumbers.size > 1
        val read = cursor.getIntValue(Threads.READ) == 1
        val isFavorite = getThreadIsFavorite(phoneNumbers)
        val isContact = getThreadIsContact(phoneNumbers)
        val conversation = Conversation(null, id, snippet, date.toInt(), read, title, photoUri, isGroupConversation, isFavorite, isContact, phoneNumbers.first())
        conversations.add(conversation)
    }

    conversations.sortByDescending { it.date }
    return conversations
}

// based on https://stackoverflow.com/a/6446831/1967672
@SuppressLint("NewApi")
fun Context.getMmsAttachment(id: Int): MessageAttachment? {
    val uri = if (isQPlus()) {
        Mms.Part.CONTENT_URI
    } else {
        Uri.parse("content://mms/part")
    }

    val projection = arrayOf(
        Mms._ID,
        Mms.Part.CONTENT_TYPE,
        Mms.Part.TEXT
    )
    val selection = "${Mms.Part.MSG_ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    val messageAttachment = MessageAttachment(id, "", arrayListOf())

    var attachmentName = ""
    queryCursor(uri, projection, selection, selectionArgs, showErrors = true) { cursor ->
        val partId = cursor.getStringValue(Mms._ID)
        val mimetype = cursor.getStringValue(Mms.Part.CONTENT_TYPE)
        if (mimetype == "text/plain") {
            messageAttachment.text = cursor.getStringValue(Mms.Part.TEXT) ?: ""
        } else if (mimetype.startsWith("image/") || mimetype.startsWith("video/")) {
            val attachment = Attachment(Uri.withAppendedPath(uri, partId), mimetype, 0, 0, "")
            messageAttachment.attachments.add(attachment)
        } else if (mimetype != "application/smil") {
            val attachment = Attachment(Uri.withAppendedPath(uri, partId), mimetype, 0, 0, attachmentName)
            messageAttachment.attachments.add(attachment)
        } else {
            val text = cursor.getStringValue(Mms.Part.TEXT)
            val cutName = text.substringAfter("ref src=\"").substringBefore("\"")
            if (cutName.isNotEmpty()) {
                attachmentName = cutName
            }
        }
    }

    return messageAttachment
}

fun Context.getLatestMMS(): Message? {
    val sortOrder = "${Mms.DATE} DESC LIMIT 1"
    return getMMS(sortOrder = sortOrder).firstOrNull()
}

fun Context.getThreadSnippet(threadId: Int): String {
    val sortOrder = "${Mms.DATE} DESC LIMIT 1"
    val latestMms = getMMS(threadId, sortOrder).firstOrNull()
    var snippet = latestMms?.body ?: ""

    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms.BODY
    )

    val selection = "${Sms.THREAD_ID} = ? AND ${Sms.DATE} > ?"
    val selectionArgs = arrayOf(
        threadId.toString(),
        latestMms?.date?.toString() ?: "0"
    )
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        cursor?.use {
            if (cursor.moveToFirst()) {
                snippet = cursor.getStringValue(Sms.BODY)
            }
        }
    } catch (ignored: Exception) {
    }
    return snippet
}

fun Context.getThreadParticipants(threadId: Int, contactsMap: HashMap<Int, SimpleContact>?): ArrayList<SimpleContact> {
    val uri = Uri.parse("${MmsSms.CONTENT_CONVERSATIONS_URI}?simple=true")
    val projection = arrayOf(
        ThreadsColumns.RECIPIENT_IDS
    )
    val selection = "${Mms._ID} = ?"
    val selectionArgs = arrayOf(threadId.toString())
    val participants = ArrayList<SimpleContact>()
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                val address = cursor.getStringValue(ThreadsColumns.RECIPIENT_IDS)
                address.split(" ").filter { it.areDigitsOnly() }.forEach {
                    val addressId = it.toInt()
                    if (contactsMap?.containsKey(addressId) == true) {
                        participants.add(contactsMap[addressId]!!)
                        return@forEach
                    }

                    val phoneNumber = getPhoneNumberFromAddressId(addressId)
                    val namePhoto = getNameAndPhotoFromPhoneNumber(phoneNumber)
                    val name = namePhoto?.name ?: ""
                    val photoUri = namePhoto?.photoUri ?: ""
                    val contact = SimpleContact(addressId, addressId, name, photoUri, arrayListOf(phoneNumber))
                    participants.add(contact)
                }
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }
    return participants
}

fun Context.getThreadPhoneNumbers(recipientIds: List<Int>): ArrayList<String> {
    val numbers = ArrayList<String>()
    recipientIds.forEach {
        numbers.add(getPhoneNumberFromAddressId(it))
    }
    return numbers
}

fun Context.getThreadContactNames(phoneNumbers: List<String>): ArrayList<String> {
    val names = ArrayList<String>()
    phoneNumbers.forEach {
        names.add(SimpleContactsHelper(this).getNameFromPhoneNumber(it))
    }
    return names
}

// return true if any of the phone numbers are starred contacts
fun Context.getThreadIsFavorite(phoneNumbers : List<String>) : Boolean {
    val helper = SimpleContactsHelperExtension(this)
    var isFavorite = false
    phoneNumbers.forEach {
        isFavorite = isFavorite || helper.getStarredFromPhoneNumber(it)
    }
    return isFavorite
}

// return true if any of the phone numbers are contacts
fun Context.getThreadIsContact(phoneNumbers : List<String>) : Boolean {
    val helper = SimpleContactsHelperExtension(this)
    var isContact = false
    phoneNumbers.forEach {
        isContact = isContact || helper.getContactFromPhoneNumber(it) != null
    }
    return isContact
}

// TODO: move getStarredFromPhoneNumber and getContactFromPhoneNumber functions to SimpleContactsHelper (?)
class SimpleContactsHelperExtension(val context: Context) {
    fun getStarredFromPhoneNumber(number: String): Boolean {
        if (!context.hasPermission(PERMISSION_READ_CONTACTS)) {
            return false
        }

        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(
            PhoneLookup.STARRED
        )

        try {
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor.use {
                if (cursor?.moveToFirst() == true) {
                    return cursor.getIntValue(PhoneLookup.STARRED) == 1
                }
            }
        } catch (e: Exception) {
            context.showErrorToast(e)
        }

        return false
    }

    fun getContactFromPhoneNumber(number: String): String? {
        if (!context.hasPermission(PERMISSION_READ_CONTACTS)) {
            return null
        }

        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(
            PhoneLookup.CONTACT_ID
        )

        try {
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor.use {
                if (cursor?.moveToFirst() == true) {
                    return cursor.getStringValue(PhoneLookup.CONTACT_ID)
                }
            }
        } catch (e: Exception) {
            context.showErrorToast(e)
        }

        return null
    }
}


fun Context.getPhoneNumberFromAddressId(canonicalAddressId: Int): String {
    val uri = Uri.withAppendedPath(MmsSms.CONTENT_URI, "canonical-addresses")
    val projection = arrayOf(
        Mms.Addr.ADDRESS
    )

    val selection = "${Mms._ID} = ?"
    val selectionArgs = arrayOf(canonicalAddressId.toString())
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getStringValue(Mms.Addr.ADDRESS)
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }
    return ""
}

fun Context.getSuggestedContacts(privateContacts: ArrayList<SimpleContact>): ArrayList<SimpleContact> {
    val contacts = ArrayList<SimpleContact>()
    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms.ADDRESS
    )

    val selection = "1 == 1) GROUP BY (${Sms.ADDRESS}"
    val selectionArgs = null
    val sortOrder = "${Sms.DATE} DESC LIMIT 20"
    val blockedNumbers = getBlockedNumbers()

    queryCursor(uri, projection, selection, selectionArgs, sortOrder, showErrors = true) { cursor ->
        val senderNumber = cursor.getStringValue(Sms.ADDRESS) ?: return@queryCursor
        val namePhoto = getNameAndPhotoFromPhoneNumber(senderNumber)
        var senderName = namePhoto?.name ?: ""
        var photoUri = namePhoto?.photoUri ?: ""
        if (namePhoto == null || isNumberBlocked(senderNumber, blockedNumbers)) {
            return@queryCursor
        } else if (namePhoto.name == senderNumber) {
            if (privateContacts.isNotEmpty()) {
                val privateContact = privateContacts.firstOrNull { it.phoneNumbers.first() == senderNumber }
                if (privateContact != null) {
                    senderName = privateContact.name
                    photoUri = privateContact.photoUri
                } else {
                    return@queryCursor
                }
            } else {
                return@queryCursor
            }
        }

        val contact = SimpleContact(0, 0, senderName, photoUri, arrayListOf(senderNumber))
        if (!contacts.map { it.phoneNumbers.first().trimToComparableNumber() }.contains(senderNumber.trimToComparableNumber())) {
            contacts.add(contact)
        }
    }

    return contacts
}

fun Context.getNameAndPhotoFromPhoneNumber(number: String): NamePhoto? {
    if (!hasPermission(PERMISSION_READ_CONTACTS)) {
        return NamePhoto(number, null, null, null)
    }

    val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    val projection = arrayOf(
        PhoneLookup.DISPLAY_NAME,
        PhoneLookup.PHOTO_URI,
        PhoneLookup.CONTACT_ID,
        PhoneLookup.STARRED
    )

    try {
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor.use {
            if (cursor?.moveToFirst() == true) {
                val name = cursor.getStringValue(PhoneLookup.DISPLAY_NAME)
                val photoUri = cursor.getStringValue(PhoneLookup.PHOTO_URI)
                val isContact = cursor.getStringValue(PhoneLookup.CONTACT_ID) != null
                val isFavorite = cursor.getIntValue(PhoneLookup.STARRED) == 1
                return NamePhoto(name, photoUri, isContact, isFavorite)
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }

    return NamePhoto(number, null, false, false)
}

fun Context.insertNewSMS(address: String, subject: String, body: String, date: Long, read: Int, threadId: Long, type: Int, subscriptionId: Int): Int {
    val uri = Sms.CONTENT_URI
    val contentValues = ContentValues().apply {
        put(Sms.ADDRESS, address)
        put(Sms.SUBJECT, subject)
        put(Sms.BODY, body)
        put(Sms.DATE, date)
        put(Sms.READ, read)
        put(Sms.THREAD_ID, threadId)
        put(Sms.TYPE, type)
        put(Sms.SUBSCRIPTION_ID, subscriptionId)
    }

    val newUri = contentResolver.insert(uri, contentValues)
    return newUri?.lastPathSegment?.toInt() ?: 0
}

fun Context.deleteConversation(threadId: Int) {
    var uri = Sms.CONTENT_URI
    val selection = "${Sms.THREAD_ID} = ?"
    val selectionArgs = arrayOf(threadId.toString())
    contentResolver.delete(uri, selection, selectionArgs)

    uri = Mms.CONTENT_URI
    contentResolver.delete(uri, selection, selectionArgs)

    conversationsDB.deleteThreadId(threadId.toLong())
}

fun Context.deleteMessage(id: Int, isMMS: Boolean) {
    val uri = if (isMMS) Mms.CONTENT_URI else Sms.CONTENT_URI
    val selection = "${Sms._ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    contentResolver.delete(uri, selection, selectionArgs)
}

fun Context.markMessageRead(id: Int, isMMS: Boolean) {
    val uri = if (isMMS) Mms.CONTENT_URI else Sms.CONTENT_URI
    val contentValues = ContentValues().apply {
        put(Sms.READ, 1)
        put(Sms.SEEN, 1)
    }
    val selection = "${Sms._ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    contentResolver.update(uri, contentValues, selection, selectionArgs)
}

fun Context.markThreadMessagesRead(threadId: Int) {
    arrayOf(Sms.CONTENT_URI, Mms.CONTENT_URI).forEach { uri ->
        val contentValues = ContentValues().apply {
            put(Sms.READ, 1)
            put(Sms.SEEN, 1)
        }
        val selection = "${Sms.THREAD_ID} = ?"
        val selectionArgs = arrayOf(threadId.toString())
        contentResolver.update(uri, contentValues, selection, selectionArgs)
    }
}

fun Context.markThreadMessagesUnread(threadId: Int) {
    arrayOf(Sms.CONTENT_URI, Mms.CONTENT_URI).forEach { uri ->
        val contentValues = ContentValues().apply {
            put(Sms.READ, 0)
            put(Sms.SEEN, 0)
        }
        val selection = "${Sms.THREAD_ID} = ?"
        val selectionArgs = arrayOf(threadId.toString())
        contentResolver.update(uri, contentValues, selection, selectionArgs)
    }
}

fun Context.updateUnreadCountBadge(conversations: List<Conversation>) {
    val unreadCount = conversations.count { !it.read }
    if (unreadCount == 0) {
        ShortcutBadger.removeCount(this)
    } else {
        ShortcutBadger.applyCount(this, unreadCount)
    }
}

@SuppressLint("NewApi")
fun Context.getThreadId(address: String): Long {
    return if (isMarshmallowPlus()) {
        try {
            Threads.getOrCreateThreadId(this, address)
        } catch (e: Exception) {
            0L
        }
    } else {
        0L
    }
}

@SuppressLint("NewApi")
fun Context.getThreadId(addresses: Set<String>): Long {
    return if (isMarshmallowPlus()) {
        try {
            Threads.getOrCreateThreadId(this, addresses)
        } catch (e: Exception) {
            0L
        }
    } else {
        0L
    }
}

fun Context.showReceivedMessageNotification(address: String, body: String, threadID: Int, bitmap: Bitmap?) {
    val privateCursor = getMyContactsCursor().loadInBackground()
    ensureBackgroundThread {
        val contactInfo = getNameAndPhotoFromPhoneNumber(address)
        val isContact = contactInfo?.isContact == true
        val isFavorite = contactInfo?.isFavorite == true
        var sender = contactInfo?.name ?: ""
        if (address == sender) {
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            sender = privateContacts.firstOrNull { it.doesContainPhoneNumber(address) }?.name ?: address
        }

        Handler(Looper.getMainLooper()).post {
            showMessageNotification(address, body, threadID, bitmap, sender, isContact, isFavorite)
        }
    }
}

@SuppressLint("NewApi")
fun Context.showMessageNotification(address: String, body: String, threadID: Int, bitmap: Bitmap?, sender: String, isContact: Boolean, isFavorite: Boolean) {

    val notificationChannelId : String
    if (isFavorite) {
        notificationChannelId = NOTIFICATION_CHANNEL_FAVORITES
    }
    else if (isContact) {
        notificationChannelId = NOTIFICATION_CHANNEL_CONTACTS
    }
    else {
        notificationChannelId = NOTIFICATION_CHANNEL_UNKNOWNS
    }

    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val intent = Intent(this, ThreadActivity::class.java).apply {
        putExtra(THREAD_ID, threadID)
    }

    val pendingIntent = PendingIntent.getActivity(this, threadID, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    val summaryText = getString(R.string.new_message)
    val markAsReadIntent = Intent(this, MarkAsReadReceiver::class.java).apply {
        action = MARK_AS_READ
        putExtra(THREAD_ID, threadID)
    }

    val markAsReadPendingIntent = PendingIntent.getBroadcast(this, 0, markAsReadIntent, PendingIntent.FLAG_CANCEL_CURRENT)
    var replyAction: NotificationCompat.Action? = null

    if (isNougatPlus()) {
        val replyLabel = getString(R.string.reply)
        val remoteInput = RemoteInput.Builder(REPLY)
            .setLabel(replyLabel)
            .build()

        val replyIntent = Intent(this, DirectReplyReceiver::class.java).apply {
            putExtra(THREAD_ID, threadID)
            putExtra(THREAD_NUMBER, address)
            putExtra(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_REPLIES)
        }

        val replyPendingIntent = PendingIntent.getBroadcast(applicationContext, threadID, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        replyAction = NotificationCompat.Action.Builder(R.drawable.ic_send_vector, replyLabel, replyPendingIntent)
            .addRemoteInput(remoteInput)
            .build()
    }

    val largeIcon = bitmap ?: SimpleContactsHelper(this).getContactLetterIcon(sender)
    val builder = NotificationCompat.Builder(this, notificationChannelId)
        .setContentTitle(sender)
        .setContentText(body)
        .setColor(config.primaryColor)
        .setSmallIcon(R.drawable.ic_messenger)
        .setLargeIcon(largeIcon)
        .setStyle(NotificationCompat.BigTextStyle().setSummaryText(summaryText).bigText(body))
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setDefaults(Notification.DEFAULT_LIGHTS)
        .setCategory(Notification.CATEGORY_MESSAGE)
        .setAutoCancel(true)

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        builder.setSound(soundUri, AudioManager.STREAM_NOTIFICATION)
    }

    if (replyAction != null) {
        builder.addAction(replyAction)
    }
    builder.addAction(R.drawable.ic_check_vector, getString(R.string.mark_as_read), markAsReadPendingIntent)
        .setChannelId(notificationChannelId)

    notificationManager.notify(threadID, builder.build())
}

@SuppressLint("NewApi")
fun Context.createNotificationChannel(id: String, name: String) {
    if (isOreoPlus()) {
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
            .build()

        val importance = NotificationManager.IMPORTANCE_HIGH
        NotificationChannel(id, name, importance).apply {
            setBypassDnd(false)
            enableLights(true)
            setSound(soundUri, audioAttributes)
            enableVibration(true)
            notificationManager.createNotificationChannel(this)
        }
    }
}
