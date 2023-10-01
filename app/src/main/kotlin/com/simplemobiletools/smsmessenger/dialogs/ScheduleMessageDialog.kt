package com.simplemobiletools.smsmessenger.dialogs

import android.app.DatePickerDialog
import android.app.DatePickerDialog.OnDateSetListener
import android.app.TimePickerDialog
import android.app.TimePickerDialog.OnTimeSetListener
import android.text.format.DateFormat
import androidx.appcompat.app.AlertDialog
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.databinding.ScheduleMessageDialogBinding
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.extensions.roundToClosestMultipleOf
import org.joda.time.DateTime
import java.util.Calendar

class ScheduleMessageDialog(
    private val activity: BaseSimpleActivity,
    private var dateTime: DateTime? = null,
    private val callback: (dateTime: DateTime?) -> Unit
) {
    private val binding = ScheduleMessageDialogBinding.inflate(activity.layoutInflater)
    private val textColor = activity.getProperTextColor()

    private var previewDialog: AlertDialog? = null
    private var previewShown = false
    private var isNewMessage = dateTime == null

    private val calendar = Calendar.getInstance()

    init {
        arrayOf(binding.subtitle, binding.editTime, binding.editDate).forEach {
            it.setTextColor(textColor)
        }

        arrayOf(binding.dateImage, binding.timeImage).forEach {
            it.applyColorFilter(textColor)
        }

        binding.editDate.setOnClickListener { showDatePicker() }
        binding.editTime.setOnClickListener { showTimePicker() }

        val targetDateTime = dateTime ?: DateTime.now().plusHours(1)
        updateTexts(targetDateTime)

        if (isNewMessage) {
            showDatePicker()
        } else {
            showPreview()
        }
    }

    private fun updateTexts(dateTime: DateTime) {
        val dateFormat = activity.config.dateFormat
        val timeFormat = activity.getTimeFormat()
        binding.editDate.text = dateTime.toString(dateFormat)
        binding.editTime.text = dateTime.toString(timeFormat)
    }

    private fun showPreview() {
        if (previewShown) {
            return
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok, null)
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .apply {
                previewShown = true
                activity.setupDialogStuff(binding.root, this, R.string.schedule_message) { dialog ->
                    previewDialog = dialog
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (validateDateTime()) {
                            callback(dateTime)
                            dialog.dismiss()
                        }
                    }

                    dialog.setOnDismissListener {
                        previewShown = false
                        previewDialog = null
                    }
                }
            }
    }

    private fun showDatePicker() {
        val year = dateTime?.year ?: calendar.get(Calendar.YEAR)
        val monthOfYear = dateTime?.monthOfYear?.minus(1) ?: calendar.get(Calendar.MONTH)
        val dayOfMonth = dateTime?.dayOfMonth ?: calendar.get(Calendar.DAY_OF_MONTH)

        val dateSetListener = OnDateSetListener { _, y, m, d -> dateSet(y, m, d) }
        DatePickerDialog(
            activity, activity.getDatePickerDialogTheme(), dateSetListener, year, monthOfYear, dayOfMonth
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
            show()
            getButton(AlertDialog.BUTTON_NEGATIVE).apply {
                text = activity.getString(com.simplemobiletools.commons.R.string.cancel)
                setOnClickListener {
                    dismiss()
                }
            }
        }
    }

    private fun showTimePicker() {
        val hourOfDay = dateTime?.hourOfDay ?: getNextHour()
        val minute = dateTime?.minuteOfHour ?: getNextMinute()

        if (activity.config.isUsingSystemTheme) {
            val timeFormat = if (DateFormat.is24HourFormat(activity)) {
                TimeFormat.CLOCK_24H
            } else {
                TimeFormat.CLOCK_12H
            }

            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(timeFormat)
                .setHour(hourOfDay)
                .setMinute(minute)
                .build()

            timePicker.addOnPositiveButtonClickListener {
                timeSet(timePicker.hour, timePicker.minute)
            }

            timePicker.show(activity.supportFragmentManager, "")
        } else {
            val timeSetListener = OnTimeSetListener { _, hours, minutes -> timeSet(hours, minutes) }
            TimePickerDialog(
                activity, activity.getDatePickerDialogTheme(), timeSetListener, hourOfDay, minute, DateFormat.is24HourFormat(activity)
            ).apply {
                show()
                getButton(AlertDialog.BUTTON_NEGATIVE).apply {
                    text = activity.getString(com.simplemobiletools.commons.R.string.cancel)
                    setOnClickListener {
                        dismiss()
                    }
                }
            }
        }
    }

    private fun dateSet(year: Int, monthOfYear: Int, dayOfMonth: Int) {
        if (isNewMessage) {
            showTimePicker()
        }

        dateTime = DateTime.now()
            .withDate(year, monthOfYear + 1, dayOfMonth)
            .run {
                if (dateTime != null) {
                    withTime(dateTime!!.hourOfDay, dateTime!!.minuteOfHour, 0, 0)
                } else {
                    withTime(getNextHour(), getNextMinute(), 0, 0)
                }
            }

        if (!isNewMessage) {
            validateDateTime()
        }

        isNewMessage = false
        updateTexts(dateTime!!)
    }

    private fun timeSet(hourOfDay: Int, minute: Int) {
        dateTime = dateTime?.withHourOfDay(hourOfDay)?.withMinuteOfHour(minute)
        if (validateDateTime()) {
            updateTexts(dateTime!!)
            showPreview()
        } else {
            showTimePicker()
        }
    }

    private fun validateDateTime(): Boolean {
        return if (dateTime?.isAfterNow == false) {
            activity.toast(R.string.must_pick_time_in_the_future)
            false
        } else {
            true
        }
    }

    private fun getNextHour() = (calendar.get(Calendar.HOUR_OF_DAY) + 1).coerceIn(0, 23)

    private fun getNextMinute() = (calendar.get(Calendar.MINUTE) + 5).roundToClosestMultipleOf(5).coerceIn(0, 59)
}
