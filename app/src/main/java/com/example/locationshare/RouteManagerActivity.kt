package com.example.locationshare

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.locationshare.databinding.ActivityRouteManagerBinding
import com.example.locationshare.model.Route
import com.example.locationshare.utils.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit

class RouteManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRouteManagerBinding
    private lateinit var prefsManager: PrefsManager
    private lateinit var webView: WebView

    private val AMAP_KEY = "99d33a7ce806060acfffa9a80ae613bc"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRouteManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PrefsManager(this)
        initWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        webView = binding.webView

        webView.apply {
            setBackgroundColor(Color.TRANSPARENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                    android.util.Log.d("WebViewConsole", "${message?.message()} -- ${message?.sourceId()}:${message?.lineNumber()}")
                    return true
                }
            }

            addJavascriptInterface(RouteWebBridge(), "AndroidBridge")

            loadUrl("file:///android_asset/web/routes.html")
        }
    }

    // WebView JS 桥接类
    inner class RouteWebBridge {

        @JavascriptInterface
        fun goBack() {
            runOnUiThread { finish() }
        }

        @JavascriptInterface
        fun loadRoutes() {
            val routes = prefsManager.getRoutes()
            val jsonArray = JSONArray()
            routes.forEach { route ->
                jsonArray.put(JSONObject().apply {
                    put("id", route.id)
                    put("name", route.name)
                    put("startName", route.startName)
                    put("startLat", route.startLat)
                    put("startLng", route.startLng)
                    put("endName", route.endName)
                    put("endLat", route.endLat)
                    put("endLng", route.endLng)
                    put("sharedWith", route.sharedWith)
                    put("sharedWithName", route.sharedWithName)
                    put("isFavorite", route.isFavorite)
                })
            }
            runOnUiThread {
                webView.evaluateJavascript(
                    "window.onRoutesLoaded('${jsonArray.toString().replace("'", "\\'")}')",
                    null
                )
            }
        }

        @JavascriptInterface
        fun loadFriendsForRoute() {
            val friends = prefsManager.getFriends()
            val jsonArray = JSONArray()
            friends.forEach { friend ->
                jsonArray.put(JSONObject().apply {
                    put("friendId", friend.friendId)
                    put("friendName", friend.friendName)
                })
            }
            runOnUiThread {
                webView.evaluateJavascript(
                    "window.onFriendsLoaded('${jsonArray.toString().replace("'", "\\'")}')",
                    null
                )
            }
        }

        @JavascriptInterface
        fun saveRoute(routeJson: String) {
            try {
                val json = JSONObject(routeJson)
                val route = Route(
                    id = UUID.randomUUID().toString(),
                    name = json.getString("name"),
                    startName = json.getString("startName"),
                    startLat = json.getDouble("startLat"),
                    startLng = json.getDouble("startLng"),
                    endName = json.getString("endName"),
                    endLat = json.getDouble("endLat"),
                    endLng = json.getDouble("endLng"),
                    sharedWith = json.optString("sharedWith", ""),
                    sharedWithName = prefsManager.getFriends()
                        .find { it.friendId == json.optString("sharedWith", "") }?.friendName ?: "",
                    isFavorite = true
                )
                prefsManager.addRoute(route)
                runOnUiThread {
                    Toast.makeText(this@RouteManagerActivity, "路线保存成功", Toast.LENGTH_SHORT).show()
                    webView.evaluateJavascript("window.onRouteSaved()", null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@RouteManagerActivity, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun startRoute(routeId: String) {
            val route = prefsManager.getRoutes().find { it.id == routeId }
            route?.let {
                runOnUiThread {
                    Toast.makeText(this@RouteManagerActivity, "开始路线: ${it.name}", Toast.LENGTH_SHORT).show()
                    // TODO: 跳转到导航页面或开始路线追踪
                }
            }
        }

        @JavascriptInterface
        fun deleteRoute(routeId: String) {
            prefsManager.deleteRoute(routeId)
            runOnUiThread {
                Toast.makeText(this@RouteManagerActivity, "已删除", Toast.LENGTH_SHORT).show()
                webView.evaluateJavascript("window.onRouteDeleted()", null)
            }
        }

        @JavascriptInterface
        fun searchAddress(type: String, keyword: String) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    android.util.Log.d("RouteSearch", "Searching for: $keyword, type: $type")

                    // URL 编码关键词
                    val encodedKeyword = java.net.URLEncoder.encode(keyword, "UTF-8")
                    val url = "https://restapi.amap.com/v3/assistant/inputtips?key=$AMAP_KEY&keywords=$encodedKeyword&city=北京"

                    android.util.Log.d("RouteSearch", "URL: $url")

                    val request = Request.Builder().url(url).build()
                    val response = httpClient.newCall(request).execute()
                    val body = response.body?.string()

                    android.util.Log.d("RouteSearch", "Response: $body")

                    val results = JSONArray()
                    if (body != null) {
                        val json = JSONObject(body)
                        if (json.optString("status") == "1") {
                            val tips = json.optJSONArray("tips") ?: JSONArray()
                            val count = Math.min(tips.length(), 10)
                            for (i in 0 until count) {
                                val tip = tips.getJSONObject(i)
                                val location = tip.optString("location", "")
                                android.util.Log.d("RouteSearch", "Tip $i: ${tip.optString("name")}, location: $location")
                                if (location.contains(",")) {
                                    val parts = location.split(",")
                                    results.put(JSONObject().apply {
                                        put("name", tip.optString("name"))
                                        put("address", tip.optString("address", ""))
                                        put("lng", parts[0].toDoubleOrNull() ?: 0.0)
                                        put("lat", parts[1].toDoubleOrNull() ?: 0.0)
                                    })
                                }
                            }
                        }
                    }

                    android.util.Log.d("RouteSearch", "Results count: ${results.length()}")

                    withContext(Dispatchers.Main) {
                        webView.evaluateJavascript(
                            "window.onSearchResults('$type', '${results.toString().replace("'", "\\'")}')",
                            null
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RouteSearch", "Error: ${e.message}", e)
                }
            }
        }

        @JavascriptInterface
        fun toggleFavorite(routeId: String) {
            val routes = prefsManager.getRoutes()
            val route = routes.find { it.id == routeId }
            route?.let {
                val updated = it.copy(isFavorite = !it.isFavorite)
                prefsManager.updateRoute(updated)
                runOnUiThread {
                    webView.evaluateJavascript("window.onFavoriteToggled()", null)
                }
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
