package com.focusos.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.TextStyle as DayStyle
import java.util.Date
import java.util.Locale

/* ════════════════════════════════════════════════════════════════════════════
 *  BLOCK SCREEN  (shown on top of a blocked app)
 * ════════════════════════════════════════════════════════════════════════════ */

class BlockActivity : ComponentActivity() {
    private var label by mutableStateOf("This app")
    private var why by mutableStateOf(Why.STUDY)

    private fun readIntent(i: Intent?) {
        label = i?.getStringExtra(FocusService.EXTRA_LABEL) ?: "This app"
        why = runCatching { Why.valueOf(i?.getStringExtra(FocusService.EXTRA_WHY) ?: "") }.getOrDefault(Why.STUDY)
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun openUrge() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_URGE, true)
        )
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        readIntent(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        val repo = (application as FocusApp).repo
        val settingsFlow: Flow<AppSettings?> = repo.settings.flow
        setContent {
            val st by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val scope = rememberCoroutineScope()
            FocusTheme(st?.theme ?: 0) {
                BlockContent(
                    label = label,
                    why = why,
                    emergencyCount = st?.emergencyCount ?: 0,
                    onHome = { goHome() },
                    onUrge = { openUrge() },
                    onEmergency = { scope.launch { repo.emergencyExit(); finish() } },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }
}

@Composable
private fun BlockContent(
    label: String,
    why: Why,
    emergencyCount: Int,
    onHome: () -> Unit,
    onUrge: () -> Unit,
    onEmergency: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    val detail = when (why) {
        Why.STUDY -> "You're in a focus session. Finish it first, then come back."
        Why.SCHEDULE -> "Your hardcore focus schedule is active right now."
        Why.CURFEW -> "Digital curfew is on. Rest is part of studying."
        Why.LIMIT -> "You've used today's allowance for this app."
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(88.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.size(24.dp))
            Text("$label is blocked", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.size(8.dp))
            Text(why.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(12.dp))
            Text(
                detail, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            BigButton("Go to home screen", null, onHome, Modifier.fillMaxWidth())
            Spacer(Modifier.size(10.dp))
            BigButton("I want to scroll", null, onUrge, Modifier.fillMaxWidth(), tonal = true)
            Spacer(Modifier.size(4.dp))
            TextButton(onClick = { confirm = true }) {
                Text("Emergency exit", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Pause blocking for 10 minutes?") },
            text = {
                Text(
                    "Use this only for a real need. Every use is counted" +
                        if (emergencyCount > 0) " (used $emergencyCount time${if (emergencyCount == 1) "" else "s"} so far)." else "."
                )
            },
            confirmButton = { TextButton(onClick = { confirm = false; onEmergency() }) { Text("Pause blocking") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Stay focused") } },
        )
    }
}

/* ════════════════════════════════════════════════════════════════════════════
 *  URGE SCREEN  ("I want to scroll")
 * ════════════════════════════════════════════════════════════════════════════ */

private val Alternatives = listOf(
    "Drink a glass of water",
    "Walk for five minutes",
    "Stretch your back and neck",
    "Do one small task",
    "Message a friend",
    "Tidy your desk",
)

@Composable
fun UrgeScreen(vm: AppViewModel, onClose: () -> Unit) {
    val urges by vm.urges.collectAsStateWithLifecycle()
    var stage by rememberSaveable { mutableIntStateOf(0) }
    var seconds by rememberSaveable { mutableIntStateOf(30) }
    var chosen by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(stage) {
        if (stage == 0) while (seconds > 0) { delay(1000); seconds-- }
    }

    val resistedToday = urges.count { it.resisted && it.tsMs >= startOfDay() }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close") }
        }
        when (stage) {
            0 -> {
                Spacer(Modifier.weight(1f))
                val t = rememberInfiniteTransition(label = "breath")
                val scale by t.animateFloat(
                    0.55f, 1f,
                    infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "scale",
                )
                val inhale = ((30 - seconds) % 8) < 4
                Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale }
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    )
                    Text(
                        if (inhale) "Breathe in" else "Breathe out",
                        style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.size(28.dp))
                Text("Pause for a moment", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.size(6.dp))
                Text(
                    if (seconds > 0) "Urges fade. Stay with your breath for $seconds more seconds."
                    else "The urge has had time to pass.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.weight(1f))
                BigButton(if (seconds > 0) "Skip" else "Continue", null, { stage = 1 }, Modifier.fillMaxWidth(), tonal = seconds > 0)
            }
            1 -> {
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    ScreenTitle("Do this instead")
                    Text("Pick one. It only has to be small.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(12.dp))
                    LazyColumn {
                        items(Alternatives) { a ->
                            ListRow(
                                title = a,
                                onClick = {
                                    chosen = a
                                    vm.logUrge(true, a)
                                    stage = 2
                                },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
                TextButton(onClick = { vm.logUrge(false, ""); onClose() }) {
                    Text("I scrolled anyway", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                Spacer(Modifier.weight(1f))
                Text("Well done", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.size(8.dp))
                Text(
                    "Go: ${chosen.replaceFirstChar { it.lowercase() }}.",
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "$resistedToday urge${if (resistedToday == 1) "" else "s"} resisted today",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                BigButton("Back to Focus OS", null, onClose, Modifier.fillMaxWidth())
            }
        }
    }
}

/* ════════════════════════════════════════════════════════════════════════════
 *  BLOCKER
 * ════════════════════════════════════════════════════════════════════════════ */

private fun wrapMin(v: Int) = ((v % 1440) + 1440) % 1440

@Composable
private fun TimeRow(title: String, minutes: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val ctx = LocalContext.current
    StepperRow(
        title = title, value = fmtMinute(ctx, minutes),
        onMinus = { onChange(wrapMin(minutes - 15)) },
        onPlus = { onChange(wrapMin(minutes + 15)) },
        enabled = enabled,
    )
}

@Composable
fun BlockerScreen(vm: AppViewModel, s: AppSettings, host: Host) {
    val ctx = LocalContext.current
    val perms by vm.perms.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val timer by vm.timer.collectAsStateWithLifecycle()
    val now by rememberNow(15_000L)

    val locked = Policy.isLocked(s, now)
    val paused = now < s.emergencyUntilMs
    val guard = guardUi(perms, s, timer, apps, now)

    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TrackedApp?>(null) }
    var confirmExit by remember { mutableStateOf(false) }

    LaunchedEffect(perms.usage, apps) { vm.refreshUsage(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 32.dp),
    ) {
        item {
            NoticeBanner(
                "${guard.title}. ${guard.detail}", warn = !guard.ok,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (!perms.usage) item {
            Spacer(Modifier.size(8.dp))
            NoticeBanner(
                "Usage access lets Focus OS see which app is open. Nothing leaves this phone.",
                action = "Allow", onAction = { startSafely(ctx, UsageAccess.usageSettingsIntent()) },
            )
        }
        if (!perms.overlay) item {
            Spacer(Modifier.size(8.dp))
            NoticeBanner(
                "Display over other apps lets the block screen appear on top of a blocked app.",
                action = "Allow", onAction = { startSafely(ctx, UsageAccess.overlaySettingsIntent(ctx)) },
            )
        }

        if (locked || paused) item {
            Spacer(Modifier.size(8.dp))
            if (paused) {
                NoticeBanner("Blocking is paused until ${fmtClockTime(s.emergencyUntilMs)}.", warn = false)
            } else {
                NoticeBanner(
                    "Hardcore focus is active, so these settings are locked.",
                    action = "Emergency exit", onAction = { confirmExit = true },
                )
            }
        }

        item {
            SectionTitle("Blocking")
            SwitchRow(
                "Block during study sessions", s.blockInStudy, { vm.set(K.blockInStudy, it) },
                subtitle = "Selected apps are blocked while a focus phase runs", enabled = !locked,
            )
            SwitchRow(
                "Background guard", s.guardEnabled, { vm.set(K.guardEnabled, it) },
                subtitle = "Keeps schedules, curfew and limits running between sessions", enabled = !locked,
            )
        }

        item {
            SectionTitle("Hardcore schedule")
            SwitchRow(
                "Block on a daily schedule", s.hardcoreEnabled, { vm.set(K.hardcoreEnabled, it) },
                subtitle = "Locks these settings while active", enabled = !locked,
            )
            if (s.hardcoreEnabled) {
                TimeRow("From", s.hardcoreStart, !locked) { vm.set(K.hardcoreStart, it) }
                TimeRow("Until", s.hardcoreEnd, !locked) { vm.set(K.hardcoreEnd, it) }
            }
        }

        item {
            SectionTitle("Digital curfew")
            SwitchRow("Block at night", s.curfewEnabled, { vm.set(K.curfewEnabled, it) }, enabled = !locked)
            if (s.curfewEnabled) {
                TimeRow("From", s.curfewStart, !locked) { vm.set(K.curfewStart, it) }
                TimeRow("Until", s.curfewEnd, !locked) { vm.set(K.curfewEnd, it) }
                SwitchRow(
                    "Bedtime reminder", s.bedtimeReminder, { vm.set(K.bedtimeReminder, it) },
                    subtitle = "A notification 30 minutes before curfew",
                )
                if (s.bedtimeReminder && !perms.notifications) {
                    NoticeBanner("Notifications are off, so the reminder can't show.", action = "Allow", onAction = host.requestNotifications)
                }
            }
        }

        item {
            SectionTitle("Daily limits")
            StepperRow(
                "Social media budget",
                if (s.socialBudgetMin == 0) "Off" else fmtHm(s.socialBudgetMin * 60_000L),
                onMinus = { vm.set(K.socialBudgetMin, (s.socialBudgetMin - 15).coerceAtLeast(0)) },
                onPlus = { vm.set(K.socialBudgetMin, (s.socialBudgetMin + 15).coerceAtMost(480)) },
                subtitle = "Total across apps marked as social", enabled = !locked,
            )
            SwitchRow(
                "Block when a limit is reached", s.blockOnLimit, { vm.set(K.blockOnLimit, it) },
                subtitle = "Otherwise you only get a warning", enabled = !locked,
            )
        }

        item {
            SectionTitle("Apps", action = {
                TextButton(onClick = { adding = true }) { Text("Add apps") }
            })
            if (apps.isEmpty()) EmptyState("No apps yet", "Add the apps that distract you.")
        }
        items(apps, key = { it.pkg }) { a ->
            val used = usage.perApp[a.pkg] ?: 0L
            val parts = buildList {
                if (a.social) add("Social")
                add(if (a.blocked) "Blocked" else "Not blocked")
                if (a.limitMin > 0) add("Limit ${fmtHm(a.limitMin * 60_000L)}")
                if (usage.granted) add("${fmtHm(used)} today")
            }
            ListRow(a.label, subtitle = parts.joinToString(" · "), onClick = { editing = a })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }

    if (adding) {
        AddAppsDialog(vm, existing = apps.map { it.pkg }.toSet(), onDismiss = { adding = false })
    }
    editing?.let { a ->
        AppDialog(
            a, locked,
            onSave = { vm.saveApp(it); editing = null },
            onRemove = { vm.removeApp(a.pkg); editing = null },
            onDismiss = { editing = null },
        )
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Pause blocking for 10 minutes?") },
            text = { Text("Use this only for a real need. Every use is counted (${s.emergencyCount} so far).") },
            confirmButton = { TextButton(onClick = { vm.emergencyExit(); confirmExit = false }) { Text("Pause blocking") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Stay focused") } },
        )
    }
}

private fun fmtClockTime(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

@Composable
private fun AppDialog(a: TrackedApp, locked: Boolean, onSave: (TrackedApp) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var social by remember { mutableStateOf(a.social) }
    var blocked by remember { mutableStateOf(a.blocked) }
    var limit by remember { mutableIntStateOf(a.limitMin) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(a.label) },
        text = {
            Column {
                SwitchRow("Counts as social media", social, { social = it }, enabled = !locked)
                SwitchRow("Block in sessions and schedules", blocked, { blocked = it }, enabled = !locked)
                StepperRow(
                    "Daily limit", if (limit == 0) "None" else fmtHm(limit * 60_000L),
                    onMinus = { limit = (limit - 15).coerceAtLeast(0) },
                    onPlus = { limit = (limit + 15).coerceAtMost(480) },
                    enabled = !locked,
                )
                if (locked) Text("Locked while hardcore focus is active.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !locked, onClick = { onSave(a.copy(social = social, blocked = blocked, limitMin = limit)) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(enabled = !locked, onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun AddAppsDialog(vm: AppViewModel, existing: Set<String>, onDismiss: () -> Unit) {
    val installed by produceState<List<AppEntry>?>(null) { value = vm.installedApps() }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<String>()) }
    val list = installed.orEmpty().filter { it.pkg !in existing && it.label.contains(query.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add apps") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.size(8.dp))
                if (installed == null) {
                    Text("Loading apps…", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(list, key = { it.pkg }) { e ->
                            val on = e.pkg in picked
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { picked = if (on) picked - e.pkg else picked + e.pkg },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.label, style = MaterialTheme.typography.bodyLarge)
                                    if (e.suggested) Text("Suggested", style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = picked.isNotEmpty(), onClick = {
                val byPkg = installed.orEmpty().associateBy { it.pkg }
                vm.addApps(picked.mapNotNull { byPkg[it] }.map { TrackedApp(it.pkg, it.label, social = it.suggested, blocked = true) })
                onDismiss()
            }) { Text(if (picked.isEmpty()) "Add" else "Add ${picked.size}") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/* ════════════════════════════════════════════════════════════════════════════
 *  ANALYTICS
 * ════════════════════════════════════════════════════════════════════════════ */

@Composable
private fun BarChart(
    values: List<Long>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    goal: Long = 0L,
    overlay: List<Long>? = null,
) {
    val bar = MaterialTheme.colorScheme.primary
    val over = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val line = MaterialTheme.colorScheme.outline
    val maxV = maxOf(values.maxOrNull() ?: 0L, goal, 1L).toFloat()
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / values.size.coerceAtLeast(1)
            val bw = slot * 0.55f
            val r = CornerRadius(6.dp.toPx())
            values.forEachIndexed { i, v ->
                val x = i * slot + (slot - bw) / 2
                drawRoundRect(track, Offset(x, 0f), Size(bw, size.height), r)
                val h = size.height * (v / maxV)
                if (h > 0f) drawRoundRect(bar, Offset(x, size.height - h), Size(bw, h), r)
                val o = overlay?.getOrNull(i) ?: 0L
                if (o > 0L) {
                    val oh = size.height * (o / maxV)
                    drawRoundRect(over, Offset(x, size.height - oh), Size(bw, oh), r)
                }
            }
            if (goal > 0L) {
                val y = size.height * (1f - goal / maxV)
                drawLine(line, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            labels.forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UsageRow(label: String, ms: Long, max: Long) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(fmtHm(ms), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(3.dp))) {
            Box(
                Modifier.fillMaxWidth((ms.toFloat() / maxOf(max, 1L)).coerceIn(0f, 1f)).height(6.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
fun AnalyticsScreen(vm: AppViewModel, s: AppSettings) {
    val ctx = LocalContext.current
    val stats by vm.stats.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val urges by vm.urges.collectAsStateWithLifecycle()
    val perms by vm.perms.collectAsStateWithLifecycle()

    LaunchedEffect(perms.usage) {
        while (true) {
            vm.refreshUsage(true)
            delay(60_000)
        }
    }

    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val dayLabels = days.map { it.dayOfWeek.getDisplayName(DayStyle.NARROW, Locale.getDefault()) }
    val focusWeek = days.map { stats.byDay[it] ?: 0L }
    val weekStart = days.first().startMs()
    val bySubject = sessions.filter { it.startMs >= weekStart }
        .groupBy { it.subject }.map { it.key to it.value.sumOf { x -> x.focusedMs } }
        .sortedByDescending { it.second }.take(6)
    val weekUrges = urges.filter { it.tsMs >= weekStart }
    val resisted = weekUrges.count { it.resisted }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 32.dp),
    ) {
        item {
            SectionTitle("Focus, last 7 days")
            BarChart(focusWeek, dayLabels, Modifier.padding(top = 8.dp), goal = s.goalMin * 60_000L)
            Text(
                "Dashed line is your daily goal of ${fmtHm(s.goalMin * 60_000L)}.",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item {
            Spacer(Modifier.size(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Current streak", "${stats.streak} d", Modifier.weight(1f))
                StatTile("Best streak", "${stats.bestStreak} d", Modifier.weight(1f))
            }
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Best day", fmtHm(stats.bestDayMs), Modifier.weight(1f))
                StatTile("Longest session", fmtHm(stats.longestSessionMs), Modifier.weight(1f))
            }
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("All time", fmtHm(stats.totalMs), Modifier.weight(1f))
                StatTile("Days at goal (7d)", "${stats.weekMet} / 7", Modifier.weight(1f))
            }
        }

        item {
            SectionTitle("By subject, last 7 days")
            if (bySubject.isEmpty()) EmptyState("No sessions this week", "Finish a focus session to see it here.")
            else bySubject.forEach { (name, ms) -> UsageRow(name, ms, bySubject.first().second) }
        }

        item {
            SectionTitle("Screen time")
            if (!perms.usage) {
                NoticeBanner(
                    "Allow Usage access to measure screen time. Everything stays on this phone.",
                    action = "Allow", onAction = { startSafely(ctx, UsageAccess.usageSettingsIntent()) },
                )
            } else if (usage.loading || usage.today == null) {
                Text("Measuring…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val t = usage.today!!
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Today", fmtHm(t.totalMs), Modifier.weight(1f))
                    StatTile("Social today", fmtHm(t.socialMs), Modifier.weight(1f))
                }
                if (usage.week.size == 7) {
                    Spacer(Modifier.size(16.dp))
                    BarChart(
                        usage.week.map { it.totalMs },
                        usage.week.map { it.date.dayOfWeek.getDisplayName(DayStyle.NARROW, Locale.getDefault()) },
                        overlay = usage.week.map { it.socialMs },
                    )
                    Text(
                        "Total phone use per day, with social apps highlighted.",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        if (perms.usage && usage.weekApps.isNotEmpty()) item {
            SectionTitle("Most used this week")
            usage.weekApps.forEach { UsageRow(it.label, it.ms, usage.weekApps.first().ms) }
        }

        item {
            SectionTitle("Urges, last 7 days")
            if (weekUrges.isEmpty()) {
                EmptyState("Nothing logged", "Tap \"I want to scroll\" on Home when an urge hits.")
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Resisted", "$resisted", Modifier.weight(1f))
                    StatTile("Gave in", "${weekUrges.size - resisted}", Modifier.weight(1f))
                }
            }
        }
    }
}

/* ════════════════════════════════════════════════════════════════════════════
 *  SETTINGS
 * ════════════════════════════════════════════════════════════════════════════ */

@Composable
fun SettingsScreen(vm: AppViewModel, s: AppSettings, host: Host) {
    val ctx = LocalContext.current
    val perms by vm.perms.collectAsStateWithLifecycle()
    var confirmImport by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 32.dp),
    ) {
        item {
            SectionTitle("Appearance")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System", "Light", "Dark").forEachIndexed { i, l ->
                    FilterChip(selected = s.theme == i, onClick = { vm.set(K.theme, i) }, label = { Text(l) })
                }
            }
        }

        item {
            SectionTitle("Daily goal")
            StepperRow(
                "Focus goal", fmtHm(s.goalMin * 60_000L),
                onMinus = { vm.set(K.goalMin, (s.goalMin - 30).coerceAtLeast(30)) },
                onPlus = { vm.set(K.goalMin, (s.goalMin + 30).coerceAtMost(1440)) },
                subtitle = "A day counts toward your streak when you reach this",
            )
        }

        item {
            SectionTitle("Permissions")
            ListRow(
                "Usage access", subtitle = if (perms.usage) "Allowed" else "Needed to measure screen time and detect open apps",
                onClick = { startSafely(ctx, UsageAccess.usageSettingsIntent()) },
            )
            ListRow(
                "Display over other apps", subtitle = if (perms.overlay) "Allowed" else "Needed to show the block screen",
                onClick = { startSafely(ctx, UsageAccess.overlaySettingsIntent(ctx)) },
            )
            ListRow(
                "Notifications", subtitle = if (perms.notifications) "Allowed" else "Needed for break and limit reminders",
                onClick = host.requestNotifications,
            )
        }

        item {
            SectionTitle("Your data")
            Text(
                "Everything is stored on this phone. Nothing is uploaded.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("Export backup", null, host.export, Modifier.weight(1f), tonal = true)
                BigButton("Import", null, { confirmImport = true }, Modifier.weight(1f), tonal = true)
            }
        }

        item {
            SectionTitle("About")
            ListRow("Focus OS", subtitle = "Version 1.0.0")
            if (s.emergencyCount > 0) {
                ListRow("Emergency exits used", subtitle = "${s.emergencyCount}")
            }
        }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Replace all data?") },
            text = { Text("Importing a backup replaces your sessions, tasks, apps and settings with the file's contents.") },
            confirmButton = { TextButton(onClick = { confirmImport = false; host.import() }) { Text("Choose file") } },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Cancel") } },
        )
    }
}
