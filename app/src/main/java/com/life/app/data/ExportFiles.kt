package com.life.app.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.ZoneId

object ExportFiles {

    const val JSON_NAME = "life-export.json"
    const val MARKDOWN_NAME = "life-export.md"

    fun authority(context: Context): String = "${context.packageName}.fileprovider"

    fun write(
        context: Context,
        data: AppData,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val export = LifeExportFormat.build(data, nowMs, zone)
        File(dir, JSON_NAME).writeText(LifeExportFormat.toJson(export))
        File(dir, MARKDOWN_NAME).writeText(LifeExportFormat.toMarkdown(export))
        return File(dir, JSON_NAME)
    }

    fun shareIntent(context: Context, jsonFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, authority(context), jsonFile)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, JSON_NAME, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
