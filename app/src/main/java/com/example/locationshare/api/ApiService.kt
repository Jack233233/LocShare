package com.example.locationshare.api

import android.content.Context
import com.example.locationshare.model.Friend
import com.example.locationshare.utils.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiService(private val context: Context) {

    companion object {
        const val SERVER_URL = "http://47.109.86.151"
        private const val API_BASE = "$SERVER_URL/api"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val prefsManager by lazy { PrefsManager(context) }

    // 生成配对码
    suspend fun generatePairCode(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val userId = prefsManager.getUserId()
            val userName = prefsManager.getUserName()

            if (userName.isEmpty()) {
                return@withContext Result.failure(Exception("请先设置昵称"))
            }

            val json = JSONObject().apply {
                put("userId", userId)
                put("userName", userName)
            }

            val request = Request.Builder()
                .url("$API_BASE/pair-code")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful && body != null) {
                val result = JSONObject(body)
                Result.success(result.getString("code"))
            } else {
                Result.failure(Exception("生成失败: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 使用配对码添加好友
    suspend fun pairWithCode(code: String): Result<Friend> = withContext(Dispatchers.IO) {
        try {
            val userId = prefsManager.getUserId()
            val userName = prefsManager.getUserName()

            if (userName.isEmpty()) {
                return@withContext Result.failure(Exception("请先设置昵称"))
            }

            val json = JSONObject().apply {
                put("userId", userId)
                put("userName", userName)
                put("code", code)
            }

            val request = Request.Builder()
                .url("$API_BASE/pair")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful && body != null) {
                val result = JSONObject(body)
                if (result.optBoolean("success")) {
                    val friendJson = result.getJSONObject("friend")
                    val friend = Friend(
                        friendId = friendJson.getString("friendId"),
                        friendName = friendJson.getString("friendName"),
                        pairRoomId = friendJson.getString("pairRoomId")
                    )
                    // 保存到本地
                    prefsManager.addFriend(friend)
                    Result.success(friend)
                } else {
                    Result.failure(Exception("配对失败"))
                }
            } else {
                val errorBody = body?.let { JSONObject(it).optString("error") } ?: "配对失败"
                Result.failure(Exception(errorBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 获取好友列表（从本地）
    fun getFriends(): List<Friend> {
        return prefsManager.getFriends()
    }

    // 从服务器获取好友列表
    suspend fun fetchFriendsFromServer(): Result<List<Friend>> = withContext(Dispatchers.IO) {
        try {
            val userId = prefsManager.getUserId()

            val request = Request.Builder()
                .url("$API_BASE/friends/$userId")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful && body != null) {
                val result = JSONObject(body)
                val friendsArray = result.getJSONArray("friends")
                val friends = mutableListOf<Friend>()
                for (i in 0 until friendsArray.length()) {
                    val friendObj = friendsArray.getJSONObject(i)
                    val friend = Friend(
                        friendId = friendObj.getString("friendId"),
                        friendName = friendObj.getString("friendName"),
                        pairRoomId = friendObj.getString("pairRoomId"),
                        createdAt = friendObj.optLong("createdAt", System.currentTimeMillis())
                    )
                    friends.add(friend)
                }
                // 同步到本地
                prefsManager.saveFriends(friends)
                Result.success(friends)
            } else {
                Result.failure(Exception("获取好友列表失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============ 用户系统 ============

    // 注册/更新用户信息
    suspend fun registerUser(userName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val userId = prefsManager.getUserId()

            val json = JSONObject().apply {
                put("userId", userId)
                put("userName", userName)
            }

            val request = Request.Builder()
                .url("$API_BASE/user/register")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("注册失败: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 从服务器获取用户信息
    suspend fun fetchUserProfile(userId: String): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$API_BASE/user/$userId")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful && body != null) {
                val result = JSONObject(body)
                val user = result.getJSONObject("user")
                Result.success(Pair(
                    user.getString("userId"),
                    user.getString("userName")
                ))
            } else {
                Result.failure(Exception("获取用户信息失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 删除好友
    suspend fun deleteFriend(friendId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = prefsManager.getUserId()

            val json = JSONObject().apply {
                put("userId", userId)
                put("friendId", friendId)
            }

            val request = Request.Builder()
                .url("$API_BASE/friends")
                .delete(json.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                // 从本地删除
                prefsManager.removeFriend(friendId)
                Result.success(Unit)
            } else {
                Result.failure(Exception("删除失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
