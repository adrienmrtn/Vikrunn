package com.atala.app.core.run

import android.app.ActivityManager
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
    /** True once onStartCommand has run (startForeground was called). */
    private var commanded = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Started with startForegroundService: startForeground comes first on every path, even when
        // the service stops right away, or the system ends the app.
        createChannel(this)
        val promoted = promote()
        commanded = true
        if (!promoted || !requested) {
            // Refused (Android 14 without location in the foreground, or the app went to the
            // background meanwhile), or the run already ended: the run keeps its clock without the
            // service, as when location is off.
            if (!promoted) requested = false
            stopEverything()
            return START_NOT_STICKY
        }
        acquireWakeLock()
        observe()
        return START_NOT_STICKY
    }

    /**
     * Puts the service in the foreground with its notification, as a location service. When that
     * is refused, Android 14+ still gets a short service, so the request made with
     * startForegroundService is honoured before the service stops. True when the location service runs.
     */
    private fun promote(): Boolean {
        val notification = notification(this, "0:00", RunFormat.distance(0.0))
        val located = runCatching {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        }.isSuccess
        if (!located && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
            }
        }
        return located
    }

    /** End of a short service (only used to stop cleanly, see [promote]). */
    override fun onTimeout(startId: Int) {
        stopEverything()
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
        if (instance === this) instance = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "run"
        private const val NOTIFICATION_ID = 4108

        /** Extra of the notification's intent: the app opens on the run flow. */
        const val EXTRA_OPEN_RUN = "com.atala.app.run.OPEN"

        /** True from [start] until [stop] (or a refusal): the service is wanted. */
        private var requested = false
        private var instance: RunTrackingService? = null

        /**
         * Starts the service, only while the app is on screen: Android refuses a foreground service
         * started from the background, and one of type location needs the permission.
         */
        fun start(context: Context) {
            if (requested) return
            if (!isAppVisible() || !RunTracker.hasPermission(context)) return
            requested = true
            runCatching { ContextCompat.startForegroundService(context, Intent(context, RunTrackingService::class.java)) }
                .onFailure { requested = false }
        }

        /**
         * Stops the service. Nothing is started for it: a service still on its way stops itself in
         * onStartCommand, after startForeground.
         */
        fun stop(@Suppress("UNUSED_PARAMETER") context: Context) {
            if (!requested) return
            requested = false
            // Before its first onStartCommand, the service stops itself there (after startForeground).
            instance?.takeIf { it.commanded }?.stopEverything()
        }

        private fun isAppVisible(): Boolean {
            val info = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(info)
            return info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
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
                ?.putExtra(EXTRA_OPEN_RUN, true)
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
