package com.atala.app.core.run

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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.atala.app.R
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps a run recording with the screen off (the iPhone's background location mode): a foreground
 * service of type location with an ongoing "Run in progress" notification showing the time and the
 * distance. Tapping it brings the app back. The recording itself lives in [ActiveRun]'s tracker.
 */
class RunTrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updates: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        createChannel(this)
        val started = runCatching {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification(this, "0:00", RunFormat.distance(0.0)),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        }.isSuccess
        if (!started) {
            // No location permission: the clock still runs from the system time, only the service is skipped.
            stopSelf()
            return START_NOT_STICKY
        }
        acquireWakeLock()
        observe()
        return START_NOT_STICKY
    }

    private fun observe() {
        if (updates != null) return
        val tracker = ActiveRun.current?.tracker ?: return
        updates = scope.launch {
            tracker.snapshot
                .map { Fmt.duration(it.elapsed) to RunFormat.distance(it.distance) }
                .distinctUntilChanged()
                .collect { (time, distance) ->
                    val manager = getSystemService(NotificationManager::class.java)
                    runCatching { manager.notify(NOTIFICATION_ID, notification(this@RunTrackingService, time, distance)) }
                }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val power = getSystemService(PowerManager::class.java) ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Atala:run").apply {
            setReferenceCounted(false)
            // A marathon and then some; released as soon as the run ends.
            acquire(8 * 60 * 60 * 1000L)
        }
    }

    private fun stopEverything() {
        updates?.cancel()
        updates = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "run"
        private const val NOTIFICATION_ID = 4108
        private const val ACTION_STOP = "com.atala.app.run.STOP"
        private var running = false

        fun start(context: Context) {
            if (running) return
            running = true
            runCatching { ContextCompat.startForegroundService(context, Intent(context, RunTrackingService::class.java)) }
                .onFailure { running = false }
        }

        fun stop(context: Context) {
            if (!running) return
            running = false
            runCatching {
                context.startService(Intent(context, RunTrackingService::class.java).setAction(ACTION_STOP))
            }.onFailure {
                context.stopService(Intent(context, RunTrackingService::class.java))
            }
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(CHANNEL_ID, L("Run in progress"), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        private fun notification(context: Context, time: String, distance: String): Notification {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val content = launch?.let {
                PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo_atala_mark)
                .setContentTitle(L("Run in progress"))
                .setContentText("$time · $distance")
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_WORKOUT)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
        }
    }
}
