package com.safewave.guardian.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.safewave.guardian.Auth
import com.safewave.guardian.Prefs
import org.json.JSONObject

/**
 * 관제 사이트(SafeWave AI Monitor)를 앱 안에서 그대로 보여 주는 화면.
 * 앱에 로그인한 계정으로 사이트에도 자동 로그인 (사이트가 쓰는 localStorage "sw-auth" 에 같은 로그인 정보를 넣어 줌).
 */
object Site {
    const val URL = "https://h71212393.github.io/safewave/"
    private const val HOST = "h71212393.github.io"

    @SuppressLint("SetJavaScriptEnabled")
    fun create(ctx: Context): WebView = WebView(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled = true            // 사이트가 자바스크립트로 동작
        settings.domStorageEnabled = true            // 사이트 로그인·설정 저장 (localStorage)
        settings.mediaPlaybackRequiresUserGesture = true   // 경보음은 앱이 따로 울리므로 사이트 소리는 누를 때만
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        webChromeClient = WebChromeClient()
        var loginTried = false
        webViewClient = object : WebViewClient() {
            // 사이트 밖 주소(전화 tel:, 다른 사이트)는 휴대폰 기본 앱으로
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val u = req.url
                if (u.host == HOST || u.host?.endsWith("firebasedatabase.app") == true) return false
                return runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (loginTried || Uri.parse(url).host != HOST) return
                loginTried = true   // 한 번만 (로그인이 안 되는 경우 계속 새로고침하지 않게)
                val p = Prefs(view.context)
                val rt = Auth.session?.refreshToken ?: p.refreshToken ?: return
                val uid = Auth.session?.uid ?: p.uid ?: return
                val email = Auth.session?.email ?: p.email ?: ""
                val auth = JSONObject().put("uid", uid).put("email", email).put("refreshToken", rt).toString()
                val js = "(function(){try{var k='sw-auth';if(!localStorage.getItem(k)){localStorage.setItem(k," +
                    JSONObject.quote(auth) + ");location.reload();return 'set'}return 'has'}catch(e){return 'err'}})()"
                view.evaluateJavascript(js, null)
            }
        }
        loadUrl(URL)
    }

    /** 앱에서 로그아웃하면 사이트 로그인 정보도 지움 */
    fun clear() {
        runCatching { WebStorage.getInstance().deleteAllData() }
    }
}

@Composable
fun SiteScreen(web: WebView) {
    AndroidView(factory = { web.apply { (parent as? ViewGroup)?.removeView(this) } }, modifier = Modifier.fillMaxSize())
}
