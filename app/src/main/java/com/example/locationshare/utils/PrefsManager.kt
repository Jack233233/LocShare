package com.example.locationshare.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.locationshare.model.Friend
import org.json.JSONArray

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "location_share_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_FRIENDS = "friends"
    }

    // 用户ID
    fun getUserId(): String {
        var userId = prefs.getString(KEY_USER_ID, null)
        if (userId == null) {
            userId = java.util.UUID.randomUUID().toString()
            prefs.edit().putString(KEY_USER_ID, userId).apply()
        }
        return userId
    }

    fun setUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getUserName(): String {
        return prefs.getString(KEY_USER_NAME, "") ?: ""
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
}
