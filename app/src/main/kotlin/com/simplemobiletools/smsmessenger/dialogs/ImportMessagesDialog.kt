package com.simplemobiletools.smsmessenger.dialogs

import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.extensions.value
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.helpers.ImportExportProgressNotification
import com.simplemobiletools.smsmessenger.helpers.MessagesImporter
import com.simplemobiletools.smsmessenger.helpers.MessagesImporter.ImportResult.IMPORT_OK
import com.simplemobiletools.smsmessenger.helpers.MessagesImporter.ImportResult.IMPORT_PARTIAL
import kotlinx.android.synthetic.main.dialog_import_messages.view.*

class ImportMessagesDialog(
    private val activity: SimpleActivity,
    private val path: String,
    private val importNotification: ImportExportProgressNotification,
) {

    private val config = activity.config

    init {
        var ignoreClicks = false
        val view = (activity.layoutInflater.inflate(R.layout.dialog_import_messages, null) as ViewGroup).apply {
            import_sms_checkbox.isChecked = config.importSms
            import_mms_checkbox.isChecked = config.importMms
        }

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view, this, R.string.import_messages) {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (ignoreClicks) {
                            return@setOnClickListener
                        }

                        if (!view.import_sms_checkbox.isChecked && !view.import_mms_checkbox.isChecked) {
                            activity.toast(R.string.no_option_selected)
                            return@setOnClickListener
                        }

                        ignoreClicks = true
                        importNotification.spawnProgressNotification()
                        activity.toast(R.string.importing)
                        config.importSms = view.import_sms_checkbox.isChecked
                        config.importMms = view.import_mms_checkbox.isChecked
                        config.importBackupPassword = view.import_messages_password.value
                        ensureBackgroundThread {
                            MessagesImporter(activity).importMessages(path,
                                {state, total, current -> importNotification.updateNotification(state, total, current)}) {
                                handleParseResult(it)
                                dismiss()
                            }
                        }
                    }
                }
            }
    }

    private fun handleParseResult(result: MessagesImporter.ImportResult) {
        activity.toast(
            when (result) {
                IMPORT_OK -> R.string.importing_successful
                IMPORT_PARTIAL -> R.string.importing_some_entries_failed
                else -> R.string.no_items_found
            }
        )
        importNotification.setFinish(result == IMPORT_OK)
    }
}
