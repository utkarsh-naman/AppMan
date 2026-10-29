package com.appman.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appman.app.ui.theme.*

@Composable internal fun TerminalPane(connected: Boolean, onConnect: () -> Unit, onCommand: (String) -> Unit, modifier: Modifier = Modifier) {
    var command by remember { mutableStateOf("") }
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Terminal", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = Black), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Text(if (connected) "ADB shell connected" else "ADB shell not connected", color = if (connected) Green else Grey, fontSize = 12.sp)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$ ",
                        color = if (connected) Green else Grey,
                        fontWeight = FontWeight.Bold
                    );
                    BasicTextField(
                        command,
                        { command = it },
                        Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(color = White),
                        singleLine = true,
                        cursorBrush = SolidColor(Blue)
                    );
                    if (connected) {
                        IconButton(onClick = {
                            if (connected && command.isNotBlank()) {
                                onCommand(command); command = ""
                            }
                        }) { Icon(Icons.Default.Send, null, tint = if (connected) Green else Grey) }
                    }
                }
                if (!connected) TextButton(onClick = onConnect) { Text("Pair / connect", color = Blue) }
            }
        }
    }
}
