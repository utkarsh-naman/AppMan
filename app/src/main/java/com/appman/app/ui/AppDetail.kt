package com.appman.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

private enum class DetailNetworkToggle {
    ALL,
    BACKGROUND
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppDetail(
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

//                        ActionButton(
//                            "Clear cache",
//                            Icons.Default.CleaningServices
//                        ) {
//                            onLog("Clear cache requested: ${item.packageName}")
//                            onRun("pm clear --cache-only --user 0 ${item.packageName}")
//                        }

                        ActionButton(
                            "Clear data",
                            Icons.Default.DeleteSweep
                        ) {
                            onLog("Clear data requested: ${item.packageName}")
                            onRun("pm clear --user 0 ${item.packageName}")
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
    icon: ImageVector,
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
    icon: ImageVector,
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
