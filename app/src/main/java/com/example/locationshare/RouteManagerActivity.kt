package com.example.locationshare

import android.annotation.SuppressLint
import android.content.Intent
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
import com.example.locationshare.databinding.ActivityRouteManagerBinding
import com.example.locationshare.model.Route
import com.example.locationshare.utils.PrefsManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class RouteManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRouteManagerBinding
    private lateinit var prefsManager: PrefsManager
    private lateinit var webView: WebView

    companion object {
        const val REQUEST_MAP_PICKER = 1001
        const val KEY_PENDING_TYPE = "pending_type"
    }

    private var pendingPickerType: String? = null
    private var pendingMapResult: MapPickerResult? = null

    data class MapPickerResult(val type: String, val address: String, val lat: Double, val lng: Double)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingPickerType?.let { outState.putString(KEY_PENDING_TYPE, it) }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        pendingPickerType = savedInstanceState.getString(KEY_PENDING_TYPE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MAP_PICKER && resultCode == RESULT_OK && data != null) {
            val address = data.getStringExtra(RouteMapPickerActivity.RESULT_ADDRESS) ?: ""
            val lat = data.getDoubleExtra(RouteMapPickerActivity.RESULT_LAT, 0.0)
            val lng = data.getDoubleExtra(RouteMapPickerActivity.RESULT_LNG, 0.0)

            android.util.Log.d("RouteManager", "onActivityResult: address=$address, lat=$lat, lng=$lng")

            // 校验数据有效性
            if (lat.isNaN() || lng.isNaN() || lat == 0.0 && lng == 0.0) {
                android.util.Log.e("RouteManager", "Invalid coordinates: lat=$lat, lng=$lng")
                Toast.makeText(this, "获取位置信息失败", Toast.LENGTH_SHORT).show()
                return
            }

            val type = pendingPickerType
            if (type == null) {
                android.util.Log.e("RouteManager", "pendingPickerType is null!")
                return
            }

            android.util.Log.d("RouteManager", "Map picked: type=$type, address=$address, lat=$lat, lng=$lng")

            // 检查 webView 是否已初始化
            if (!::webView.isInitialized) {
                android.util.Log.e("RouteManager", "WebView not initialized!")
                return
            }

            // 存储结果，让 JS 通过 bridge 来获取
            pendingMapResult = MapPickerResult(type, address, lat, lng)

            // 延迟通知 JS
            webView.postDelayed({
                try {
                    webView.evaluateJavascript("window.onMapPicked()") { result ->
                        android.util.Log.d("RouteManager", "JS notified, result: $result")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RouteManager", "JS execution failed", e)
                }
            }, 200)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置全屏显示，让 WebView 延伸到状态栏下方
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }

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
        fun openMapPicker(type: String) {
            runOnUiThread {
                RouteMapPickerActivity.start(this@RouteManagerActivity, type, REQUEST_MAP_PICKER)
                pendingPickerType = type
            }
        }

        @JavascriptInterface
        fun getMapPickerResult(): String {
            val result = pendingMapResult
            return if (result != null) {
                val json = org.json.JSONObject().apply {
                    put("type", result.type)
                    put("address", result.address)
                    put("lat", result.lat)
                    put("lng", result.lng)
                }
                pendingMapResult = null  // 消费掉
                json.toString()
            } else {
                "{}"
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

        @JavascriptInterface
        fun saveRouteDraft(draftJson: String) {
            try {
                val json = JSONObject(draftJson)
                val draft = PrefsManager.RouteDraft(
                    name = json.getString("name"),
                    startName = json.getString("startName"),
                    startLat = json.getDouble("startLat"),
                    startLng = json.getDouble("startLng"),
                    endName = json.getString("endName"),
                    endLat = json.getDouble("endLat"),
                    endLng = json.getDouble("endLng"),
                    sharedWith = json.optString("sharedWith", "")
                )
                prefsManager.saveRouteDraft(draft)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @JavascriptInterface
        fun getRouteDraft(): String {
            val draft = prefsManager.getRouteDraft()
            return if (draft != null) {
                JSONObject().apply {
                    put("name", draft.name)
                    put("startName", draft.startName)
                    put("startLat", draft.startLat)
                    put("startLng", draft.startLng)
                    put("endName", draft.endName)
                    put("endLat", draft.endLat)
                    put("endLng", draft.endLng)
                    put("sharedWith", draft.sharedWith)
                }.toString()
            } else {
                "{}"
            }
        }

        @JavascriptInterface
        fun clearRouteDraft() {
            prefsManager.clearRouteDraft()
        }

        @JavascriptInterface
        fun hasRouteDraft(): Boolean {
            return prefsManager.hasRouteDraft()
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
