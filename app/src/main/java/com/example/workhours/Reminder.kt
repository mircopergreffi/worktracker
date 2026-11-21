package com.example.workhours

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.Notification
import android.os.Build
import com.example.workhours.data.AppDatabase
import com.example.workhours.data.absSeconds
import com.example.workhours.data.nowOn
import com.example.workhours.data.parseSeconds
import com.example.workhours.data.workedSeconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Schedules one alarm for the moment today's worked time reaches the chosen amount.
 * The alarm only exists while the timer is running and the amount has not been reached yet.
 */
object ReminderScheduler {
    private const val CHANNEL = "worked_time"
    private const val REQUEST = 1

    /** Recomputes and (re)sets or cancels the alarm from the database and the settings. */
    suspend fun reschedule(context: Context) {
        val ctx = context.applicationContext
        val prefs = ctx.getSharedPreferences("prefs", 0)
        val enabled = prefs.getBoolean("notify_enabled", false)
        val minutes = prefs.getInt("notify_minutes", 8 * 60)
        val trigger = if (enabled && minutes > 0) triggerMillis(ctx, minutes) else null
        if (trigger == null) cancel(ctx) else schedule(ctx, trigger, minutes)
    }

    /** When the running session will push the day's worked time to [minutes]; null if not running or already past. */
    private suspend fun triggerMillis(ctx: Context, minutes: Int): Long? {
        val dao = AppDatabase.get(ctx).entryDao()
        val last = dao.lastEntry() ?: return null
        if (last.inOut != "IN") return null
        val day = LocalDate.parse(last.day)
        if (nowOn(day, LocalDateTime.now()) == null) return null // forgotten IN from days ago

        val closed = workedSeconds(dao.entriesOfDay(last.day), null)
        val abs = absSeconds(day, parseSeconds(last.time)) + minutes * 60L - closed
        val millis = LocalDateTime.ofEpochSecond(abs, 0, ZoneOffset.UTC)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return millis.takeIf { it > System.currentTimeMillis() }
    }

    private fun pending(ctx: Context, minutes: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQUEST,
            Intent(ctx, ReminderReceiver::class.java).putExtra("minutes", minutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun schedule(ctx: Context, trigger: Long, minutes: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, minutes)
        // Exact timing needs a permission the user can grant in settings; otherwise Android may delay it a bit.
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    private fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, 0))
    }

    fun showNotification(ctx: Context, minutes: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = ctx.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Worked time", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
            PendingIntent.getActivity(ctx, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Work Hours")
            .setContentText("You have worked ${minutes / 60}h %02dm today.".format(minutes % 60))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(1, notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.showNotification(context, intent.getIntExtra("minutes", 0))
    }
}

/** Alarms are lost on reboot, so set it again from the database. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.reschedule(context)
            } finally {
                result.finish()
            }
        }
    }
}
