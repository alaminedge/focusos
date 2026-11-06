package com.focusos.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

const val EXTRA_URGE = "open_urge"

/* ════════════════════════════════════════════════════════════════════════════
 *  THEME  (mint accent, neutral surfaces, one type scale, one spacing rhythm)
 * ════════════════════════════════════════════════════════════════════════════ */

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F8F69), onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F4E6), onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFF4A635A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE8DC), onSecondaryContainer = Color(0xFF062017),
    tertiary = Color(0xFF8A6D3B), onTertiary = Color.White,
    background = Color(0xFFF7FAF8), onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF7FAF8), onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDCE5DF), onSurfaceVariant = Color(0xFF404944),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F5F2),
    surfaceContainer = Color(0xFFEBF0EC), surfaceContainerHigh = Color(0xFFE5EAE6),
    surfaceContainerHighest = Color(0xFFDFE4E0),
    outline = Color(0xFF707973), outlineVariant = Color(0xFFBFC9C2),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6EDBB4), onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0B4F3B), onPrimaryContainer = Color(0xFFB9F2DC),
    secondary = Color(0xFFB1CCC0), onSecondary = Color(0xFF1C352C),
    secondaryContainer = Color(0xFF324B42), onSecondaryContainer = Color(0xFFCDE8DC),
    tertiary = Color(0xFFE0C38C), onTertiary = Color(0xFF3F2E05),
    background = Color(0xFF0E1512), onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF0E1512), onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF404944), onSurfaceVariant = Color(0xFFBFC9C2),
    surfaceContainerLowest = Color(0xFF09100D), surfaceContainerLow = Color(0xFF151D19),
    surfaceContainer = Color(0xFF19211D), surfaceContainerHigh = Color(0xFF242C28),
    surfaceContainerHighest = Color(0xFF2F3732),
    outline = Color(0xFF8A938D), outlineVariant = Color(0xFF404944),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp, fontFeatureSettings = "tnum"),
    displayMedium = TextStyle(fontSize = 44.sp, lineHeight = 50.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum"),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontFeatureSettings = "tnum"),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum"),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun FocusTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val c = WindowCompat.getInsetsController(window, view)
                c.isAppearanceLightStatusBars = !dark
                c.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/* ════════════════════════════════════════════════════════════════════════════
 *  SHARED COMPONENTS
 * ════════════════════════════════════════════════════════════════════════════ */

