package com.appman.app

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
//import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.appman.app.adb.AdbManager
import com.appman.app.service.AdbPairingService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.io.File
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.withTimeoutOrNull

private val Ink = Color(0xFF17202A)
private val Teal = Color(0xFF218DAE)
private val Cyan = Color(0xFF8ACFF8)
private val Blue = Color(0xFF3A86FF)
private val ActionButtonBlue = Color(0xFFAAC4F5)
private val Cream = Color(0xFFFCE59A)
private val Yellow = Color(0xFFFFD758)
private val Sand = Color(0xFFE9DFC3)
private val White = Color(0xFFFFF8F8)
private val Black = Color(0xFF000000)

data class AppItem(val label: String, val packageName: String, val icon: Drawable, val system: Boolean, val sizeBytes: Long)

enum class AppFilter { INSTALLED, SYSTEM, ALL }
enum class SortBy { NAME, SIZE }

data class HoodLine(val kind: String, val text: String)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hideSystemBars()

        setContent {
            AppManTheme {
                AppManApp()
            }
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            hideSystemBars()
        }
    }
}

@Composable private fun AppManTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Blue, secondary = Blue, background = White, surface = White, onBackground = Ink, onSurface = Ink), content = content)
}

@Composable private fun AppManApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var terminal by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var filter by remember { mutableStateOf(AppFilter.INSTALLED) }
    var sort by remember { mutableStateOf(SortBy.NAME) }
    var ascending by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var detail by remember { mutableStateOf<AppItem?>(null) }
    var pairingDialog by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }
    var hoodExpanded by remember { mutableStateOf(false) }
    var hoodMax by remember { mutableStateOf(false) }
    var hood by remember { mutableStateOf(listOf(HoodLine("info", "HOOD ready. ADB output and commands will appear here."))) }
    // Independently selectable bulk actions: any combination of
    // "cache", "data", "stop", "share", "uninstall". Net is tracked
    // separately below since it's governed by its own two toggles.
    var multiActions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var netAll by remember { mutableStateOf(false) }
    var netBackground by remember { mutableStateOf(false) }
    var netMenuOpen by remember { mutableStateOf(false) }
    var multiRunning by remember { mutableStateOf(false) }
    var multiJob by remember { mutableStateOf<Job?>(null) }
    var snack by remember { mutableStateOf<String?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        apps = loadApps(context)

        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Check immediately when AppMan starts.
        connected = withContext(Dispatchers.IO) {
            AdbManager.get(context).isConnected()
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val state = withContext(Dispatchers.IO) {
                AdbManager.get(context).isConnected()
            }

            if (connected != state) {
                connected = state
            }

            kotlinx.coroutines.delay(1000)
        }
    }

    val visible = remember(apps, filter, sort, ascending, query) {
        apps.filter { a ->
            (filter == AppFilter.ALL || (filter == AppFilter.SYSTEM && a.system) || (filter == AppFilter.INSTALLED && !a.system)) &&
                    (query.isBlank() || a.label.contains(query, true) || a.packageName.contains(query, true))
        }.sortedWith(if (sort == SortBy.NAME) compareBy<AppItem> { it.label.lowercase() } else compareBy { it.sizeBytes }).let { if (ascending) it else it.reversed() }
    }

    fun log(line: String, kind: String = "cmd") { hood = (hood + HoodLine(kind, line)).takeLast(500) }
    suspend fun execAwait(command: String): String {
        log("$ $command", "cmd")
        val result = withContext(Dispatchers.IO) {
            try {
                AdbManager.get(context).exec(command)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                "ERROR: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        log(result.ifBlank { "(no output)" }, if (result.startsWith("ERROR:")) "err" else "out")
        connected = withContext(Dispatchers.IO) { AdbManager.get(context).isConnected() }
        return result
    }

    suspend fun execBulk(command: String): String {
        currentCoroutineContext().ensureActive()
        return execAwait(command)
    }
    fun runCommand(command: String) {
        scope.launch { execAwait(command) }
    }
    fun startPairing() {
        ContextCompat.startForegroundService(context, Intent(context, AdbPairingService::class.java).setAction(AdbPairingService.ACTION_PREPARE))
        pairingDialog = true
    }

    Surface(Modifier.fillMaxSize(), color = White) {
        Column(Modifier.fillMaxSize()) {
            TopBar(terminal, { terminal = it }, connected, onPair = { startPairing() }, onDisconnect = { scope.launch { withContext(Dispatchers.IO) { AdbManager.get(context).disconnect() }; connected = false; log("Disconnected") } })
            if (!terminal && !developerOptionsEnabled(context)) SetupCard(onOpen = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) })
            if (terminal) {
                TerminalPane(connected, onConnect = { startPairing() }, onCommand = { runCommand(it) }, modifier = Modifier.weight(1f))
            } else if (detail != null) {
                AppDetail(item = detail!!, onBack = { detail = null }, onRun = { cmd -> runCommand(cmd) }, onShare = { shareApk(context, it) }, onLog = { log(it) })
            } else {
                Column(Modifier.weight(1f)) {
                    SearchBar(
                        query,
                        { query = it },
                        selected.isNotEmpty(),
                        onClear = { selected = emptySet() },
                        onSelectAll = {
                            selected =
                                if (selected.size == visible.size) emptySet() else visible.map { it.packageName }
                                    .toSet()
                        },
                        filter,
                        { filter = it },
                        sort,
                        { sort = it },
                        ascending,
                        { ascending = !ascending })
                    var dragSelecting by remember { mutableStateOf(false) }
                    var dragY by remember { mutableStateOf(0f) }
                    var dragAnchorIndex by remember { mutableStateOf<Int?>(null) }

                    val listState = rememberLazyListState()

                    fun selectRange(anchor: Int, current: Int) {
                        if (visible.isEmpty()) return

                        val start = minOf(anchor, current).coerceIn(0, visible.lastIndex)
                        val end = maxOf(anchor, current).coerceIn(0, visible.lastIndex)

                        val rangePackages = visible
                            .subList(start, end + 1)
                            .map { it.packageName }
                            .toSet()

                        selected = selected + rangePackages
                    }

                    LaunchedEffect(dragSelecting) {
                        while (dragSelecting) {
                            val layout = listState.layoutInfo
                            val top = layout.viewportStartOffset.toFloat()
                            val bottom = layout.viewportEndOffset.toFloat()
                            val edge = 110f

                            val scrollAmount = when {
                                dragY < top + edge -> {
                                    -24f * (
                                            1f - (
                                                    (dragY - top)
                                                        .coerceIn(0f, edge) / edge
                                                    )
                                            )
                                }

                                dragY > bottom - edge -> {
                                    24f * (
                                            1f - (
                                                    (bottom - dragY)
                                                        .coerceIn(0f, edge) / edge
                                                    )
                                            )
                                }

                                else -> 0f
                            }

                            if (scrollAmount != 0f) {
                                listState.scroll {
                                    scrollBy(scrollAmount)
                                }
                            }

                            val currentIndex = findItemIndex(listState, dragY)
                            val anchor = dragAnchorIndex

                            if (anchor != null && currentIndex != null) {
                                selectRange(anchor, currentIndex)
                            }

                            delay(16)
                        }
                    }

                    Box(
                        Modifier
                            .fillMaxSize()
                    ) {

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(visible) {

                                    awaitEachGesture {

                                        val down = awaitFirstDown(
                                            requireUnconsumed = false,
                                            pass = PointerEventPass.Initial
                                        )

                                        val longPressed = withTimeoutOrNull<Boolean>(
                                            viewConfiguration.longPressTimeoutMillis
                                        ) {
                                            while (true) {
                                                val event = awaitPointerEvent(
                                                    PointerEventPass.Initial
                                                )

                                                val change = event.changes
                                                    .firstOrNull { it.id == down.id }
                                                    ?: return@withTimeoutOrNull false

                                                if (!change.pressed) {
                                                    return@withTimeoutOrNull false
                                                }

                                                val dx = change.position.x - down.position.x
                                                val dy = change.position.y - down.position.y

                                                val distanceSquared = dx * dx + dy * dy
                                                val slop = viewConfiguration.touchSlop

                                                if (distanceSquared > slop * slop) {
                                                    return@withTimeoutOrNull false
                                                }
                                            }

                                            @Suppress("UNREACHABLE_CODE")
                                            true
                                        } ?: true

                                        if (!longPressed) {
                                            return@awaitEachGesture
                                        }

                                        val anchor =
                                            findItemIndex(listState, down.position.y)

                                        if (anchor == null || anchor !in visible.indices) {
                                            return@awaitEachGesture
                                        }

                                        down.consume()

                                        dragSelecting = true
                                        dragAnchorIndex = anchor
                                        dragY = down.position.y

                                        // Select the first long-pressed app.
                                        selectRange(anchor, anchor)

                                        while (true) {

                                            val event = awaitPointerEvent(
                                                PointerEventPass.Initial
                                            )

                                            val change = event.changes
                                                .firstOrNull { it.id == down.id }

                                            if (change == null || !change.pressed) {
                                                // Consume the finger-up that ends this
                                                // long-press drag-select gesture too, or
                                                // it leaks through to AppRow's
                                                // combinedClickable on the Main pass and
                                                // fires a conflicting toggle-off click.
                                                change?.consume()
                                                break
                                            }

                                            change.consume()

                                            dragY = change.position.y

                                            val currentIndex =
                                                findItemIndex(
                                                    listState,
                                                    dragY
                                                )

                                            if (
                                                currentIndex != null &&
                                                currentIndex in visible.indices
                                            ) {
                                                selectRange(
                                                    anchor,
                                                    currentIndex
                                                )
                                            }
                                        }

                                        dragSelecting = false
                                        dragAnchorIndex = null
                                    }
                                },
                            contentPadding = PaddingValues(
                                bottom = if (selected.isNotEmpty()) {
                                    112.dp
                                } else {
                                    16.dp
                                }
                            )
                        ) {
                            items(
                                visible,
                                key = { it.packageName }
                            ) { item ->

                                AppRow(
                                    item = item,
                                    selected = selected.contains(item.packageName),

                                    onClick = {
                                        if (selected.isNotEmpty()) {
                                            selected =
                                                if (
                                                    selected.contains(item.packageName)
                                                ) {
                                                    selected - item.packageName
                                                } else {
                                                    selected + item.packageName
                                                }
                                        } else {
                                            detail = item
                                        }
                                    }
                                )
                            }
                        }

                        if (selected.isNotEmpty()) {
                            MultiBar(
                                count = selected.size,
                                actions = multiActions,
                                toggleAction = { key ->
                                    multiActions =
                                        if (multiActions.contains(key)) {
                                            multiActions - key
                                        } else {
                                            multiActions + key
                                        }
                                },
                                netSelected = netAll || netBackground,
                                netAll = netAll,
                                netBackground = netBackground,
                                toggleNetAll = { netAll = !netAll },
                                toggleNetBackground = { netBackground = !netBackground },
                                netMenuOpen = netMenuOpen,
                                onNetClick = { netMenuOpen = !netMenuOpen },
                                onNetDismiss = { netMenuOpen = false },
                                running = multiRunning,
                                onGo = {
                                    if (multiRunning) {
                                        // Cancel the current bulk job. The coroutine checks for
                                        // cancellation before every command, so later stages
                                        // cannot start after the user cancels.
                                        multiJob?.cancel()
                                    } else {
                                        val targets = selected.toList()
                                        val actions = multiActions
                                        val doNetAll = netAll
                                        val doNetBackground = netBackground

                                        val hasWork =
                                            targets.isNotEmpty() &&
                                                    (actions.isNotEmpty() || doNetAll || doNetBackground)

                                        if (hasWork) {
                                            multiJob = scope.launch {
                                                multiRunning = true
                                                try {
                                                    // 2. Data, for every selected app.
                                                    if (actions.contains("data")) {
                                                        targets.forEach { pkg ->
                                                            execBulk("pm clear $pkg")
                                                        }
                                                    }

                                                    // 3. Network, for every selected app.
                                                    if (doNetAll || doNetBackground) {
                                                        if (doNetAll) {
                                                            val chain3 =
                                                                execBulk("cmd connectivity get-chain3-enabled").trim()
                                                            val chain3Disabled =
                                                                chain3.equals("false", ignoreCase = true) ||
                                                                        chain3.equals("disabled", ignoreCase = true) ||
                                                                        chain3.endsWith(": false", ignoreCase = true) ||
                                                                        chain3.endsWith(": disabled", ignoreCase = true)

                                                            if (chain3Disabled) {
                                                                execBulk("cmd connectivity set-chain3-enabled true")
                                                            }
                                                        }

                                                        targets.forEach { pkg ->
                                                            if (doNetAll) {
                                                                execBulk(
                                                                    "cmd connectivity set-package-networking-enabled false $pkg"
                                                                )
                                                            }
                                                            if (doNetBackground) {
                                                                val uid = runCatching {
                                                                    context.packageManager
                                                                        .getApplicationInfo(pkg, 0)
                                                                        .uid
                                                                }.getOrNull()

                                                                if (uid != null) {
                                                                    execBulk(
                                                                        "cmd netpolicy add restrict-background-blacklist $uid"
                                                                    )
                                                                } else {
                                                                    log(
                                                                        "Could not resolve UID for $pkg, skipped background restrict",
                                                                        "err"
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // 4. Force stop, for every selected app.
                                                    if (actions.contains("stop")) {
                                                        targets.forEach { pkg ->
                                                            execBulk("am force-stop $pkg")
                                                        }
                                                    }

                                                    // 5. Share APK, for every selected app.
                                                    if (actions.contains("share")) {
                                                        val uris = mutableListOf<android.net.Uri>()

                                                        targets.forEach { pkg ->
                                                            currentCoroutineContext().ensureActive()
                                                            log("Extracting APK: $pkg")
                                                            val out = extractApkFile(context, pkg)

                                                            if (out != null) {
                                                                uris += FileProvider.getUriForFile(
                                                                    context,
                                                                    "com.appman.app.fileprovider",
                                                                    out
                                                                )
                                                            } else {
                                                                log("ERROR: APK extraction failed for $pkg", "err")
                                                            }
                                                        }

                                                        currentCoroutineContext().ensureActive()
                                                        if (uris.isNotEmpty()) {
                                                            withContext(Dispatchers.Main) {
                                                                context.startActivity(
                                                                    Intent.createChooser(
                                                                        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                                                            type = "application/vnd.android.package-archive"
                                                                            putParcelableArrayListExtra(
                                                                                Intent.EXTRA_STREAM,
                                                                                ArrayList(uris)
                                                                            )
                                                                            addFlags(
                                                                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                                                        Intent.FLAG_ACTIVITY_NEW_TASK
                                                                            )
                                                                        },
                                                                        "Share APKs"
                                                                    )
                                                                )
                                                            }
                                                        }
                                                    }

                                                    // 6. Uninstall, for every selected app.
                                                    if (actions.contains("uninstall")) {
                                                        targets.forEach { pkg ->
                                                            execBulk("pm uninstall --user 0 $pkg")
                                                        }
                                                    }
                                                } catch (e: CancellationException) {
                                                    log("Bulk operation cancelled", "err")
                                                } finally {
                                                    multiRunning = false
                                                    multiJob = null
                                                    selected = emptySet()
                                                    multiActions = emptySet()
                                                    netAll = false
                                                    netBackground = false
                                                    netMenuOpen = false
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(12.dp)
                            )
                        }
                    }
                }
            }
            // may revert back
//            if (selected.isNotEmpty() && detail == null && !terminal) {
//                MultiBar(selected.size, multiAction, { multiAction = it }, connectivityDrop, { connectivityDrop = !connectivityDrop }, onGo = {
//                    val targets = selected.toList(); selected = emptySet()
//                    val commandFor: (String) -> String = { pkg ->
//                        when (multiAction) {
//                            "clear_cache" -> "pm clear --cache-only $pkg"
//                            "clear_data" -> "pm clear $pkg"
//                            "force_stop" -> "am force-stop $pkg"
//                            "uninstall" -> "pm uninstall --user 0 $pkg"
//                            "share" -> "echo APK: $(pm path $pkg)"
//                            "wifi" -> "cmd connectivity set-package-networking-enabled false $pkg"
//                            "mobile" -> "cmd connectivity set-package-networking-enabled false $pkg"
//                            "background" -> "cmd appops set $pkg RUN_ANY_IN_BACKGROUND ignore"
//                            else -> "cmd connectivity set-package-networking-enabled false $pkg"
//                        }
//                    }
//                    targets.forEach { runCommand(commandFor(it)) }
//                })
//            }
            if (!hoodMax) HoodPanel(hood, hoodExpanded, { hoodExpanded = !hoodExpanded }, onClear = { hood = emptyList() }, onMax = { hoodMax = true }, modifier = Modifier.height(if (hoodExpanded) 300.dp else 94.dp))
            if (hoodMax) FullHood(hood, onClear = { hood = emptyList() }, onClose = { hoodMax = false })
        }
    }
    if (pairingDialog) PairDialog(code, { code = it }, onDismiss = { pairingDialog = false }, onPair = {
        if (code.length == 6) {
            val i = Intent(context, AdbPairingService::class.java).setAction(AdbPairingService.ACTION_PAIR).putExtra(AdbPairingService.EXTRA_CODE, code)
            ContextCompat.startForegroundService(context, i); pairingDialog = false; log("Pairing requested via AppMan")
        } else snack = "Enter the current 6-digit code"
    })
}

private fun findItemIndex(
    state: LazyListState,
    y: Float
): Int? {
    val items = state.layoutInfo.visibleItemsInfo

    if (items.isEmpty()) return null

    // First try to find the row directly underneath the finger.
    items.firstOrNull {
        y >= it.offset &&
                y < it.offset + it.size
    }?.let {
        return it.index
    }

    // If the finger is just outside the visible rows,
    // use the nearest row.
    return items.minByOrNull {
        kotlin.math.abs(
            (it.offset + it.size / 2f) - y
        )
    }?.index
}
@Composable private fun TopBar(terminal: Boolean, setTerminal: (Boolean) -> Unit, connected: Boolean, onPair: () -> Unit, onDisconnect: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(White).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("AppMan", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Ink); Text(if (connected) "ADB • connected" else "ADB • not connected", fontSize = 12.sp, color = if (connected) Blue else Ink.copy(alpha = .55f)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { setTerminal(!terminal) }, label = { Text(if (terminal) "UI mode" else "Terminal") }, leadingIcon = { Icon(if (terminal) Icons.Default.Apps else Icons.Default.Terminal, null) })
            if (!connected) Button(onClick = onPair, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("Pair") } else OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
        }
    }
}

@Composable private fun SetupCard(onOpen: () -> Unit) {
    Card(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Cream), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Developer options are off", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("AppMan cannot enable them for you. Open Developer options, enable Wireless debugging, then choose “Pair device with pairing code”. Keep AppMan open while pairing.", fontSize = 13.sp)
            Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("Open Developer options") }
        }
    }
}

@Composable private fun SearchBar(query: String, onQuery: (String) -> Unit, selecting: Boolean, onClear: () -> Unit, onSelectAll: () -> Unit, filter: AppFilter, onFilter: (AppFilter) -> Unit, sort: SortBy, onSort: (SortBy) -> Unit, asc: Boolean, toggleAsc: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(query, onQuery, Modifier.weight(1f), singleLine = true, placeholder = { Text("Search apps…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp))
        if (selecting) { IconButton(onClick = onSelectAll) { Icon(Icons.Default.SelectAll, "Select all") }; IconButton(onClick = onClear) { Icon(Icons.Default.Close, "Clear selection") } }
        Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.Tune, "Filters") }; DropdownMenu(menu, { menu = false }) {
            Text("Apps", Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            AppFilter.values().forEach { DropdownMenuItem(text = { Text(it.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }, onClick = { onFilter(it); menu = false }) }
            Divider(); Text("Sort", Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            DropdownMenuItem(text = { Text("A–Z name") }, onClick = { onSort(SortBy.NAME); menu = false })
            DropdownMenuItem(text = { Text("Size") }, onClick = { onSort(SortBy.SIZE); menu = false })
            DropdownMenuItem(text = { Text(if (asc) "Ascending" else "Descending") }, onClick = { toggleAsc(); menu = false })
        } }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    item: AppItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick
            )
            .background(
                if (selected) {
                    Blue.copy(alpha = 0.14f)
                } else {
                    Color.Transparent
                }
            )
            .padding(
                horizontal = 16.dp,
                vertical = 9.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AndroidView(
            factory = { android.widget.ImageView(it) },
            update = {
                it.setImageDrawable(item.icon)
                it.setPadding(8, 8, 8, 8)
            },
            modifier = Modifier
                .size(54.dp)
                .background(
                    if (selected) {
                        Blue.copy(alpha = 0.18f)
                    } else {
                        Sand.copy(alpha = 0.28f)
                    },
                    RoundedCornerShape(14.dp)
                )
        )

        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                item.label,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Text(
                item.packageName,
                fontSize = 12.sp,
                color = Ink.copy(alpha = .55f),
                maxLines = 1
            )
        }

        Text(
            formatSize(item.sizeBytes),
            fontSize = 11.sp,
            color = Ink.copy(alpha = .55f),
            modifier = Modifier.padding(end = 4.dp)
        )

        Icon(
            Icons.Default.ChevronRight,
            null,
            tint = Ink.copy(.5f)
        )
    }
}

private enum class DetailNetworkToggle {
    ALL,
    BACKGROUND
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppDetail(
    item: AppItem,
    onBack: () -> Unit,
    onRun: (String) -> Unit,
    onShare: (String) -> Unit,
    onLog: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

    var dataExpanded by remember { mutableStateOf(true) }
    var networkExpanded by remember { mutableStateOf(true) }

    // These two switches are independent UI states. They are only synchronized
    // when the requested ADB operation necessarily changes the other state.
    var allEnabled by remember { mutableStateOf(true) }
    var backgroundEnabled by remember { mutableStateOf(true) }

    val uid = remember(item.packageName) {
        context.packageManager
            .getApplicationInfo(item.packageName, 0)
            .uid
    }

    val networkScope = rememberCoroutineScope()

    LaunchedEffect(item.packageName) {
        withContext(Dispatchers.IO) {
            runCatching {
                val adb = AdbManager.get(context)

                // Background: present in the restrict-background blacklist = OFF.
                val blacklistCommand =
                    "cmd netpolicy list restrict-background-blacklist"
                onLog("$ $blacklistCommand")
                val blacklist = adb.exec(blacklistCommand)
                onLog(blacklist.ifBlank { "(no output)" })

                // netpolicy reports blacklisted apps by UID (userId*100000 + appId).
                // ApplicationInfo.uid already returns that full per-user UID, not the
                // bare appId, so it's reused as-is for the policy commands below.
                val packageIsBlacklisted =
                    blacklist.lineSequence().any { line ->
                        Regex("\\b$uid\\b")
                            .containsMatchIn(line)
                    }

                // All: <PACKAGE_NAME>:deny = OFF, <PACKAGE_NAME>:allow = ON.
                val networkingCommand =
                    "cmd connectivity get-package-networking-enabled ${item.packageName}"
                onLog("$ $networkingCommand")
                val packageNetworking = adb.exec(networkingCommand)
                onLog(packageNetworking.ifBlank { "(no output)" })

                val networkingAllowed = packageNetworking
                    .lineSequence()
                    .any { line ->
                        line.trim().equals(
                            "${item.packageName}:allow",
                            ignoreCase = true
                        )
                    }

                withContext(Dispatchers.Main) {
                    backgroundEnabled = !packageIsBlacklisted
                    allEnabled = networkingAllowed
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    onLog(
                        "Could not fetch network state: ${
                            it.message ?: it.javaClass.simpleName
                        }"
                    )
                }
            }
        }
    }

    fun copyPackageName() {
        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText("Package name", item.packageName)
        )
        android.widget.Toast.makeText(
            context,
            "Package name copied",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    fun openApp() {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(item.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            onLog("No launch activity found: ${item.packageName}")
            android.widget.Toast.makeText(
                context,
                "This app cannot be opened directly",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun toggleAll(enabled: Boolean) {
        allEnabled = enabled

        networkScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val adb = AdbManager.get(context)

                    fun execTransparent(command: String): String {
                        onLog("$ $command")
                        val output = adb.exec(command)
                        onLog(output.ifBlank { "(no output)" })
                        return output
                    }

                    if (!enabled) {
                        // First check chain3. Only enable it when it is disabled.
                        val chain3Command =
                            "cmd connectivity get-chain3-enabled"
                        val chain3 = execTransparent(chain3Command).trim()

                        val chain3Disabled =
                            chain3.equals("false", ignoreCase = true) ||
                                    chain3.equals("disabled", ignoreCase = true) ||
                                    chain3.endsWith(": false", ignoreCase = true) ||
                                    chain3.endsWith(": disabled", ignoreCase = true)

                        if (chain3Disabled) {
                            execTransparent(
                                "cmd connectivity set-chain3-enabled true"
                            )
                        }

                        execTransparent(
                            "cmd connectivity set-package-networking-enabled false ${item.packageName}"
                        )

                        withContext(Dispatchers.Main) {
                            backgroundEnabled = false
                        }
                    } else {
                        execTransparent(
                            "cmd connectivity set-package-networking-enabled true ${item.packageName}"
                        )
                        execTransparent(
                            "cmd netpolicy remove restrict-background-blacklist $uid"
                        )

                        withContext(Dispatchers.Main) {
                            backgroundEnabled = true
                        }
                    }
                }.onFailure {
                    withContext(Dispatchers.Main) {
                        allEnabled = !enabled
                        onLog(
                            "ERROR: ${it.message ?: it.javaClass.simpleName}"
                        )
                    }
                }
            }
        }
    }

    fun toggleBackground(enabled: Boolean) {
        backgroundEnabled = enabled

        networkScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val adb = AdbManager.get(context)

                    fun execTransparent(command: String) {
                        onLog("$ $command")
                        val output = adb.exec(command)
                        onLog(output.ifBlank { "(no output)" })
                    }

                    if (!enabled) {
                        execTransparent(
                            "cmd netpolicy add restrict-background-blacklist $uid"
                        )
                    } else {
                        execTransparent(
                            "cmd netpolicy remove restrict-background-blacklist $uid"
                        )
                        execTransparent(
                            "cmd connectivity set-package-networking-enabled true ${item.packageName}"
                        )

                        withContext(Dispatchers.Main) {
                            allEnabled = true
                        }
                    }
                }.onFailure {
                    withContext(Dispatchers.Main) {
                        backgroundEnabled = !enabled
                        onLog(
                            "ERROR: ${it.message ?: it.javaClass.simpleName}"
                        )
                    }
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back")
            }
            Text(
                "App info",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AndroidView(
                        factory = { android.widget.ImageView(it) },
                        update = {
                            it.setImageDrawable(item.icon)
                            it.setPadding(8, 8, 8, 8)
                        },
                        modifier = Modifier
                            .size(88.dp)
                            .background(
                                Sand.copy(alpha = .28f),
                                RoundedCornerShape(20.dp)
                            )
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        item.label,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        item.packageName,
                        fontSize = 12.sp,
                        color = Ink.copy(alpha = .58f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { copyPackageName() },
                                onLongClick = { copyPackageName() }
                            )
                            .padding(vertical = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Text(
                        "Tap or long-press package name to copy",
                        fontSize = 10.sp,
                        color = Ink.copy(alpha = .42f)
                    )
                }
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DetailQuickAction(
                        icon = Icons.Default.OpenInNew,
                        label = "Open",
                        onClick = ::openApp
                    )
                    DetailQuickAction(
                        icon = Icons.Default.Delete,
                        label = "Uninstall",
                        onClick = {
                            onLog("Uninstall requested: ${item.packageName}")
                            onRun("pm uninstall --user 0 ${item.packageName}")
                        }
                    )
                    DetailQuickAction(
                        icon = Icons.Default.PauseCircleOutline,
                        label = "Force stop",
                        onClick = {
                            onLog("Force stop requested: ${item.packageName}")
                            onRun("am force-stop ${item.packageName}")
                        }
                    )
                }
            }

            item {
                DetailExpandableHeader(
                    title = "Data",
                    expanded = dataExpanded,
                    onClick = { dataExpanded = !dataExpanded }
                )

                if (dataExpanded) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DataInfoRow("App size", formatSize(item.sizeBytes))

                        ActionButton(
                            "Clear cache",
                            Icons.Default.CleaningServices
                        ) {
                            onLog("Clear cache requested: ${item.packageName}")
                            onRun("pm clear --cache-only ${item.packageName}")
                        }

                        ActionButton(
                            "Clear data",
                            Icons.Default.DeleteSweep
                        ) {
                            onLog("Clear data requested: ${item.packageName}")
                            onRun("pm clear ${item.packageName}")
                        }

                        ActionButton(
                            "Extract / share APK",
                            Icons.Default.Share
                        ) {
                            onLog("Extracting APK: ${item.packageName}")
                            onShare(item.packageName)
                        }
                    }
                }
            }

            item {
                DetailExpandableHeader(
                    title = "Network",
                    expanded = networkExpanded,
                    onClick = { networkExpanded = !networkExpanded }
                )

                if (networkExpanded) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Choose the app's network/background mode",
                            fontSize = 12.sp,
                            color = Ink.copy(alpha = .58f)
                        )

                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            DetailNetworkToggle(
                                label = "All",
                                checked = allEnabled,
                                onCheckedChange = ::toggleAll
                            )

                            DetailNetworkToggle(
                                label = "Background",
                                checked = backgroundEnabled,
                                onCheckedChange = ::toggleBackground
                            )
                        }
                    }
                }
            }

            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                ) {
                    Text(
                        "APP location",
                        fontWeight = FontWeight.Bold,
                        color = Blue
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Sand.copy(alpha = .18f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Public,
                            null,
                            tint = Ink.copy(alpha = .55f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            appLocation(context, item.packageName),
                            fontSize = 12.sp,
                            color = Ink.copy(alpha = .68f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailQuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(52.dp)
        ) {
            Icon(icon, contentDescription = label)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailExpandableHeader(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Blue,
            modifier = Modifier.weight(1f)
        )
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse $title" else "Expand $title"
        )
    }
}

@Composable
private fun DetailNetworkToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Blue,
                checkedBorderColor = Blue,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE2E5EA),
                uncheckedBorderColor = Color(0xFFB8BEC8)
            )
        )
    }
}

