package com.appman.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appman.app.ui.theme.*

@Composable internal fun MultiBar(
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
    onNetExpandClick: () -> Unit,   // added new
    onNetDismiss: () -> Unit,
    running: Boolean,
    onGo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), shadowElevation = 10.dp, color = Black) {
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
                    val hasSelectedFilter = actions.isNotEmpty() || netSelected
                    Button(
                        onClick = onGo,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasSelectedFilter) Green else Yellow,
                            contentColor = if (hasSelectedFilter) White else Black
                        ),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text("GO", color = Black, fontWeight = FontWeight.Bold)
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
                // commented out the cache part cuz pm clear --cache-only gets stuck
//                FilterChip(
//                    modifier = Modifier.height(48.dp),
//                    selected = actions.contains("cache"),
//                    onClick = { toggleAction("cache") },
//                    leadingIcon = { Icon(Icons.Default.Cookie, null, Modifier.size(16.dp)) },
//                    label = { Text("Cache", fontSize = 11.sp) }
//                )

                FilterChip(
                    modifier = Modifier.height(48.dp),
                    selected = actions.contains("data"),
                    onClick = { toggleAction("data") },
                    leadingIcon = { Icon(Icons.Default.Storage, null, Modifier.size(16.dp)) },
                    label = { Text("Data", fontSize = 11.sp, color=White) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Blue,
                        selectedLabelColor = White,
                        selectedLeadingIconColor = White,
                        selectedTrailingIconColor = White,
                    ),
                )

//                Box {
//                    FilterChip(
//                        modifier = Modifier.height(48.dp),
//                        selected = netSelected,
//                        onClick = onNetClick,
//                        leadingIcon = { Icon(Icons.Default.CellTower, null, Modifier.size(16.dp)) },
//                        trailingIcon = { Icon(Icons.Default.ExpandMore, null, Modifier.size(16.dp)) },
//                        label = { Text("Net", fontSize = 11.sp) }
//                    )
//                    DropdownMenu(netMenuOpen, onNetDismiss) {
//                        DropdownMenuItem(
//                            text = { Text("Background") },
//                            leadingIcon = {
//                                Checkbox(checked = netBackground, onCheckedChange = { toggleNetBackground() })
//                            },
//                            onClick = toggleNetBackground
//                        )
//                        DropdownMenuItem(
//                            text = { Text("All") },
//                            leadingIcon = {
//                                Checkbox(checked = netAll, onCheckedChange = { toggleNetAll() })
//                            },
//                            onClick = toggleNetAll
//                        )
//                    }
//                }
                Box {
                    FilterChip(
                        modifier = Modifier.height(48.dp),
                        selected = netSelected, // independent state, default false
                        onClick = onNetClick,   // toggles netSelected only
                        leadingIcon = { Icon(Icons.Default.CellTower, null, Modifier.size(16.dp)) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onNetExpandClick() } // opens the dropdown only
                            )
                        },
                        label = { Text("Net", fontSize = 11.sp, color = White) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Blue,
                            selectedLabelColor = White,
                            selectedLeadingIconColor = White,
                            selectedTrailingIconColor = White,
                        ),
                    )

                    DropdownMenu(
                        expanded = netMenuOpen,
                        onDismissRequest = onNetDismiss,
                        shape = RoundedCornerShape(14.dp),
                        containerColor = Black,
                        tonalElevation = 0.dp,
                        shadowElevation = 6.dp,
                        border = BorderStroke(1.dp, White.copy(alpha = .3f))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Background", color = White) },
                            trailingIcon = {
                                Switch(
                                    checked = netBackground,
                                    onCheckedChange = { toggleNetBackground() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = White,
                                        checkedTrackColor = Blue,
                                        checkedBorderColor = Blue,
                                        uncheckedThumbColor = White,
                                        uncheckedTrackColor = Color(0xFF3A3F47),
                                        uncheckedBorderColor = Color(0xFF8A9099)
                                    )
                                )
                            },
                            onClick = toggleNetBackground
                        )
                        DropdownMenuItem(
                            text = { Text("All", color = White) },
                            trailingIcon = {
                                Switch(
                                    checked = netAll,
                                    onCheckedChange = { toggleNetAll() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = White,
                                        checkedTrackColor = Blue,
                                        checkedBorderColor = Blue,
                                        uncheckedThumbColor = White,
                                        uncheckedTrackColor = Color(0xFF3A3F47),
                                        uncheckedBorderColor = Color(0xFF8A9099)
                                    )
                                )
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
                    label = { Text("Stop", fontSize = 11.sp, color = White) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Blue,
                        selectedLabelColor = White,
                        selectedLeadingIconColor = White,
                        selectedTrailingIconColor = White,
                    ),
                )
                FilterChip(
                    modifier = Modifier.height(48.dp),
                    selected = actions.contains("share"),
                    onClick = { toggleAction("share") },
                    leadingIcon = { Icon(Icons.Default.Share, null, Modifier.size(16.dp)) },
                    label = { Text("Share", fontSize = 11.sp, color = White) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Blue,
                        selectedLabelColor = White,
                        selectedLeadingIconColor = White,
                        selectedTrailingIconColor = White,
                    ),
                )
                FilterChip(
                    modifier = Modifier.height(48.dp),
                    selected = actions.contains("uninstall"),
                    onClick = { toggleAction("uninstall") },
                    leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = if (actions.contains("uninstall")) White else Color(0xFFE5484D)) },
                    label = { Text("Uninstall", fontSize = 11.sp, color = White) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Red,
                        selectedLabelColor = White,
                        selectedLeadingIconColor = White,
                        selectedTrailingIconColor = White,
                    ),
                )
            }
        }
    }
}
