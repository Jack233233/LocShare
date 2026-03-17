package com.example.locationshare

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.example.locationshare.adapter.FriendAdapter
import com.example.locationshare.api.ApiService
import com.example.locationshare.databinding.ActivityMainBinding
import com.example.locationshare.model.Friend
import com.example.locationshare.model.ShareMode
import com.example.locationshare.service.LocationShareService
import com.example.locationshare.utils.PrefsManager
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mapView: MapView? = null
    private var aMap: AMap? = null
    private var locationClient: AMapLocationClient? = null
    private var socket: Socket? = null

    private lateinit var prefsManager: PrefsManager
    private lateinit var apiService: ApiService

    private var currentMode: ShareMode = ShareMode.NONE
    private var currentRoomId: String = ""
    private var userName: String = ""
    private var userId: String = ""
    private var isSharingLocation = false
    private var isFollowing = false
    private var isFirstLocation = true

    // 好友列表
    private lateinit var friendAdapter: FriendAdapter
    private var friends: List<Friend> = emptyList()
    private var selectedFriend: Friend? = null

    // 标记点管理
    private val userMarkers = mutableMapOf<String, Marker>()
    private var myLocation: LatLng? = null

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

        initViews()
        checkLocationPermission()
    }

    private fun initMap() {
        aMap = mapView?.map
        aMap?.apply {
            uiSettings.isMyLocationButtonEnabled = true
            uiSettings.isZoomControlsEnabled = true
            moveCamera(CameraUpdateFactory.zoomTo(15f))
        }
    }

    private fun initViews() {
        // 恢复用户名
        val savedName = prefsManager.getUserName()
        if (savedName.isNotEmpty()) {
            binding.etUserName.setText(savedName)
            userName = savedName
        }

        // 模式切换按钮
        binding.btnModePair.setOnClickListener {
            switchMode(ShareMode.PAIR)
        }
        binding.btnModeMulti.setOnClickListener {
            switchMode(ShareMode.MULTI)
        }

        // 生成配对码
        binding.btnGenerateCode.setOnClickListener {
            val name = binding.etUserName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "请先输入你的名字", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            userName = name
            prefsManager.setUserName(name)
            generatePairCode()
        }

        // 点击配对码区域收起
        binding.layoutPairCodeDisplay.setOnClickListener {
            binding.layoutPairCodeDisplay.visibility = View.GONE
        }

        // 输入配对码
        binding.btnInputCode.setOnClickListener {
            val name = binding.etUserName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "请先输入你的名字", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            userName = name
            prefsManager.setUserName(name)
            showInputCodeDialog()
        }

        // 好友列表折叠
        var isFriendsExpanded = true
        binding.layoutFriendsHeader.setOnClickListener {
            isFriendsExpanded = !isFriendsExpanded
            binding.recyclerFriends.visibility = if (isFriendsExpanded) View.VISIBLE else View.GONE
            binding.ivExpandIcon.rotation = if (isFriendsExpanded) 0f else 180f
        }

        // 初始化好友列表
        friendAdapter = FriendAdapter(
            onFriendClick = { friend ->
                selectedFriend = friend
                friendAdapter.setSelected(friend.friendId)
                updateShareButtonState()
            },
            onDeleteClick = { friend ->
                showDeleteFriendDialog(friend)
            }
        )
        binding.recyclerFriends.layoutManager = LinearLayoutManager(this)
        binding.recyclerFriends.adapter = friendAdapter

        // 开始/停止共享
        binding.btnJoin.setOnClickListener {
            if (isSharingLocation) {
                stopSharing()
            } else {
                startSharing()
            }
        }

        // 跟随按钮
        binding.btnFollow.setOnClickListener {
            isFollowing = !isFollowing
            binding.btnFollow.isChecked = isFollowing
            Toast.makeText(this, if (isFollowing) "地图跟随开启" else "地图跟随关闭", Toast.LENGTH_SHORT).show()
        }

        // 房间号输入监听
        binding.etRoomId.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateShareButtonState()
            }
        })

        // 加载好友列表
        loadFriends()
    }

    private fun switchMode(mode: ShareMode) {
        currentMode = mode
        when (mode) {
            ShareMode.PAIR -> {
                binding.layoutPairMode.visibility = View.VISIBLE
                binding.layoutMultiMode.visibility = View.GONE
                binding.btnModePair.setBackgroundColor(Color.parseColor("#9C27B0"))
                binding.btnModeMulti.setBackgroundColor(Color.parseColor("#333333"))
                loadFriends()
            }
            ShareMode.MULTI -> {
                binding.layoutPairMode.visibility = View.GONE
                binding.layoutMultiMode.visibility = View.VISIBLE
                binding.btnModePair.setBackgroundColor(Color.parseColor("#333333"))
                binding.btnModeMulti.setBackgroundColor(Color.parseColor("#9C27B0"))
                selectedFriend = null
            }
            else -> {
                binding.layoutPairMode.visibility = View.GONE
                binding.layoutMultiMode.visibility = View.GONE
            }
        }
        updateShareButtonState()
    }

    private fun updateShareButtonState() {
        val canStart = when (currentMode) {
            ShareMode.PAIR -> selectedFriend != null
            ShareMode.MULTI -> binding.etRoomId.text.toString().trim().isNotEmpty()
            else -> false
        }
        binding.btnJoin.isEnabled = canStart || isSharingLocation
        binding.btnJoin.text = if (isSharingLocation) "停止共享" else "开始共享"
    }

    private fun generatePairCode() {
        lifecycleScope.launch {
            val result = apiService.generatePairCode()
            result.onSuccess { code ->
                binding.tvPairCode.text = code
                binding.layoutPairCodeDisplay.visibility = View.VISIBLE
                Toast.makeText(this@MainActivity, "配对码已生成: $code", Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(this@MainActivity, "生成失败: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showInputCodeDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "请输入6位配对码"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            maxLines = 1
        }

        AlertDialog.Builder(this)
            .setTitle("添加好友")
            .setView(input)
            .setPositiveButton("添加") { _, _ ->
                val code = input.text.toString().trim()
                if (code.length == 6) {
                    pairWithCode(code)
                } else {
                    Toast.makeText(this, "请输入6位配对码", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun pairWithCode(code: String) {
        lifecycleScope.launch {
            val result = apiService.pairWithCode(code)
            result.onSuccess { friend ->
                Toast.makeText(this@MainActivity, "成功添加好友: ${friend.friendName}", Toast.LENGTH_SHORT).show()
                loadFriends()
            }.onFailure { error ->
                Toast.makeText(this@MainActivity, "添加失败: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadFriends() {
        lifecycleScope.launch {
            val result = apiService.fetchFriendsFromServer()
            result.onSuccess { friendList ->
                friends = friendList
                friendAdapter.setFriends(friends)
                binding.tvFriendCount.text = "(${friends.size})"
            }.onFailure {
                // 使用本地缓存
                friends = apiService.getFriends()
                friendAdapter.setFriends(friends)
                binding.tvFriendCount.text = "(${friends.size})"
            }
        }
    }

    private fun showDeleteFriendDialog(friend: Friend) {
        AlertDialog.Builder(this)
            .setTitle("删除好友")
            .setMessage("确定要删除 ${friend.friendName} 吗？")
            .setPositiveButton("删除") { _, _ ->
                deleteFriend(friend.friendId)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteFriend(friendId: String) {
        lifecycleScope.launch {
            val result = apiService.deleteFriend(friendId)
            result.onSuccess {
                Toast.makeText(this@MainActivity, "已删除", Toast.LENGTH_SHORT).show()
                loadFriends()
            }.onFailure { error ->
                Toast.makeText(this@MainActivity, "删除失败: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startSharing() {
        val name = binding.etUserName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "请先输入你的名字", Toast.LENGTH_SHORT).show()
            return
        }
        userName = name
        prefsManager.setUserName(name)

        // 检查通知权限
        if (!checkNotificationPermission()) {
            requestNotificationPermission()
            return
        }

        when (currentMode) {
            ShareMode.PAIR -> {
                val friend = selectedFriend
                if (friend == null) {
                    Toast.makeText(this, "请先选择一个好友", Toast.LENGTH_SHORT).show()
                    return
                }
                currentRoomId = friend.pairRoomId

                // 启动前台服务
                LocationShareService.start(this)

                connectSocket(isPairMode = true)
            }
            ShareMode.MULTI -> {
                val roomId = binding.etRoomId.text.toString().trim()
                if (roomId.isEmpty()) {
                    Toast.makeText(this, "请输入房间号", Toast.LENGTH_SHORT).show()
                    return
                }
                currentRoomId = roomId

                // 启动前台服务
                LocationShareService.start(this)

                connectSocket(isPairMode = false)
            }
            else -> {}
        }
    }

    private fun stopSharing() {
        exitSharing()
    }

    private fun connectSocket(isPairMode: Boolean) {
        lifecycleScope.launch {
            try {
                socket = IO.socket(SERVER_URL).apply {
                    on(Socket.EVENT_CONNECT) {
                        android.util.Log.d("LocationShare", "Socket connected")
                        runOnUiThread {
                            updateShareButtonState()
                            Toast.makeText(this@MainActivity, "已连接服务器", Toast.LENGTH_SHORT).show()
                            startSharingLocation()
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
                            Toast.makeText(
                                this@MainActivity,
                                "${data.optString("userName")} 加入了",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    on("user-left") { args ->
                        val data = args[0] as JSONObject
                        runOnUiThread {
                            removeUserMarker(data.optString("userId"))
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
                            Toast.makeText(
                                this@MainActivity,
                                "$friendName 添加你为好友",
                                Toast.LENGTH_SHORT
                            ).show()
                            // 刷新好友列表
                            loadFriends()
                        }
                    }

                    on(Socket.EVENT_DISCONNECT) {
                        runOnUiThread {
                            stopSharingLocation()
                            updateShareButtonState()
                            Toast.makeText(this@MainActivity, "连接断开", Toast.LENGTH_SHORT).show()
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
            isFirstLocation = true
            isFollowing = false
            binding.btnFollow.isChecked = false
            isSharingLocation = false
            updateShareButtonState()
            Toast.makeText(this, "已退出共享", Toast.LENGTH_SHORT).show()
        }
    }

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
                myLocation = LatLng(lat, lng)

                val data = JSONObject().apply {
                    put("lat", lat)
                    put("lng", lng)
                    put("accuracy", location.accuracy)
                    put("userName", userName)
                }
                socket?.emit("location-update", data)

                runOnUiThread {
                    updateMyMarker(lat, lng)
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

    private fun updateMyMarker(lat: Double, lng: Double) {
        android.util.Log.d("LocationShare", "updateMyMarker: lat=$lat, lng=$lng, userId=$userId")
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
            android.util.Log.d("LocationShare", "Updating existing marker for me")
            marker.position = latLng
            marker.title = displayName
        } else {
            android.util.Log.d("LocationShare", "Creating new green marker for me: $displayName")
            val newMarker = aMap?.addMarker(MarkerOptions()
                .position(latLng)
                .title(displayName)
                .icon(icon)
                .visible(true)
            )
            newMarker?.let {
                userMarkers[userId] = it
                android.util.Log.d("LocationShare", "Marker created and saved, total markers: ${userMarkers.size}")
            } ?: android.util.Log.e("LocationShare", "Failed to create marker - aMap is null?")
        }
    }

    private fun updateUserLocationOnMap(data: JSONObject) {
        val id = data.optString("userId")
        android.util.Log.d("LocationShare", "updateUserLocationOnMap: userId=$id, myId=$userId")
        if (id == userId) return

        val lat = data.optDouble("lat")
        val lng = data.optDouble("lng")
        val name = data.optString("userName", "未知用户")
        android.util.Log.d("LocationShare", "Other user location: $name at ($lat, $lng)")
        val latLng = LatLng(lat, lng)

        val marker = userMarkers[id]
        val icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)

        if (marker != null) {
            android.util.Log.d("LocationShare", "Updating existing marker for $name")
            marker.position = latLng
            marker.title = name
        } else {
            android.util.Log.d("LocationShare", "Creating new red marker for $name")
            val newMarker = aMap?.addMarker(MarkerOptions()
                .position(latLng)
                .title(name)
                .icon(icon)
                .visible(true)
            )
            newMarker?.let {
                userMarkers[id] = it
                android.util.Log.d("LocationShare", "Red marker created, total markers: ${userMarkers.size}")
            } ?: android.util.Log.e("LocationShare", "Failed to create red marker - aMap is null?")
        }
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
            // 不使用高德地图自带的蓝点，只使用我们自己的标记
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
    }
}