@Composable
private fun DataInfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                Sand.copy(alpha = .18f),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = ActionButtonBlue,
            contentColor = Ink
        )
    ) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp)
    }
}

@Composable private fun MultiBar(
    count: Int,
    actions: Set<String>,
    toggleAction: (String) -> Unit,
    netSelected: Boolean,
    netAll: Boolean,
    netBackground: Boolean,
    toggleNetAll: () -> Unit,
    toggleNetBackground: () -> Unit,
    netMenuOpen: Boolean,
    onNetClick: () -> Unit,
    onNetDismiss: () -> Unit,
    running: Boolean,
    onGo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), shadowElevation = 10.dp, color = Ink) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$count selected", color = White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (running) {
                    Button(
                        onClick = onGo,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE5484D),
                            contentColor = White
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(38.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.fillMaxSize(),
                                color = White,
                                strokeWidth = 2.dp
                            )
                            Icon(
                                Icons.Default.Pause,
                                contentDescription = "Cancel bulk operation",
                                tint = White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onGo,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Yellow,
                            contentColor = Ink
                        ),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text("GO", color = Ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    modifier = Modifier.height(48.dp),
                    selected = actions.contains("data"),
                    onClick = { toggleAction("data") },
                    leadingIcon = { Icon(Icons.Default.Storage, null, Modifier.size(16.dp)) },
                    label = { Text("Data", fontSize = 11.sp) }
                )
                Box {
                    FilterChip(
                        modifier = Modifier.height(48.dp),
                        selected = netSelected,
                        onClick = onNetClick,
                        leadingIcon = { Icon(Icons.Default.CellTower, null, Modifier.size(16.dp)) },
                        trailingIcon = { Icon(Icons.Default.ExpandMore, null, Modifier.size(16.dp)) },
                        label = { Text("Net", fontSize = 11.sp) }
                    )
                    DropdownMenu(netMenuOpen, onNetDismiss) {
                        DropdownMenuItem(
                            text = { Text("Background") },
                            leadingIcon = {
                                Checkbox(checked = netBackground, onCheckedChange = { toggleNetBackground() })
                            },
                            onClick = toggleNetBackground
                        )
                        DropdownMenuItem(
                            text = { Text("All") },
                            leadingIcon = {
                                Checkbox(checked = netAll, onCheckedChange = { toggleNetAll() })
                            },
                            onClick = toggleNetAll
                        )
                    }
                }
                FilterChip(
                    modifier = Modifier.height(48.dp),
                    selected = actions.contains("stop"),
                    onClick = { toggleAction("stop") },
                    leadingIcon = { Icon(Icons.Default.Pause, null, Modifier.size(16.dp)) },
                    label = { Text("Stop", fontSize = 11.sp) }
                )
                FilterChip(
                    modifier = Modifier.height(48.dp),
                    selected = actions.contains("share"),
                    onClick = { toggleAction("share") },
                    leadingIcon = { Icon(Icons.Default.Share, null, Modifier.size(16.dp)) },
                    label = { Text("Share", fontSize = 11.sp) }
                )
                FilterChip(
                    modifier = Modifier.height(48.dp),
                    selected = actions.contains("uninstall"),
                    onClick = { toggleAction("uninstall") },
                    leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = Color(0xFFE5484D)) },
                    label = { Text("Uninstall", fontSize = 11.sp) }
                )
            }
        }
    }
}

