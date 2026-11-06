package com.focusos.app

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/* ════════════════════════════════════════════════════════════════════════════
 *  Application
 * ════════════════════════════════════════════════════════════════════════════ */

class FocusApp : Application() {
    val repo by lazy { Repository(this) }

    override fun onCreate() {
        super.onCreate()
        TimerEngine.init(this)
        Notifs.ensureChannels(this)
    }
}

/* ════════════════════════════════════════════════════════════════════════════
 *  Notifications
 * ════════════════════════════════════════════════════════════════════════════ */

object Notifs {
    const val CH_SESSION = "session"
    const val CH_ALERT = "alerts"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SESSION, "Focus session and guard", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows your running session and whether blocking is active"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Alerts and reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Break reminders, usage warnings and bedtime reminder"
            }
        )
    }

    fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @SuppressLint("MissingPermission")
    fun alert(ctx: Context, id: Int, title: String, text: String) {
        val nmc = NotificationManagerCompat.from(ctx)
        if (!nmc.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        nmc.notify(id, n)
    }

    fun notificationSettingsIntent(ctx: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/* ════════════════════════════════════════════════════════════════════════════
 *  Foreground app detection
 * ════════════════════════════════════════════════════════════════════════════ */

/** Follows which app is in front by reading usage events incrementally. Needs Usage Access. */
class ForegroundTracker(ctx: Context) {
    private val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private var last = System.currentTimeMillis() - 3 * 3_600_000L
    private var current: String? = null

    fun poll(): String? {
        val now = System.currentTimeMillis()
        val events = try {
            usm.queryEvents(last, now)
        } catch (e: SecurityException) {
            return null
        }
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                1 -> current = e.packageName   // ACTIVITY_RESUMED
                16 -> current = null           // screen off
            }
        }
        last = now
        return current
    }
}

/* ════════════════════════════════════════════════════════════════════════════
 *  FocusService
 *
 *  One foreground service with two jobs:
 *   1. Keep the session notification and Pomodoro phase changes alive.
 *   2. Enforce blocking, but only if Usage Access and Display-over-apps are granted.
 *
 *  It runs while a session is active or while the user has turned on Background guard,
 *  and stops itself otherwise.
 * ════════════════════════════════════════════════════════════════════════════ */

