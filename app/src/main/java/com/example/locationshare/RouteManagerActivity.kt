package com.example.locationshare

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.locationshare.databinding.ActivityRouteManagerBinding
import com.example.locationshare.databinding.DialogAddRouteBinding
import com.example.locationshare.model.Route
import com.example.locationshare.utils.PrefsManager
import org.json.JSONObject
import java.util.*

class RouteManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRouteManagerBinding
    private lateinit var prefsManager: PrefsManager
    private lateinit var routeAdapter: RouteAdapter

    private var routes: MutableList<Route> = mutableListOf()

    // 你的高德 Web JS API Key
    private val AMAP_WEB_KEY = "99d33a7ce806060acfffa9a80ae613bc"
    private val AMAP_SECURITY_KEY = "2e4a9748ed8767c2402ee4f26009ccfc"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRouteManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PrefsManager(this)

        initViews()
        loadRoutes()
    }

    private fun initViews() {
        routeAdapter = RouteAdapter(
            onStartClick = { route ->
                startRouteTrip(route)
            },
            onDeleteClick = { route ->
                showDeleteConfirm(route)
            },
            onFavoriteClick = { route ->
                toggleFavorite(route)
            }
        )

        binding.recyclerRoutes.layoutManager = LinearLayoutManager(this)
        binding.recyclerRoutes.adapter = routeAdapter

        binding.btnAddRoute.setOnClickListener {
            showAddRouteDialog()
        }
    }

    private fun loadRoutes() {
        routes = prefsManager.getRoutes().toMutableList()
        routeAdapter.submitList(routes)
    }

    // 用于存储 WebView 选择的地址
    private var pendingStart: AddressResult? = null
    private var pendingEnd: AddressResult? = null

    @SuppressLint("SetJavaScriptEnabled")
    private fun showAddRouteDialog() {
        val dialogBinding = DialogAddRouteBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
            .setView(dialogBinding.root)
            .create()

        pendingStart = null
        pendingEnd = null

        // 设置 WebView
        val webView = dialogBinding.webViewAddressPicker
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true

        // 启用 WebView 调试
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // 添加 WebChromeClient 用于调试
        webView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                android.util.Log.d("WebViewJS", "${message.message()} -- From line ${message.lineNumber()} of ${message.sourceId()}")
                return true
            }
        }

        // 添加 WebViewClient 监听加载状态
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                android.util.Log.d("WebView", "页面加载完成: $url")
            }

            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                super.onReceivedError(view, request, error)
                android.util.Log.e("WebView", "加载错误: $error")
            }
        }

        // JavaScript 接口
        webView.addJavascriptInterface(AddressPickerBridge { start, end ->
            pendingStart = start
            pendingEnd = end
            runOnUiThread {
                Toast.makeText(this, "已选择: ${start.name} → ${end.name}", Toast.LENGTH_SHORT).show()
            }
        }, "AndroidBridge")

        // 加载 HTML 并注入 API Key 和安全密钥
        try {
            var html = assets.open("address_picker.html").bufferedReader().use { it.readText() }
            html = html.replace("__API_KEY__", AMAP_WEB_KEY)
            html = html.replace("__SECURITY_KEY__", AMAP_SECURITY_KEY)
            webView.loadDataWithBaseURL("https://webapi.amap.com", html, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            Toast.makeText(this, "加载地图失败", Toast.LENGTH_SHORT).show()
            return
        }

        // 加载好友列表
        val friends = prefsManager.getFriends()
        val spinnerItems = listOf("选择共享对象") + friends.map { it.friendName }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, spinnerItems)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerSharedWith.adapter = spinnerAdapter

        var selectedFriendId = ""
        dialogBinding.spinnerSharedWith.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedFriendId = if (position > 0) friends[position - 1].friendId else ""
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 创建保存按钮的 Dialog
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "保存路线") { _, _ ->
            val routeName = dialogBinding.etRouteName.text.toString().trim()
            if (routeName.isEmpty()) {
                Toast.makeText(this, "请输入路线名称", Toast.LENGTH_SHORT).show()
                return@setButton
            }
            if (pendingStart == null || pendingEnd == null) {
                Toast.makeText(this, "请在地图中选择起点和终点", Toast.LENGTH_SHORT).show()
                return@setButton
            }
            if (selectedFriendId.isEmpty()) {
                Toast.makeText(this, "请选择共享对象", Toast.LENGTH_SHORT).show()
                return@setButton
            }

            val newRoute = Route(
                id = UUID.randomUUID().toString(),
                name = routeName,
                startName = pendingStart!!.name,
                startLat = pendingStart!!.lat,
                startLng = pendingStart!!.lng,
                endName = pendingEnd!!.name,
                endLat = pendingEnd!!.lat,
                endLng = pendingEnd!!.lng,
                sharedWith = selectedFriendId,
                sharedWithName = friends.find { it.friendId == selectedFriendId }?.friendName ?: "",
                isFavorite = true
            )

            prefsManager.addRoute(newRoute)
            loadRoutes()
            Toast.makeText(this, "路线保存成功", Toast.LENGTH_SHORT).show()
        }

        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "取消") { _, _ -> }

        dialog.show()

        // 设置 Dialog 大小
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.8).toInt()
        )
    }


    private fun startRouteTrip(route: Route) {
        Toast.makeText(this, "开始路线：${route.name}", Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteConfirm(route: Route) {
        AlertDialog.Builder(this)
            .setTitle("删除路线")
            .setMessage("确定要删除路线 \"${route.name}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                prefsManager.deleteRoute(route.id)
                loadRoutes()
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun toggleFavorite(route: Route) {
        val updatedRoute = route.copy(isFavorite = !route.isFavorite)
        prefsManager.updateRoute(updatedRoute)
        loadRoutes()
    }

    data class AddressResult(
        val name: String,
        val lat: Double,
        val lng: Double
    )

    // WebView JS 接口类
    inner class AddressPickerBridge(private val onSelected: (AddressResult, AddressResult) -> Unit) {
        @android.webkit.JavascriptInterface
        fun onAddressSelected(resultJson: String) {
            try {
                val json = JSONObject(resultJson)
                val startJson = json.getJSONObject("start")
                val endJson = json.getJSONObject("end")

                val start = AddressResult(
                    name = startJson.getString("name"),
                    lat = startJson.getDouble("lat"),
                    lng = startJson.getDouble("lng")
                )
                val end = AddressResult(
                    name = endJson.getString("name"),
                    lat = endJson.getDouble("lat"),
                    lng = endJson.getDouble("lng")
                )
                onSelected(start, end)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @android.webkit.JavascriptInterface
        fun onPointSelected(resultJson: String) {
            // 单个点选择时的实时回调
            android.util.Log.d("WebViewBridge", "onPointSelected called: $resultJson")
            try {
                val json = JSONObject(resultJson)
                val isStart = json.getBoolean("isStart")
                val pointJson = json.getJSONObject("point")

                val point = AddressResult(
                    name = pointJson.getString("name"),
                    lat = pointJson.getDouble("lat"),
                    lng = pointJson.getDouble("lng")
                )

                if (isStart) {
                    pendingStart = point
                    android.util.Log.d("WebViewBridge", "pendingStart set: ${point.name}")
                } else {
                    pendingEnd = point
                    android.util.Log.d("WebViewBridge", "pendingEnd set: ${point.name}")
                }
            } catch (e: Exception) {
                android.util.Log.e("WebViewBridge", "Error in onPointSelected: ${e.message}")
                e.printStackTrace()
            }
        }

        @android.webkit.JavascriptInterface
        fun onCancel() {
            // 不需要处理
        }
    }

    inner class RouteAdapter(
        private val onStartClick: (Route) -> Unit,
        private val onDeleteClick: (Route) -> Unit,
        private val onFavoriteClick: (Route) -> Unit
    ) : RecyclerView.Adapter<RouteAdapter.RouteViewHolder>() {

        private var routes: List<Route> = emptyList()

        fun submitList(newRoutes: List<Route>) {
            routes = newRoutes
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_route, parent, false)
            return RouteViewHolder(view)
        }

        override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
            holder.bind(routes[position])
        }

        override fun getItemCount(): Int = routes.size

        inner class RouteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvRouteName: TextView = itemView.findViewById(R.id.tvRouteName)
            private val ivFavorite: ImageView = itemView.findViewById(R.id.ivFavorite)
            private val tvStartLocation: TextView = itemView.findViewById(R.id.tvStartLocation)
            private val tvEndLocation: TextView = itemView.findViewById(R.id.tvEndLocation)
            private val tvSharedWith: TextView = itemView.findViewById(R.id.tvSharedWith)
            private val btnStartTrip: Button = itemView.findViewById(R.id.btnStartTrip)
            private val btnDeleteRoute: ImageButton = itemView.findViewById(R.id.btnDeleteRoute)

            fun bind(route: Route) {
                tvRouteName.text = route.name
                tvStartLocation.text = route.startName
                tvEndLocation.text = route.endName
                tvSharedWith.text = "共享给：${route.sharedWithName}"

                ivFavorite.setImageResource(
                    if (route.isFavorite) android.R.drawable.btn_star_big_on
                    else android.R.drawable.btn_star_big_off
                )

                ivFavorite.setOnClickListener {
                    onFavoriteClick(route)
                }

                btnStartTrip.setOnClickListener {
                    onStartClick(route)
                }

                btnDeleteRoute.setOnClickListener {
                    onDeleteClick(route)
                }
            }
        }
    }


}