@Composable private fun TerminalPane(connected: Boolean, onConnect: () -> Unit, onCommand: (String) -> Unit, modifier: Modifier = Modifier) {
    var command by remember { mutableStateOf("") }
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Terminal", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = Black), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Text(if (connected) "ADB shell connected" else "ADB shell not connected", color = if (connected) Blue else Yellow, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    command,
                    { command = it },
                    Modifier.fillMaxWidth().weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 14.sp),
                    cursorBrush = SolidColor(Blue),
                    decorationBox = { inner -> Box(Modifier.fillMaxSize()) {
                        if (command.isEmpty())
                            Text(
                                if (connected) "Run any shell command…"
                                else "Pair first…", color = White.copy(.4f));
                        inner() } })
                Row(verticalAlignment = Alignment.CenterVertically) { Text("$ ", color = Yellow, fontWeight = FontWeight.Bold);
                    BasicTextField(command, { command = it },  Modifier.weight(1f), textStyle = androidx.compose.ui.text.TextStyle(color = White), singleLine = true, cursorBrush = SolidColor(Blue));  IconButton(onClick = { if (connected && command.isNotBlank()) { onCommand(command); command = "" } }) { Icon(Icons.Default.Send, null, tint = Blue) } }
                if (!connected) TextButton(onClick = onConnect) { Text("Pair / connect", color = Blue) }
            }
        }
    }
}

