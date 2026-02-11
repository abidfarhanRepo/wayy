package com.wayy.debug

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import com.google.gson.Gson
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportBundleManager(
    private val context: Context,
    private val diagnosticLogger: DiagnosticLogger
) {

    private val gson = Gson()
    private val captureDir = File(context.filesDir, "capture")
    private val diagnosticsDir = File(context.filesDir, "diagnostics")
    private val exportDir = File(context.cacheDir, "exports")

    init {
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
    }

    fun createExportBundle(): File? {
        val captureFiles = listFiles(captureDir)
        val diagnosticFiles = listFiles(diagnosticsDir)
        if (captureFiles.isEmpty() && diagnosticFiles.isEmpty()) {
            diagnosticLogger.log(tag = "WayyExport", message = "No files to export")
            return null
        }

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val exportFile = File(exportDir, "wayy_export_$stamp.zip")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(exportFile))).use { zip ->
            addDir(zip, captureDir, "capture")
            addDir(zip, diagnosticsDir, "diagnostics")
            val manifest = buildManifest(captureFiles, diagnosticFiles)
            val manifestEntry = ZipEntry("manifest.json")
            zip.putNextEntry(manifestEntry)
            zip.write(manifest.toByteArray())
            zip.closeEntry()
        }

        diagnosticLogger.log(
            tag = "WayyExport",
            message = "Export bundle created",
            data = mapOf("path" to exportFile.absolutePath)
        )
        return exportFile
    }

    private fun addDir(zip: ZipOutputStream, dir: File, prefix: String) {
        if (!dir.exists()) return
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
            val entryName = "$prefix/${file.relativeTo(dir).path}"
            zip.putNextEntry(ZipEntry(entryName))
            file.inputStream().use { input ->
                input.copyTo(zip)
            }
            zip.closeEntry()
        }
    }

    private fun listFiles(dir: File): List<File> {
        return if (dir.exists()) {
            dir.listFiles()?.filter { it.isFile } ?: emptyList()
        } else {
            emptyList()
        }
    }

    private fun buildManifest(captureFiles: List<File>, diagnosticFiles: List<File>): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName
        val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
        val manifest = mapOf(
            "timestamp" to System.currentTimeMillis(),
            "package" to context.packageName,
            "versionName" to versionName,
            "versionCode" to versionCode,
            "captures" to captureFiles.map { it.toManifestEntry() },
            "diagnostics" to diagnosticFiles.map { it.toManifestEntry() }
        )
        return gson.toJson(manifest)
    }

    private fun File.toManifestEntry(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "sizeBytes" to length(),
            "lastModified" to lastModified()
        )
    }
}
