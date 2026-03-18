package com.example.locationshare.bridge

import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import com.example.locationshare.MainActivity
import com.example.locationshare.ProfileActivity
import com.example.locationshare.RouteManagerActivity
import com.example.locationshare.api.ApiService
import com.example.locationshare.service.LocationShareService
import com.example.locationshare.utils.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * WebView 与原生代码的桥接类
 * 提供 JS 调用原生功能的接口
 */
class WebAppBridge(
    private val context: Context,
    private val webView: WebView,
    private val activity: MainActivity,
    private val prefsManager: PrefsManager,
    private val apiService: ApiService,
    private val scope: CoroutineScope
) {

    companion object {
        const val INTERFACE_NAME = "AndroidBridge"
    }

    // ========== 页面导航 ==========

    @JavascriptInterface
    fun showProfile() {
        context.startActivity(Intent(context, ProfileActivity::class.java))
    }

    @JavascriptInterface
    fun toggleSheet() {
        activity.toggleSheet()
    }

    @JavascriptInterface
    fun showRouteManager() {
        context.startActivity(Intent(context, RouteManagerActivity::class.java))
    }

    // ========== 用户信息 ==========

    @JavascriptInterface
    fun getUserInfo(): String {
        val user = prefsManager.getUser()
        return JSONObject().apply {
            put("userId", user?.userId ?: "")
            put("userName", user?.userName ?: "")
        }.toString()
    }

    // ========== 配对码 ==========

    @JavascriptInterface
    fun generatePairCode(userName: String) {
        scope.launch {
            val result = apiService.generatePairCode()
            withContext(Dispatchers.Main) {
                result.onSuccess { code ->
                    webView.evaluateJavascript("window.onPairCode('$code')", null)
                }.onFailure { error ->
                    showToast("生成失败: ${error.message}")
                }
            }
        }
    }

    @JavascriptInterface
    fun pairWithCode(code: String, userName: String) {
        scope.launch {
            val result = apiService.pairWithCode(code)
            withContext(Dispatchers.Main) {
                result.onSuccess { friend ->
                    webView.evaluateJavascript(
                        "window.onFriendAdded(true, '成功添加好友: ${friend.friendName}')",
                        null
                    )
                    loadFriends()
                }.onFailure { error ->
                    webView.evaluateJavascript(
                        "window.onFriendAdded(false, '${error.message}')",
                        null
                    )
                }
            }
        }
    }

    // ========== 好友管理 ==========

    @JavascriptInterface
    fun loadFriends() {
        scope.launch {
            val result = apiService.fetchFriendsFromServer()
            withContext(Dispatchers.Main) {
                result.onSuccess { friends ->
                    val jsonArray = JSONArray()
                    friends.forEach { friend ->
                        jsonArray.put(JSONObject().apply {
                            put("friendId", friend.friendId)
                            put("friendName", friend.friendName)
                            put("pairRoomId", friend.pairRoomId)
                        })
                    }
                    webView.evaluateJavascript(
                        "window.onFriends('${jsonArray.toString().replace("'", "\\'")}')",
                        null
                    )
                }.onFailure {
                    // 使用本地缓存
                    val friends = apiService.getFriends()
                    val jsonArray = JSONArray()
                    friends.forEach { friend ->
                        jsonArray.put(JSONObject().apply {
                            put("friendId", friend.friendId)
                            put("friendName", friend.friendName)
                            put("pairRoomId", friend.pairRoomId)
                        })
                    }
                    webView.evaluateJavascript(
                        "window.onFriends('${jsonArray.toString().replace("'", "\\'")}')",
                        null
                    )
                }
            }
        }
    }

    @JavascriptInterface
    fun deleteFriend(friendId: String) {
        scope.launch {
            val result = apiService.deleteFriend(friendId)
            withContext(Dispatchers.Main) {
                result.onSuccess {
                    showToast("已删除")
                    loadFriends()
                }.onFailure { error ->
                    showToast("删除失败: ${error.message}")
                }
            }
        }
    }

    // ========== 位置共享 ==========

    @JavascriptInterface
    fun startPairSharing(userName: String, roomId: String) {
        activity.runOnUiThread {
            activity.startPairSharing(userName, roomId)
        }
    }

    @JavascriptInterface
    fun startMultiSharing(userName: String, roomId: String) {
        activity.runOnUiThread {
            activity.startMultiSharing(userName, roomId)
        }
    }

    @JavascriptInterface
    fun stopSharing() {
        activity.runOnUiThread {
            activity.stopSharing()
        }
    }

    @JavascriptInterface
    fun toggleFollow() {
        activity.runOnUiThread {
            activity.toggleFollow()
        }
    }

    @JavascriptInterface
    fun isSharing(): Boolean {
        return activity.isSharingLocation()
    }

    // ========== 地图控制 ==========

    @JavascriptInterface
    fun showMap() {
        // 地图始终显示在底层
    }

    @JavascriptInterface
    fun hideMap() {
        // 返回主菜单时不需要隐藏地图
    }

    @JavascriptInterface
    fun moveToLocation(lat: Double, lng: Double) {
        activity.runOnUiThread {
            activity.moveCameraTo(lat, lng)
        }
    }

    @JavascriptInterface
    fun getCurrentLocation(): String {
        val location = activity.getCurrentLocation()
        return JSONObject().apply {
            put("lat", location?.latitude ?: 0.0)
            put("lng", location?.longitude ?: 0.0)
        }.toString()
    }

    // ========== 辅助方法 ==========

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
