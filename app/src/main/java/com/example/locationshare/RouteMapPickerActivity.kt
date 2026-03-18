package com.example.locationshare

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.example.locationshare.databinding.ActivityRouteMapPickerBinding

class RouteMapPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRouteMapPickerBinding
    private var mapView: MapView? = null
    private var aMap: AMap? = null
    private var selectedLatLng: LatLng? = null
    private var selectedAddress: String = ""

    companion object {
        const val EXTRA_TYPE = "type" // "start" or "end"
        const val RESULT_ADDRESS = "address"
        const val RESULT_LAT = "lat"
        const val RESULT_LNG = "lng"

        fun start(activity: Activity, type: String, requestCode: Int) {
            val intent = Intent(activity, RouteMapPickerActivity::class.java)
            intent.putExtra(EXTRA_TYPE, type)
            activity.startActivityForResult(intent, requestCode)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRouteMapPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val type = intent.getStringExtra(EXTRA_TYPE) ?: "start"
        binding.tvTitle.text = if (type == "start") "选择起点" else "选择终点"

        // 隐私合规
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)

        mapView = binding.mapView
        mapView?.onCreate(savedInstanceState)

        initMap()
        initViews()
    }

    private fun initMap() {
        aMap = mapView?.map
        aMap?.apply {
            uiSettings.isMyLocationButtonEnabled = true
            uiSettings.isZoomControlsEnabled = true
            moveCamera(CameraUpdateFactory.zoomTo(15f))

            // 定位到当前位置
            myLocationStyle = com.amap.api.maps.model.MyLocationStyle().apply {
                myLocationType(com.amap.api.maps.model.MyLocationStyle.LOCATION_TYPE_LOCATE)
            }
            isMyLocationEnabled = true

            // 地图点击事件
            setOnMapClickListener { latLng ->
                selectedLatLng = latLng
                addMarker(latLng)
                // 反向地理编码获取地址
                reverseGeocode(latLng)
            }
        }
    }

    private fun addMarker(latLng: LatLng) {
        aMap?.clear()
        aMap?.addMarker(MarkerOptions()
            .position(latLng)
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )
    }

    private fun reverseGeocode(latLng: LatLng) {
        // 使用高德逆地理编码获取地址名称
        val geocoderSearch = com.amap.api.services.geocoder.GeocodeSearch(this)
        geocoderSearch.setOnGeocodeSearchListener(object : com.amap.api.services.geocoder.GeocodeSearch.OnGeocodeSearchListener {
            override fun onRegeocodeSearched(result: com.amap.api.services.geocoder.RegeocodeResult?, rCode: Int) {
                if (rCode == 1000 && result?.regeocodeAddress != null) {
                    val address = result.regeocodeAddress.formatAddress
                    selectedAddress = address
                    binding.tvSelectedAddress.text = address
                    binding.tvSelectedAddress.visibility = android.view.View.VISIBLE
                }
            }

            override fun onGeocodeSearched(result: com.amap.api.services.geocoder.GeocodeResult?, rCode: Int) {}
        })

        val query = com.amap.api.services.geocoder.RegeocodeQuery(
            com.amap.api.services.core.LatLonPoint(latLng.latitude, latLng.longitude),
            200f,
            com.amap.api.services.geocoder.GeocodeSearch.AMAP
        )
        geocoderSearch.getFromLocationAsyn(query)
    }

    private fun initViews() {
        binding.btnBack.setOnClickListener { safeFinish() }

        binding.btnConfirm.setOnClickListener {
            if (selectedLatLng == null) {
                Toast.makeText(this, "请先在地图上选择位置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent()
            intent.putExtra(RESULT_ADDRESS, selectedAddress)
            intent.putExtra(RESULT_LAT, selectedLatLng!!.latitude)
            intent.putExtra(RESULT_LNG, selectedLatLng!!.longitude)
            setResult(RESULT_OK, intent)
            safeFinish()
        }
    }

    private fun safeFinish() {
        // 先禁用地图功能，避免销毁时崩溃
        aMap?.isMyLocationEnabled = false
        finish()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
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
        mapView?.onDestroy()
        super.onDestroy()
    }
}
