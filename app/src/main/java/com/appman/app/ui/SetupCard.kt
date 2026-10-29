package com.appman.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appman.app.ui.theme.*

@Composable internal fun SetupCard(onOpen: () -> Unit) {
    Card(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Cream), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Developer options are off", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("AppMan cannot enable them for you. Open Developer options, enable Wireless debugging, then choose “Pair device with pairing code”. Keep AppMan open while pairing.", fontSize = 13.sp)
            Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("Open Developer options") }
        }
    }
}
