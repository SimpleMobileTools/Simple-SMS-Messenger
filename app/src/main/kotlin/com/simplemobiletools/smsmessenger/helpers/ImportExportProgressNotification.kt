package com.simplemobiletools.smsmessenger.helpers

import android.app.*
import android.content.Context.NOTIFICATION_SERVICE
import androidx.core.app.NotificationCompat
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import java.util.*

class ImportExportProgressNotification(
    private val activity: SimpleActivity,
    private val type: ImportOrExport
){
    enum class ImportOrExport{
        IMPORT, EXPORT
    }
    private var isInit = false
    private lateinit var notification: NotificationCompat.Builder
    private lateinit var channelId: String
    private val progressMax = 100
    private val notificationManager = activity.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    private var waitTime = 0L
    private var startTime = 0L

    init {
        startTime = System.currentTimeMillis()
        if (isOreoPlus()) {
            channelId = activity.getString(R.string.channel_import_export)
            val name = activity.getString(R.string.channel_import_export)
            val importance = NotificationManager.IMPORTANCE_LOW
            val mChannel = NotificationChannel(channelId, name, importance)

            notificationManager.createNotificationChannel(mChannel)
        }
    }

    fun setFinish(hasSucceed: Boolean) {
        if (isInit) {
            isInit = false
            val contentTitle =  if(type == ImportOrExport.EXPORT) activity.getString(R.string.exporting_successful)
                                else activity.getString(R.string.importing_successful)
            notification.setContentTitle(contentTitle)

            if(hasSucceed) {
                notification.setContentText("Success")
                    .setProgress(0,0, false)
                    .setOngoing(false)
                    .setAutoCancel(true)
            } else {
                notification.setContentText("Something went wrong")
                    .setProgress(0,0, false)
                    .setOngoing(false)
                    .setAutoCancel(true)
            }
            notificationManager.notify(EXPORT_IMPORT_NOTIFICATION_ID, notification.build())
        }
    }

    fun spawnProgressNotification()
    {
        val contentTitle =  if(type == ImportOrExport.IMPORT) activity.getString(R.string.importing_messages)
                            else activity.getString(R.string.exporting_messages)
        //Creating a notification and setting its various attributes
        notification =
            NotificationCompat.Builder(activity, channelId)
                .setSmallIcon(R.drawable.ic_messenger)
                .setContentTitle(contentTitle)
                .setContentText("0%")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(true)
                .setProgress(progressMax, 0, false)

        notificationManager.notify(EXPORT_IMPORT_NOTIFICATION_ID, notification.build())
        isInit = true
        }

    fun updateNotification(state: Any, total: Int, current: Int) {
        if(isInit) {
            if (waitTime > 500)
            {
                if (total > 1 && current <= total && current > 0) {
                    val progress = current.toDouble() / total * 100.00
                    notification.setContentText(progress.toInt().toString() + "%")
                                .setProgress(progressMax, progress.toInt(), false)
                }

                if (type == ImportOrExport.EXPORT) {
                    when(state) {
                        MessagesExporter.ExportState.EXPORT -> notification.setContentTitle(activity.getString(R.string.exporting_messages))
                        MessagesExporter.ExportState.ENCRYPT -> notification.setContentTitle(activity.getString(R.string.encrypting_backup))
                    }
                } else {
                    when(state) {
                        MessagesImporter.ImportState.DECRYPTING -> {
                            notification.setContentTitle(activity.getString(R.string.decrypting_backup))
                                        .setProgress(100, 50, true)
                        }
                        MessagesImporter.ImportState.RESTORING -> notification.setContentTitle(activity.getString(R.string.importing_messages))
                    }
                }

                notificationManager.notify(EXPORT_IMPORT_NOTIFICATION_ID, notification.build())
                startTime = System.currentTimeMillis()
                waitTime = 0L
            } else {
                waitTime = Date().time - startTime
            }
        }
    }
}
