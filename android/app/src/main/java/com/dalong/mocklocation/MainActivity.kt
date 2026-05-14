package com.dalong.mocklocation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var etLatitude: EditText
    private lateinit var etLongitude: EditText
    private lateinit var etSearchPlace: EditText
    private lateinit var btnSearch: Button
    private lateinit var btnSetLocation: Button
    private lateinit var btnCurrentLocation: Button
    private lateinit var btnSaveLocation: Button
    private lateinit var btnSavedLocations: Button
    private lateinit var switchMocking: SwitchMaterial
    private lateinit var tvStatus: TextView
    private lateinit var lvSavedLocations: ListView
    private lateinit var tvInstructions: TextView

    private var selectedLat = 30.5728  // 默认：武汉
    private var selectedLng = 104.0668
    private var isMocking = false
    private var marker: Marker? = null
    private var accuracyCircle: Polygon? = null

    private val savedLocations = mutableListOf<SavedLocation>()
    private lateinit var locationAdapter: ArrayAdapter<String>

    data class SavedLocation(
        val name: String,
        val latitude: Double,
        val longitude: Double
    )

    companion object {
        private const val PERMISSION_REQUEST = 100
        private const val OVERLAY_REQUEST = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化OSMDroid配置
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidTileCache = cacheDir
        }
        
        setContentView(R.layout.activity_main)
        initViews()
        checkPermissions()
        loadSavedLocations()
    }

    private fun initViews() {
        mapView = findViewById(R.id.map_view)
        etLatitude = findViewById(R.id.et_latitude)
        etLongitude = findViewById(R.id.et_longitude)
        etSearchPlace = findViewById(R.id.et_search_place)
        btnSearch = findViewById(R.id.btn_search)
        btnSetLocation = findViewById(R.id.btn_set_location)
        btnCurrentLocation = findViewById(R.id.btn_current_location)
        btnSaveLocation = findViewById(R.id.btn_save_location)
        btnSavedLocations = findViewById(R.id.btn_saved_locations)
        switchMocking = findViewById(R.id.switch_mocking)
        tvStatus = findViewById(R.id.tv_status)
        lvSavedLocations = findViewById(R.id.lv_saved_locations)
        tvInstructions = findViewById(R.id.tv_instructions)

        // 设置地图
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(GeoPoint(selectedLat, selectedLng))

        // 地图点击选择位置
        mapView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                val p = mapView.projection.fromPixels(event.x.toInt(), event.y.toInt())
                updateSelectedLocation(p.latitude, p.longitude)
            }
            false
        }

        // 坐标输入
        etLatitude.setText(selectedLat.toString())
        etLongitude.setText(selectedLng.toString())

        // 搜索地点
        btnSearch.setOnClickListener { searchPlace() }

        // 设置虚拟定位
        btnSetLocation.setOnClickListener { applyMockLocation() }

        // 回到当前位置（武汉默认）
        btnCurrentLocation.setOnClickListener {
            updateSelectedLocation(30.5728, 104.0668)
        }

        // 保存位置
        btnSaveLocation.setOnClickListener { saveCurrentLocation() }

        // 查看保存的位置
        btnSavedLocations.setOnClickListener { showSavedLocationsDialog() }

        // 开关虚拟定位
        switchMocking.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkMockSettingAndEnable()
            } else {
                disableMocking()
            }
        }

        // 保存位置列表适配器
        locationAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        lvSavedLocations.adapter = locationAdapter
        lvSavedLocations.setOnItemClickListener { _, _, pos, _ ->
            val loc = savedLocations[pos]
            updateSelectedLocation(loc.latitude, loc.longitude)
            Toast.makeText(this, "已选择: ${loc.name}", Toast.LENGTH_SHORT).show()
        }
        lvSavedLocations.setOnItemLongClickListener { _, _, pos, _ ->
            savedLocations.removeAt(pos)
            saveSavedLocations()
            refreshSavedList()
            true
        }

        updateStatus()
    }

    private fun checkPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            perms.add(Manifest.permission.SYSTEM_ALERT_WINDOW)
        }
        
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST)
        }

        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请在设置中开启悬浮窗权限", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkMockSettingAndEnable() {
        // 检查是否已开启"允许模拟位置"
        val isMockEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ALLOW_MOCK_LOCATION, 0
        ) == 1

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6+ 使用"选择模拟位置应用"
            // Android 6+ 使用模拟位置应用选择
            @Suppress("DEPRECATION")
            val mockEnabled = Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.ALLOW_MOCK_LOCATION, 0
            ) == 1
            
            // 实际上在Android 6+上，需要开发者选项中设置
            // 提示用户去设置
            AlertDialog.Builder(this)
                .setTitle("设置模拟位置")
                .setMessage("请前往：\n设置 → 开发者选项 → 选择模拟位置信息应用\n\n选择「大龙虚拟定位」\n\n如果找不到开发者选项，请前往「关于手机」连续点击版本号7次开启")
                .setPositiveButton("去设置") { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                }
                .setNegativeButton("取消") { _, _ ->
                    switchMocking.isChecked = false
                }
                .show()
        } else {
            // 低版本直接使用
            enableMocking()
        }
    }

    private fun enableMocking() {
        try {
            val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, false, false, false, true, true, true, 0, 5
            )
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            isMocking = true
            applyMockLocation()
            tvStatus.text = "✅ 虚拟定位已启用"
            tvStatus.setTextColor(0xFF4CAF50.toInt())
        } catch (e: Exception) {
            Toast.makeText(this, "启用失败: ${e.message}", Toast.LENGTH_LONG).show()
            switchMocking.isChecked = false
        }
    }

    private fun disableMocking() {
        try {
            val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {}
        isMocking = false
        tvStatus.text = "⏹ 虚拟定位已关闭"
        tvStatus.setTextColor(0xFFF44336.toInt())
    }

    private fun applyMockLocation() {
        val latStr = etLatitude.text.toString()
        val lngStr = etLongitude.text.toString()
        
        if (latStr.isEmpty() || lngStr.isEmpty()) {
            Toast.makeText(this, "请输入经纬度", Toast.LENGTH_SHORT).show()
            return
        }

        val lat = latStr.toDoubleOrNull()
        val lng = lngStr.toDoubleOrNull()
        
        if (lat == null || lng == null || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            Toast.makeText(this, "经纬度格式不正确", Toast.LENGTH_SHORT).show()
            return
        }

        selectedLat = lat
        selectedLng = lng
        updateMapMarker()

        if (isMocking) {
            try {
                val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
                val location = android.location.Location(LocationManager.GPS_PROVIDER)
                location.latitude = lat
                location.longitude = lng
                location.accuracy = 5.0f
                location.time = System.currentTimeMillis()
                location.bearing = 0.0f
                location.speed = 0.0f
                location.altitude = 0.0
                
                locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, location)
                tvStatus.text = "📍 已定位到 ($lat, $lng)"
                Toast.makeText(this, "虚拟定位已更新", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "定位注入失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "请先开启虚拟定位开关", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSelectedLocation(lat: Double, lng: Double) {
        selectedLat = lat
        selectedLng = lng
        etLatitude.setText(String.format("%.6f", lat))
        etLongitude.setText(String.format("%.6f", lng))
        updateMapMarker()
        mapView.controller.animateTo(GeoPoint(lat, lng))
    }

    private fun updateMapMarker() {
        marker?.let { mapView.overlays.remove(it) }
        accuracyCircle?.let { mapView.overlays.remove(it) }

        val point = GeoPoint(selectedLat, selectedLng)
        marker = Marker(mapView).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "目标位置"
            snippet = "${"%.6f".format(selectedLat)}, ${"%.6f".format(selectedLng)}"
            mapView.overlays.add(this)
        }

        // 精度圈
        accuracyCircle = Polygon(mapView).apply {
            val circlePoints = mutableListOf<GeoPoint>()
            val radius = 100.0
            for (i in 0 until 360 step 10) {
                val rad = Math.toRadians(i.toDouble())
                val latOffset = radius / 111320.0
                val lngOffset = radius / (111320.0 * Math.cos(Math.toRadians(selectedLat)))
                circlePoints.add(GeoPoint(
                    selectedLat + latOffset * Math.sin(rad),
                    selectedLng + lngOffset * Math.cos(rad)
                ))
            }
            setPoints(circlePoints)
            fillColor = 0x220000FF.toInt()
            strokeColor = 0xFF0000FF.toInt()
            strokeWidth = 2.0f
            mapView.overlays.add(this)
        }

        mapView.invalidate()
    }

    private fun searchPlace() {
        val query = etSearchPlace.text.toString().trim()
        if (query.isEmpty()) {
            Toast.makeText(this, "请输入地名", Toast.LENGTH_SHORT).show()
            return
        }

        tvStatus.text = "🔍 搜索中..."
        Thread {
            try {
                val url = "https://nominatim.openstreetmap.org/search?format=json&q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=5&accept-language=zh"
                val request = okhttp3.Request.Builder().url(url)
                    .header("User-Agent", "MockLocationApp/1.0")
                    .build()
                val response = okhttp3.OkHttpClient().newCall(request).execute()
                val body = response.body?.string()
                
                if (body != null) {
                    val type = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
                    val results: List<Map<String, Any>> = com.google.gson.Gson().fromJson(body, type)
                    if (results.isEmpty()) {
                        runOnUiThread {
                            tvStatus.text = "❌ 未找到地点"
                            Toast.makeText(this, "未找到地点", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // 显示搜索结果选择对话框
                        val names = results.map { it["display_name"].toString().take(50) + "..." }.toTypedArray()
                        runOnUiThread {
                            AlertDialog.Builder(this)
                                .setTitle("选择地点")
                                .setItems(names) { _, pos ->
                                    val result = results[pos]
                                    val lat = result["lat"].toString().toDouble()
                                    val lng = result["lon"].toString().toDouble()
                                    updateSelectedLocation(lat, lng)
                                    tvStatus.text = "📍 ${result["display_name"].toString().take(30)}..."
                                }
                                .show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = "❌ 搜索失败: ${e.message}"
                }
            }
        }.start()
    }

    private fun saveCurrentLocation() {
        val name = etSearchPlace.text.toString().trim().ifEmpty { 
            "位置${savedLocations.size + 1}" 
        }
        savedLocations.add(SavedLocation(name, selectedLat, selectedLng))
        saveSavedLocations()
        refreshSavedList()
        Toast.makeText(this, "已保存: $name", Toast.LENGTH_SHORT).show()
    }

    private fun showSavedLocationsDialog() {
        if (savedLocations.isEmpty()) {
            Toast.makeText(this, "暂无保存的位置", Toast.LENGTH_SHORT).show()
            return
        }
        val names = savedLocations.map { "${it.name} (${"%.4f".format(it.latitude)}, ${"%.4f".format(it.longitude)})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("已保存的位置")
            .setItems(names) { _, pos ->
                val loc = savedLocations[pos]
                updateSelectedLocation(loc.latitude, loc.longitude)
            }
            .setNeutralButton("删除") { _, _ ->
                // 长按已可删除，这里留空
            }
            .show()
    }

    private fun refreshSavedList() {
        locationAdapter.clear()
        locationAdapter.addAll(savedLocations.map { "${it.name} (${"%.4f".format(it.latitude)}, ${"%.4f".format(it.longitude)})" })
        locationAdapter.notifyDataSetChanged()
    }

    private fun saveSavedLocations() {
        val prefs = getSharedPreferences("saved_locations", MODE_PRIVATE)
        val editor = prefs.edit()
        val gson = com.google.gson.Gson()
        editor.putString("locations", gson.toJson(savedLocations))
        editor.apply()
    }

    private fun loadSavedLocations() {
        val prefs = getSharedPreferences("saved_locations", MODE_PRIVATE)
        val json = prefs.getString("locations", "[]") ?: "[]"
        val type = object : com.google.gson.reflect.TypeToken<List<SavedLocation>>() {}.type
        savedLocations.clear()
        savedLocations.addAll(com.google.gson.Gson().fromJson(json, type))
        refreshSavedList()
    }

    private fun updateStatus() {
        tvStatus.text = "就绪 - 长按地图选择位置"
        tvStatus.setTextColor(0xFF2196F3.toInt())
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        disableMocking()
        super.onDestroy()
    }
}
