package com.focusos.app

import android.app.AppOpsManager
import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/* ════════════════════════════════════════════════════════════════════════════
 *  1. DATABASE (Room)
 * ════════════════════════════════════════════════════════════════════════════ */

@Entity(tableName = "sessions")
data class StudySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val startMs: Long,
    val endMs: Long,
    /** Active focus time only. Breaks and paused time are never included. */
    val focusedMs: Long,
    val mode: String,
)

@Entity(tableName = "tasks")
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subject: String = "",
    /** 0 low, 1 normal, 2 high */
    val priority: Int = 1,
    val done: Boolean = false,
    val createdMs: Long,
    val doneMs: Long = 0,
)

@Entity(tableName = "urges")
data class UrgeLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tsMs: Long,
    val resisted: Boolean,
    val activity: String,
)

@Entity(tableName = "tracked_apps")
data class TrackedApp(
    @PrimaryKey val pkg: String,
    val label: String,
    /** Counts toward the social media budget. */
    val social: Boolean = true,
    /** Blocked during study sessions, schedules and curfew. */
    val blocked: Boolean = true,
    /** Daily limit in minutes, 0 = none. */
    val limitMin: Int = 0,
)

@Dao
interface FocusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSession(s: StudySession): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSessions(l: List<StudySession>)
    @Query("SELECT * FROM sessions ORDER BY startMs DESC") fun observeSessions(): Flow<List<StudySession>>
    @Query("DELETE FROM sessions WHERE id = :id") suspend fun deleteSession(id: Long)
    @Query("SELECT * FROM sessions") suspend fun allSessions(): List<StudySession>
    @Query("DELETE FROM sessions") suspend fun clearSessions()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTask(t: TaskItem): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTasks(l: List<TaskItem>)
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun deleteTask(id: Long)
    @Query(
        "SELECT * FROM tasks WHERE done = 0 OR doneMs >= :since " +
            "ORDER BY done ASC, priority DESC, createdMs DESC"
    )
    fun observeTasks(since: Long): Flow<List<TaskItem>>
    @Query("SELECT * FROM tasks") suspend fun allTasks(): List<TaskItem>
    @Query("DELETE FROM tasks") suspend fun clearTasks()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putUrge(u: UrgeLog)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putUrges(l: List<UrgeLog>)
    @Query("SELECT * FROM urges ORDER BY tsMs DESC") fun observeUrges(): Flow<List<UrgeLog>>
    @Query("SELECT * FROM urges") suspend fun allUrges(): List<UrgeLog>
    @Query("DELETE FROM urges") suspend fun clearUrges()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putApp(a: TrackedApp)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putApps(l: List<TrackedApp>)
    @Query("DELETE FROM tracked_apps WHERE pkg = :pkg") suspend fun deleteApp(pkg: String)
    @Query("SELECT * FROM tracked_apps ORDER BY label COLLATE NOCASE") fun observeApps(): Flow<List<TrackedApp>>
    @Query("SELECT * FROM tracked_apps") suspend fun apps(): List<TrackedApp>
    @Query("DELETE FROM tracked_apps") suspend fun clearApps()
}

@Database(
    entities = [StudySession::class, TaskItem::class, UrgeLog::class, TrackedApp::class],
    version = 1,
    exportSchema = false,
)
abstract class FocusDb : RoomDatabase() {
    abstract fun dao(): FocusDao

    companion object {
        @Volatile private var instance: FocusDb? = null
        fun get(ctx: Context): FocusDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, FocusDb::class.java, "focusos.db")
                .build().also { instance = it }
        }
    }
}

/* ════════════════════════════════════════════════════════════════════════════
 *  2. SETTINGS (DataStore)
 * ════════════════════════════════════════════════════════════════════════════ */

private val Context.settingsStore by preferencesDataStore("focusos_settings")

data class Preset(val pomodoro: Boolean, val focusMin: Int, val breakMin: Int)

data class AppSettings(
    val goalMin: Int = 600,
    /** 0 system, 1 light, 2 dark */
    val theme: Int = 0,
    val timerMode: String = "stopwatch",
    val customFocus: Int = 45,
    val customBreak: Int = 10,
    val lastSubject: String = "",
    val blockInStudy: Boolean = true,
    val guardEnabled: Boolean = false,
    val hardcoreEnabled: Boolean = false,
    val hardcoreStart: Int = 9 * 60,
    val hardcoreEnd: Int = 17 * 60,
    val curfewEnabled: Boolean = false,
    val curfewStart: Int = 23 * 60,
    val curfewEnd: Int = 6 * 60,
    val bedtimeReminder: Boolean = true,
    val socialBudgetMin: Int = 60,
    val blockOnLimit: Boolean = false,
    val emergencyUntilMs: Long = 0L,
    val emergencyCount: Int = 0,
) {
    fun preset(): Preset = when (timerMode) {
        "25/5" -> Preset(true, 25, 5)
        "50/10" -> Preset(true, 50, 10)
        "custom" -> Preset(true, customFocus.coerceIn(1, 240), customBreak.coerceIn(1, 60))
        else -> Preset(false, 0, 0)
    }
}