@Composable private fun HoodPanel(lines: List<HoodLine>, expanded: Boolean, onExpand: () -> Unit, onClear: () -> Unit, onMax: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = Black) {
        Column(Modifier.fillMaxSize()) { Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Text("HOOD", color = White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f)); IconButton(onClick = onClear) { Icon(Icons.Default.RemoveCircleOutline, "Clear HOOD", tint = White) }; IconButton(onClick = onExpand) { Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess, null, tint = White) }; IconButton(onClick = onMax) { Icon(Icons.Default.OpenInFull, null, tint = White) } }; LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), reverseLayout = false) { items(lines) { Text(it.text, color = if (it.kind == "err") Color(0xFFFF7B72) else White.copy(.88f), fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp)) } } }
    }
}

@Composable private fun FullHood(lines: List<HoodLine>, onClear: () -> Unit, onClose: () -> Unit) { Surface(Modifier.fillMaxSize(), color = Black) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("HOOD", color = White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f)); IconButton(onClick = onClear) { Icon(Icons.Default.RemoveCircleOutline, "Clear HOOD", tint = White) }; IconButton(onClick = onClose) { Icon(Icons.Default.CloseFullscreen, null, tint = White) } }; LazyColumn(Modifier.fillMaxSize()) { items(lines) { Text(it.text, color = White.copy(.9f), fontSize = 12.sp, modifier = Modifier.padding(vertical = 3.dp)) } } } } }