class FocusService : Service() {
    companion object {
        const val ACTION_PAUSE = "com.focusos.app.PAUSE"
        const val ACTION_RESUME = "com.focusos.app.RESUME"
        const val ACTION_STOP = "com.focusos.app.STOP"
        const val EXTRA_LABEL = "label"
        const val EXTRA_WHY = "why"
        private const val NOTIF_ID = 1

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, FocusService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private lateinit var repo: Repository
    private lateinit var tracker: ForegroundTracker

    private var settings = AppSettings()
    private var apps: List<TrackedApp> = emptyList()
    private var usage: Map<String, Long> = emptyMap()
    private var lastUsageAt = 0L
    private var lastBlockAt = 0L
    private var lastBeatAt = 0L
    private var lastShown: TimerState? = null

    private val alertPrefs by lazy { getSharedPreferences("alert_flags", MODE_PRIVATE) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repo = (application as FocusApp).repo
        tracker = ForegroundTracker(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        enterForeground()
        val now = System.currentTimeMillis()
        when (intent?.action) {
            ACTION_PAUSE -> TimerEngine.pause(now)
            ACTION_RESUME -> TimerEngine.resume(now)
            ACTION_STOP -> scope.launch { repo.finishSession() }
        }
        startLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun enterForeground() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun startLoop() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            settings = repo.settings.current()
            apps = repo.dao.apps()
            launch { repo.settings.flow.collect { settings = it } }
            launch { repo.dao.observeApps().collect { apps = it } }

            while (isActive) {
                tick(System.currentTimeMillis())
                if (TimerEngine.state.value.run == RunState.IDLE && !settings.guardEnabled) {
                    stopSelf()
                    break
                }
                delay(700)
            }
        }
    }

    private fun tick(now: Long) {
        val before = TimerEngine.state.value
        TimerEngine.tick(now)
        val t = TimerEngine.state.value

        if (t.run == RunState.RUNNING) {
            if (now - lastBeatAt > 10_000L) { TimerEngine.heartbeat(now); lastBeatAt = now }
            if (before.run == RunState.RUNNING && t.phase != before.phase) {
                if (t.phase == Phase.BREAK) {
                    Notifs.alert(this, 2, "Break time", "Rest for ${t.breakLenMs / 60_000} min. Look away from screens.")
                } else {
                    Notifs.alert(this, 2, "Back to focus", "Your next ${t.focusLenMs / 60_000} min block has started.")
                }
            }
        }
        if (t != lastShown) {
            lastShown = t
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
        }
        if (now - lastUsageAt > 10_000L) {
            lastUsageAt = now
            refreshUsage(now)
        }
        enforce(t, now)
    }

    private fun refreshUsage(now: Long) {
        if (!UsageAccess.granted(this)) { usage = emptyMap(); return }
        usage = repo.usage.usageByPackage(startOfDay(), now)
        checkWarnings()
    }

    /** Never claims to block when it cannot. Without both permissions this simply does nothing. */
    private fun enforce(t: TimerState, now: Long) {
        if (!UsageAccess.granted(this) || !Settings.canDrawOverlays(this)) return
        val pkg = tracker.poll() ?: return
        if (pkg == packageName) return
        val app = apps.firstOrNull { it.pkg == pkg } ?: return
        val socialMs = apps.filter { it.social }.sumOf { usage[it.pkg] ?: 0L }
        val why = Policy.reasonFor(app, settings, t, usage[pkg] ?: 0L, socialMs, now) ?: return
        if (now - lastBlockAt < 1_500L) return
        lastBlockAt = now
        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_LABEL, app.label)
                .putExtra(EXTRA_WHY, why.name)
        )
    }

    /** Each warning is sent once per day. */
    private fun once(key: String): Boolean {
        val prefix = "${LocalDate.now()}:"
        val k = prefix + key
        if (alertPrefs.getBoolean(k, false)) return false
        val e = alertPrefs.edit()
        alertPrefs.all.keys.filterNot { it.startsWith(prefix) }.forEach { e.remove(it) }
        e.putBoolean(k, true).apply()
        return true
    }

    private fun checkWarnings() {
        val social = apps.filter { it.social }
        val socialMs = social.sumOf { usage[it.pkg] ?: 0L }
        val budget = settings.socialBudgetMin * 60_000L
        if (budget > 0 && social.isNotEmpty()) {
            if (socialMs >= budget) {
                if (once("social100")) Notifs.alert(this, 3, "Social media budget reached",
                    "You've used all ${fmtHm(budget)} for today.")
            } else if (socialMs >= budget * 0.8) {
                if (once("social80")) Notifs.alert(this, 3, "80% of social budget used",
                    "${fmtHm(socialMs)} of ${fmtHm(budget)} so far today.")
            }
        }
        apps.filter { it.limitMin > 0 }.forEach { a ->
            val limit = a.limitMin * 60_000L
            val used = usage[a.pkg] ?: 0L
            if (used >= limit) {
                if (once("${a.pkg}:100")) Notifs.alert(this, 1000 + (a.pkg.hashCode() and 0xFFFF),
                    "${a.label} limit reached", "You've used ${fmtHm(used)} today.")
            } else if (used >= limit * 0.8) {
                if (once("${a.pkg}:80")) Notifs.alert(this, 1000 + (a.pkg.hashCode() and 0xFFFF),
                    "${a.label}: 80% of today's limit", "${fmtHm(used)} of ${fmtHm(limit)} used.")
            }
        }
    }

    private fun svcIntent(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(),
        Intent(this, FocusService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(): Notification {
        val t = TimerEngine.state.value
        val now = System.currentTimeMillis()
        val b = NotificationCompat.Builder(this, Notifs.CH_SESSION)
            .setSmallIcon(R.drawable.ic_stat)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(Notifs.openApp(this))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (t.run == RunState.IDLE) {
            val ready = UsageAccess.granted(this) && Settings.canDrawOverlays(this)
            if (ready) b.setContentTitle("Guard is on").setContentText("Schedules, curfew and limits are active")
            else b.setContentTitle("Guard can't block yet").setContentText("Open Focus OS and grant the missing permission")
        } else {
            b.setContentTitle(if (t.phase == Phase.FOCUS) "Focus · ${t.subject}" else "Break")
            if (t.run == RunState.RUNNING) {
                b.setShowWhen(true).setUsesChronometer(true)
                if (t.pomodoro) {
                    b.setWhen(now + (t.phaseTarget - t.phaseElapsed(now))).setChronometerCountDown(true)
                } else {
                    b.setWhen(now - t.phaseElapsed(now))
                }
                b.addAction(0, "Pause", svcIntent(ACTION_PAUSE))
            } else {
                b.setContentText("Paused · ${fmtHm(t.focused(now))} focused")
                b.addAction(0, "Resume", svcIntent(ACTION_RESUME))
            }
            b.addAction(0, "Finish", svcIntent(ACTION_STOP))
        }
        return b.build()
    }
}

/* ════════════════════════════════════════════════════════════════════════════
 *  Bedtime reminder (30 minutes before curfew)
 * ════════════════════════════════════════════════════════════════════════════ */

object Reminders {
    private const val ACTION = "com.focusos.app.BEDTIME"

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 7,
        Intent(ctx, ReminderReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Uses an inexact alarm, so no special alarm permission is needed. It may fire a few minutes late. */
    fun schedule(ctx: Context, s: AppSettings) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx)
        am.cancel(pi)
        if (!s.curfewEnabled || !s.bedtimeReminder) return
        val remindMin = (s.curfewStart - 30 + 1440) % 1440
        var at = LocalDate.now().atStartOfDay().plusMinutes(remindMin.toLong())
        if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext as FocusApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = app.repo.settings.current()
                if (s.curfewEnabled && s.bedtimeReminder) {
                    Notifs.alert(ctx, 9, "Time to wind down",
                        "Curfew starts at ${fmtMinute(ctx, s.curfewStart)}. Put the phone away and rest.")
                }
                Reminders.schedule(ctx, s)
            } finally {
                pending.finish()
            }
        }
    }
}

/* ════════════════════════════════════════════════════════════════════════════
 *  Boot / app update
 * ════════════════════════════════════════════════════════════════════════════ */

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext as FocusApp
        val pending = goAsync()
        val rebooted = intent.action == Intent.ACTION_BOOT_COMPLETED
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // A reboot means the phone was off, so that time must not count as study time.
                if (rebooted && TimerEngine.recoverAfterBoot()) {
                    Notifs.alert(ctx, 4, "Session paused after restart",
                        "Time while the phone was off wasn't counted. Open Focus OS to resume or finish.")
                }
                val s = app.repo.settings.current()
                Reminders.schedule(ctx, s)
                if (s.guardEnabled || TimerEngine.state.value.run != RunState.IDLE) FocusService.start(ctx)
            } finally {
                pending.finish()
            }
        }
    }
}
