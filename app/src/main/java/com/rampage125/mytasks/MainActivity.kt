package com.rampage125.mytasks

import android.annotation.SuppressLint
import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.databaseEnabled = true
            settings.textZoom = 100
            webViewClient = WebViewClient()
            addJavascriptInterface(AndroidBackup(this@MainActivity), "AndroidBackup")
            setBackgroundColor(0xFF0A0A0A.toInt())
        }
        setContentView(webView)

        ViewCompat.setOnApplyWindowInsetsListener(webView) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, maxOf(ime.bottom, sysBars.bottom))
            insets
        }

        webView.loadUrl("file:///android_asset/notes_app.html")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}

class AndroidBackup(private val ctx: MainActivity) {

    private val TAG = "MyTasksBackup"
    private val relativeDir = "Download/MyTasks"

    @JavascriptInterface
    fun writeBackup(json: String): Boolean {
        return try {
            val ts = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US)
                .format(java.util.Date())
            val fileName = "backup_$ts.json"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = ctx.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, relativeDir)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                val uri = resolver.insert(collection, values)
                    ?: throw Exception("MediaStore insert returned null")

                resolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    ?: throw Exception("openOutputStream returned null")

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "MyTasks")
                if (!dir.exists()) dir.mkdirs()
                val outFile = File(dir, fileName)
                FileOutputStream(outFile).use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }

            ctx.runOnUiThread {
                Toast.makeText(ctx, "saved → $fileName", Toast.LENGTH_SHORT).show()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "writeBackup failed", e)
            ctx.runOnUiThread {
                Toast.makeText(ctx, "backup failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
            false
        }
    }

    /**
     * Reads the most recent backup file (or by name if fileName != "").
     * Returns JSON content as string, or empty string on failure.
     */
    @JavascriptInterface
    fun readBackup(fileName: String): String {
        return try {
            val targetName = fileName.trim()
            val pickedPath: String?
            val content: String?

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = ctx.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                val (selection, args) = if (targetName.isNotEmpty()) {
                    "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME}=?" to
                        arrayOf("$relativeDir/", targetName)
                } else {
                    "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?" to
                        arrayOf("$relativeDir/", "backup_%.json")
                }

                val cursor = resolver.query(
                    collection,
                    arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.DATE_MODIFIED),
                    selection, args,
                    "${MediaStore.Downloads.DATE_MODIFIED} DESC"
                ) ?: throw Exception("query returned null")

                cursor.use { c ->
                    if (!c.moveToFirst()) throw Exception("no backup files found")
                    val id = c.getLong(0)
                    pickedPath = c.getString(1)
                    val uri = android.content.ContentUris.withAppendedId(collection, id)
                    content = resolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: throw Exception("openInputStream returned null")
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "MyTasks")
                if (!dir.exists() || !dir.isDirectory) throw Exception("MyTasks folder not found")
                val file = if (targetName.isNotEmpty()) {
                    File(dir, targetName).takeIf { it.exists() }
                        ?: throw Exception("file not found: $targetName")
                } else {
                    dir.listFiles { f -> f.name.startsWith("backup_") && f.name.endsWith(".json") }
                        ?.maxByOrNull { it.lastModified() }
                        ?: throw Exception("no backup files found")
                }
                pickedPath = file.name
                content = file.readText(Charsets.UTF_8)
            }

            ctx.runOnUiThread {
                Toast.makeText(ctx, "read $pickedPath", Toast.LENGTH_SHORT).show()
            }
            content ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "readBackup failed", e)
            ctx.runOnUiThread {
                Toast.makeText(ctx, "import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
            ""
        }
    }

    /**
     * Returns newline-separated list of backup filenames, newest first.
     */
    @JavascriptInterface
    fun listBackups(): String {
        return try {
            val names = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = ctx.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                resolver.query(
                    collection,
                    arrayOf(MediaStore.Downloads.DISPLAY_NAME),
                    "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
                    arrayOf("$relativeDir/", "backup_%.json"),
                    "${MediaStore.Downloads.DATE_MODIFIED} DESC"
                )?.use { c ->
                    while (c.moveToNext()) names.add(c.getString(0))
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "MyTasks")
                dir.listFiles { f -> f.name.startsWith("backup_") && f.name.endsWith(".json") }
                    ?.sortedByDescending { it.lastModified() }
                    ?.forEach { names.add(it.name) }
            }
            names.joinToString("\n")
        } catch (e: Exception) {
            Log.e(TAG, "listBackups failed", e)
            ""
        }
    }
}
