package com.appman.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.viewinterop.AndroidView
import com.appman.app.model.*
import com.appman.app.ui.theme.*
import com.appman.app.util.formatSize

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppRow(
    item: AppItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick
            )
            .background(
                if (selected) {
                    Blue.copy(alpha = 0.14f)
                } else {
                    Color.Transparent
                }
            )
            .padding(
                horizontal = 16.dp,
                vertical = 9.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AndroidView(
            factory = { android.widget.ImageView(it) },
            update = {
                it.setImageDrawable(item.icon)
                it.setPadding(8, 8, 8, 8)
            },
            modifier = Modifier
                .size(54.dp)
                .background(
                    if (selected) {
                        Blue.copy(alpha = 0.18f)
                    } else {
                        Sand.copy(alpha = 0.28f)
                    },
                    RoundedCornerShape(14.dp)
                )
        )

        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                item.label,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Text(
                item.packageName,
                fontSize = 12.sp,
                color = Ink.copy(alpha = .55f),
                maxLines = 1
            )
        }

        Text(
            formatSize(item.sizeBytes),
            fontSize = 11.sp,
            color = Ink.copy(alpha = .55f),
            modifier = Modifier.padding(end = 4.dp)
        )

        Icon(
            Icons.Default.ChevronRight,
            null,
            tint = Ink.copy(.5f)
        )
    }
}
