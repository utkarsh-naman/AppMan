//package com.appman.app.ui
//
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.items
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import com.appman.app.model.*
//import com.appman.app.ui.theme.*
//
//@Composable internal fun HoodPanel(lines: List<HoodLine>, expanded: Boolean, onExpand: () -> Unit, onClear: () -> Unit, onMax: () -> Unit, modifier: Modifier = Modifier) {
//    Surface(modifier.fillMaxWidth(), color = Black) {
//        Column(Modifier.fillMaxSize()) { Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Text("HOOD", color = White, fontWeight = FontWeight.ExtraBold, fontFamily = NdotFont, modifier = Modifier.weight(1f)); IconButton(onClick = onClear) { Icon(Icons.Default.RemoveCircleOutline, "Clear HOOD", tint = White) }; IconButton(onClick = onExpand) { Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess, null, tint = White) }; IconButton(onClick = onMax) { Icon(Icons.Default.OpenInFull, null, tint = White) } }; LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), reverseLayout = false) { items(lines) { Text(it.text, color = if (it.kind == "err") Color(0xFFFF7B72) else White.copy(.88f), fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp)) } } }
//    }
//}
//
//@Composable internal fun FullHood(lines: List<HoodLine>, onClear: () -> Unit, onClose: () -> Unit) { Surface(Modifier.fillMaxSize(), color = Black) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("HOOD", color = White, fontWeight = FontWeight.ExtraBold, fontFamily = NdotFont, modifier = Modifier.weight(1f)); IconButton(onClick = onClear) { Icon(Icons.Default.RemoveCircleOutline, "Clear HOOD", tint = White) }; IconButton(onClick = onClose) { Icon(Icons.Default.CloseFullscreen, null, tint = White) } }; LazyColumn(Modifier.fillMaxSize()) { items(lines) { Text(it.text, color = White.copy(.9f), fontSize = 12.sp, modifier = Modifier.padding(vertical = 3.dp)) } } } } }


package com.appman.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appman.app.R
import com.appman.app.model.*
import com.appman.app.ui.theme.*

@Composable
internal fun HoodPanel(
    lines: List<HoodLine>,
    expanded: Boolean,
    onExpand: () -> Unit,
    onClear: () -> Unit,
    onMax: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier.fillMaxWidth(), color = Black) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.hood_svg),
                    contentDescription = "HOOD",
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart
                )

                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.RemoveCircleOutline,
                        "Clear HOOD",
                        tint = White
                    )
                }

                IconButton(onClick = onExpand) {
                    Icon(
                        if (expanded) Icons.Default.ExpandMore
                        else Icons.Default.ExpandLess,
                        null,
                        tint = White
                    )
                }

                IconButton(onClick = onMax) {
                    Icon(
                        Icons.Default.OpenInFull,
                        null,
                        tint = White
                    )
                }
            }

            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                reverseLayout = false
            ) {
                items(lines) {
                    Text(
                        it.text,
                        color = if (it.kind == "err")
                            Color(0xFFFF7B72)
                        else White.copy(.88f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun FullHood(
    lines: List<HoodLine>,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = Black) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.hood_svg),
                    contentDescription = "HOOD",
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart
                )

                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.RemoveCircleOutline,
                        "Clear HOOD",
                        tint = White
                    )
                }

                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.CloseFullscreen,
                        null,
                        tint = White
                    )
                }
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(lines) {
                    Text(
                        it.text,
                        color = White.copy(.9f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
            }
        }
    }
}
