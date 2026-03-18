package com.example.locationshare.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.locationshare.model.Friend
import com.example.locationshare.model.Route
import com.example.locationshare.model.User
import org.json.JSONArray
import org.json.JSONObject

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "location_share_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_DATA = "user_data"      // 完整的用户数据 JSON
        private const val KEY_IS_REGISTERED = "is_registered"
        private const val KEY_FRIENDS = "friends"
        private const val KEY_ROUTES = "routes"
        private const val KEY_ROUTE_DRAFT = "route_draft"   // 路线编辑草稿
    }

    // ==================== 用户管理（新版）====================

    /**
     * 获取或创建用户ID（UUID）
     */
    fun getUserId(): String {
        var userId = prefs.getString(KEY_USER_ID, null)
        if (userId == null) {
            userId = java.util.UUID.randomUUID().toString()
            prefs.edit().putString(KEY_USER_ID, userId).apply()
        }
        return userId
    }

    /**
     * 检查用户是否已注册
     */
    fun isUserRegistered(): Boolean {
        return prefs.getBoolean(KEY_IS_REGISTERED, false)
    }

    /**
     * 保存用户注册信息
     */
    fun saveUser(user: User) {
        prefs.edit().apply {
            putString(KEY_USER_DATA, user.toJson().toString())
            putString(KEY_USER_NAME, user.userName)
            putBoolean(KEY_IS_REGISTERED, true)
            apply()
        }
    }

    /**
     * 获取当前用户
     */
    fun getUser(): User? {
        val jsonStr = prefs.getString(KEY_USER_DATA, null) ?: return null
        return User.fromJsonString(jsonStr)
    }

    /**
     * 更新用户名
     */
    fun updateUserName(newName: String) {
        val user = getUser() ?: User(getUserId(), newName)
        user.userName = newName
        saveUser(user)
    }

    /**
     * 获取用户名（兼容旧版）
     */
    fun getUserName(): String {
        return getUser()?.userName ?: prefs.getString(KEY_USER_NAME, "") ?: ""
    }

    /**
     * 清除用户数据（退出登录）
     */
    fun clearUserData() {
        prefs.edit().apply {
            remove(KEY_USER_DATA)
            remove(KEY_USER_NAME)
            remove(KEY_IS_REGISTERED)
            // 保留 userId 和好友/路线数据
            apply()
        }
    }

    // ==================== 兼容旧版（已废弃）====================

    @Deprecated("使用 saveUser() 替代")
    fun setUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    // 好友列表
    fun saveFriends(friends: List<Friend>) {
        val jsonArray = JSONArray()
        friends.forEach { jsonArray.put(it.toJson()) }
        prefs.edit().putString(KEY_FRIENDS, jsonArray.toString()).apply()
    }

    fun getFriends(): List<Friend> {
        val jsonStr = prefs.getString(KEY_FRIENDS, "[]") ?: "[]"
        val friends = mutableListOf<Friend>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                Friend.fromJson(jsonArray.getString(i))?.let { friends.add(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return friends
    }

    fun addFriend(friend: Friend) {
        val friends = getFriends().toMutableList()
        // 避免重复添加
        if (friends.none { it.friendId == friend.friendId }) {
            friends.add(friend)
            saveFriends(friends)
        }
    }

    fun removeFriend(friendId: String) {
        val friends = getFriends().filter { it.friendId != friendId }
        saveFriends(friends)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    // ==================== 路线管理 ====================

    fun saveRoutes(routes: List<Route>) {
        val jsonArray = JSONArray()
        routes.forEach { jsonArray.put(it.toJson()) }
        prefs.edit().putString(KEY_ROUTES, jsonArray.toString()).apply()
    }

    fun getRoutes(): List<Route> {
        val jsonStr = prefs.getString(KEY_ROUTES, "[]") ?: "[]"
        val routes = mutableListOf<Route>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                Route.fromJson(jsonArray.getJSONObject(i))?.let { routes.add(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return routes
    }

    fun addRoute(route: Route) {
        val routes = getRoutes().toMutableList()
        routes.add(route)
        saveRoutes(routes)
    }

    fun updateRoute(route: Route) {
        val routes = getRoutes().toMutableList()
        val index = routes.indexOfFirst { it.id == route.id }
        if (index != -1) {
            routes[index] = route
            saveRoutes(routes)
        }
    }

    fun deleteRoute(routeId: String) {
        val routes = getRoutes().filter { it.id != routeId }
        saveRoutes(routes)
    }

    fun getRouteById(routeId: String): Route? {
        return getRoutes().find { it.id == routeId }
    }

    fun getFavoriteRoutes(): List<Route> {
        return getRoutes().filter { it.isFavorite }
    }

    // ==================== 路线编辑草稿 ====================

    data class RouteDraft(
        val name: String,
        val startName: String,
        val startLat: Double,
        val startLng: Double,
        val endName: String,
        val endLat: Double,
        val endLng: Double,
        val sharedWith: String
    )

    /**
     * 保存路线编辑草稿
     */
    fun saveRouteDraft(draft: RouteDraft) {
        val json = JSONObject().apply {
            put("name", draft.name)
            put("startName", draft.startName)
            put("startLat", draft.startLat)
            put("startLng", draft.startLng)
            put("endName", draft.endName)
            put("endLat", draft.endLat)
            put("endLng", draft.endLng)
            put("sharedWith", draft.sharedWith)
        }
        prefs.edit().putString(KEY_ROUTE_DRAFT, json.toString()).apply()
    }

    /**
     * 获取路线编辑草稿
     */
    fun getRouteDraft(): RouteDraft? {
        val jsonStr = prefs.getString(KEY_ROUTE_DRAFT, null) ?: return null
        return try {
            val json = JSONObject(jsonStr)
            RouteDraft(
                name = json.getString("name"),
                startName = json.getString("startName"),
                startLat = json.getDouble("startLat"),
                startLng = json.getDouble("startLng"),
                endName = json.getString("endName"),
                endLat = json.getDouble("endLat"),
                endLng = json.getDouble("endLng"),
                sharedWith = json.optString("sharedWith", "")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 清除路线编辑草稿
     */
    fun clearRouteDraft() {
        prefs.edit().remove(KEY_ROUTE_DRAFT).apply()
    }

    /**
     * 检查是否有未保存的路线草稿
     */
    fun hasRouteDraft(): Boolean {
        return prefs.getString(KEY_ROUTE_DRAFT, null) != null
    }
}