/** Wall-clock ticker. Always derived from System.currentTimeMillis, so it can't drift. */
@Composable
fun rememberNow(intervalMs: Long): State<Long> = produceState(System.currentTimeMillis(), intervalMs) {
    while (true) {
        value = System.currentTimeMillis()
        delay(intervalMs)
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 14.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val arc = Size(size.width - w, size.height - w)
            val topLeft = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(w))
            if (p > 0.001f) drawArc(color, -90f, 360f * p, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 28.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Fixed top bar: stays put while the screen content scrolls underneath. */
@Composable
fun AppHeader(title: String, subtitle: String? = null) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding()) {
            Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 20.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.headlineSmall)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.headlineLarge, modifier = modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
fun BigButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tonal: Boolean = false,
) {
    val shape = RoundedCornerShape(18.dp)
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, null)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp))
    }
    if (tonal) FilledTonalButton(onClick, modifier.heightIn(min = 56.dp), enabled, shape = shape, content = content)
    else Button(onClick, modifier.heightIn(min = 56.dp), enabled, shape = shape, content = content)
}

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Row(m.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.padding(end = 16.dp).size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun StepperRow(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, subtitle: String? = null, enabled: Boolean = true) {
    ListRow(title, subtitle = subtitle, trailing = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onMinus, enabled = enabled) { Icon(Icons.Rounded.Remove, "Decrease") }
            Text(value, Modifier.widthIn(min = 64.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            FilledTonalIconButton(onClick = onPlus, enabled = enabled) { Icon(Icons.Rounded.Add, "Increase") }
        }
    })
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun NoticeBanner(text: String, modifier: Modifier = Modifier, warn: Boolean = true, action: String? = null, onAction: () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = if (warn) cs.errorContainer else cs.primaryContainer) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f).padding(vertical = 4.dp), style = MaterialTheme.typography.bodyMedium,
                color = if (warn) cs.onErrorContainer else cs.onPrimaryContainer)
            if (action != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

fun startSafely(ctx: Context, intent: Intent) {
    try { ctx.startActivity(intent) } catch (e: Exception) { /* settings screen unavailable on this device */ }
}

/* ════════════════════════════════════════════════════════════════════════════
 *  GUARD STATUS  (never claims blocking works when it can't)
 * ════════════════════════════════════════════════════════════════════════════ */

data class GuardUi(val title: String, val detail: String, val ok: Boolean)

fun guardUi(perms: Perms, s: AppSettings, t: TimerState, apps: List<TrackedApp>, now: Long): GuardUi {
    val missing = listOfNotNull(
        if (!perms.usage) "Usage access" else null,
        if (!perms.overlay) "Display over other apps" else null,
    )
    return when {
        missing.isNotEmpty() -> GuardUi("Blocking is off", "Needs: ${missing.joinToString(" and ")}", false)
        apps.none { it.blocked } -> GuardUi("No apps selected", "Choose apps to block in the Blocker tab", false)
        (s.hardcoreEnabled || s.curfewEnabled || s.blockOnLimit) && !s.guardEnabled ->
            GuardUi("Schedules aren't running", "Turn on Background guard in the Blocker tab", false)
        else -> {
            val w = Policy.activeWindow(s, t, now)
            if (w != null) GuardUi("Blocking active", "${w.title} · ${apps.count { it.blocked }} apps", true)
            else GuardUi("Blocking ready", "Starts with your next session or schedule", true)
        }
    }
}

/* ════════════════════════════════════════════════════════════════════════════
 *  ACTIVITY + ROOT
 * ════════════════════════════════════════════════════════════════════════════ */

class Host(val requestNotifications: () -> Unit, val export: () -> Unit, val import: () -> Unit)

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Timer("Timer", Icons.Rounded.Timer),
    Blocker("Blocker", Icons.Rounded.Shield),
    Analytics("Analytics", Icons.Rounded.BarChart),
    Settings("Settings", Icons.Rounded.Settings),
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var urgeTick by mutableIntStateOf(0)

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && Build.VERSION.SDK_INT >= 33 &&
            !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            startSafely(this, Notifs.notificationSettingsIntent(this))
        }
        vm.refreshPerms()
    }
    private val exportDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    private val importDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(EXTRA_URGE, false) == true) urgeTick = 1
        vm.onAppStart()
        val host = Host(
            requestNotifications = {
                if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else startSafely(this, Notifs.notificationSettingsIntent(this))
            },
            export = { exportDoc.launch("focusos-backup-${LocalDate.now()}.json") },
            import = { importDoc.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        )
        setContent {
            val st by vm.settings.collectAsStateWithLifecycle()
            FocusTheme(st?.theme ?: 0) { AppRoot(vm, host, urgeTick) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_URGE, false)) urgeTick++
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPerms()
    }
}

@Composable
fun AppRoot(vm: AppViewModel, host: Host, urgeTick: Int) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var urge by rememberSaveable { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(urgeTick) { if (urgeTick > 0) urge = true }
    LaunchedEffect(Unit) { vm.events.collect { snack.showSnackbar(it) } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (s == null) return@Box
        if (urge) {
            BackHandler { urge = false }
            UrgeScreen(vm, onClose = { urge = false })
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snack) },
                topBar = {
                    if (tab == Tab.Home) {
                        AppHeader("Today", LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())).uppercase())
                    } else AppHeader(tab.label)
                },
                floatingActionButton = {
                    if (tab == Tab.Home) {
                        ExtendedFloatingActionButton(
                            onClick = { urge = true },
                            icon = { Icon(Icons.Rounded.SelfImprovement, null) },
                            text = { Text("I want to scroll") },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                },
                bottomBar = {
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon, null) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                },
            ) { inner ->
                Box(Modifier.padding(inner)) {
                    Crossfade(tab, label = "tab") { current ->
                        when (current) {
                            Tab.Home -> HomeScreen(vm, s, goTimer = { tab = Tab.Timer }, goBlocker = { tab = Tab.Blocker })
                            Tab.Timer -> TimerScreen(vm, s)
                            Tab.Blocker -> BlockerScreen(vm, s, host)
                            Tab.Analytics -> AnalyticsScreen(vm, s)
                            Tab.Settings -> SettingsScreen(vm, s, host)
                        }
                    }
                }
            }
        }
    }
}

