package io.github.aiya000.webbyimagesdownloader

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Tracks one batch of DownloadManager downloads and reports when the whole batch has finished,
 * both as a notification and through [completed] for an in-app toast.
 *
 * State lives in SharedPreferences so the completion broadcast can be handled even if the process was killed.
 */
object DownloadTracker {
    data class BatchResult(val total: Int, val failed: Int) {
        val succeeded: Int get() = total - failed
    }

    private const val PREFS = "download_tracker"
    private const val KEY_PENDING_IDS = "pending_ids"
    private const val KEY_TOTAL = "total"
    private const val KEY_FAILED = "failed"

    private const val CHANNEL_ID = "download_complete"
    private const val NOTIFICATION_ID = 1

    private val _completed = MutableSharedFlow<BatchResult>(extraBufferCapacity = 1)

    /** Emits once per finished batch while someone is collecting (i.e. the app is in the foreground). */
    val completed: SharedFlow<BatchResult> = _completed.asSharedFlow()

    @Synchronized
    fun startBatch(context: Context, downloadIds: List<Long>) {
        if (downloadIds.isEmpty()) return
        val prefs = prefs(context)
        val pending = prefs.getStringSet(KEY_PENDING_IDS, emptySet()).orEmpty()
        val isNewBatch = pending.isEmpty()
        prefs.edit {
            putStringSet(KEY_PENDING_IDS, pending + downloadIds.map { it.toString() })
            putInt(KEY_TOTAL, (if (isNewBatch) 0 else prefs.getInt(KEY_TOTAL, 0)) + downloadIds.size)
            putInt(KEY_FAILED, if (isNewBatch) 0 else prefs.getInt(KEY_FAILED, 0))
        }
    }

    @Synchronized
    fun onDownloadComplete(context: Context, downloadId: Long) {
        val prefs = prefs(context)
        val pending = prefs.getStringSet(KEY_PENDING_IDS, emptySet()).orEmpty().toMutableSet()
        if (!pending.remove(downloadId.toString())) return

        val failed = prefs.getInt(KEY_FAILED, 0) + if (isSuccessful(context, downloadId)) 0 else 1
        if (pending.isNotEmpty()) {
            prefs.edit {
                putStringSet(KEY_PENDING_IDS, pending)
                putInt(KEY_FAILED, failed)
            }
            return
        }

        val result = BatchResult(total = prefs.getInt(KEY_TOTAL, 0), failed = failed)
        prefs.edit { clear() }
        showNotification(context, result)
        _completed.tryEmit(result)
    }

    fun message(context: Context, result: BatchResult): String =
        if (result.failed == 0) {
            context.getString(R.string.download_complete_text, result.succeeded)
        } else {
            context.getString(R.string.download_complete_text_with_failures, result.succeeded, result.failed)
        }

    private fun isSuccessful(context: Context, downloadId: Long): Boolean {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return false
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            return status == DownloadManager.STATUS_SUCCESSFUL
        }
    }

    private fun showNotification(context: Context, result: BatchResult) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_download_complete),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )

        val openDownloads = PendingIntent.getActivity(
            context,
            0,
            Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.download_complete_title))
            .setContentText(message(context, result))
            .setContentIntent(openDownloads)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
