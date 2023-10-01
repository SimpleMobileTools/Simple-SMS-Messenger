package com.simplemobiletools.smsmessenger.activities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.smsmessenger.adapters.ContactsAdapter
import com.simplemobiletools.smsmessenger.databinding.ActivityConversationDetailsBinding
import com.simplemobiletools.smsmessenger.dialogs.RenameConversationDialog
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.THREAD_ID
import com.simplemobiletools.smsmessenger.models.Conversation

class ConversationDetailsActivity : SimpleActivity() {

    private var threadId: Long = 0L
    private var conversation: Conversation? = null
    private lateinit var participants: ArrayList<SimpleContact>

    private val binding by viewBinding(ActivityConversationDetailsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        updateMaterialActivityViews(
            mainCoordinatorLayout = binding.conversationDetailsCoordinator,
            nestedView = binding.participantsRecyclerview,
            useTransparentNavigation = true,
            useTopSearchMenu = false
        )
        setupMaterialScrollListener(scrollingView = binding.participantsRecyclerview, toolbar = binding.conversationDetailsToolbar)

        threadId = intent.getLongExtra(THREAD_ID, 0L)
        ensureBackgroundThread {
            conversation = conversationsDB.getConversationWithThreadId(threadId)
            participants = if (conversation != null && conversation!!.isScheduled) {
                val message = messagesDB.getThreadMessages(conversation!!.threadId).firstOrNull()
                message?.participants ?: arrayListOf()
            } else {
                getThreadParticipants(threadId, null)
            }
            runOnUiThread {
                setupTextViews()
                setupParticipants()
                if (isOreoPlus()) {
                    setupCustomNotifications()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.conversationDetailsToolbar, NavigationIcon.Arrow)
        updateTextColors(binding.conversationDetailsHolder)

        val primaryColor = getProperPrimaryColor()
        binding.conversationNameHeading.setTextColor(primaryColor)
        binding.membersHeading.setTextColor(primaryColor)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupCustomNotifications() {
        notifications_heading.beVisible()
        custom_notifications_holder.beVisible()
        custom_notifications.isChecked = config.customNotifications.contains(threadId.toString())
        custom_notifications_button.beVisibleIf(custom_notifications.isChecked)

        custom_notifications_holder.setOnClickListener {
            custom_notifications.toggle()
            if (custom_notifications.isChecked) {
                custom_notifications_button.beVisible()
                config.addCustomNotificationsByThreadId(threadId)
                createNotificationChannel()
            } else {
                custom_notifications_button.beGone()
                config.removeCustomNotificationsByThreadId(threadId)
                removeNotificationChannel()
            }
        }

        custom_notifications_button.setOnClickListener {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, threadId.hashCode().toString())
                startActivity(this)
            }
        }
    }

    private fun getNotificationManager() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val name = conversation?.title
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
            .build()

        NotificationChannel(threadId.hashCode().toString(), name, NotificationManager.IMPORTANCE_HIGH).apply {
            setBypassDnd(false)
            enableLights(true)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes)
            enableVibration(true)
            getNotificationManager().createNotificationChannel(this)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun removeNotificationChannel() {
        getNotificationManager().deleteNotificationChannel(threadId.hashCode().toString())
    }

    private fun setupTextViews() {
        binding.conversationName.apply {
            ResourcesCompat.getDrawable(resources, com.simplemobiletools.commons.R.drawable.ic_edit_vector, theme)?.apply {
                applyColorFilter(getProperTextColor())
                setCompoundDrawablesWithIntrinsicBounds(null, null, this, null)
            }

            text = conversation?.title
            setOnClickListener {
                RenameConversationDialog(this@ConversationDetailsActivity, conversation!!) { title ->
                    text = title
                    ensureBackgroundThread {
                        conversation = renameConversation(conversation!!, newTitle = title)
                    }
                }
            }
        }
    }

    private fun setupParticipants() {
        val adapter = ContactsAdapter(this, participants, binding.participantsRecyclerview) {
            val contact = it as SimpleContact
            val address = contact.phoneNumbers.first().normalizedNumber
            getContactFromAddress(address) { simpleContact ->
                if (simpleContact != null) {
                    startContactDetailsIntent(simpleContact)
                }
            }
        }
        binding.participantsRecyclerview.adapter = adapter
    }
}
