package com.maqbool.merapartoonbill

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var pendingBackupJson: String? = null

    private val createBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) {
            showMessage("بیک اپ محفوظ نہیں کیا گیا")
            return@registerForActivityResult
        }
        try {
            val data = pendingBackupJson ?: throw IllegalStateException("No backup data")
            contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray(Charsets.UTF_8)) }
                ?: throw IllegalStateException("Cannot open destination")
            showMessage("بیک اپ کامیابی سے محفوظ ہوگیا")
        } catch (e: Exception) {
            showMessage("بیک اپ محفوظ نہیں ہو سکا")
        } finally {
            pendingBackupJson = null
        }
    }

    private val openBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            showMessage("کوئی فائل منتخب نہیں کی گئی")
            return@registerForActivityResult
        }
        try {
            val raw = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: throw IllegalStateException("Cannot read backup")
            val quoted = JSONObject.quote(raw)
            webView.evaluateJavascript("window.onNativeBackupLoaded && window.onNativeBackupLoaded($quoted);", null)
        } catch (e: Exception) {
            showMessage("بیک اپ فائل نہیں پڑھی جا سکی")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                return openExternal(url)
            }
            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                return openExternal(Uri.parse(url))
            }
        }
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
        if (savedInstanceState == null) {
            webView.loadUrl("file:///android_asset/index.html")
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    private fun openExternal(uri: Uri): Boolean {
        val scheme = uri.scheme ?: return false
        if (scheme == "file" || scheme == "about" || scheme == "javascript") return false
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (e: Exception) {
            showMessage("یہ لنک نہیں کھل سکا")
            true
        }
    }

    private fun showMessage(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun saveBackup(json: String, filename: String) {
            runOnUiThread {
                pendingBackupJson = json
                createBackup.launch(filename.ifBlank { "Mera_Partoon_Bill_Backup.json" })
            }
        }

        @JavascriptInterface
        fun restoreBackup() {
            runOnUiThread { openBackup.launch(arrayOf("application/json", "text/json", "application/octet-stream")) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
