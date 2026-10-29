package com.appman.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appman.app.model.*
import com.appman.app.ui.theme.*

@Composable internal fun SearchBar(query: String, onQuery: (String) -> Unit, selecting: Boolean, onClear: () -> Unit, onSelectAll: () -> Unit, filter: AppFilter, onFilter: (AppFilter) -> Unit, sort: SortBy, onSort: (SortBy) -> Unit, asc: Boolean, toggleAsc: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(query, onQuery, Modifier.weight(1f), singleLine = true, placeholder = { Text("Search apps…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp))
        if (selecting) { IconButton(onClick = onSelectAll) { Icon(Icons.Default.SelectAll, "Select all") }; IconButton(onClick = onClear) { Icon(Icons.Default.Close, "Clear selection") } }
        Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.Tune, "Filters") }; DropdownMenu(
            menu, { menu = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = Black,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp
        ) {
            CompositionLocalProvider(LocalContentColor provides White) {
                Text("Apps", Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = White)
                AppFilter.values().forEach { DropdownMenuItem(text = { Text(it.name.lowercase().replaceFirstChar { c -> c.uppercase() }, color = White) }, onClick = { onFilter(it); menu = false }) }
                Divider(color = White.copy(alpha = .2f)); Text("Sort", Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = White)
                DropdownMenuItem(text = { Text("A–Z name", color = White) }, onClick = { onSort(SortBy.NAME); menu = false })
                DropdownMenuItem(text = { Text("Size", color = White) }, onClick = { onSort(SortBy.SIZE); menu = false })
                DropdownMenuItem(text = { Text(if (asc) "Ascending" else "Descending", color = White) }, onClick = { toggleAsc(); menu = false })
            }
        } }
    }
}
