package wales.tucker.seren.files.ops

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import wales.tucker.seren.core.ui.formatSize
import wales.tucker.seren.files.MainActivity
import wales.tucker.seren.files.R
import wales.tucker.seren.files.SerenApp

/**
 * Keeps Seren Files running while a copy, move, compress, extract or save goes on, so leaving the
 * app doesn't stop it halfway. Shows how far it's got with Cancel, and says how it ended if
 * people are elsewhere by then.
 */
class OperationService : Service() {

    private val container get() = (application as SerenApp).container
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastMessage: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
        startInForeground(progressNotification(this, container.operations.current.value))
        scope.launch { container.operations.messages.collect { lastMessage = it.text } }
        scope.launch {
            container.operations.current.collect { op ->
                if (op != null) {
                    getSystemService(NotificationManager::class.java).notify(PROGRESS_ID, progressNotification(this@OperationService, op))
                } else {
                    finish()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) container.operations.cancel()
        return START_NOT_STICKY
    }

    /** Android 15 limits how long this kind of service may run; the job goes on, unprotected. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // In the app, the snackbar says how it went; elsewhere, a notification does.
        val message = lastMessage
        if (message != null && !container.appVisible) {
            getSystemService(NotificationManager::class.java).notify(DONE_ID, doneNotification(this, message))
        }
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, PROGRESS_ID, notification, type)
    }

    companion object {
        const val CHANNEL_PROGRESS = "progress"
        const val CHANNEL_DONE = "done"
        const val PROGRESS_ID = 1
        const val DONE_ID = 2
        const val ACTION_CANCEL = "wales.tucker.seren.files.CANCEL_OPERATION"

        /** Starts the service for a job that's just begun; harmless if it's already running. */
        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, OperationService::class.java)) }
        }

        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_PROGRESS, "Copying and moving", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Progress of copies, moves and other long jobs, with Cancel"
                    setShowBadge(false)
                },
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, "Finished jobs", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "How a copy, move or other long job ended, when Seren Files wasn't open"
                },
            )
        }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** "Copying 3 items", "photo.jpg · 12.0 MB of 40.0 MB", a progress bar and Cancel. */
        fun progressNotification(context: Context, op: Operation?): Notification {
            val cancel = PendingIntent.getService(
                context,
                1,
                Intent(context, OperationService::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(op?.title ?: context.getString(R.string.app_name))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(openApp(context))
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(0, "Cancel", cancel)
            if (op != null) {
                builder.setContentText(progressText(op))
                if (op.total > 0) {
                    val percent = (op.done * 100 / op.total).toInt().coerceIn(0, 100)
                    builder.setProgress(100, percent, false)
                } else {
                    builder.setProgress(0, 0, true)
                }
            }
            return builder.build()
        }

        fun progressText(op: Operation): String {
            val amount = when {
                op.total <= 0 -> ""
                op.countsItems -> "${op.done} of ${op.total}"
                else -> "${formatSize(op.done)} of ${formatSize(op.total)}"
            }
            return listOf(op.detail, amount).filter { it.isNotEmpty() }.joinToString("  ·  ")
        }

        fun doneNotification(context: Context, message: String): Notification =
            NotificationCompat.Builder(context, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setContentIntent(openApp(context))
                .build()
    }
}
