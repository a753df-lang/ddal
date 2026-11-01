package com.ddal.pos

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONObject
import java.io.File

/**
 * 커피트럭 포스 — 안드로이드 앱
 *  - 포스 웹페이지를 앱 화면으로 띄움 (포스를 고치면 앱도 자동으로 최신)
 *  - 로그인하면 아이패드·PC와 같은 장부로 이어서 판매
 *  - 한 번 열고 나면 인터넷 없이도 실행 (포스의 오프라인 기능)
 *  - 영수증 인쇄(window.print) → 안드로이드 인쇄/PDF 저장, 메뉴 사진 고르기, 달력(.ics) 저장 지원
 */
open class MainActivity : Activity() {

    protected open val startUrl = "https://a753df-lang.github.io/ddal/"

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)   // 판매 중 화면 꺼짐 방지

        web = WebView(this)
        setContentView(web)
        hideSystemBars()

        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true            // 판매 기록(localStorage) 저장
        s.databaseEnabled = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.textZoom = 100                      // 휴대폰 글자 크기 설정 때문에 화면이 깨지지 않게
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.displayZoomControls = false
        s.useWideViewPort = true
        s.loadWithOverviewMode = false
        s.allowFileAccess = false
        CookieManager.getInstance().setAcceptCookie(true)

        web.addJavascriptInterface(Bridge(), "DdalApp")

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                CookieManager.getInstance().flush()
                // 포스·기록 달력의 '인쇄' 버튼 → 안드로이드 인쇄 화면 (PDF 저장도 여기서)
                view.evaluateJavascript(
                    "window.print=function(){DdalApp.print(document.title||'영수증');};", null
                )
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                // 인터넷이 없고 아직 한 번도 열린 적이 없을 때만 안내 화면
                if (request.isForMainFrame) {
                    view.loadDataWithBaseURL(null, offlineHtml(), "text/html", "utf-8", null)
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.host == "a753df-lang.github.io") return false        // 포스·기록 달력은 앱 안에서
                if (u.scheme == "data") return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, u))           // 그 밖의 링크는 다른 앱(브라우저 등)으로
                } catch (e: ActivityNotFoundException) {
                }
                return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            // 알림·확인·입력 창은 기본 안드로이드 창으로 표시됨
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    startActivityForResult(params.createIntent(), REQ_FILE)
                    true
                } catch (e: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }
        }

        // 기록 달력의 '휴대폰 달력으로 보내기'(.ics 파일)
        web.setDownloadListener { url, _, _, mimeType, _ ->
            if (url.startsWith("blob:")) {
                val mime = if (mimeType.isNullOrBlank()) "application/octet-stream" else mimeType
                val name = if (mime.contains("calendar")) "커피트럭_행사.ics" else "download"
                val js = "fetch(" + JSONObject.quote(url) + ").then(function(r){return r.blob();}).then(function(b){" +
                        "var f=new FileReader();f.onload=function(){DdalApp.saveFile(" + JSONObject.quote(name) + "," +
                        JSONObject.quote(mime) + ",String(f.result).split(',')[1]);};f.readAsDataURL(b);});"
                web.evaluateJavascript(js, null)
            } else {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: ActivityNotFoundException) {
                }
            }
        }

        if (savedInstanceState == null) web.loadUrl(startUrl) else web.restoreState(savedInstanceState)
    }

    private fun offlineHtml(): String = """
        <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>body{font-family:sans-serif;background:#f1f3f5;display:flex;align-items:center;justify-content:center;height:100vh;margin:0}
        .b{background:#fff;border-radius:14px;padding:28px;box-shadow:0 2px 10px rgba(0,0,0,.15);text-align:center;max-width:440px;margin:16px}
        p{color:#444;line-height:1.6}a{display:inline-block;margin-top:12px;background:#2e7d32;color:#fff;padding:12px 26px;border-radius:8px;text-decoration:none;font-weight:bold}</style></head>
        <body><div class="b"><h2>☁ 인터넷 연결이 필요합니다</h2>
        <p>처음 한 번은 인터넷(와이파이)에 연결된 상태에서 켜야 합니다.<br>한 번 열리고 나면 그 다음부터는 인터넷 없이도 열립니다.</p>
        <a href="$startUrl">다시 시도</a></div></body></html>
    """.trimIndent()

    inner class Bridge {
        @JavascriptInterface
        fun print(title: String) {
            runOnUiThread {
                val pm = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val job = "커피트럭 $title"
                pm.print(job, web.createPrintDocumentAdapter(job), PrintAttributes.Builder().build())
            }
        }

        @JavascriptInterface
        fun saveFile(name: String, mime: String, base64: String) {
            val bytes = try { Base64.decode(base64, Base64.DEFAULT) } catch (e: IllegalArgumentException) { return }
            runOnUiThread { saveDownload(name, mime, bytes) }
        }
    }

    private fun saveDownload(name: String, mime: String, bytes: ByteArray) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                // 달력 파일이면 바로 달력 앱으로 열기 (없으면 '다운로드' 폴더에 저장된 것으로 끝)
                try {
                    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, "다운로드 폴더에 저장했습니다: $name", Toast.LENGTH_LONG).show()
                }
            } else {
                val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                val f = File(dir, name)
                f.writeBytes(bytes)
                Toast.makeText(this, "저장했습니다: ${f.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "파일 저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_FILE) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    // 뒤로 버튼: 앱을 끄지 않고 뒤로 보냄 (판매 중 실수로 꺼지는 것 방지)
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else moveTaskToBack(true)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        web.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    // 위·아래 상태바를 숨겨 화면을 넓게 (화면 끝을 밀면 잠깐 나타남)
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }

    companion object {
        private const val REQ_FILE = 1001
    }
}
