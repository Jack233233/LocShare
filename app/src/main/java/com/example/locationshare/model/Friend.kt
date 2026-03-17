package com.example.locationshare.model

data class Friend(
    val friendId: String,
    val friendName: String,
    val pairRoomId: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromJson(json: String): Friend? {
            return try {
                val obj = org.json.JSONObject(json)
                Friend(
                    friendId = obj.getString("friendId"),
                    friendName = obj.getString("friendName"),
                    pairRoomId = obj.getString("pairRoomId"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toJson(): String {
        val obj = org.json.JSONObject()
        obj.put("friendId", friendId)
        obj.put("friendName", friendName)
        obj.put("pairRoomId", pairRoomId)
        obj.put("createdAt", createdAt)
        return obj.toString()
    }
}
