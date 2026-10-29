package com.appman.app.util

import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.core.content.FileProvider
import com.appman.app.adb.AdbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Pulls a package's APK off the device via ADB and writes it to a local
// cache file. Used by both the single-app "Share" button and the bulk
// Share stage in the floating action bar, so both stay in sync.
internal suspend fun extractApkFile(context: Context, packageName: String): File? =
    withContext(Dispatchers.IO) {
        runCatching {
            val adb = AdbManager.get(context)
            val pathLine = adb.exec("pm path $packageName").lineSequence().firstOrNull { it.startsWith("package:") }
                ?: throw IllegalStateException("Android did not return an APK path")
            val remotePath = pathLine.removePrefix("package:").trim()
            val b64 = adb.exec("base64 $remotePath").replace(Regex("\\s"), "")
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val dir = File(context.cacheDir, "shared_apks").apply { mkdirs() }
            val out = File(dir, packageName.replace('.', '_') + ".apk")
            out.writeBytes(bytes)
            out
        }.getOrNull()
    }

internal fun shareApk(context: Context, packageName: String) {
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        val out = extractApkFile(context, packageName)
        if (out == null) {
            withContext(Dispatchers.Main) { android.widget.Toast.makeText(context, "APK extraction failed", android.widget.Toast.LENGTH_LONG).show() }
            return@launch
        }
        val uri = FileProvider.getUriForFile(context, "com.appman.app.fileprovider", out)
        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/vnd.android.package-archive"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK) }, "Share APK"))
        }
    }
}