object K {
    val goalMin = intPreferencesKey("goal_min")
    val theme = intPreferencesKey("theme")
    val timerMode = stringPreferencesKey("timer_mode")
    val customFocus = intPreferencesKey("custom_focus")
    val customBreak = intPreferencesKey("custom_break")
    val lastSubject = stringPreferencesKey("last_subject")
    val blockInStudy = booleanPreferencesKey("block_in_study")
    val guardEnabled = booleanPreferencesKey("guard_enabled")
    val hardcoreEnabled = booleanPreferencesKey("hardcore_enabled")
    val hardcoreStart = intPreferencesKey("hardcore_start")
    val hardcoreEnd = intPreferencesKey("hardcore_end")
    val curfewEnabled = booleanPreferencesKey("curfew_enabled")
    val curfewStart = intPreferencesKey("curfew_start")
    val curfewEnd = intPreferencesKey("curfew_end")
    val bedtimeReminder = booleanPreferencesKey("bedtime_reminder")
    val socialBudgetMin = intPreferencesKey("social_budget_min")
    val blockOnLimit = booleanPreferencesKey("block_on_limit")
    val emergencyUntilMs = longPreferencesKey("emergency_until")
    val emergencyCount = intPreferencesKey("emergency_count")

    /** Settings that cannot be changed while hardcore focus is active. */
    val lockedKeys: Set<Preferences.Key<*>> = setOf(
        blockInStudy, guardEnabled, hardcoreEnabled, hardcoreStart, hardcoreEnd,
        curfewEnabled, curfewStart, curfewEnd, blockOnLimit, socialBudgetMin,
    )

    /** Turning these on needs the background guard to be running. */
    val needsGuard: Set<Preferences.Key<*>> = setOf(hardcoreEnabled, curfewEnabled, blockOnLimit)

    val intKeys = listOf(
        goalMin, theme, customFocus, customBreak, hardcoreStart, hardcoreEnd,
        curfewStart, curfewEnd, socialBudgetMin,
    )
    val boolKeys = listOf(
        blockInStudy, guardEnabled, hardcoreEnabled, curfewEnabled, bedtimeReminder, blockOnLimit,
    )
    val stringKeys = listOf(timerMode, lastSubject)
}

class SettingsStore(private val ctx: Context) {
    val flow: Flow<AppSettings> = ctx.settingsStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            goalMin = p[K.goalMin] ?: d.goalMin,
            theme = p[K.theme] ?: d.theme,
            timerMode = p[K.timerMode] ?: d.timerMode,
            customFocus = p[K.customFocus] ?: d.customFocus,
            customBreak = p[K.customBreak] ?: d.customBreak,
            lastSubject = p[K.lastSubject] ?: d.lastSubject,
            blockInStudy = p[K.blockInStudy] ?: d.blockInStudy,
            guardEnabled = p[K.guardEnabled] ?: d.guardEnabled,
            hardcoreEnabled = p[K.hardcoreEnabled] ?: d.hardcoreEnabled,
            hardcoreStart = p[K.hardcoreStart] ?: d.hardcoreStart,
            hardcoreEnd = p[K.hardcoreEnd] ?: d.hardcoreEnd,
            curfewEnabled = p[K.curfewEnabled] ?: d.curfewEnabled,
            curfewStart = p[K.curfewStart] ?: d.curfewStart,
            curfewEnd = p[K.curfewEnd] ?: d.curfewEnd,
            bedtimeReminder = p[K.bedtimeReminder] ?: d.bedtimeReminder,
            socialBudgetMin = p[K.socialBudgetMin] ?: d.socialBudgetMin,
            blockOnLimit = p[K.blockOnLimit] ?: d.blockOnLimit,
            emergencyUntilMs = p[K.emergencyUntilMs] ?: d.emergencyUntilMs,
            emergencyCount = p[K.emergencyCount] ?: d.emergencyCount,
        )
    }

    suspend fun current(): AppSettings = flow.first()
    suspend fun snapshot(): Preferences = ctx.settingsStore.data.first()
    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        ctx.settingsStore.edit { it[key] = value }
    }
    suspend fun edit(block: (MutablePreferences) -> Unit) {
        ctx.settingsStore.edit { block(it) }
    }
}

