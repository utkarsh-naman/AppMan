package com.appman.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun PairDialog(code: String, onCode: (String) -> Unit, onDismiss: () -> Unit, onPair: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Pair wireless debugging") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Open Developer options → Wireless debugging → Pair device with pairing code. Enter the current six-digit code below. The persistent AppMan notification also accepts the code.", fontSize = 13.sp); OutlinedTextField(code, { onCode(it.filter(Char::isDigit).take(6)) }, label = { Text("Pairing code") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) } }, confirmButton = { Button(onClick = onPair) { Text("Pair") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
