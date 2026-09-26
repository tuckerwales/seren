package wales.tucker.terminal.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import wales.tucker.terminal.MainActivity
import wales.tucker.terminal.R
import wales.tucker.terminal.TerminalApp

/**
 * Foreground service that keeps the process (and therefore the SSH sessions) alive while the app
 * is in the background, with a notification summarising the active sessions.
 */
class SessionService : LifecycleService() {

    private val manager get() = (application as TerminalApp).container.sessionManager

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground(buildNotification(emptyList()))
        lifecycleScope.launch {
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            manager.sessions.flatMapLatest { sessions ->
                if (sessions.isEmpty()) flowOf(emptyList())
                else combine(sessions.map { s -> s.state }) { sessions.zip(it.toList()) }
            }.collectLatest { list ->
                if (list.isEmpty()) {
                    ServiceCompat.stopForeground(this@SessionService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(list))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_DISCONNECT_ALL) {
            manager.closeAll()
        } else {
            startInForeground(buildNotification(manager.sessions.value.map { it to it.state.value }))
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_sessions), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.notification_channel_sessions_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(sessions: List<Pair<TerminalSession, SessionState>>): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnect = PendingIntent.getService(
            this, 1,
            Intent(this, SessionService::class.java).setAction(ACTION_DISCONNECT_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val connected = sessions.count { it.second == SessionState.Connected }
        val title = when (sessions.size) {
            0 -> getString(R.string.app_name)
            1 -> sessions[0].first.spec.title
            else -> resources.getQuantityString(R.plurals.notification_sessions, sessions.size, sessions.size)
        }
        val text = when {
            sessions.isEmpty() -> ""
            sessions.size == 1 -> when (val s = sessions[0].second) {
                SessionState.Connected -> getString(R.string.state_connected_to, sessions[0].first.spec.subtitle)
                SessionState.Connecting -> getString(R.string.state_connecting)
                is SessionState.Disconnected -> s.reason
                is SessionState.Failed -> s.error
            }
            else -> resources.getQuantityString(R.plurals.notification_connected_count, connected, connected)
        }
        val style = NotificationCompat.InboxStyle()
        sessions.forEach { (s, st) ->
            val label = when (st) {
                SessionState.Connected -> "●"
                SessionState.Connecting -> "…"
                else -> "○"
            }
            style.addLine("$label ${s.spec.title} · ${s.spec.subtitle}")
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(if (sessions.size > 1) style else null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.action_disconnect_all), disconnect)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "sessions"
        const val NOTIFICATION_ID = 1
        const val ACTION_DISCONNECT_ALL = "wales.tucker.terminal.DISCONNECT_ALL"
    }
}
