package com.example.locationshare

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.example.locationshare.api.ApiService
import com.example.locationshare.bridge.WebAppBridge
import com.example.locationshare.databinding.ActivityMainBinding
import com.example.locationshare.service.LocationShareService
import com.example.locationshare.utils.PrefsManager
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 主界面 - WebView + 原生地图混合架构
 * WebView 负责底部抽屉UI，原生高德地图负责地图显示
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView
    private var mapView: MapView? = null
    private var aMap: AMap? = null
    private var locationClient: AMapLocationClient? = null
    private var socket: Socket? = null

    private lateinit var prefsManager: PrefsManager
    private lateinit var apiService: ApiService
    private lateinit var webAppBridge: WebAppBridge
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>

    private var currentRoomId: String = ""
    private var userName: String = ""
    private var userId: String = ""
    private var isSharingLocation = false
    private var isFollowing = false
    private var isFirstLocation = true

    // 标记点管理
    private val userMarkers = mutableMapOf<String, Marker>()
    private var myLocation: LatLng? = null

    // 用户位置信息（速度、方向）
    data class UserLocationInfo(
        var speed: Float = 0f,
        var bearing: Float = 0f,
        var lastUpdateTime: Long = 0
    )
    private val userLocationInfos = mutableMapOf<String, UserLocationInfo>()

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
        private const val NOTIFICATION_PERMISSION_REQUEST = 1002
        private const val SERVER_URL = "http://47.109.86.151"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PrefsManager(this)
        apiService = ApiService(this)
        userId = prefsManager.getUserId()

        // 隐私合规
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)

        // 初始化地图
        mapView = binding.mapView
        mapView?.onCreate(savedInstanceState)
        initMap()

        // 初始化 WebView
        initWebView()

        // 初始化 BottomSheet
        initBottomSheet()

        checkLocationPermission()
    }

    // 初始化 BottomSheet
    private fun initBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet).apply {
            // 设置默认状态为折叠（只显示 peekHeight）
            state = BottomSheetBehavior.STATE_COLLAPSED
            // 允许拖动展开
            isDraggable = true
            // 根据内容调整高度
            isFitToContents = true
            // 设置回调监听状态变化
            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            webView.evaluateJavascript("window.onSheetState('expanded')", null)
                        }
                        BottomSheetBehavior.STATE_COLLAPSED -> {
                            webView.evaluateJavascript("window.onSheetState('collapsed')", null)
                        }
                        else -> {}
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    // slideOffset: 0 (折叠) ~ 1 (展开)
                    // 可以在这里添加视差效果
                }
            })
        }
    }

    // 切换抽屉状态
    fun toggleSheet() {
        bottomSheetBehavior.apply {
            if (this.state == BottomSheetBehavior.STATE_EXPANDED) {
                this.state = BottomSheetBehavior.STATE_COLLAPSED
            } else {
                this.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun initMap() {
        aMap = mapView?.map
        aMap?.apply {
            uiSettings.isMyLocationButtonEnabled = true
            uiSettings.isZoomControlsEnabled = true
            moveCamera(CameraUpdateFactory.zoomTo(15f))

            // 标记点击监听
            setOnMarkerClickListener { marker ->
                val clickedUserId = userMarkers.entries.find { it.value == marker }?.key
                clickedUserId?.let { id ->
                    val name = marker.title ?: "未知用户"
                    val info = userLocationInfos[id]
                    val speed = info?.speed ?: 0f
                    val bearing = info?.bearing ?: 0f
                    webView.evaluateJavascript(
                        "window.onMarkerClicked('$id', '${name.replace("'", "\\'")}', $speed, $bearing)",
                        null
                    )
                }
                true
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        webView = binding.webView
        webAppBridge = WebAppBridge(this, webView, this, prefsManager, apiService, lifecycleScope)

        webView.apply {
            // 设置 WebView 透明背景
            setBackgroundColor(Color.TRANSPARENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            } else {
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            }

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                setRenderPriority(WebSettings.RenderPriority.HIGH)
            }

            // 设置 WebViewClient
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    return false
                }
            }

            webChromeClient = WebChromeClient()

            // 添加 JS 桥接
            addJavascriptInterface(webAppBridge, WebAppBridge.INTERFACE_NAME)

            // 加载本地 HTML
            loadUrl("file:///android_asset/web/index.html")
        }
    }

    // ========== 公开方法供 WebAppBridge 调用 ==========

    fun startPairSharing(name: String, roomId: String) {
        userName = name
        currentRoomId = roomId

        if (!checkNotificationPermission()) {
            requestNotificationPermission()
            return
        }

        LocationShareService.start(this)
        connectSocket(isPairMode = true)
    }

    fun startMultiSharing(name: String, roomId: String) {
        userName = name
        currentRoomId = roomId

        if (!checkNotificationPermission()) {
            requestNotificationPermission()
            return
        }

        LocationShareService.start(this)
        connectSocket(isPairMode = false)
    }

    fun stopSharing() {
        exitSharing()
    }

    fun toggleFollow() {
        isFollowing = !isFollowing
        Toast.makeText(this, if (isFollowing) "地图跟随开启" else "地图跟随关闭", Toast.LENGTH_SHORT).show()
    }

    fun isSharingLocation(): Boolean = isSharingLocation

    fun moveCameraTo(lat: Double, lng: Double) {
        aMap?.moveCamera(CameraUpdateFactory.changeLatLng(LatLng(lat, lng)))
    }

    fun getCurrentLocation(): LatLng? = myLocation

    // ========== Socket.io 连接 ==========

    private fun connectSocket(isPairMode: Boolean) {
        lifecycleScope.launch {
            try {
                socket = IO.socket(SERVER_URL).apply {
                    on(Socket.EVENT_CONNECT) {
                        android.util.Log.d("LocationShare", "Socket connected")
                        runOnUiThread {
                            startSharingLocation()
                            webView.evaluateJavascript("window.onShareState(true)", null)
                        }

                        val data = JSONObject().apply {
                            put("userId", userId)
                            put("userName", userName)
                            if (isPairMode) {
                                put("pairRoomId", currentRoomId)
                            } else {
                                put("roomId", currentRoomId)
                            }
                        }

                        if (isPairMode) {
                            emit("join-pair", data)
                        } else {
                            emit("join", data)
                        }
                    }

                    on("location-update") { args ->
                        val data = args[0] as JSONObject
                        runOnUiThread {
                            updateUserLocationOnMap(data)
                        }
                    }

                    on("user-joined") { args ->
                        val data = args[0] as JSONObject
                        runOnUiThread {
                            val name = data.optString("userName")
                            webView.evaluateJavascript("window.onUserJoin('$name')", null)
                        }
                    }

                    on("user-left") { args ->
                        val data = args[0] as JSONObject
                        runOnUiThread {
                            val leftUserId = data.optString("userId")
                            removeUserMarker(leftUserId)
                            webView.evaluateJavascript("window.onUserLeave('$leftUserId')", null)
                        }
                    }

                    on("room-users") { args ->
                        val users = args[0] as JSONObject
                        runOnUiThread {
                            users.keys().forEach { key ->
                                updateUserLocationOnMap(users.getJSONObject(key))
                            }
                        }
                    }

                    on("new-friend:$userId") { args ->
                        val data = args[0] as JSONObject
                        runOnUiThread {
                            val friendName = data.optString("friendName", "新好友")
                            webView.evaluateJavascript("window.onNewFriend('$friendName')", null)
                            webAppBridge.loadFriends()
                        }
                    }

                    on(Socket.EVENT_DISCONNECT) {
                        runOnUiThread {
                            exitSharing()
                            webView.evaluateJavascript("window.onShareState(false)", null)
                        }
                    }

                    connect()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "连接失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun exitSharing() {
        if (isSharingLocation) {
            stopSharingLocation()
        }

        LocationShareService.stop(this)

        socket?.emit("leave")
        socket?.disconnect()
        socket?.off()
        socket = null

        runOnUiThread {
            userMarkers.values.forEach { it.remove() }
            userMarkers.clear()
            userLocationInfos.clear()
            isFirstLocation = true
            isFollowing = false
            isSharingLocation = false
            webView.evaluateJavascript("window.onShareState(false)", null)
        }
    }

    // ========== 位置共享 ==========

    private fun startSharingLocation() {
        if (socket == null || socket?.connected() != true) {
            Toast.makeText(this, "请先连接到服务器", Toast.LENGTH_SHORT).show()
            return
        }

        if (isSharingLocation) return

        locationClient?.setLocationListener { location ->
            if (location.errorCode == 0) {
                val lat = location.latitude
                val lng = location.longitude
                val speed = location.speed
                val bearing = location.bearing
                myLocation = LatLng(lat, lng)

                val info = userLocationInfos.getOrPut(userId) { UserLocationInfo() }
                info.speed = speed
                info.bearing = bearing
                info.lastUpdateTime = System.currentTimeMillis()

                val data = JSONObject().apply {
                    put("lat", lat)
                    put("lng", lng)
                    put("accuracy", location.accuracy)
                    put("userName", userName)
                    put("speed", speed)
                    put("bearing", bearing)
                }
                socket?.emit("location-update", data)

                runOnUiThread {
                    updateMyMarker(lat, lng, speed, bearing)
                }
            }
        }

        locationClient?.startLocation()
        isSharingLocation = true
        Toast.makeText(this, "开始共享位置", Toast.LENGTH_LONG).show()
    }

    private fun stopSharingLocation() {
        locationClient?.stopLocation()
    }

    // ========== 地图标记 ==========

    private fun updateMyMarker(lat: Double, lng: Double, speed: Float = 0f, bearing: Float = 0f) {
        val latLng = LatLng(lat, lng)
        myLocation = latLng

        if (isFirstLocation) {
            aMap?.moveCamera(CameraUpdateFactory.changeLatLng(latLng))
            isFirstLocation = false
        } else if (isFollowing) {
            aMap?.moveCamera(CameraUpdateFactory.changeLatLng(latLng))
        }

        val marker = userMarkers[userId]
        val displayName = if (userName.isNotEmpty()) userName else "我"
        val icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)

        if (marker != null) {
            marker.position = latLng
            marker.title = displayName
        } else {
            val newMarker = aMap?.addMarker(MarkerOptions()
                .position(latLng)
                .title(displayName)
                .icon(icon)
                .visible(true)
            )
            newMarker?.let { userMarkers[userId] = it }
        }

        // 发送自己的位置到 WebView
        runOnUiThread {
            webView.evaluateJavascript(
                "window.onLocationUpdate('$userId', $lat, $lng, '${displayName.replace("'", "\\'")}', $speed, $bearing)",
                null
            )
        }
    }

    private fun updateUserLocationOnMap(data: JSONObject) {
        val id = data.optString("userId")
        if (id == userId) return

        val lat = data.optDouble("lat")
        val lng = data.optDouble("lng")
        val name = data.optString("userName", "未知用户")
        val speed = data.optDouble("speed", 0.0).toFloat()
        val bearing = data.optDouble("bearing", 0.0).toFloat()
        val latLng = LatLng(lat, lng)

        val info = userLocationInfos.getOrPut(id) { UserLocationInfo() }
        info.speed = speed
        info.bearing = bearing
        info.lastUpdateTime = System.currentTimeMillis()

        val marker = userMarkers[id]
        val icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)

        if (marker != null) {
            marker.position = latLng
            marker.title = name
        } else {
            val newMarker = aMap?.addMarker(MarkerOptions()
                .position(latLng)
                .title(name)
                .icon(icon)
                .visible(true)
            )
            newMarker?.let { userMarkers[id] = it }
        }

        webView.evaluateJavascript(
            "window.onLocationUpdate('$id', $lat, $lng, '${name.replace("'", "\\'")}', $speed, $bearing)",
            null
        )
    }

    private fun removeUserMarker(userId: String) {
        userMarkers[userId]?.remove()
        userMarkers.remove(userId)
    }

    // ========== 权限处理 ==========

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST
            )
        }
    }

    private fun checkLocationPermission() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }) {
            initLocation()
        } else {
            ActivityCompat.requestPermissions(this, permissions, LOCATION_PERMISSION_REQUEST)
        }
    }

    private fun initLocation() {
        try {
            locationClient = AMapLocationClient(applicationContext).apply {
                setLocationOption(AMapLocationClientOption().apply {
                    locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                    interval = 3000
                    isNeedAddress = false
                })
            }
            aMap?.isMyLocationEnabled = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    initLocation()
                } else {
                    Toast.makeText(this, "需要定位权限", Toast.LENGTH_LONG).show()
                }
            }
            NOTIFICATION_PERMISSION_REQUEST -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // 重新尝试开始共享
                } else {
                    Toast.makeText(this, "需要通知权限才能在后台保持共享", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ========== 生命周期 ==========

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        if (isSharingLocation && socket?.connected() == true) {
            locationClient?.startLocation()
        }
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSharingLocation()
        locationClient?.onDestroy()
        mapView?.onDestroy()
        socket?.disconnect()
        socket?.off()
        webView.destroy()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
