package com.simplemobiletools.smsmessenger.dialogs

import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.dialogs.FilePickerDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.helpers.EXPORT_FILE_EXT
import com.simplemobiletools.smsmessenger.helpers.EXPORT_SECURE_FILE_EXT
import kotlinx.android.synthetic.main.dialog_export_messages.view.*
import java.io.File

class ExportMessagesDialog(
    private val activity: SimpleActivity,
    private val path: String,
    private val hidePath: Boolean,
    private val callback: (file: File) -> Unit,
) {
    private var realPath = if (path.isEmpty()) activity.internalStoragePath else path
    private val config = activity.config

    init {
        val view = (activity.layoutInflater.inflate(R.layout.dialog_export_messages, null) as ViewGroup).apply {
            export_messages_folder.text = activity.humanizePath(realPath)
            export_messages_filename.setText("${activity.getString(R.string.messages)}_${activity.getCurrentFormattedDateTime()}")
            export_sms_checkbox.isChecked = config.exportSms
            export_mms_checkbox.isChecked = config.exportMms

            if (hidePath) {
                export_messages_folder_label.beGone()
                export_messages_folder.beGone()
            } else {
                export_messages_folder.setOnClickListener {
                    activity.hideKeyboard(export_messages_filename)
                    FilePickerDialog(activity, realPath, false, showFAB = true) {
                        export_messages_folder.text = activity.humanizePath(it)
                        realPath = it
                    }
                }
            }
        }

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view, this, R.string.export_messages) {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val filename = view.export_messages_filename.value
                        when {
                            filename.isEmpty() -> activity.toast(R.string.empty_name)
                            filename.isAValidFilename() -> {
                                config.exportBackupPassword = view.export_messages_password.value //We need to get this early to set proper extension
                                val file = if (config.exportBackupPassword == "") File(realPath, "$filename$EXPORT_FILE_EXT") else File(realPath, "$filename$EXPORT_SECURE_FILE_EXT")
                                if (!hidePath && file.exists()) {
                                    activity.toast(R.string.name_taken)
                                    return@setOnClickListener
                                }

                                if (!view.export_sms_checkbox.isChecked && !view.export_mms_checkbox.isChecked) {
                                    activity.toast(R.string.no_option_selected)
                                    return@setOnClickListener
                                }

                                config.exportSms = view.export_sms_checkbox.isChecked
                                config.exportMms = view.export_mms_checkbox.isChecked
                                config.lastExportPath = file.absolutePath.getParentPath()
                                callback(file)
                                dismiss()
                            }
                            else -> activity.toast(R.string.invalid_name)
                        }
                    }
                }
            }
    }
}
