package com.atala.app.core.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.atala.app.R
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.AtalaJson
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutKind
import com.atala.app.core.model.WorkoutStatus
import com.atala.app.core.purchases.PurchaseService
import com.atala.app.core.store.AppState
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Local reminders for the sessions of the plan, on the morning of each one (RunReminders.swift).
 *
 * The plan is free, so everyone gets them, with one exception: someone in the middle of a free
 * trial gets none. Nothing ever mentions a trial, and nothing asks anyone to subscribe.
 *
 * Android: inexact alarms (no special permission), one per session day, re-registered after a
 * reboot from the list kept in the preferences; the notification opens the app.
 */
object RunReminders {
    /** Hour of the morning reminder, in the phone's own time zone. */
    const val HOUR = 8
    /** How many sessions ahead are scheduled. Rescheduled every time the app opens. */
    const val HORIZON = 12

    internal const val CHANNEL_ID = "atala.reminders"
    internal const val ACTION_REMIND = "com.atala.app.action.SESSION_REMINDER"
    internal const val EXTRA_ID = "id"
    private const val PREFS = "atala.reminders"
    private const val KEY_SCHEDULED = "scheduled"
    private const val REQUEST_BASE = 7300

    @Serializable
    internal data class Scheduled(val slot: Int, val id: String, val fireAt: Long, val title: String, val body: String)

    /** True when Atala may post notifications (the system switch, and on Android 13+ the permission). */
    fun isAuthorized(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** The system page of Atala's notifications ("Settings" on the iPhone). */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, L("Session reminders"), NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)
    }

    /**
     * Removes Atala's pending reminders and schedules the coming ones again, from the plan as it is
     * now. Safe to call often: on launch, when the plan changes, when the subscription changes.
     */
    fun refresh(context: Context, state: AppState) {
        createChannel(context) // its name follows the app's language
        cancelAll(context)
        if (!isAuthorized(context) || !state.hasCompletedOnboarding) return
        val plan = state.plan ?: return
        // No reminder of any kind during a free trial.
        if (PurchaseService.isInTrial.value) return

        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val coach = state.coach.name
        val scheduled = mutableListOf<Scheduled>()
        val daysTaken = mutableSetOf<java.time.LocalDate>()
        for (workout in plan.allWorkouts) {
            if (workout.status != WorkoutStatus.Planned) continue
            if (scheduled.size >= HORIZON) break
            // One reminder a day: the first session of the day (runs come first in the plan).
            val day = workout.date.atZone(zone).toLocalDate()
            if (day in daysTaken) continue
            val fireAt = day.atTime(LocalTime.of(HOUR, 0)).atZone(zone).toInstant()
            if (!fireAt.isAfter(now)) continue
            scheduled += Scheduled(
                slot = scheduled.size, id = workout.id.toString(), fireAt = fireAt.toEpochMilli(),
                title = title(workout), body = body(workout, coach),
            )
            daysTaken += day
        }
        scheduled.forEach { register(context, it) }
        save(context, scheduled)
    }

    /** After a reboot alarms are gone: the ones still to come are registered again. */
    internal fun restore(context: Context) {
        val now = System.currentTimeMillis()
        val kept = load(context).filter { it.fireAt > now }
        kept.forEach { register(context, it) }
        save(context, kept)
    }

    /** What an alarm shows; null when it was cancelled in the meantime. */
    internal fun take(context: Context, id: String): Scheduled? {
        val list = load(context)
        val item = list.firstOrNull { it.id == id } ?: return null
        save(context, list - item)
        return item
    }

    // "Easy run · 5 km today"
    private fun title(workout: Workout): String =
        if (workout.kind.isRun && workout.distanceKm > 0) L("%@ · %@ today", workout.title, Fmt.km(workout.distanceKm))
        else L("%@ today", workout.title)

    private fun body(workout: Workout, coach: String): String = when (workout.kind) {
        WorkoutKind.Easy, WorkoutKind.Free ->
            L("%@ here. Keep it easy today: you should be able to talk the whole way.", coach)
        WorkoutKind.Long -> L("%@ here. Long run day: slow and steady, the distance does the work.", coach)
        WorkoutKind.Intervals, WorkoutKind.Tempo, WorkoutKind.RacePace ->
            L("%@ here. A quality session today. Warm up well, I'll guide you through it.", coach)
        WorkoutKind.Race -> L("%@ here. This is your day. Start calm and trust your training.", coach)
        WorkoutKind.Strength -> L("%@ here. A short strength session today: it keeps you running without pain.", coach)
        WorkoutKind.Stretching -> L("%@ here. A few minutes of stretching today, your legs will thank you.", coach)
    }

    // Alarms

    private fun pendingIntent(context: Context, slot: Int, id: String?, flags: Int): PendingIntent? {
        val intent = Intent(context, RunReminderReceiver::class.java).setAction(ACTION_REMIND)
        if (id != null) intent.putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, REQUEST_BASE + slot, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun register(context: Context, item: Scheduled) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pendingIntent(context, item.slot, item.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.fireAt, intent)
    }

    private fun cancelAll(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        for (slot in 0 until HORIZON) {
            pendingIntent(context, slot, null, PendingIntent.FLAG_NO_CREATE)?.let { intent ->
                alarms?.cancel(intent)
                intent.cancel()
            }
        }
        save(context, emptyList())
    }

    private fun load(context: Context): List<Scheduled> {
        val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SCHEDULED, null) ?: return emptyList()
        return runCatching { AtalaJson.decodeFromString(ListSerializer(Scheduled.serializer()), text) }.getOrDefault(emptyList())
    }

    private fun save(context: Context, list: List<Scheduled>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SCHEDULED, AtalaJson.encodeToString(ListSerializer(Scheduled.serializer()), list))
            .apply()
    }

    /** Posts the reminder; a tap opens the app. */
    internal fun post(context: Context, item: Scheduled) {
        if (!isAuthorized(context)) return
        createChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val open = launch?.let {
            PendingIntent.getActivity(context, REQUEST_BASE + 100, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo_atala_mark)
            .setContentTitle(item.title)
            .setContentText(item.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .apply { if (open != null) setContentIntent(open) }
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(item.id.hashCode(), notification) }
    }
}
