package wales.tucker.seren.ssh.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import wales.tucker.seren.core.ui.formatSize
import wales.tucker.seren.ssh.MainActivity
import wales.tucker.seren.ssh.R

/**
 * Progress notification for an SFTP upload or download. SessionService already keeps the process
 * alive; this is a second, low-importance notification so people can leave the Files screen and
 * still see (and cancel) the transfer.
 */
class SftpTransferNotifier(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(NotificationManager::class.java)

    @Volatile private var cancelHandler: (() -> Unit)? = null
    private var lastPercent = -1
    private var lastNotifyAt = 0L
    private var sessionId: Int = -1
    private var shown = false

    /** True while the SFTP screen is composed and can show a snackbar for the result. */
    @Volatile var sftpVisible: Boolean = false

    fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                app.getString(R.string.notification_channel_transfers),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = app.getString(R.string.notification_channel_transfers_description)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DONE,
                app.getString(R.string.notification_channel_transfers_done),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = app.getString(R.string.notification_channel_transfers_done_description)
            },
        )
    }

    /**
     * Starts or refreshes the progress notification. [onCancel] is invoked from the notification's
     * Cancel action (and replaced on each call).
     */
    fun update(
        name: String,
        upload: Boolean,
        done: Long,
        total: Long,
        sessionId: Int,
        onCancel: () -> Unit,
    ) {
        cancelHandler = onCancel
        this.sessionId = sessionId
        createChannel()
        val percent = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else -1
        val now = SystemClock.elapsedRealtime()
        if (shown && percent == lastPercent && percent >= 0 && now - lastNotifyAt < 250L && done < total) {
            return
        }
        lastPercent = percent
        lastNotifyAt = now
        shown = true
        notifyProgress(progressNotification(name, upload, done, total, sessionId))
    }

    /** Clears the progress notification. When Files is not on screen, posts [message] as a brief result. */
    fun finish(message: String?) {
        cancelHandler = null
        shown = false
        lastPercent = -1
        manager.cancel(NOTIFICATION_ID)
        if (message != null && !sftpVisible) {
            runCatching { NotificationManagerCompat.from(app).notify(DONE_ID, doneNotification(message)) }
        }
    }

    fun clear() {
        cancelHandler = null
        shown = false
        lastPercent = -1
        manager.cancel(NOTIFICATION_ID)
    }

    fun requestCancel() {
        cancelHandler?.invoke()
    }

    private fun notifyProgress(notification: Notification) {
        // POST_NOTIFICATIONS may be denied; posting still keeps SessionService's FGS, so ignore failures.
        runCatching { NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification) }
    }

    private fun progressNotification(
        name: String,
        upload: Boolean,
        done: Long,
        total: Long,
        sessionId: Int,
    ): Notification {
        val title = app.getString(
            if (upload) R.string.notification_uploading else R.string.notification_downloading,
            name,
        )
        val amount = when {
            total <= 0 && done <= 0 -> ""
            total <= 0 -> formatSize(done)
            else -> "${formatSize(done)} of ${formatSize(total)}"
        }
        val cancel = PendingIntent.getService(
            app,
            CANCEL_REQUEST_CODE,
            Intent(app, SessionService::class.java).setAction(SessionService.ACTION_CANCEL_TRANSFER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            app,
            OPEN_REQUEST_CODE + sessionId,
            Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_SESSION_ID, sessionId)
                .putExtra(MainActivity.EXTRA_OPEN_SFTP, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(amount)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(0, app.getString(R.string.action_cancel_transfer), cancel)
            .apply {
                if (total > 0) {
                    setProgress(100, ((done * 100) / total).toInt().coerceIn(0, 100), false)
                } else {
                    setProgress(0, 0, true)
                }
            }
            .build()
    }

    private fun doneNotification(message: String): Notification {
        val open = PendingIntent.getActivity(
            app,
            OPEN_REQUEST_CODE,
            Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { if (sessionId >= 0) putExtra(MainActivity.EXTRA_SESSION_ID, sessionId) },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(app, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(app.getString(R.string.app_name))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "transfers"
        const val CHANNEL_DONE = "transfers_done"
        const val NOTIFICATION_ID = 2
        const val DONE_ID = 3
        private const val CANCEL_REQUEST_CODE = 20
        private const val OPEN_REQUEST_CODE = 30
    }
}