/* ════════════════════════════════════════════════════════════════════════════
 *  3. FORMATTING + TIME HELPERS
 * ════════════════════════════════════════════════════════════════════════════ */

fun fmtHm(ms: Long): String {
    val m = (ms / 60_000L).coerceAtLeast(0)
    val h = m / 60
    return if (h > 0) "${h}h ${m % 60}m" else "${m}m"
}

fun fmtClock(ms: Long): String {
    val s = (ms / 1000L).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.getDefault(), "%02d:%02d", m, sec)
}

fun fmtMinute(ctx: Context, min: Int): String {
    val h = min / 60
    val m = min % 60
    return if (android.text.format.DateFormat.is24HourFormat(ctx)) {
        String.format(Locale.getDefault(), "%02d:%02d", h, m)
    } else {
        String.format(Locale.getDefault(), "%d:%02d %s", if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")
    }
}

fun LocalDate.startMs(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun startOfDay(): Long = LocalDate.now().startMs()
fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

fun minuteOfDay(now: Long): Int {
    val t = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    return t.hour * 60 + t.minute
}

/** True when [nowMin] is inside the window. Windows may wrap past midnight (e.g. 23:00 to 06:00). */
fun inWindow(nowMin: Int, start: Int, end: Int): Boolean = when {
    start == end -> false
    start < end -> nowMin in start until end
    else -> nowMin >= start || nowMin < end
}

/* ════════════════════════════════════════════════════════════════════════════
 *  4. TIMER ENGINE
 *
 *  The timer never counts ticks. It stores timestamps and derives every value
 *  from the clock, so recomposition, process death and screen-off cannot make
 *  it drift or reset. State lives in SharedPreferences (synchronous, survives
 *  process death) and is exposed as a StateFlow.
 * ════════════════════════════════════════════════════════════════════════════ */

enum class Phase { FOCUS, BREAK }
enum class RunState { IDLE, RUNNING, PAUSED }

data class TimerState(
    val run: RunState = RunState.IDLE,
    val phase: Phase = Phase.FOCUS,
    val subject: String = "",
    val pomodoro: Boolean = false,
    val focusLenMs: Long = 0,
    val breakLenMs: Long = 0,
    val sessionStartMs: Long = 0,
    /** Focus time accumulated before the current running segment. */
    val focusBaseMs: Long = 0,
    /** Time spent in the current phase before the current running segment. */
    val phaseBaseMs: Long = 0,
    /** Start of the current running segment, 0 while paused. */
    val segStartMs: Long = 0,
) {
    private fun running(now: Long) = if (run == RunState.RUNNING) (now - segStartMs).coerceAtLeast(0) else 0L

    fun phaseElapsed(now: Long): Long = phaseBaseMs + running(now)
    fun focused(now: Long): Long = focusBaseMs + if (phase == Phase.FOCUS) running(now) else 0L
    val phaseTarget: Long get() = if (!pomodoro) 0L else if (phase == Phase.FOCUS) focusLenMs else breakLenMs

    fun pause(now: Long): TimerState =
        if (run != RunState.RUNNING) this
        else copy(run = RunState.PAUSED, focusBaseMs = focused(now), phaseBaseMs = phaseElapsed(now), segStartMs = 0)

    fun resume(now: Long): TimerState =
        if (run == RunState.PAUSED) copy(run = RunState.RUNNING, segStartMs = now) else this

    /** Moves through any Pomodoro phase boundaries that have passed, crediting focus time exactly. */
    fun advance(now: Long): TimerState {
        var s = this
        if (s.run != RunState.RUNNING || !s.pomodoro) return s
        var guard = 0
        while (guard++ < 500) {
            val target = s.phaseTarget
            val elapsed = s.phaseElapsed(now)
            if (target <= 0 || elapsed < target) break
            val boundary = now - (elapsed - target)
            val ranSeg = (boundary - s.segStartMs).coerceAtLeast(0)
            s = s.copy(
                focusBaseMs = s.focusBaseMs + if (s.phase == Phase.FOCUS) ranSeg else 0L,
                phase = if (s.phase == Phase.FOCUS) Phase.BREAK else Phase.FOCUS,
                phaseBaseMs = 0,
                segStartMs = boundary,
            )
        }
        return s
    }
}

data class Finished(val subject: String, val startMs: Long, val endMs: Long, val focusedMs: Long, val pomodoro: Boolean)

object TimerEngine {
    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    fun init(ctx: Context) {
        val p = ctx.getSharedPreferences("timer_state", Context.MODE_PRIVATE)
        prefs = p
        _state.value = runCatching {
            TimerState(
                run = RunState.valueOf(p.getString("run", "IDLE") ?: "IDLE"),
                phase = Phase.valueOf(p.getString("phase", "FOCUS") ?: "FOCUS"),
                subject = p.getString("subject", "") ?: "",
                pomodoro = p.getBoolean("pomo", false),
                focusLenMs = p.getLong("focusLen", 0),
                breakLenMs = p.getLong("breakLen", 0),
                sessionStartMs = p.getLong("start", 0),
                focusBaseMs = p.getLong("focusBase", 0),
                phaseBaseMs = p.getLong("phaseBase", 0),
                segStartMs = p.getLong("seg", 0),
            )
        }.getOrDefault(TimerState())
    }

    @Synchronized private fun set(s: TimerState) {
        if (s == _state.value) return
        _state.value = s
        val e = prefs?.edit() ?: return
        e.putString("run", s.run.name)
        e.putString("phase", s.phase.name)
        e.putString("subject", s.subject)
        e.putBoolean("pomo", s.pomodoro)
        e.putLong("focusLen", s.focusLenMs)
        e.putLong("breakLen", s.breakLenMs)
        e.putLong("start", s.sessionStartMs)
        e.putLong("focusBase", s.focusBaseMs)
        e.putLong("phaseBase", s.phaseBaseMs)
        e.putLong("seg", s.segStartMs)
        e.apply()
    }

    @Synchronized fun start(subject: String, pomodoro: Boolean, focusMin: Int, breakMin: Int, now: Long) {
        if (_state.value.run != RunState.IDLE) return
        heartbeat(now)
        set(
            TimerState(
                run = RunState.RUNNING, phase = Phase.FOCUS, subject = subject, pomodoro = pomodoro,
                focusLenMs = focusMin * 60_000L, breakLenMs = breakMin * 60_000L,
                sessionStartMs = now, segStartMs = now,
            )
        )
    }

    @Synchronized fun pause(now: Long) = set(_state.value.advance(now).pause(now))
    @Synchronized fun resume(now: Long) = set(_state.value.resume(now))
    @Synchronized fun tick(now: Long) = set(_state.value.advance(now))

    /** Ends the session and returns what should be saved. */
    @Synchronized fun finish(now: Long): Finished? {
        var s = _state.value
        if (s.run == RunState.IDLE) return null
        s = s.advance(now)
        if (s.run == RunState.RUNNING) s = s.pause(now)
        val f = Finished(s.subject, s.sessionStartMs, now, s.focusBaseMs, s.pomodoro)
        set(TimerState())
        return f
    }

    /** Written regularly while a session runs, so a reboot can be told apart from time spent studying. */
    fun heartbeat(now: Long) {
        prefs?.edit()?.putLong("alive", now)?.apply()
    }

    /** After a device reboot the phone was off, so that time must not count as focus. */
    @Synchronized fun recoverAfterBoot(): Boolean {
        val s = _state.value
        if (s.run != RunState.RUNNING) return false
        val alive = prefs?.getLong("alive", 0L) ?: 0L
        val at = maxOf(alive, s.segStartMs)
        set(s.advance(at).pause(at))
        return true
    }
}

/* ════════════════════════════════════════════════════════════════════════════
 *  5. PERMISSIONS + USAGE STATS
 * ════════════════════════════════════════════════════════════════════════════ */

data class Perms(val usage: Boolean = false, val overlay: Boolean = false, val notifications: Boolean = false)

object UsageAccess {
    @Suppress("DEPRECATION")
    fun granted(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            ctx.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else mode == AppOpsManager.MODE_ALLOWED
    }

    fun perms(ctx: Context) = Perms(
        usage = granted(ctx),
        overlay = Settings.canDrawOverlays(ctx),
        notifications = NotificationManagerCompat.from(ctx).areNotificationsEnabled(),
    )

    fun usageSettingsIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun overlaySettingsIntent(ctx: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

data class AppEntry(val pkg: String, val label: String, val suggested: Boolean)

val KnownSocial = setOf(
    "com.facebook.katana", "com.facebook.lite", "com.facebook.orca", "com.facebook.mlite",
    "com.instagram.android", "com.instagram.lite", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
    "com.google.android.youtube", "com.twitter.android", "com.reddit.frontpage", "com.snapchat.android",
    "org.telegram.messenger", "com.pinterest", "com.linkedin.android", "com.whatsapp",
)

/** Measures real foreground time from system usage events. Nothing is estimated or invented. */
class UsageHelper(private val ctx: Context) {
    private val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val labels = HashMap<String, String>()

    private val ignored: Set<String> by lazy {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val launchers = ctx.packageManager.queryIntentActivities(home, 0).map { it.activityInfo.packageName }
        (launchers + listOf("com.android.systemui", ctx.packageName)).toSet()
    }

    fun usageByPackage(from: Long, to: Long): Map<String, Long> {
        if (to <= from) return emptyMap()
        val events = try {
            usm.queryEvents(from, to)
        } catch (e: SecurityException) {
            return emptyMap()
        }
        val e = UsageEvents.Event()
        val open = HashMap<String, Long>()
        val totals = HashMap<String, Long>()

        fun close(key: String, at: Long) {
            val started = open.remove(key) ?: return
            val pkg = key.substringBefore('/')
            if (at > started) totals[pkg] = (totals[pkg] ?: 0L) + (at - started)
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val key = e.packageName + "/" + (e.className ?: "")
            when (e.eventType) {
                1 -> open[key] = e.timeStamp                     // ACTIVITY_RESUMED
                2, 23 -> close(key, e.timeStamp)                 // PAUSED, STOPPED
                16, 17 -> open.keys.toList().forEach { close(it, e.timeStamp) } // screen off, keyguard
            }
        }
        // An app still in the foreground right now counts up to the end of the window.
        if (to >= System.currentTimeMillis() - 60_000L) open.keys.toList().forEach { close(it, to) }
        return totals.filterKeys { it !in ignored }
    }

    fun label(pkg: String): String = labels.getOrPut(pkg) {
        try {
            @Suppress("DEPRECATION")
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    fun installedApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val pkgs = ctx.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }
        return pkgs.distinct().filter { it != ctx.packageName }
            .map { AppEntry(it, label(it), it in KnownSocial) }
            .sortedWith(compareByDescending<AppEntry> { it.suggested }.thenBy { it.label.lowercase() })
    }
}

/* ════════════════════════════════════════════════════════════════════════════
 *  6. BLOCKING POLICY
 * ════════════════════════════════════════════════════════════════════════════ */

enum class Why(val title: String) {
    STUDY("Study session"),
    SCHEDULE("Hardcore schedule"),
    CURFEW("Digital curfew"),
    LIMIT("Daily limit reached"),
}

object Policy {
    /** Which time-based rule is active right now, if any. */
    fun activeWindow(s: AppSettings, t: TimerState, now: Long): Why? {
        if (now < s.emergencyUntilMs) return null
        // Pausing a session does not unblock apps. Breaks in Pomodoro mode do.
        if (s.blockInStudy && t.run != RunState.IDLE && t.phase == Phase.FOCUS) return Why.STUDY
        val m = minuteOfDay(now)
        if (s.hardcoreEnabled && inWindow(m, s.hardcoreStart, s.hardcoreEnd)) return Why.SCHEDULE
        if (s.curfewEnabled && inWindow(m, s.curfewStart, s.curfewEnd)) return Why.CURFEW
        return null
    }

    fun reasonFor(app: TrackedApp, s: AppSettings, t: TimerState, appMs: Long, socialMs: Long, now: Long): Why? {
        if (now < s.emergencyUntilMs) return null
        if (app.blocked) activeWindow(s, t, now)?.let { return it }
        if (s.blockOnLimit) {
            if (app.limitMin > 0 && appMs >= app.limitMin * 60_000L) return Why.LIMIT
            if (app.social && s.socialBudgetMin > 0 && socialMs >= s.socialBudgetMin * 60_000L) return Why.LIMIT
        }
        return null
    }

    /** Settings are locked while a hardcore window is active, unless the emergency exit was used. */
    fun isLocked(s: AppSettings, now: Long): Boolean =
        s.hardcoreEnabled && now >= s.emergencyUntilMs && inWindow(minuteOfDay(now), s.hardcoreStart, s.hardcoreEnd)
}

/* ════════════════════════════════════════════════════════════════════════════
 *  7. STATS (study streaks and records)
 * ════════════════════════════════════════════════════════════════════════════ */

data class StudyStats(
    val byDay: Map<LocalDate, Long> = emptyMap(),
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val bestDayMs: Long = 0,
    val longestSessionMs: Long = 0,
    val totalMs: Long = 0,
    val weekMet: Int = 0,
)

/**
 * A day counts when focused time reaches the goal. One missed day never resets a streak;
 * two missed days in a row do. Today being unfinished is never counted as a miss.
 */
fun computeStats(sessions: List<StudySession>, goalMin: Int, today: LocalDate): StudyStats {
    val byDay = sessions.groupBy { dateOf(it.startMs) }.mapValues { (_, l) -> l.sumOf { it.focusedMs } }
    val goalMs = goalMin * 60_000L
    fun met(d: LocalDate) = goalMs > 0 && (byDay[d] ?: 0L) >= goalMs

    var best = 0
    var cur = 0
    val first = byDay.keys.minOrNull()
    if (first != null) {
        var misses = 0
        var d: LocalDate = first
        val last = if (met(today)) today else today.minusDays(1)
        while (!d.isAfter(last)) {
            if (met(d)) { cur++; misses = 0 } else { misses++; if (misses >= 2) cur = 0 }
            best = maxOf(best, cur)
            d = d.plusDays(1)
        }
    }
    return StudyStats(
        byDay = byDay,
        streak = cur,
        bestStreak = best,
        bestDayMs = byDay.values.maxOrNull() ?: 0L,
        longestSessionMs = sessions.maxOfOrNull { it.focusedMs } ?: 0L,
        totalMs = sessions.sumOf { it.focusedMs },
        weekMet = (0..6).count { met(today.minusDays(it.toLong())) },
    )
}

/* ════════════════════════════════════════════════════════════════════════════
 *  8. REPOSITORY + BACKUP
 * ════════════════════════════════════════════════════════════════════════════ */

class Repository(ctx: Context) {
    val appCtx: Context = ctx.applicationContext
    val db = FocusDb.get(appCtx)
    val dao = db.dao()
    val settings = SettingsStore(appCtx)
    val usage = UsageHelper(appCtx)

    /** Ends the running session and stores it. Sessions under 10 seconds are treated as accidental taps. */
    suspend fun finishSession(): StudySession? {
        val f = TimerEngine.finish(System.currentTimeMillis()) ?: return null
        if (f.focusedMs < 10_000L) return null
        val s = StudySession(
            subject = f.subject.ifBlank { "General" },
            startMs = f.startMs,
            endMs = f.endMs,
            focusedMs = f.focusedMs,
            mode = if (f.pomodoro) "pomodoro" else "stopwatch",
        )
        dao.putSession(s)
        return s
    }

    suspend fun emergencyExit(minutes: Int = 10) {
        val cur = settings.current()
        settings.set(K.emergencyUntilMs, System.currentTimeMillis() + minutes * 60_000L)
        settings.set(K.emergencyCount, cur.emergencyCount + 1)
    }
}

object Backup {
    suspend fun export(repo: Repository): String {
        val d = repo.dao
        val p = repo.settings.snapshot()
        val root = JSONObject()
        root.put("app", "focusos")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("sessions", JSONArray().also { a ->
            d.allSessions().forEach {
                a.put(JSONObject().put("id", it.id).put("subject", it.subject).put("startMs", it.startMs)
                    .put("endMs", it.endMs).put("focusedMs", it.focusedMs).put("mode", it.mode))
            }
        })
        root.put("tasks", JSONArray().also { a ->
            d.allTasks().forEach {
                a.put(JSONObject().put("id", it.id).put("title", it.title).put("subject", it.subject)
                    .put("priority", it.priority).put("done", it.done).put("createdMs", it.createdMs)
                    .put("doneMs", it.doneMs))
            }
        })
        root.put("urges", JSONArray().also { a ->
            d.allUrges().forEach {
                a.put(JSONObject().put("id", it.id).put("tsMs", it.tsMs).put("resisted", it.resisted)
                    .put("activity", it.activity))
            }
        })
        root.put("apps", JSONArray().also { a ->
            d.apps().forEach {
                a.put(JSONObject().put("pkg", it.pkg).put("label", it.label).put("social", it.social)
                    .put("blocked", it.blocked).put("limitMin", it.limitMin))
            }
        })
        val st = JSONObject()
        K.intKeys.forEach { k -> p[k]?.let { st.put(k.name, it) } }
        K.boolKeys.forEach { k -> p[k]?.let { st.put(k.name, it) } }
        K.stringKeys.forEach { k -> p[k]?.let { st.put(k.name, it) } }
        root.put("settings", st)
        return root.toString(2)
    }

    /** Replaces all local data with the backup. Parsing happens first, so a bad file changes nothing. */
    suspend fun restore(repo: Repository, text: String) {
        val root = JSONObject(text)
        require(root.optString("app") == "focusos") { "This is not a Focus OS backup" }

        fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> = (0 until length()).map { f(getJSONObject(it)) }

        val sessions = root.getJSONArray("sessions").mapObjects {
            StudySession(it.getLong("id"), it.getString("subject"), it.getLong("startMs"),
                it.getLong("endMs"), it.getLong("focusedMs"), it.getString("mode"))
        }
        val tasks = root.getJSONArray("tasks").mapObjects {
            TaskItem(it.getLong("id"), it.getString("title"), it.getString("subject"), it.getInt("priority"),
                it.getBoolean("done"), it.getLong("createdMs"), it.getLong("doneMs"))
        }
        val urges = root.getJSONArray("urges").mapObjects {
            UrgeLog(it.getLong("id"), it.getLong("tsMs"), it.getBoolean("resisted"), it.getString("activity"))
        }
        val apps = root.getJSONArray("apps").mapObjects {
            TrackedApp(it.getString("pkg"), it.getString("label"), it.getBoolean("social"),
                it.getBoolean("blocked"), it.getInt("limitMin"))
        }
        val d = repo.dao
        repo.db.withTransaction {
            d.clearSessions(); d.clearTasks(); d.clearUrges(); d.clearApps()
            d.putSessions(sessions); d.putTasks(tasks); d.putUrges(urges); d.putApps(apps)
        }
        root.optJSONObject("settings")?.let { st ->
            repo.settings.edit { p ->
                K.intKeys.forEach { k -> if (st.has(k.name)) p[k] = st.getInt(k.name) }
                K.boolKeys.forEach { k -> if (st.has(k.name)) p[k] = st.getBoolean(k.name) }
                K.stringKeys.forEach { k -> if (st.has(k.name)) p[k] = st.getString(k.name) }
            }
        }
    }
}

/* ════════════════════════════════════════════════════════════════════════════
 *  9. VIEWMODEL
 * ════════════════════════════════════════════════════════════════════════════ */

data class DayUsage(val date: LocalDate, val totalMs: Long, val socialMs: Long)
data class AppUse(val pkg: String, val label: String, val ms: Long)

data class UsageUi(
    val loading: Boolean = true,
    val granted: Boolean = false,
    val today: DayUsage? = null,
    val perApp: Map<String, Long> = emptyMap(),
    val topToday: List<AppUse> = emptyList(),
    val socialToday: List<AppUse> = emptyList(),
    val week: List<DayUsage> = emptyList(),
    val weekApps: List<AppUse> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FocusApp).repo
    private val dao = repo.dao
    private fun now() = System.currentTimeMillis()

    val settings: StateFlow<AppSettings?> =
        repo.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val timer: StateFlow<TimerState> = TimerEngine.state

    private val dayStart = MutableStateFlow(startOfDay())
    val sessions = dao.observeSessions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val tasks = dayStart.flatMapLatest { dao.observeTasks(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val urges = dao.observeUrges().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val apps = dao.observeApps().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val subjects: StateFlow<List<String>> = combine(sessions, tasks) { s, t ->
        (s.map { it.subject } + t.map { it.subject }).filter { it.isNotBlank() }.distinct().take(10)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val todayFocusedSaved: StateFlow<Long> = combine(sessions, dayStart) { list, start ->
        list.filter { it.startMs >= start }.sumOf { it.focusedMs }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val stats: StateFlow<StudyStats> = combine(sessions, settings, dayStart) { s, st, _ ->
        computeStats(s, st?.goalMin ?: 600, LocalDate.now())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StudyStats())

    val perms = MutableStateFlow(Perms())
    private val _usage = MutableStateFlow(UsageUi())
    val usage: StateFlow<UsageUi> = _usage.asStateFlow()
    private val usageLock = Mutex()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()
    private fun say(msg: String) { _events.tryEmit(msg) }

    init {
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                val d = startOfDay()
                if (d != dayStart.value) dayStart.value = d
            }
        }
    }

    /** Called when the UI opens: re-arms reminders and the background guard after process death. */
    fun onAppStart() {
        viewModelScope.launch {
            val s = repo.settings.current()
            Reminders.schedule(getApplication(), s)
            if (s.guardEnabled || TimerEngine.state.value.run != RunState.IDLE) FocusService.start(getApplication())
        }
    }

    fun refreshPerms() { perms.value = UsageAccess.perms(getApplication()) }

    /* ---- settings ---- */

    fun <T> set(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch {
            val cur = repo.settings.current()
            if (key in K.lockedKeys && Policy.isLocked(cur, now())) {
                say("Locked while hardcore focus is active. Use Emergency exit to change it.")
                return@launch
            }
            repo.settings.set(key, value)
            if (value == true && key in K.needsGuard) repo.settings.set(K.guardEnabled, true)
            val s = repo.settings.current()
            Reminders.schedule(getApplication(), s)
            if (s.guardEnabled) FocusService.start(getApplication())
        }
    }

    fun emergencyExit() {
        viewModelScope.launch {
            repo.emergencyExit()
            say("Blocking paused for 10 minutes")
        }
    }

    /* ---- timer ---- */

    fun startSession(subject: String) {
        viewModelScope.launch {
            val p = repo.settings.current().preset()
            val subj = subject.trim().ifBlank { "General" }
            repo.settings.set(K.lastSubject, subject.trim())
            TimerEngine.start(subj, p.pomodoro, p.focusMin, p.breakMin, now())
            FocusService.start(getApplication())
        }
    }

    fun pause() = TimerEngine.pause(now())
    fun resume() = TimerEngine.resume(now())

    fun finish() {
        viewModelScope.launch {
            val s = repo.finishSession()
            say(if (s != null) "Saved ${fmtHm(s.focusedMs)} of ${s.subject}" else "Session was too short to save")
        }
    }

    fun deleteSession(id: Long) { viewModelScope.launch { dao.deleteSession(id) } }

    /* ---- tasks ---- */

    fun saveTask(t: TaskItem) {
        viewModelScope.launch { dao.putTask(if (t.id == 0L) t.copy(createdMs = now()) else t) }
    }

    fun toggleTask(t: TaskItem) {
        viewModelScope.launch { dao.putTask(t.copy(done = !t.done, doneMs = if (!t.done) now() else 0L)) }
    }

    fun deleteTask(id: Long) { viewModelScope.launch { dao.deleteTask(id) } }

    /* ---- urges ---- */

    fun logUrge(resisted: Boolean, activity: String) {
        viewModelScope.launch {
            dao.putUrge(UrgeLog(tsMs = now(), resisted = resisted, activity = activity))
            say(if (resisted) "Logged. That's one urge resisted." else "Logged. Noticing it counts.")
        }
    }

    /* ---- tracked apps ---- */

    private fun appsLocked() = settings.value?.let { Policy.isLocked(it, now()) } == true

    fun saveApp(a: TrackedApp) {
        if (appsLocked()) { say("Locked while hardcore focus is active."); return }
        viewModelScope.launch { dao.putApp(a) }
    }

    fun addApps(list: List<TrackedApp>) { viewModelScope.launch { dao.putApps(list) } }

    fun removeApp(pkg: String) {
        if (appsLocked()) { say("Locked while hardcore focus is active."); return }
        viewModelScope.launch { dao.deleteApp(pkg) }
    }

    suspend fun installedApps(): List<AppEntry> = withContext(Dispatchers.Default) { repo.usage.installedApps() }

    /* ---- screen time ---- */

    /** [full] also recomputes the previous six days, which is slower and only needed for Analytics. */
    fun refreshUsage(full: Boolean) {
        viewModelScope.launch(Dispatchers.Default) {
            usageLock.withLock {
                val ctx = getApplication<Application>()
                if (!UsageAccess.granted(ctx)) {
                    _usage.value = UsageUi(loading = false, granted = false)
                    return@withLock
                }
                val social = apps.value.filter { it.social }.map { it.pkg }.toSet()
                val nowMs = now()
                val today = LocalDate.now()

                fun day(d: LocalDate): Pair<DayUsage, Map<String, Long>> {
                    val from = d.startMs()
                    val to = minOf(d.plusDays(1).startMs(), nowMs)
                    val m = repo.usage.usageByPackage(from, to)
                    return DayUsage(d, m.values.sum(), m.filterKeys { it in social }.values.sum()) to m
                }

                val (todayUsage, todayMap) = day(today)
                val prev = _usage.value
                var week = prev.week
                var weekApps = prev.weekApps
                if (full || week.size != 7 || week.last().date != today) {
                    val results = (6 downTo 0).map { day(today.minusDays(it.toLong())) }
                    week = results.map { it.first }
                    weekApps = results.flatMap { it.second.entries }
                        .groupBy({ it.key }, { it.value })
                        .map { (k, v) -> AppUse(k, repo.usage.label(k), v.sum()) }
                        .sortedByDescending { it.ms }.take(6)
                } else {
                    week = week.dropLast(1) + todayUsage
                }
                _usage.value = UsageUi(
                    loading = false,
                    granted = true,
                    today = todayUsage,
                    perApp = todayMap,
                    topToday = todayMap.entries.filter { it.value >= 30_000L }
                        .map { AppUse(it.key, repo.usage.label(it.key), it.value) }
                        .sortedByDescending { it.ms }.take(8),
                    socialToday = todayMap.filterKeys { it in social }
                        .map { AppUse(it.key, repo.usage.label(it.key), it.value) }
                        .sortedByDescending { it.ms },
                    week = week,
                    weekApps = weekApps,
                )
            }
        }
    }

    /* ---- backup ---- */

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = Backup.export(repo)
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!
                        .use { it.write(json.toByteArray()) }
                }
                say("Backup saved")
            } catch (e: Exception) {
                say("Backup failed: ${e.message}")
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)!!
                        .use { String(it.readBytes()) }
                }
                Backup.restore(repo, text)
                say("Backup restored")
            } catch (e: Exception) {
                say("Restore failed: ${e.message}")
            }
        }
    }
}
