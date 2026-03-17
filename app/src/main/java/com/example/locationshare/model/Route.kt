package com.example.locationshare.model

import com.amap.api.maps.model.LatLng

/**
 * 路线数据模型
 * 保存用户常用的路线信息
 */
data class Route(
    val id: String = "",           // 路线唯一ID
    val name: String = "",         // 路线名称（如"上班路线"）
    val startName: String = "",    // 起始地名称
    val startLat: Double = 0.0,    // 起始地纬度
    val startLng: Double = 0.0,    // 起始地经度
    val endName: String = "",      // 目的地名称
    val endLat: Double = 0.0,      // 目的地纬度
    val endLng: Double = 0.0,      // 目的地经度
    val sharedWith: String = "",   // 共享对象ID（爱人/好友）
    val sharedWithName: String = "", // 共享对象名称
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false // 是否收藏（常用路线）
) {
    /**
     * 获取起始地坐标
     */
    fun getStartLatLng(): LatLng = LatLng(startLat, startLng)

    /**
     * 获取目的地坐标
     */
    fun getEndLatLng(): LatLng = LatLng(endLat, endLng)

    /**
     * 计算与目的地的距离（米）
     */
    fun distanceToEnd(currentLat: Double, currentLng: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            currentLat, currentLng,
            endLat, endLng,
            results
        )
        return results[0]
    }

    /**
     * 检查是否已到达目的地（距离小于阈值）
     */
    fun hasArrived(currentLat: Double, currentLng: Double, thresholdMeters: Float = 100f): Boolean {
        return distanceToEnd(currentLat, currentLng) < thresholdMeters
    }

    /**
     * 转换为 JSON
     */
    fun toJson(): org.json.JSONObject {
        return org.json.JSONObject().apply {
            put("id", id)
            put("name", name)
            put("startName", startName)
            put("startLat", startLat)
            put("startLng", startLng)
            put("endName", endName)
            put("endLat", endLat)
            put("endLng", endLng)
            put("sharedWith", sharedWith)
            put("sharedWithName", sharedWithName)
            put("createdAt", createdAt)
            put("isFavorite", isFavorite)
        }
    }

    companion object {
        const val ARRIVAL_THRESHOLD = 100f // 到达判定阈值：100米

        /**
         * 从 JSON 解析
         */
        fun fromJson(json: org.json.JSONObject): Route? {
            return try {
                Route(
                    id = json.getString("id"),
                    name = json.getString("name"),
                    startName = json.getString("startName"),
                    startLat = json.getDouble("startLat"),
                    startLng = json.getDouble("startLng"),
                    endName = json.getString("endName"),
                    endLat = json.getDouble("endLat"),
                    endLng = json.getDouble("endLng"),
                    sharedWith = json.optString("sharedWith", ""),
                    sharedWithName = json.optString("sharedWithName", ""),
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                    isFavorite = json.optBoolean("isFavorite", false)
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