/* ════════════════════════════════════════════════════════════════════════════
 *  HOME
 * ════════════════════════════════════════════════════════════════════════════ */

@Composable
fun HomeScreen(vm: AppViewModel, s: AppSettings, goTimer: () -> Unit, goBlocker: () -> Unit) {
    val ctx = LocalContext.current
    val timer by vm.timer.collectAsStateWithLifecycle()
    val savedToday by vm.todayFocusedSaved.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val perms by vm.perms.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val now by rememberNow(if (timer.run == RunState.RUNNING) 1_000L else 15_000L)

    LaunchedEffect(perms.usage, apps) {
        while (true) {
            vm.refreshUsage(false)
            delay(30_000)
        }
    }

    val goalMs = s.goalMin * 60_000L
    val focused = savedToday + timer.focused(now)
    val progress = if (goalMs > 0) focused.toFloat() / goalMs else 0f
    val pending = tasks.filter { !it.done }
    val completed = tasks.filter { it.done }
    val guard = guardUi(perms, s, timer, apps, now)
    val social = apps.filter { it.social }

    var editing by remember { mutableStateOf<TaskItem?>(null) }
    var adding by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 112.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ProgressRing(
                    progress,
                    Modifier.size(244.dp).semantics(mergeDescendants = true) {
                        contentDescription = "Focused ${fmtHm(focused)} of ${fmtHm(goalMs)} goal"
                    },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fmtHm(focused), style = MaterialTheme.typography.displayMedium)
                        Text("of ${fmtHm(goalMs)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    if (focused >= goalMs) "Goal reached" else "${fmtHm(goalMs - focused)} to go",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                )
                val weekLine = buildString {
                    if (stats.streak > 0) append("${stats.streak}-day streak · ")
                    append("${stats.weekMet} of 7 days at goal")
                }
                Text(weekLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            Spacer(Modifier.size(20.dp))
            val (label, icon) = when (timer.run) {
                RunState.IDLE -> "Start focus session" to Icons.Rounded.PlayArrow
                RunState.RUNNING -> "Open session" to Icons.Rounded.Timer
                RunState.PAUSED -> "Resume session" to Icons.Rounded.PlayArrow
            }
            BigButton(label, icon, modifier = Modifier.fillMaxWidth(), onClick = {
                when (timer.run) {
                    RunState.IDLE -> vm.startSession(s.lastSubject)
                    RunState.PAUSED -> vm.resume()
                    RunState.RUNNING -> Unit
                }
                goTimer()
            })
        }

        item {
            SectionTitle("Blocking")
            ListRow(
                title = guard.title, subtitle = guard.detail, icon = Icons.Rounded.Shield,
                tint = if (guard.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                onClick = goBlocker,
            )
        }

        item {
            SectionTitle("Social media today")
            when {
                !perms.usage -> NoticeBanner(
                    "Allow Usage access to measure screen time. Everything stays on this phone.",
                    action = "Allow", onAction = { startSafely(ctx, UsageAccess.usageSettingsIntent()) },
                )
                social.isEmpty() -> ListRow("No social apps selected", subtitle = "Choose them in the Blocker tab", onClick = goBlocker)
                else -> {
                    val used = usage.socialToday.sumOf { it.ms }
                    val budget = s.socialBudgetMin * 60_000L
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text(fmtHm(used), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        if (budget > 0) Text("of ${fmtHm(budget)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (budget > 0) {
                        Spacer(Modifier.size(8.dp))
                        val over = used >= budget
                        LinearProgressIndicator(
                            progress = { (used.toFloat() / budget).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 8.dp),
                            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            strokeCap = StrokeCap.Round,
                        )
                    }
                }
            }
        }

        item {
            SectionTitle("Tasks", action = {
                TextButton(onClick = { adding = true }) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            })
            if (tasks.isEmpty()) EmptyState("Nothing planned", "Add what you want to finish today.")
        }
        items(pending, key = { it.id }) { t -> TaskRow(t, { vm.toggleTask(t) }, { editing = t }) }
        if (completed.isNotEmpty()) {
            item {
                Text("Done today", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            }
            items(completed, key = { it.id }) { t -> TaskRow(t, { vm.toggleTask(t) }, { editing = t }) }
        }
    }

    if (adding) {
        TaskDialog(null, subjects, onSave = { vm.saveTask(it); adding = false }, onDelete = null, onDismiss = { adding = false })
    }
    editing?.let { t ->
        TaskDialog(t, subjects, onSave = { vm.saveTask(it); editing = null },
            onDelete = { vm.deleteTask(t.id); editing = null }, onDismiss = { editing = null })
    }
}

@Composable
fun TaskRow(t: TaskItem, onToggle: () -> Unit, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = t.done, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                t.title, style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (t.done) TextDecoration.LineThrough else null,
                color = if (t.done) cs.onSurfaceVariant else cs.onSurface,
            )
            if (t.subject.isNotBlank()) Text(t.subject, style = MaterialTheme.typography.labelMedium, color = cs.primary)
        }
        if (t.priority == 2 && !t.done) {
            Text("High", style = MaterialTheme.typography.labelMedium, color = cs.error, modifier = Modifier.padding(end = 12.dp))
        }
    }
}

