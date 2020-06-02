package com.simplemobiletools.smsmessenger.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.WindowManager
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.MyContactsContentProvider
import com.simplemobiletools.commons.helpers.PERMISSION_READ_CONTACTS
import com.simplemobiletools.commons.helpers.SimpleContactsHelper
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.ContactsAdapter
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.extensions.getSuggestedContacts
import com.simplemobiletools.smsmessenger.extensions.getThreadId
import com.simplemobiletools.smsmessenger.helpers.*
import kotlinx.android.synthetic.main.activity_new_conversation.*
import kotlinx.android.synthetic.main.item_suggested_contact.view.*
import java.net.URLDecoder
import java.util.*
import kotlin.collections.ArrayList

class NewConversationActivity : SimpleActivity() {
    private var allContacts = ArrayList<SimpleContact>()
    private var privateContacts = ArrayList<SimpleContact>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_conversation)
        title = getString(R.string.new_conversation)
        updateTextColors(new_conversation_holder)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        new_conversation_address.requestFocus()

        // READ_CONTACTS permission is not mandatory, but without it we won't be able to show any suggestions during typing
        handlePermission(PERMISSION_READ_CONTACTS) {
            initContacts()
        }
    }

    override fun onResume() {
        super.onResume()
        no_contacts_placeholder_2.setTextColor(getAdjustedPrimaryColor())
        no_contacts_placeholder_2.underlineText()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun initContacts() {
        if (isThirdPartyIntent()) {
            return
        }

        fetchContacts()
        new_conversation_address.onTextChangeListener {
            val searchString = it
            val filteredContacts = ArrayList<SimpleContact>()
            allContacts.forEach {
                if (it.phoneNumber.contains(searchString, true) || it.name.contains(searchString, true)) {
                    filteredContacts.add(it)
                }
            }

            filteredContacts.sortWith(compareBy { !it.name.startsWith(searchString, true) })
            setupAdapter(filteredContacts)

            new_conversation_confirm.beVisibleIf(searchString.length > 2)
        }

        new_conversation_confirm.applyColorFilter(config.textColor)
        new_conversation_confirm.setOnClickListener {
            val number = new_conversation_address.value
            launchThreadActivity(number, number)
        }

        no_contacts_placeholder_2.setOnClickListener {
            handlePermission(PERMISSION_READ_CONTACTS) {
                if (it) {
                    fetchContacts()
                }
            }
        }

        contacts_letter_fastscroller.textColor = config.textColor.getColorStateList()
        contacts_letter_fastscroller_thumb.setupWithFastScroller(contacts_letter_fastscroller)
        contacts_letter_fastscroller_thumb.textColor = config.primaryColor.getContrastColor()
    }

    private fun isThirdPartyIntent(): Boolean {
        if ((intent.action == Intent.ACTION_SENDTO || intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_VIEW) && intent.dataString != null) {
            val number = intent.dataString!!.removePrefix("sms:").removePrefix("smsto:").removePrefix("mms").removePrefix("mmsto:").trim()
            launchThreadActivity(URLDecoder.decode(number), "")
            finish()
            return true
        }
        return false
    }

    private fun fetchContacts() {
        fillSuggestedContacts {
            SimpleContactsHelper(this).getAvailableContacts(false) {
                allContacts = it

                if (privateContacts.isNotEmpty()) {
                    allContacts.addAll(privateContacts)
                    allContacts.sort()
                }

                runOnUiThread {
                    setupAdapter(allContacts)
                }
            }
        }
    }

    private fun setupAdapter(contacts: ArrayList<SimpleContact>) {
        val hasContacts = contacts.isNotEmpty()
        contacts_list.beVisibleIf(hasContacts)
        no_contacts_placeholder.beVisibleIf(!hasContacts)
        no_contacts_placeholder_2.beVisibleIf(!hasContacts && !hasPermission(PERMISSION_READ_CONTACTS))

        if (!hasContacts) {
            val placeholderText = if (hasPermission(PERMISSION_READ_CONTACTS)) R.string.no_contacts_found else R.string.no_access_to_contacts
            no_contacts_placeholder.text = getString(placeholderText)
        }

        ContactsAdapter(this, contacts, contacts_list, null) {
            hideKeyboard()
            launchThreadActivity((it as SimpleContact).phoneNumber, it.name)
        }.apply {
            contacts_list.adapter = this
        }

        setupLetterFastscroller(contacts)
    }

    private fun fillSuggestedContacts(callback: () -> Unit) {
        val privateCursor = getMyContactsContentProviderCursorLoader().loadInBackground()
        ensureBackgroundThread {
            privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            val suggestions = getSuggestedContacts(privateContacts)
            runOnUiThread {
                suggestions_holder.removeAllViews()
                if (suggestions.isEmpty()) {
                    suggestions_label.beGone()
                    suggestions_scrollview.beGone()
                } else {
                    suggestions_label.beVisible()
                    suggestions_scrollview.beVisible()
                    suggestions.forEach {
                        val contact = it
                        layoutInflater.inflate(R.layout.item_suggested_contact, null).apply {
                            suggested_contact_name.text = contact.name
                            SimpleContactsHelper(this@NewConversationActivity).loadContactImage(contact.photoUri, suggested_contact_image, contact.name)
                            suggestions_holder.addView(this)
                            setOnClickListener {
                                launchThreadActivity(contact.phoneNumber, contact.name)
                            }
                        }
                    }
                }
                callback()
            }
        }
    }

    private fun setupLetterFastscroller(contacts: ArrayList<SimpleContact>) {
        contacts_letter_fastscroller.setupWithRecyclerView(contacts_list, { position ->
            try {
                val name = contacts[position].name
                val character = if (name.isNotEmpty()) name.substring(0, 1) else ""
                FastScrollItemIndicator.Text(character.toUpperCase(Locale.getDefault()))
            } catch (e: Exception) {
                FastScrollItemIndicator.Text("")
            }
        })
    }

    private fun launchThreadActivity(phoneNumber: String, name: String) {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, getThreadId(phoneNumber).toInt())
            putExtra(THREAD_TITLE, name)
            putExtra(THREAD_TEXT, text)
            putExtra(THREAD_NUMBER, phoneNumber)

            if (intent.action == Intent.ACTION_SEND && intent.extras?.containsKey(Intent.EXTRA_STREAM) == true) {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                putExtra(THREAD_ATTACHMENT_URI, uri?.toString())
            } else if (intent.action == Intent.ACTION_SEND_MULTIPLE && intent.extras?.containsKey(Intent.EXTRA_STREAM) == true) {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                putExtra(THREAD_ATTACHMENT_URIS, uris)
            }

            startActivity(this)
        }
    }
}
