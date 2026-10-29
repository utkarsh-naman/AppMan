//package com.appman.app.ui
//
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.*
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import com.appman.app.ui.theme.*
//
//@Composable internal fun TopBar(terminal: Boolean, setTerminal: (Boolean) -> Unit, connected: Boolean, onPair: () -> Unit, onDisconnect: () -> Unit) {
//    Row(Modifier.fillMaxWidth().background(White).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
//        Column(Modifier.weight(1f)) { Text("AppMan", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = NdotFont, color = Ink); Text(if (connected) "ADB • connected" else "ADB • not connected", fontSize = 12.sp, color = if (connected) Blue else Ink.copy(alpha = .55f)) }
//        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
//            AssistChip(onClick = { setTerminal(!terminal) }, label = { Text(if (terminal) "UI mode" else "Terminal") }, leadingIcon = { Icon(if (terminal) Icons.Default.Apps else Icons.Default.Terminal, null) })
//            if (!connected) Button(onClick = onPair, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("Pair") } else OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
//        }
//    }
//}


package com.appman.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appman.app.R
import com.appman.app.ui.theme.*

@Composable
internal fun TopBar(
    terminal: Boolean,
    setTerminal: (Boolean) -> Unit,
    connected: Boolean,
    onPair: () -> Unit,
    onDisconnect: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(White)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Image(
                painter = painterResource(id = R.drawable.appman_svg),
                contentDescription = "AppMan",
                modifier = Modifier.height(28.dp).width(97.dp),
                contentScale = ContentScale.FillBounds
            )

//            Text(
//                if (connected) "ADB • connected" else "ADB • not connected",
//                fontSize = 12.sp,
//                color = if (connected) Green else Ink.copy(alpha = .55f)
//            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ADB ",
                    fontSize = 12.sp,
                    color = Ink.copy(alpha = .55f)
//                    color = if (connected) Green else Ink.copy(alpha = .55f)
                )
                Text(
                    "•",
                    fontSize = 12.sp,
                    color = if (connected) Green else Red
                )
                Text(
                    if (connected) " connected" else " not connected",
                    fontSize = 12.sp,
                    color = Ink.copy(alpha = .55f)
//                    color = if (connected) Green else Ink.copy(alpha = .55f)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { setTerminal(!terminal) },
                label = {
                    Text(if (terminal) "UI mode" else "Terminal")
                },
                leadingIcon = {
                    Icon(
                        if (terminal) Icons.Default.Apps
                        else Icons.Default.Terminal,
                        null,
                        tint = if (terminal) Green else Black
                    )
                }
            )

            if (!connected) {
                Button(
                    onClick = onPair,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Green
                    )
                ) {
                    Text("Pair", color = Black)
                }
            } else {
                Button(
                    onClick = onDisconnect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Red
                        )
                    ) {
                    Text("Disconnect", color = White)
                }
            }
        }
    }
}