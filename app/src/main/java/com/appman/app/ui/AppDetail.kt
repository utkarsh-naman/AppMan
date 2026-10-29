package com.appman.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.appman.app.adb.AdbManager
import com.appman.app.model.*
import com.appman.app.ui.theme.*
import com.appman.app.util.appLocation
import com.appman.app.util.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class DetailNetworkToggle {
    ALL,
    BACKGROUND
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppDetail(
    item: AppItem,
    connected: Boolean,
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
    // They stay off until ADB can actually report the real policy state.
    var allEnabled by remember { mutableStateOf(false) }
    var backgroundEnabled by remember { mutableStateOf(false) }

    val uid = remember(item.packageName) {
        context.packageManager
            .getApplicationInfo(item.packageName, 0)
            .uid
    }

    val networkScope = rememberCoroutineScope()

    LaunchedEffect(item.packageName, connected) {
        if (!connected) {
            // Reading the network policy needs ADB. While disconnected the
            // toggles are replaced by a locked control, so don't guess state.
            allEnabled = false
            backgroundEnabled = false
            return@LaunchedEffect
        }

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

    fun uninstallApp() {
        if (connected) {
            onLog("Uninstall requested: ${item.packageName}")
            onRun("pm uninstall --user 0 ${item.packageName}")
            return
        }

        // No ADB: open the system uninstall dialog. REQUEST_DELETE_PACKAGES
        // in the manifest is what allows the package installer to accept
        // this request on API 28+.
        val packageUri = Uri.parse("package:${item.packageName}")
        onLog("Uninstall requested via system prompt: ${item.packageName}")

        val launch = { action: String ->
            context.startActivity(
                Intent(action, packageUri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        runCatching { launch(Intent.ACTION_DELETE) }
            .recoverCatching {
                // Some OEM installers only advertise the legacy action.
                launch(Intent.ACTION_UNINSTALL_PACKAGE)
            }
            .onFailure {
                onLog(
                    "ERROR: Could not open the uninstall prompt: ${
                        it.message ?: it.javaClass.simpleName
                    }"
                )
                android.widget.Toast.makeText(
                    context,
                    "Could not open the uninstall prompt",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
    }

    fun toggleAll(enabled: Boolean) {
        if (!connected) return
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
                    val chain3Command =
                        "cmd connectivity get-chain3-enabled"
                    val chain3 = execTransparent(chain3Command).trim()

                    val chain3Disabled =
                        chain3.equals("chain:false", ignoreCase = true) ||
                                chain3.equals("chain:disabled", ignoreCase = true) ||
                                chain3.equals("false", ignoreCase = true) ||
                                chain3.equals("disabled", ignoreCase = true) ||
                                chain3.endsWith(": false", ignoreCase = true) ||
                                chain3.endsWith(": disabled", ignoreCase = true)

                    if (chain3Disabled) {
                        execTransparent(
                            "cmd connectivity set-chain3-enabled true"
                        )
                    }

                    if (!enabled) {
                        // First check chain3. Only enable it when it is disabled.
//                        val chain3Command =
//                            "cmd connectivity get-chain3-enabled"
//                        val chain3 = execTransparent(chain3Command).trim()
//
//                        val chain3Disabled =
//                            chain3.equals("false", ignoreCase = true) ||
//                                    chain3.equals("disabled", ignoreCase = true) ||
//                                    chain3.endsWith(": false", ignoreCase = true) ||
//                                    chain3.endsWith(": disabled", ignoreCase = true)
//
//                        if (chain3Disabled) {
//                            execTransparent(
//                                "cmd connectivity set-chain3-enabled true"
//                            )
//                        }

                        execTransparent(
                            "cmd connectivity set-package-networking-enabled false ${item.packageName}"
                        )
                        execTransparent(
                            "cmd netpolicy add restrict-background-blacklist $uid"
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
        if (!connected) return
        backgroundEnabled = enabled

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
                    val chain3Command =
                        "cmd connectivity get-chain3-enabled"
                    val chain3 = execTransparent(chain3Command).trim()

                    val chain3Disabled =
                        chain3.equals("chain:false", ignoreCase = true) ||
                                chain3.equals("chain:disabled", ignoreCase = true) ||
                                chain3.equals("false", ignoreCase = true) ||
                                chain3.equals("disabled", ignoreCase = true) ||
                                chain3.endsWith(": false", ignoreCase = true) ||
                                chain3.endsWith(": disabled", ignoreCase = true)

                    if (chain3Disabled) {
                        execTransparent(
                            "cmd connectivity set-chain3-enabled true"
                        )
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
                        onClick = ::uninstallApp
                    )
                    DetailQuickAction(
                        icon = Icons.Default.PauseCircleOutline,
                        label = "Force stop",
                        enabled = connected,
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

//                        ActionButton(
//                            "Clear cache",
//                            Icons.Default.CleaningServices
//                        ) {
//                            onLog("Clear cache requested: ${item.packageName}")
//                            onRun("pm clear --cache-only --user 0 ${item.packageName}")
//                        }

                        ActionButton(
                            "Clear data",
                            Icons.Default.DeleteSweep,
                            enabled = connected
                        ) {
                            onLog("Clear data requested: ${item.packageName}")
                            onRun("pm clear --user 0 ${item.packageName}")
                        }

                        ActionButton(
                            "Extract / share APK",
                            Icons.Default.Share,
                            enabled = connected
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
                                connected = connected,
                                onCheckedChange = ::toggleAll
                            )

                            DetailNetworkToggle(
                                label = "Background",
                                checked = backgroundEnabled,
                                connected = connected,
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
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(52.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Blue,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFE2E5EA),
                disabledContentColor = Ink.copy(alpha = .35f)
            )
        ) {
            Icon(icon, contentDescription = label)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = if (enabled) Ink else Ink.copy(alpha = .38f)
        )
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
    connected: Boolean,
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

        // Fixed slot so the lock and the switch occupy exactly the same
        // footprint while one cross-fades into the other.
        Box(
            Modifier.size(width = 52.dp, height = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = connected,
                transitionSpec = {
                    ((fadeIn(tween(220)) + scaleIn(tween(220), initialScale = .85f)) togetherWith
                            (fadeOut(tween(160)) + scaleOut(tween(160), targetScale = .85f)))
                        .using(SizeTransform(clip = false))
                },
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
                label = "networkToggleLock"
            ) { adbConnected ->
                if (adbConnected) {
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
                } else {
                    LockedNetworkToggle()
                }
            }
        }
    }
}

/**
 * Stand-in for the switch while ADB is disconnected. Same size as the
 * switch track, but black with a white lock. Tapping shakes it to signal
 * that the setting is unavailable rather than silently doing nothing.
 */
@Composable
private fun LockedNetworkToggle() {
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }

    Box(
        Modifier
            .size(width = 52.dp, height = 32.dp)
            .offset { IntOffset(shake.value.roundToInt(), 0) }
            .clip(RoundedCornerShape(10.dp))
            .background(Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                scope.launch {
                    shake.snapTo(0f)
                    shake.animateTo(
                        targetValue = 0f,
                        animationSpec = keyframes {
                            durationMillis = 420
                            (-9f) at 0
                            9f at 60
                            (-7f) at 120
                            7f at 180
                            (-4f) at 240
                            4f at 300
                            0f at 420
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = "Connect ADB to change network settings",
            tint = Color.White,
            modifier = Modifier.size(15.dp)
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
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = ActionButtonBlue,
            contentColor = Ink,
            disabledContainerColor = Color(0xFFE2E5EA),
            disabledContentColor = Ink.copy(alpha = .35f)
        ),
        border = BorderStroke(
            1.dp,
            if (enabled) Blue.copy(alpha = .45f) else Color(0xFFD3D8DF)
        )
    ) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp)
    }
}
