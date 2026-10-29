package com.appman.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.appman.app.adb.AdbManager
import com.appman.app.model.*
import com.appman.app.service.AdbPairingService
import com.appman.app.ui.theme.*
import com.appman.app.util.developerOptionsEnabled
import com.appman.app.util.extractApkFile
import com.appman.app.util.loadApps
import com.appman.app.util.shareApk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Composable internal fun AppManApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var terminal by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var appsLoading by remember { mutableStateOf(true) }
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
    var netSelected by remember { mutableStateOf(false) } // chip selection, independent of the toggles above
    var multiRunning by remember { mutableStateOf(false) }
    var multiJob by remember { mutableStateOf<Job?>(null) }
    var snack by remember { mutableStateOf<String?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadApps(context) }
        appsLoading = false

        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Check immediately when AppMan starts. A fresh process has a
        // brand new (unconnected) AdbManager even if the device was
        // paired and connected in a previous session, so a plain
        // isConnected() here will almost always come back false right
        // after launch. Try a silent autoConnect() as a one-shot retry
        // before giving up and asking the user to pair again — if the
        // device still remembers this app's key, this reconnects
        // without any code entry.
        connected = withContext(Dispatchers.IO) {
            val adb = AdbManager.get(context)
            if (adb.isConnected()) {
                true
            } else {
                val reconnected = runCatching { adb.autoConnect() }.getOrDefault(false)
                reconnected || adb.isConnected()
            }
        }
    }
    // Keep the connection indicator honest, and opportunistically retry
    // a silent autoConnect() while disconnected — covers the case where
    // wireless debugging only became reachable (network back, device
    // unlocked, etc.) after AppMan had already given up.
    LaunchedEffect(Unit) {
        var sinceLastAutoConnect = 0
        while (true) {
            val state = withContext(Dispatchers.IO) {
                val adb = AdbManager.get(context)
                if (adb.isConnected()) {
                    true
                } else {
                    sinceLastAutoConnect++
                    // Try roughly every 5 seconds while disconnected,
                    // not every poll tick, so we don't hammer the
                    // transport.
                    if (sinceLastAutoConnect >= 5) {
                        sinceLastAutoConnect = 0
                        val reconnected = runCatching { adb.autoConnect() }.getOrDefault(false)
                        reconnected || adb.isConnected()
                    } else {
                        false
                    }
                }
            }

            if (connected != state) {
                connected = state
            }

            kotlinx.coroutines.delay(1000)
        }
    }
    // Periodically refresh the installed-app list in the background so
    // installs/uninstalls/size changes made outside AppMan (or via ADB
    // commands run from the terminal) show up without a manual restart.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            val refreshed = withContext(Dispatchers.IO) { loadApps(context) }

            // loadApps() creates fresh Drawable instances every time, so a
            // plain `apps = refreshed` would always look like a change and
            // rebuild the visible list (which restarts the drag-select
            // pointer handler mid-gesture). Reuse unchanged AppItem
            // instances so an identical refresh is a no-op.
            val previous = apps.associateBy { it.packageName }
            apps = refreshed.map { fresh ->
                val old = previous[fresh.packageName]
                if (old != null &&
                    old.label == fresh.label &&
                    old.system == fresh.system &&
                    old.sizeBytes == fresh.sizeBytes
                ) {
                    old
                } else {
                    fresh
                }
            }
        }
    }

    val visible = remember(apps, filter, sort, ascending, query) {
        apps.filter { a ->
            (filter == AppFilter.ALL || (filter == AppFilter.SYSTEM && a.system) || (filter == AppFilter.INSTALLED && !a.system)) &&
                    (query.isBlank() || a.label.contains(query, true) || a.packageName.contains(query, true))
        }.sortedWith(if (sort == SortBy.NAME) compareBy<AppItem> { it.label.lowercase() } else compareBy { it.sizeBytes }).let { if (ascending) it else it.reversed() }
    }

    fun log(line: String, kind: String = "cmd") { hood = (hood + HoodLine(kind, line)).takeLast(500) }
    suspend fun execAwait(command: String, timeoutMs: Long = 8000): String {
        log("$ $command", "cmd")
        val result = withContext(Dispatchers.IO) {
            try {
                withTimeoutOrNull(timeoutMs + 3000) { AdbManager.get(context).exec(command, timeoutMs) }
                    ?: "ERROR: timed out after ${timeoutMs}ms, skipping"
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

    suspend fun execBulk(command: String, timeoutMs: Long = 8000): String {
        currentCoroutineContext().ensureActive()
        return execAwait(command, timeoutMs)
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
                AppDetail(item = detail!!, connected = connected, onBack = { detail = null }, onRun = { cmd -> runCommand(cmd) }, onShare = { shareApk(context, it) }, onLog = { log(it) })
            } else {
                Column(Modifier.weight(1f)) {
                    // Declared before SearchBar so "clear selection" can also
                    // stop an in-flight drag-select auto-scroll. Otherwise the
                    // drag loop keeps re-adding packages right after the X is
                    // tapped, which looks like the list failed to clear.
                    var dragSelecting by remember { mutableStateOf(false) }
                    var dragY by remember { mutableStateOf(0f) }
                    var dragAnchorIndex by remember { mutableStateOf<Int?>(null) }

                    SearchBar(
                        query,
                        { query = it },
                        selected.isNotEmpty(),
                        onClear = {
                            selected = emptySet()
                            dragSelecting = false
                            dragAnchorIndex = null
                        },
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

                            if (!dragSelecting) break

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

                        if (appsLoading && apps.isEmpty()) {
                            Column(
                                Modifier
                                    .align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                PixelLoader()
                                Text("Loading apps…", color = Ink.copy(alpha = 0.55f), fontSize = 13.sp)
                            }
                        }

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

                                        try {
                                            // Checking the state each tick lets
                                            // the X button stop this gesture.
                                            while (dragSelecting) {

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

                                                // X may have cleared the selection
                                                // while this event was in flight.
                                                if (!dragSelecting) break

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
                                        } finally {
                                            // Also runs when this pointer handler
                                            // is cancelled (e.g. by a list
                                            // refresh), so the auto-scroll loop
                                            // can never be left running.
                                            dragSelecting = false
                                            dragAnchorIndex = null
                                        }
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
                                netSelected = netSelected,
                                netAll = netAll,
                                netBackground = netBackground,
                                toggleNetAll = {
                                    val newValue = !netAll
                                    netAll = newValue
                                    netBackground = newValue
                                },
                                toggleNetBackground = {netBackground = !netBackground},
                                netMenuOpen = netMenuOpen,
                                onNetClick = { netSelected = !netSelected },
                                onNetExpandClick = { netMenuOpen = !netMenuOpen },
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
                                        // Both switch positions are meaningful now (on = allow,
                                        // off = restrict), so whether net work happens is driven
                                        // by the chip being selected, not by the toggle values.
                                        val doNet = netSelected
                                        val allowAll = netAll
                                        val allowBackground = netBackground

                                        val hasWork =
                                            targets.isNotEmpty() &&
                                                    (actions.isNotEmpty() || doNet)

                                        if (hasWork) {
                                            multiJob = scope.launch {
                                                multiRunning = true
                                                // Pause between each app so one slow/stuck adb
                                                // command doesn't run straight into the next
                                                // without the connection settling.
                                                val interAppDelayMs = 1500L
                                                try {
                                                    // Net setup that only needs to happen once,
                                                    // not per app: make sure chain3 enforcement
                                                    // is on before any per-app networking calls.
                                                    if (doNet) {
                                                        val chain3 =
                                                            execBulk("cmd connectivity get-chain3-enabled").trim()
                                                        val chain3Disabled =
                                                            chain3.equals("chain:false", ignoreCase = true) ||
                                                                    chain3.equals("chain:disabled", ignoreCase = true) ||
                                                                    chain3.equals("false", ignoreCase = true) ||
                                                                    chain3.equals("disabled", ignoreCase = true) ||
                                                                    chain3.endsWith(": false", ignoreCase = true) ||
                                                                    chain3.endsWith(": disabled", ignoreCase = true)

                                                        if (chain3Disabled) {
                                                            execBulk("cmd connectivity set-chain3-enabled true")
                                                        }
                                                    }

                                                    val shareUris = mutableListOf<android.net.Uri>()

                                                    // Run one app fully (all its selected chips,
                                                    // in priority order) before moving to the
                                                    // next app, with a short pause in between.
                                                    targets.forEachIndexed { index, pkg ->
                                                        currentCoroutineContext().ensureActive()
                                                        log("=== $pkg ===")

                                                        // 1. Data
                                                        if (actions.contains("data")) {
                                                            execBulk("pm clear --user 0 $pkg")
                                                        }

                                                        // 2. Network
                                                        if (doNet) {
                                                            execBulk(
                                                                "cmd connectivity set-package-networking-enabled $allowAll $pkg"
                                                            )

                                                            val uid = runCatching {
                                                                context.packageManager
                                                                    .getApplicationInfo(pkg, 0)
                                                                    .uid
                                                            }.getOrNull()

                                                            if (uid != null) {
                                                                if (allowBackground) {
                                                                    execBulk(
                                                                        "cmd netpolicy remove restrict-background-blacklist $uid"
                                                                    )
                                                                } else {
                                                                    execBulk(
                                                                        "cmd netpolicy add restrict-background-blacklist $uid"
                                                                    )
                                                                }
                                                            } else {
                                                                log(
                                                                    "Could not resolve UID for $pkg, skipped background toggle",
                                                                    "err"
                                                                )
                                                            }
                                                        }

                                                        // 3. Force stop
                                                        if (actions.contains("stop")) {
                                                            execBulk("am force-stop $pkg")
                                                        }

                                                        // 4. Share APK (collected, sent once at the end)
                                                        if (actions.contains("share")) {
                                                            log("Extracting APK: $pkg")
                                                            val out = extractApkFile(context, pkg)
                                                            if (out != null) {
                                                                shareUris += FileProvider.getUriForFile(
                                                                    context,
                                                                    "com.appman.app.fileprovider",
                                                                    out
                                                                )
                                                            } else {
                                                                log("ERROR: APK extraction failed for $pkg", "err")
                                                            }
                                                        }

                                                        // 5. Uninstall (lowest priority, do last per app)
                                                        if (actions.contains("uninstall")) {
                                                            execBulk("pm uninstall --user 0 $pkg")
                                                        }

                                                        // Pause before the next app, unless this
                                                        // was the last one.
                                                        if (index != targets.lastIndex) {
                                                            delay(interAppDelayMs)
                                                        }
                                                    }

                                                    currentCoroutineContext().ensureActive()
                                                    if (shareUris.isNotEmpty()) {
                                                        withContext(Dispatchers.Main) {
                                                            context.startActivity(
                                                                Intent.createChooser(
                                                                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                                                        type = "application/vnd.android.package-archive"
                                                                        putParcelableArrayListExtra(
                                                                            Intent.EXTRA_STREAM,
                                                                            ArrayList(shareUris)
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
                                                } catch (e: CancellationException) {
                                                    log("Bulk operation cancelled", "err")
                                                } finally {
                                                    multiRunning = false
                                                    multiJob = null
                                                    selected = emptySet()
                                                    dragSelecting = false
                                                    dragAnchorIndex = null
                                                    multiActions = emptySet()
                                                    netAll = false
                                                    netBackground = false
                                                    netMenuOpen = false
                                                    netSelected = false
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
