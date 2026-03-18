package com.example.locationshare.model

import org.json.JSONObject

/**
 * 用户数据模型
 */
data class User(
    val userId: String,           // 唯一用户ID (UUID)
    var userName: String,         // 昵称
    val createdAt: Long = System.currentTimeMillis(),  // 注册时间
    var avatarUrl: String? = null // 头像URL（预留）
) {
    /**
     * 转换为 JSON 用于本地存储或网络传输
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("userId", userId)
            put("userName", userName)
            put("createdAt", createdAt)
            avatarUrl?.let { put("avatarUrl", it) }
        }
    }

    /**
     * 检查用户是否已注册（有昵称）
     */
    fun isRegistered(): Boolean {
        return userName.isNotBlank()
    }

    companion object {
        /**
         * 从 JSON 解析 User
         */
        fun fromJson(json: JSONObject): User? {
            return try {
                User(
                    userId = json.getString("userId"),
                    userName = json.getString("userName"),
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                    avatarUrl = json.optString("avatarUrl").takeIf { it.isNotBlank() }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        /**
         * 从 JSON 字符串解析
         */
        fun fromJsonString(jsonStr: String): User? {
            return try {
                fromJson(JSONObject(jsonStr))
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * 用户注册请求
 */
data class RegisterRequest(
    val userId: String,
    val userName: String
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("userId", userId)
            put("userName", userName)
        }
    }
}

/**
 * 用户资料响应
 */
data class UserProfile(
    val userId: String,
    val userName: String,
    val isOnline: Boolean = false
) {
    companion object {
        fun fromJson(json: JSONObject): UserProfile? {
            return try {
                UserProfile(
                    userId = json.getString("userId"),
                    userName = json.getString("userName"),
                    isOnline = json.optBoolean("isOnline", false)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