@Composable
fun TaskDialog(initial: TaskItem?, subjects: List<String>, onSave: (TaskItem) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var subject by remember { mutableStateOf(initial?.subject ?: "") }
    var priority by remember { mutableIntStateOf(initial?.priority ?: 1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New task" else "Edit task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Task") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(subject, { subject = it }, label = { Text("Subject (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (subjects.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        subjects.forEach { sub -> FilterChip(selected = subject == sub, onClick = { subject = sub }, label = { Text(sub) }) }
                    }
                }
                Text("Priority", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Low", "Normal", "High").forEachIndexed { i, l ->
                        FilterChip(selected = priority == i, onClick = { priority = i }, label = { Text(l) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                val base = initial ?: TaskItem(title = "", createdMs = 0L)
                onSave(base.copy(title = title.trim(), subject = subject.trim(), priority = priority))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/* ════════════════════════════════════════════════════════════════════════════
 *  TIMER
 * ════════════════════════════════════════════════════════════════════════════ */

private fun dayLabel(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
    }
}

private fun clockTime(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

@Composable
fun TimerScreen(vm: AppViewModel, s: AppSettings) {
    val timer by vm.timer.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val perms by vm.perms.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val savedToday by vm.todayFocusedSaved.collectAsStateWithLifecycle()
    val now by rememberNow(if (timer.run == RunState.RUNNING) 500L else 5_000L)

    val running = timer.run != RunState.IDLE
    var subject by rememberSaveable { mutableStateOf(s.lastSubject) }
    var focusTxt by rememberSaveable { mutableStateOf(s.customFocus.toString()) }
    var breakTxt by rememberSaveable { mutableStateOf(s.customBreak.toString()) }
    var deleting by remember { mutableStateOf<StudySession?>(null) }

    val preset = s.preset()
    val big: String
    val caption: String
    val progress: Float
    when {
        !running -> {
            big = if (preset.pomodoro) fmtClock(preset.focusMin * 60_000L) else "00:00"
            caption = if (preset.pomodoro) "${preset.focusMin} min focus · ${preset.breakMin} min break" else "Stopwatch"
            progress = 0f
        }
        timer.pomodoro -> {
            val el = timer.phaseElapsed(now)
            val target = timer.phaseTarget.coerceAtLeast(1L)
            big = fmtClock((target - el).coerceAtLeast(0))
            caption = (if (timer.phase == Phase.FOCUS) "Focus · ${timer.subject}" else "Break") +
                if (timer.run == RunState.PAUSED) " · paused" else ""
            progress = el.toFloat() / target
        }
        else -> {
            val el = timer.phaseElapsed(now)
            big = fmtClock(el)
            caption = "Focus · ${timer.subject}" + if (timer.run == RunState.PAUSED) " · paused" else ""
            progress = (el % 3_600_000L) / 3_600_000f
        }
    }
    val guard = guardUi(perms, s, timer, apps, now)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 32.dp),
    ) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("stopwatch" to "Stopwatch", "25/5" to "25 / 5", "50/10" to "50 / 10", "custom" to "Custom").forEach { (key, label) ->
                    FilterChip(selected = s.timerMode == key, enabled = !running, onClick = { vm.set(K.timerMode, key) }, label = { Text(label) })
                }
            }
            if (s.timerMode == "custom" && !running) {
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        focusTxt, {
                            focusTxt = it.filter(Char::isDigit).take(3)
                            focusTxt.toIntOrNull()?.takeIf { v -> v in 1..240 }?.let { v -> vm.set(K.customFocus, v) }
                        },
                        label = { Text("Focus (min)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        breakTxt, {
                            breakTxt = it.filter(Char::isDigit).take(2)
                            breakTxt.toIntOrNull()?.takeIf { v -> v in 1..60 }?.let { v -> vm.set(K.customBreak, v) }
                        },
                        label = { Text("Break (min)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            Spacer(Modifier.size(12.dp))
            OutlinedTextField(
                value = if (running) timer.subject else subject,
                onValueChange = { subject = it },
                enabled = !running,
                label = { Text("Subject") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!running && subjects.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    subjects.forEach { sub -> FilterChip(selected = subject == sub, onClick = { subject = sub }, label = { Text(sub) }) }
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ProgressRing(progress, Modifier.size(260.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(big, style = MaterialTheme.typography.displayLarge)
                        Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 36.dp))
                    }
                }
                Spacer(Modifier.size(12.dp))
                Text("Focused today ${fmtHm(savedToday + timer.focused(now))}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            when (timer.run) {
                RunState.IDLE -> BigButton("Start", Icons.Rounded.PlayArrow, { vm.startSession(subject) }, Modifier.fillMaxWidth())
                RunState.RUNNING -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton("Pause", Icons.Rounded.Pause, { vm.pause() }, Modifier.weight(1f), tonal = true)
                    BigButton("Finish", Icons.Rounded.Stop, { vm.finish() }, Modifier.weight(1f))
                }
                RunState.PAUSED -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton("Resume", Icons.Rounded.PlayArrow, { vm.resume() }, Modifier.weight(1f))
                    BigButton("Finish", Icons.Rounded.Stop, { vm.finish() }, Modifier.weight(1f), tonal = true)
                }
            }
            Spacer(Modifier.size(12.dp))
            Text(
                if (!s.blockInStudy) "App blocking during sessions is off." else "${guard.title} · ${guard.detail}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }

        item { SectionTitle("Session history") }
        if (sessions.isEmpty()) {
            item { EmptyState("No sessions yet", "Finished sessions appear here. Breaks and paused time are never counted.") }
        } else {
            var lastDay: LocalDate? = null
            sessions.take(30).forEach { ses ->
                val d = dateOf(ses.startMs)
                if (d != lastDay) {
                    lastDay = d
                    item(key = "h$d") {
                        Text(dayLabel(d), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                    }
                }
                item(key = "s${ses.id}") {
                    ListRow(
                        title = ses.subject,
                        subtitle = "${clockTime(ses.startMs)} – ${clockTime(ses.endMs)} · ${if (ses.mode == "pomodoro") "Pomodoro" else "Stopwatch"}",
                        trailing = {
                            Text(fmtHm(ses.focusedMs), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { deleting = ses }) {
                                Icon(Icons.Rounded.Delete, "Delete session", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }

    deleting?.let { ses ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete session?") },
            text = { Text("${ses.subject} · ${fmtHm(ses.focusedMs)} will be removed from your totals.") },
            confirmButton = { TextButton(onClick = { vm.deleteSession(ses.id); deleting = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