@Composable private fun PairDialog(code: String, onCode: (String) -> Unit, onDismiss: () -> Unit, onPair: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Pair wireless debugging") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Open Developer options → Wireless debugging → Pair device with pairing code. Enter the current six-digit code below. The persistent AppMan notification also accepts the code.", fontSize = 13.sp); OutlinedTextField(code, { onCode(it.filter(Char::isDigit).take(6)) }, label = { Text("Pairing code") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) } }, confirmButton = { Button(onClick = onPair) { Text("Pair") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private fun appLocation(context: Context, packageName: String): String {
    return runCatching {
        context.packageManager
            .getApplicationInfo(packageName, 0)
            .sourceDir
    }.getOrElse {
        "Location unavailable"
    }
}

// Pulls a package's APK off the device via ADB and writes it to a local
// cache file. Used by both the single-app "Share" button and the bulk
// Share stage in the floating action bar, so both stay in sync.
private suspend fun extractApkFile(context: Context, packageName: String): File? =
    withContext(Dispatchers.IO) {
        runCatching {
            val adb = AdbManager.get(context)
            val pathLine = adb.exec("pm path $packageName").lineSequence().firstOrNull { it.startsWith("package:") }
                ?: throw IllegalStateException("Android did not return an APK path")
            val remotePath = pathLine.removePrefix("package:").trim()
            val b64 = adb.exec("base64 $remotePath").replace(Regex("\\s"), "")
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val dir = File(context.cacheDir, "shared_apks").apply { mkdirs() }
            val out = File(dir, packageName.replace('.', '_') + ".apk")
            out.writeBytes(bytes)
            out
        }.getOrNull()
    }

private fun shareApk(context: Context, packageName: String) {
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        val out = extractApkFile(context, packageName)
        if (out == null) {
            withContext(Dispatchers.Main) { android.widget.Toast.makeText(context, "APK extraction failed", android.widget.Toast.LENGTH_LONG).show() }
            return@launch
        }
        val uri = FileProvider.getUriForFile(context, "com.appman.app.fileprovider", out)
        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/vnd.android.package-archive"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK) }, "Share APK"))
        }
    }
}

private fun developerOptionsEnabled(context: Context): Boolean = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1 }.getOrDefault(false)

private fun loadApps(context: Context): List<AppItem> {
    val pm = context.packageManager

    val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getInstalledApplications(
            PackageManager.ApplicationInfoFlags.of(
                PackageManager.GET_META_DATA.toLong()
            )
        )
    } else {
        @Suppress("DEPRECATION")
        pm.getInstalledApplications(
            PackageManager.GET_META_DATA
        )
    }

    return installed.mapNotNull { info ->
        if (info.packageName == context.packageName) {
            return@mapNotNull null
        }

        val label = runCatching {
            pm.getApplicationLabel(info).toString()
        }.getOrDefault(info.packageName)

        val size = runCatching {
            File(info.sourceDir).length()
        }.getOrDefault(0L)

        AppItem(
            label = label,
            packageName = info.packageName,
            icon = pm.getApplicationIcon(info),
            system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            sizeBytes = size
        )
    }
}

private fun formatSize(bytes: Long): String { if (bytes <= 0) return "—"; val units = arrayOf("B","KB","MB","GB"); var v = bytes.toDouble(); var i = 0; while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }; return "${DecimalFormat("0.0").format(v)} ${units[i]}" }