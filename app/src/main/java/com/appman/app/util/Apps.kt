package com.appman.app.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.appman.app.model.AppItem
import java.io.File
import java.text.DecimalFormat

internal fun appLocation(context: Context, packageName: String): String {
    return runCatching {
        context.packageManager
            .getApplicationInfo(packageName, 0)
            .sourceDir
    }.getOrElse {
        "Location unavailable"
    }
}

internal fun developerOptionsEnabled(context: Context): Boolean = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1 }.getOrDefault(false)

internal fun loadApps(context: Context): List<AppItem> {
    val pm = context.packageManager

    val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getInstalledApplications(
            PackageManager.ApplicationInfoFlags.of(
                PackageManager.GET_META_DATA.toLong()
            )
        )
    } else {
        @Suppress("DEPRECATION")
        pm.getInstalledApplications(
            PackageManager.GET_META_DATA
        )
    }

    return installed.mapNotNull { info ->
        if (info.packageName == context.packageName) {
            return@mapNotNull null
        }

        val label = runCatching {
            pm.getApplicationLabel(info).toString()
        }.getOrDefault(info.packageName)

        val size = runCatching {
            File(info.sourceDir).length()
        }.getOrDefault(0L)

        AppItem(
            label = label,
            packageName = info.packageName,
            icon = pm.getApplicationIcon(info),
            system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            sizeBytes = size
        )
    }
}

internal fun formatSize(bytes: Long): String { if (bytes <= 0) return "—"; val units = arrayOf("B","KB","MB","GB"); var v = bytes.toDouble(); var i = 0; while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }; return "${DecimalFormat("0.0").format(v)} ${units[i]}" }
