package com.appman.app.model

import android.graphics.drawable.Drawable

data class AppItem(val label: String, val packageName: String, val icon: Drawable, val system: Boolean, val sizeBytes: Long)

enum class AppFilter { INSTALLED, SYSTEM, ALL }
enum class SortBy { NAME, SIZE }

data class HoodLine(val kind: String, val text: String)
