package com.simplemobiletools.smsmessenger.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.text.TextUtils
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.RelativeLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.AutoCompleteTextViewAdapter
import com.simplemobiletools.smsmessenger.adapters.ThreadAdapter
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.*
import com.simplemobiletools.smsmessenger.models.*
import com.simplemobiletools.smsmessenger.receivers.SmsStatusDeliveredReceiver
import com.simplemobiletools.smsmessenger.receivers.SmsStatusSentReceiver
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_thread.*
import kotlinx.android.synthetic.main.item_attachment.view.*
import kotlinx.android.synthetic.main.item_selected_contact.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.InputStream
import java.io.OutputStream

class ThreadActivity : SimpleActivity() {
    private val MIN_DATE_TIME_DIFF_SECS = 300
    private val PICK_ATTACHMENT_INTENT = 1
    private val PICK_SAVE_FILE_INTENT = 11

    private var threadId = 0L
    private var currentSIMCardIndex = 0
    private var isActivityVisible = false
    private var refreshedSinceSent = false
    private var threadItems = ArrayList<ThreadItem>()
    private var bus: EventBus? = null
    private var participants = ArrayList<SimpleContact>()
    private var privateContacts = ArrayList<SimpleContact>()
    private var messages = ArrayList<Message>()
    private val availableSIMCards = ArrayList<SIMCard>()
    private var attachmentSelections = mutableMapOf<String, AttachmentSelection>()
    private val imageCompressor by lazy { ImageCompressor(this) }
    private var lastAttachmentUri: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thread)

        val extras = intent.extras
        if (extras == null) {
            toast(R.string.unknown_error_occurred)
            finish()
            return
        }

        threadId = intent.getLongExtra(THREAD_ID, 0L)
        intent.getStringExtra(THREAD_TITLE)?.let {
            supportActionBar?.title = it
        }

        bus = EventBus.getDefault()
        bus!!.register(this)
        handlePermission(PERMISSION_READ_PHONE_STATE) {
            if (it) {
                setupButtons()
                setupCachedMessages {
                    val searchedMessageId = intent.getLongExtra(SEARCHED_MESSAGE_ID, -1L)
                    intent.removeExtra(SEARCHED_MESSAGE_ID)
                    if (searchedMessageId != -1L) {
                        val index = threadItems.indexOfFirst { (it as? Message)?.id == searchedMessageId }
                        if (index != -1) {
                            thread_messages_list.smoothScrollToPosition(index)
                        }
                    }

                    setupThread()
                }
            } else {
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val smsDraft = getSmsDraft(threadId)
        if (smsDraft != null) {
            thread_type_message.setText(smsDraft)
        }
        isActivityVisible = true
    }

    override fun onPause() {
        super.onPause()

        if (thread_type_message.value != "" && attachmentSelections.isEmpty()) {
            saveSmsDraft(thread_type_message.value, threadId)
        } else {
            deleteSmsDraft(threadId)
        }

        bus?.post(Events.RefreshMessages())

        isActivityVisible = false
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_thread, menu)
        menu.apply {
            findItem(R.id.delete).isVisible = threadItems.isNotEmpty()
            findItem(R.id.block_number).isVisible = isNougatPlus()
            findItem(R.id.dial_number).isVisible = participants.size == 1
        }

        updateMenuItemColors(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (participants.isEmpty()) {
            return true
        }

        when (item.itemId) {
            R.id.block_number -> blockNumber()
            R.id.delete -> askConfirmDelete()
            R.id.dial_number -> dialNumber()
            R.id.manage_people -> managePeople()
            R.id.mark_as_unread -> markAsUnread()
            android.R.id.home -> onHomePressed()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == PICK_ATTACHMENT_INTENT && resultCode == Activity.RESULT_OK && resultData != null && resultData.data != null) {
            addAttachment(resultData.data!!)
        } else if (requestCode == PICK_SAVE_FILE_INTENT && resultCode == Activity.RESULT_OK && resultData != null && resultData.data != null) {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            applicationContext.contentResolver.takePersistableUriPermission(resultData.data!!, takeFlags)
            var inputStream: InputStream? = null
            var outputStream: OutputStream? = null
            try {
                inputStream = contentResolver.openInputStream(Uri.parse(lastAttachmentUri))
                outputStream = contentResolver.openOutputStream(Uri.parse(resultData.dataString!!), "rwt")
                inputStream!!.copyTo(outputStream!!)
                outputStream.flush()
                toast(R.string.file_saved)
            } catch (e: Exception) {
                showErrorToast(e)
            } finally {
                inputStream?.close()
                outputStream?.close()
            }
            lastAttachmentUri = null
        }
    }

    private fun onHomePressed() {
        Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(this)
        }
        finish()
    }

    private fun setupCachedMessages(callback: () -> Unit) {
        ensureBackgroundThread {
            messages = try {
                messagesDB.getThreadMessages(threadId).toMutableList() as ArrayList<Message>
            } catch (e: Exception) {
                ArrayList()
            }

            setupParticipants()
            setupAdapter()

            runOnUiThread {
                if (messages.isEmpty()) {
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                    thread_type_message.requestFocus()
                }

                setupThreadTitle()
                setupSIMSelector()
                callback()
            }
        }
    }

    private fun setupThread() {
        val privateCursor = getMyContactsCursor(false, true)?.loadInBackground()
        ensureBackgroundThread {
            privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)

            val cachedMessagesCode = messages.clone().hashCode()
            messages = getMessages(threadId)

            val hasParticipantWithoutName = participants.any {
                it.phoneNumbers.contains(it.name)
            }

            try {
                if (participants.isNotEmpty() && messages.hashCode() == cachedMessagesCode && !hasParticipantWithoutName) {
                    setupAdapter()
                    return@ensureBackgroundThread
                }
            } catch (ignored: Exception) {
            }

            setupParticipants()

            // check if no participant came from a privately stored contact in Simple Contacts
            if (privateContacts.isNotEmpty()) {
                val senderNumbersToReplace = HashMap<String, String>()
                participants.filter { it.doesHavePhoneNumber(it.name) }.forEach { participant ->
                    privateContacts.firstOrNull { it.doesHavePhoneNumber(participant.phoneNumbers.first()) }?.apply {
                        senderNumbersToReplace[participant.phoneNumbers.first()] = name
                        participant.name = name
                        participant.photoUri = photoUri
                    }
                }

                messages.forEach { message ->
                    if (senderNumbersToReplace.keys.contains(message.senderName)) {
                        message.senderName = senderNumbersToReplace[message.senderName]!!
                    }
                }
            }

            if (participants.isEmpty()) {
                val name = intent.getStringExtra(THREAD_TITLE) ?: ""
                val number = intent.getStringExtra(THREAD_NUMBER)
                if (number == null) {
                    toast(R.string.unknown_error_occurred)
                    finish()
                    return@ensureBackgroundThread
                }

                val contact = SimpleContact(0, 0, name, "", arrayListOf(number), ArrayList(), ArrayList())
                participants.add(contact)
            }

            messages.chunked(30).forEach { currentMessages ->
                messagesDB.insertMessages(*currentMessages.toTypedArray())
            }

            setupAttachmentSizes()
            setupAdapter()
            runOnUiThread {
                setupThreadTitle()
                setupSIMSelector()
            }
        }
    }

    private fun setupAdapter() {
        threadItems = getThreadItems()
        invalidateOptionsMenu()

        runOnUiThread {
            val currAdapter = thread_messages_list.adapter
            if (currAdapter == null) {
                ThreadAdapter(this, threadItems, thread_messages_list) {
                    (it as? ThreadError)?.apply {
                        thread_type_message.setText(it.messageText)
                    }
                }.apply {
                    thread_messages_list.adapter = this
                }
            } else {
                (currAdapter as ThreadAdapter).updateMessages(threadItems)
            }
        }

        SimpleContactsHelper(this).getAvailableContacts(false) { contacts ->
            contacts.addAll(privateContacts)
            runOnUiThread {
                val adapter = AutoCompleteTextViewAdapter(this, contacts)
                add_contact_or_number.setAdapter(adapter)
                add_contact_or_number.imeOptions = EditorInfo.IME_ACTION_NEXT
                add_contact_or_number.setOnItemClickListener { _, _, position, _ ->
                    val currContacts = (add_contact_or_number.adapter as AutoCompleteTextViewAdapter).resultList
                    val selectedContact = currContacts[position]
                    addSelectedContact(selectedContact)
                }

                add_contact_or_number.onTextChangeListener {
                    confirm_inserted_number.beVisibleIf(it.length > 2)
                }
            }
        }

        confirm_inserted_number?.setOnClickListener {
            val number = add_contact_or_number.value
            val contact = SimpleContact(number.hashCode(), number.hashCode(), number, "", arrayListOf(number), ArrayList(), ArrayList())
            addSelectedContact(contact)
        }
    }

    private fun setupButtons() {
        updateTextColors(thread_holder)
        val textColor = config.textColor
        thread_send_message.applyColorFilter(textColor)
        confirm_manage_contacts.applyColorFilter(textColor)
        thread_add_attachment.applyColorFilter(textColor)

        val adjustedPrimaryColor = getAdjustedPrimaryColor()
        thread_messages_fastscroller.updateColors(adjustedPrimaryColor)

        thread_character_counter.beVisibleIf(config.showCharacterCounter)
        thread_character_counter.setTextSize(TypedValue.COMPLEX_UNIT_PX, getTextSize())

        thread_type_message.setTextSize(TypedValue.COMPLEX_UNIT_PX, getTextSize())
        thread_send_message.setOnClickListener {
            sendMessage()
        }

        thread_send_message.isClickable = false
        thread_type_message.onTextChangeListener {
            checkSendMessageAvailability()
            thread_character_counter.text = it.length.toString()
        }

        confirm_manage_contacts.setOnClickListener {
            hideKeyboard()
            thread_add_contacts.beGone()

            val numbers = HashSet<String>()
            participants.forEach {
                it.phoneNumbers.forEach {
                    numbers.add(it)
                }
            }

            val newThreadId = getThreadId(numbers)
            if (threadId != newThreadId) {
                Intent(this, ThreadActivity::class.java).apply {
                    putExtra(THREAD_ID, newThreadId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(this)
                }
            }
        }

        thread_type_message.setText(intent.getStringExtra(THREAD_TEXT))
        thread_add_attachment.setOnClickListener {
            launchPickPhotoVideoIntent()
        }

        if (intent.extras?.containsKey(THREAD_ATTACHMENT_URI) == true) {
            val uri = Uri.parse(intent.getStringExtra(THREAD_ATTACHMENT_URI))
            addAttachment(uri)
        } else if (intent.extras?.containsKey(THREAD_ATTACHMENT_URIS) == true) {
            (intent.getSerializableExtra(THREAD_ATTACHMENT_URIS) as? ArrayList<Uri>)?.forEach {
                addAttachment(it)
            }
        }
    }

    private fun setupAttachmentSizes() {
        messages.filter { it.attachment != null }.forEach {
            it.attachment!!.attachments.forEach {
                try {
                    if (it.mimetype.startsWith("image/")) {
                        val fileOptions = BitmapFactory.Options()
                        fileOptions.inJustDecodeBounds = true
                        BitmapFactory.decodeStream(contentResolver.openInputStream(it.getUri()), null, fileOptions)
                        it.width = fileOptions.outWidth
                        it.height = fileOptions.outHeight
                    } else if (it.mimetype.startsWith("video/")) {
                        val metaRetriever = MediaMetadataRetriever()
                        metaRetriever.setDataSource(this, it.getUri())
                        it.width = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
                        it.height = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
                    }

                    if (it.width < 0) {
                        it.width = 0
                    }

                    if (it.height < 0) {
                        it.height = 0
                    }
                } catch (ignored: Exception) {
                }
            }
        }
    }

    private fun setupParticipants() {
        if (participants.isEmpty()) {
            participants = if (messages.isEmpty()) {
                val intentNumbers = getPhoneNumbersFromIntent()
                val participants = getThreadParticipants(threadId, null)
                fixParticipantNumbers(participants, intentNumbers)
            } else {
                messages.first().participants
            }
        }
    }

    private fun setupThreadTitle() {
        val threadTitle = participants.getThreadTitle()
        if (threadTitle.isNotEmpty()) {
            supportActionBar?.title = participants.getThreadTitle()
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupSIMSelector() {
        val availableSIMs = SubscriptionManager.from(this).activeSubscriptionInfoList ?: return
        if (availableSIMs.size > 1) {
            availableSIMs.forEachIndexed { index, subscriptionInfo ->
                var label = subscriptionInfo.displayName?.toString() ?: ""
                if (subscriptionInfo.number?.isNotEmpty() == true) {
                    label += " (${subscriptionInfo.number})"
                }
                val SIMCard = SIMCard(index + 1, subscriptionInfo.subscriptionId, label)
                availableSIMCards.add(SIMCard)
            }

            val numbers = ArrayList<String>()
            participants.forEach {
                it.phoneNumbers.forEach {
                    numbers.add(it)
                }
            }

            if (numbers.isEmpty()) {
                return
            }

            currentSIMCardIndex = availableSIMs.indexOfFirstOrNull { it.subscriptionId == config.getUseSIMIdAtNumber(numbers.first()) } ?: 0

            thread_select_sim_icon.applyColorFilter(config.textColor)
            thread_select_sim_icon.beVisible()
            thread_select_sim_number.beVisible()

            if (availableSIMCards.isNotEmpty()) {
                thread_select_sim_icon.setOnClickListener {
                    currentSIMCardIndex = (currentSIMCardIndex + 1) % availableSIMCards.size
                    val currentSIMCard = availableSIMCards[currentSIMCardIndex]
                    thread_select_sim_number.text = currentSIMCard.id.toString()
                    toast(currentSIMCard.label)
                }
            }

            thread_select_sim_number.setTextColor(config.textColor.getContrastColor())
            thread_select_sim_number.text = (availableSIMCards[currentSIMCardIndex].id).toString()
        }
    }

    private fun blockNumber() {
        val numbers = ArrayList<String>()
        participants.forEach {
            it.phoneNumbers.forEach {
                numbers.add(it)
            }
        }

        val numbersString = TextUtils.join(", ", numbers)
        val question = String.format(resources.getString(R.string.block_confirmation), numbersString)

        ConfirmationDialog(this, question) {
            ensureBackgroundThread {
                numbers.forEach {
                    addBlockedNumber(it)
                }
                refreshMessages()
                finish()
            }
        }
    }

    private fun askConfirmDelete() {
        ConfirmationDialog(this, getString(R.string.delete_whole_conversation_confirmation)) {
            ensureBackgroundThread {
                deleteConversation(threadId)
                runOnUiThread {
                    refreshMessages()
                    finish()
                }
            }
        }
    }

    private fun dialNumber() {
        val phoneNumber = participants.first().phoneNumbers.first()
        dialNumber(phoneNumber)
    }

    private fun managePeople() {
        if (thread_add_contacts.isVisible()) {
            hideKeyboard()
            thread_add_contacts.beGone()
        } else {
            showSelectedContacts()
            thread_add_contacts.beVisible()
            add_contact_or_number.requestFocus()
            showKeyboard(add_contact_or_number)
        }
    }

    private fun showSelectedContacts() {
        val adjustedColor = getAdjustedPrimaryColor()

        val views = ArrayList<View>()
        participants.forEach {
            val contact = it
            layoutInflater.inflate(R.layout.item_selected_contact, null).apply {
                val selectedContactBg = resources.getDrawable(R.drawable.item_selected_contact_background)
                (selectedContactBg as LayerDrawable).findDrawableByLayerId(R.id.selected_contact_bg).applyColorFilter(adjustedColor)
                selected_contact_holder.background = selectedContactBg

                selected_contact_name.text = contact.name
                selected_contact_name.setTextColor(adjustedColor.getContrastColor())
                selected_contact_remove.applyColorFilter(adjustedColor.getContrastColor())

                selected_contact_remove.setOnClickListener {
                    if (contact.rawId != participants.first().rawId) {
                        removeSelectedContact(contact.rawId)
                    }
                }
                views.add(this)
            }
        }
        showSelectedContact(views)
    }

    private fun addSelectedContact(contact: SimpleContact) {
        add_contact_or_number.setText("")
        if (participants.map { it.rawId }.contains(contact.rawId)) {
            return
        }

        participants.add(contact)
        showSelectedContacts()
    }

    private fun markAsUnread() {
        ensureBackgroundThread {
            conversationsDB.markUnread(threadId)
            markThreadMessagesUnread(threadId)
            runOnUiThread {
                finish()
                bus?.post(Events.RefreshMessages())
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getThreadItems(): ArrayList<ThreadItem> {
        val items = ArrayList<ThreadItem>()
        if (isFinishing) {
            return items
        }

        messages.sortBy { it.date }

        val subscriptionIdToSimId = HashMap<Int, String>()
        subscriptionIdToSimId[-1] = "?"
        SubscriptionManager.from(this).activeSubscriptionInfoList?.forEachIndexed { index, subscriptionInfo ->
            subscriptionIdToSimId[subscriptionInfo.subscriptionId] = "${index + 1}"
        }

        var prevDateTime = 0
        var hadUnreadItems = false
        val cnt = messages.size
        for (i in 0 until cnt) {
            val message = messages.getOrNull(i) ?: continue
            // do not show the date/time above every message, only if the difference between the 2 messages is at least MIN_DATE_TIME_DIFF_SECS
            if (message.date - prevDateTime > MIN_DATE_TIME_DIFF_SECS) {
                val simCardID = subscriptionIdToSimId[message.subscriptionId] ?: "?"
                items.add(ThreadDateTime(message.date, simCardID))
                prevDateTime = message.date
            }
            items.add(message)

            if (message.type == Telephony.Sms.MESSAGE_TYPE_FAILED) {
                items.add(ThreadError(message.id, message.body))
            }

            if (message.type == Telephony.Sms.MESSAGE_TYPE_OUTBOX) {
                items.add(ThreadSending(message.id))
            }

            if (!message.read) {
                hadUnreadItems = true
                markMessageRead(message.id, message.isMMS)
                conversationsDB.markRead(threadId)
            }

            if (i == cnt - 1 && (message.type == Telephony.Sms.MESSAGE_TYPE_SENT)) {
                items.add(ThreadSent(message.id, delivered = message.status == Telephony.Sms.STATUS_COMPLETE))
            }
        }

        if (hadUnreadItems) {
            bus?.post(Events.RefreshMessages())
        }

        return items
    }

    private fun launchPickPhotoVideoIntent() {
        val mimeTypes = arrayOf("image/*", "video/*")
        Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            startActivityForResult(this, PICK_ATTACHMENT_INTENT)
        }
    }

    private fun addAttachment(uri: Uri) {
        val originalUriString = uri.toString()
        if (attachmentSelections.containsKey(originalUriString)) {
            return
        }

        attachmentSelections[originalUriString] = AttachmentSelection(uri, false)
        val attachmentView = addAttachmentView(originalUriString, uri)
        val mimeType = contentResolver.getType(uri) ?: return

        if (mimeType.isImageMimeType() && config.mmsFileSizeLimit != FILE_SIZE_NONE) {
            val selection = attachmentSelections[originalUriString]
            attachmentSelections[originalUriString] = selection!!.copy(isPending = true)
            checkSendMessageAvailability()
            attachmentView.thread_attachment_progress.beVisible()
            imageCompressor.compressImage(uri, config.mmsFileSizeLimit) { compressedUri ->
                runOnUiThread {
                    if (compressedUri != null) {
                        attachmentSelections[originalUriString] = AttachmentSelection(compressedUri, false)
                        loadAttachmentPreview(attachmentView, compressedUri)
                    } else {
                        toast(R.string.compress_error)
                        removeAttachment(attachmentView, originalUriString)
                    }
                    checkSendMessageAvailability()
                    attachmentView.thread_attachment_progress.beGone()
                }
            }
        }
    }

    private fun addAttachmentView(originalUri: String, uri: Uri): View {
        thread_attachments_holder.beVisible()
        val attachmentView = layoutInflater.inflate(R.layout.item_attachment, null).apply {
            thread_attachments_wrapper.addView(this)
            thread_remove_attachment.setOnClickListener {
                removeAttachment(this, originalUri)
            }
        }

        loadAttachmentPreview(attachmentView, uri)
        return attachmentView
    }

    private fun loadAttachmentPreview(attachmentView: View, uri: Uri) {
        if (isDestroyed || isFinishing) {
            return
        }

        val roundedCornersRadius = resources.getDimension(R.dimen.medium_margin).toInt()
        val options = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .transform(CenterCrop(), RoundedCorners(roundedCornersRadius))

        Glide.with(attachmentView.thread_attachment_preview)
            .load(uri)
            .transition(DrawableTransitionOptions.withCrossFade())
            .apply(options)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                    attachmentView.thread_attachment_preview.beGone()
                    attachmentView.thread_remove_attachment.beGone()
                    return false
                }

                override fun onResourceReady(dr: Drawable?, a: Any?, t: Target<Drawable>?, d: DataSource?, i: Boolean): Boolean {
                    attachmentView.thread_attachment_preview.beVisible()
                    attachmentView.thread_remove_attachment.beVisible()
                    checkSendMessageAvailability()
                    return false
                }
            })
            .into(attachmentView.thread_attachment_preview)
    }

    private fun removeAttachment(attachmentView: View, originalUri: String) {
        thread_attachments_wrapper.removeView(attachmentView)
        attachmentSelections.remove(originalUri)
        if (attachmentSelections.isEmpty()) {
            thread_attachments_holder.beGone()
        }
    }

    private fun checkSendMessageAvailability() {
        if (thread_type_message.text.isNotEmpty() || (attachmentSelections.isNotEmpty() && !attachmentSelections.values.any { it.isPending })) {
            thread_send_message.isClickable = true
            thread_send_message.alpha = 0.9f
        } else {
            thread_send_message.isClickable = false
            thread_send_message.alpha = 0.4f
        }
    }

    private fun sendMessage() {
        var msg = thread_type_message.value
        if (msg.isEmpty() && attachmentSelections.isEmpty()) {
            return
        }

        msg = removeDiacriticsIfNeeded(msg)

        val numbers = ArrayList<String>()
        participants.forEach {
            it.phoneNumbers.forEach {
                numbers.add(it)
            }
        }

        val settings = Settings()
        settings.useSystemSending = true
        settings.deliveryReports = config.enableDeliveryReports

        val SIMId = availableSIMCards.getOrNull(currentSIMCardIndex)?.subscriptionId
        if (SIMId != null) {
            settings.subscriptionId = SIMId
            numbers.forEach {
                config.saveUseSIMIdAtNumber(it, SIMId)
            }
        }

        val transaction = Transaction(this, settings)
        val message = com.klinker.android.send_message.Message(msg, numbers.toTypedArray())

        if (attachmentSelections.isNotEmpty()) {
            for (selection in attachmentSelections.values) {
                try {
                    val byteArray = contentResolver.openInputStream(selection.uri)?.readBytes() ?: continue
                    val mimeType = contentResolver.getType(selection.uri) ?: continue
                    message.addMedia(byteArray, mimeType)
                } catch (e: Exception) {
                    showErrorToast(e)
                } catch (e: Error) {
                    toast(e.localizedMessage ?: getString(R.string.unknown_error_occurred))
                }
            }
        }

        try {
            val smsSentIntent = Intent(this, SmsStatusSentReceiver::class.java)
            val deliveredIntent = Intent(this, SmsStatusDeliveredReceiver::class.java)

            transaction.setExplicitBroadcastForSentSms(smsSentIntent)
            transaction.setExplicitBroadcastForDeliveredSms(deliveredIntent)

            refreshedSinceSent = false
            transaction.sendNewMessage(message, threadId)
            thread_type_message.setText("")
            attachmentSelections.clear()
            thread_attachments_holder.beGone()
            thread_attachments_wrapper.removeAllViews()

            Handler().postDelayed({
                if (!refreshedSinceSent) {
                    refreshMessages()
                }
            }, 2000)
        } catch (e: Exception) {
            showErrorToast(e)
        } catch (e: Error) {
            toast(e.localizedMessage ?: getString(R.string.unknown_error_occurred))
        }
    }

    // show selected contacts, properly split to new lines when appropriate
    // based on https://stackoverflow.com/a/13505029/1967672
    private fun showSelectedContact(views: ArrayList<View>) {
        selected_contacts.removeAllViews()
        var newLinearLayout = LinearLayout(this)
        newLinearLayout.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        newLinearLayout.orientation = LinearLayout.HORIZONTAL

        val sideMargin = (selected_contacts.layoutParams as RelativeLayout.LayoutParams).leftMargin
        val mediumMargin = resources.getDimension(R.dimen.medium_margin).toInt()
        val parentWidth = realScreenSize.x - sideMargin * 2
        val firstRowWidth = parentWidth - resources.getDimension(R.dimen.normal_icon_size).toInt() + sideMargin / 2
        var widthSoFar = 0
        var isFirstRow = true

        for (i in views.indices) {
            val LL = LinearLayout(this)
            LL.orientation = LinearLayout.HORIZONTAL
            LL.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            LL.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            views[i].measure(0, 0)

            var params = LayoutParams(views[i].measuredWidth, LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, mediumMargin, 0)
            LL.addView(views[i], params)
            LL.measure(0, 0)
            widthSoFar += views[i].measuredWidth + mediumMargin

            val checkWidth = if (isFirstRow) firstRowWidth else parentWidth
            if (widthSoFar >= checkWidth) {
                isFirstRow = false
                selected_contacts.addView(newLinearLayout)
                newLinearLayout = LinearLayout(this)
                newLinearLayout.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                newLinearLayout.orientation = LinearLayout.HORIZONTAL
                params = LayoutParams(LL.measuredWidth, LL.measuredHeight)
                params.topMargin = mediumMargin
                newLinearLayout.addView(LL, params)
                widthSoFar = LL.measuredWidth
            } else {
                if (!isFirstRow) {
                    (LL.layoutParams as LayoutParams).topMargin = mediumMargin
                }
                newLinearLayout.addView(LL)
            }
        }
        selected_contacts.addView(newLinearLayout)
    }

    private fun removeSelectedContact(id: Int) {
        participants = participants.filter { it.rawId != id }.toMutableList() as ArrayList<SimpleContact>
        showSelectedContacts()
    }

    private fun getPhoneNumbersFromIntent(): ArrayList<String> {
        val numberFromIntent = intent.getStringExtra(THREAD_NUMBER)
        val numbers = ArrayList<String>()

        if (numberFromIntent != null) {
            if (numberFromIntent.startsWith('[') && numberFromIntent.endsWith(']')) {
                val type = object : TypeToken<List<String>>() {}.type
                numbers.addAll(Gson().fromJson(numberFromIntent, type))
            } else {
                numbers.add(numberFromIntent)
            }
        }
        return numbers
    }

    private fun fixParticipantNumbers(participants: ArrayList<SimpleContact>, properNumbers: ArrayList<String>): ArrayList<SimpleContact> {
        for (number in properNumbers) {
            for (participant in participants) {
                participant.phoneNumbers = participant.phoneNumbers.map {
                    val numberWithoutPlus = number.replace("+", "")
                    if (numberWithoutPlus == it.trim()) {
                        if (participant.name == it) {
                            participant.name = number
                        }
                        number
                    } else {
                        it
                    }
                } as ArrayList<String>
            }
        }

        return participants
    }

    fun startContactDetailsIntent(contact: SimpleContact) {
        val simpleContacts = "com.simplemobiletools.contacts.pro"
        val simpleContactsDebug = "com.simplemobiletools.contacts.pro.debug"
        if (contact.rawId > 1000000 && contact.contactId > 1000000 && contact.rawId == contact.contactId &&
            (isPackageInstalled(simpleContacts) || isPackageInstalled(simpleContactsDebug))
        ) {
            Intent().apply {
                action = Intent.ACTION_VIEW
                putExtra(CONTACT_ID, contact.rawId)
                putExtra(IS_PRIVATE, true)
                `package` = if (isPackageInstalled(simpleContacts)) simpleContacts else simpleContactsDebug
                setDataAndType(ContactsContract.Contacts.CONTENT_LOOKUP_URI, "vnd.android.cursor.dir/person")
                launchActivityIntent(this)
            }
        } else {
            ensureBackgroundThread {
                val lookupKey = SimpleContactsHelper(this).getContactLookupKey((contact).rawId.toString())
                val publicUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
                runOnUiThread {
                    launchViewContactIntent(publicUri)
                }
            }
        }
    }

    fun saveMMS(mimeType: String, path: String) {
        lastAttachmentUri = path
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = mimeType
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, path.split("/").last())
            startActivityForResult(this, PICK_SAVE_FILE_INTENT)
        }
    }

    @SuppressLint("MissingPermission")
    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun refreshMessages(event: Events.RefreshMessages) {
        refreshedSinceSent = true
        if (isActivityVisible) {
            notificationManager.cancel(threadId.hashCode())
        }

        val lastMaxId = messages.maxByOrNull { it.id }?.id ?: 0L
        messages = getMessages(threadId)

        messages.filter { !it.isReceivedMessage() && it.id > lastMaxId }.forEach { latestMessage ->
            // subscriptionIds seem to be not filled out at sending with multiple SIM cards, so fill it manually
            if (SubscriptionManager.from(this).activeSubscriptionInfoList?.size ?: 0 > 1) {
                val SIMId = availableSIMCards.getOrNull(currentSIMCardIndex)?.subscriptionId
                if (SIMId != null) {
                    updateMessageSubscriptionId(latestMessage.id, SIMId)
                    latestMessage.subscriptionId = SIMId
                }
            }

            messagesDB.insertOrIgnore(latestMessage)
        }

        setupAdapter()
    }
}
