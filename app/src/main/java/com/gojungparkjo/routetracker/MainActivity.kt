package com.gojungparkjo.routetracker

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gojungparkjo.routetracker.ProjUtil.toLatLng
import com.gojungparkjo.routetracker.data.RoadRepository
import com.gojungparkjo.routetracker.databinding.ActivityMainBinding
import com.gojungparkjo.routetracker.model.*
import com.google.android.gms.location.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.MapFragment
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.PolygonOverlay
import kotlinx.coroutines.*
import org.locationtech.proj4j.ProjCoordinate
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*


class MainActivity : AppCompatActivity(), SensorEventListener,
    OnMapReadyCallback {
    private val TAG = "MainActivity"
    private var tts: TextToSpeech? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    val repository = RoadRepository()

    val db = Firebase.firestore
    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyMMdd-hhmmss.S")

    val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted.
                startTracking(fusedLocationClient)
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Only approximate location access granted.
                Toast.makeText(this, "정밀 권한을 주세요", Toast.LENGTH_SHORT).show()
            }
            else -> {
                // No location access granted.
                Toast.makeText(this, "위치 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val locationCallback by lazy {
        object : LocationCallback() {
            @SuppressLint("SetTextI18n")
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                for (location in locationResult.locations) {
                    binding.textView.setText(
                        "위도 ${location.latitude}" +
                                "경도 ${location.longitude}" +
                                "속도 ${location.speed}"
                    )
                    db.collection(android.os.Build.MODEL)
                        .document(LocalDateTime.now(ZoneId.of("JST")).format(dateTimeFormatter))
                        .set(mapOf(Pair("lat", location.latitude), Pair("lng", location.longitude)))
                        .addOnSuccessListener {
                            Toast.makeText(applicationContext, "위치 정보 기록됨", Toast.LENGTH_SHORT)
                                .show()
                        }
                }
            }
        }
    }

    lateinit var binding: ActivityMainBinding

    private lateinit var naverMap: NaverMap

    var mapPointList: List<TrafficSign>? = null

//    var nearestSign: MapPOIItem? = null

    private var requesting = false

    var job: Job? = null

    lateinit var sensorManager: SensorManager
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            if (it.keySet().contains(REQUESTING_CODE)) {
                requesting = it.getBoolean(REQUESTING_CODE)
            }
        }
        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        binding.trackingButton.setOnClickListener {
            if (requesting) {
                stopTracking()
                it.setBackgroundColor(ContextCompat.getColor(this, R.color.purple_200))
            } else {
                checkPermissions()
            }
        }
        binding.infoTextView.setOnClickListener {
            it.visibility = View.GONE
        }
        binding.numberPicker.apply {
            minValue = 0
            maxValue = 22
            setOnValueChangedListener { _, _, _ ->
                addMarkersWithInBound(naverMap.contentBounds)
            }
        }
        binding.filterSwitch.setOnCheckedChangeListener { _, _ ->
            addMarkersWithInBound(naverMap.contentBounds)
        }

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        val fm = supportFragmentManager
        val mapFragment = fm.findFragmentById(R.id.map) as MapFragment?
            ?: MapFragment.newInstance().also {
                fm.beginTransaction().add(R.id.map, it).commit()
            }
        mapFragment.getMapAsync(this)


        readAsset()

    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
        }
        updateOrientationAngles()
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }

    fun updateOrientationAngles() {
        // Update rotation matrix, which is needed to update orientation angles.
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )

        // "mRotationMatrix" now has up-to-date information.

        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        // "mOrientationAngles" now has up-to-date information.
        val degree = ((Math.toDegrees(orientationAngles[0].toDouble()) + 360) % 360).toInt()
        binding.compassTextView.text = degree.toString()
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensorManager.registerListener(
                this,
                magneticField,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onMapReady(naverMap: NaverMap) {
        this.naverMap = naverMap
        naverMap.addOnCameraIdleListener {
            addMarkersWithInBound(naverMap.contentBounds)
        }

    }

//    override fun onPOIItemSelected(p0: MapView?, p1: MapPOIItem?) {
//        binding.infoTextView.text = p1?.itemName ?: return
//        binding.infoTextView.visibility = View.VISIBLE
//    }

//    override fun onCurrentLocationUpdate(p0: MapView?, p1: MapPoint?, p2: Float) {
//        p1?.let {
//            CoroutineScope(Dispatchers.Main).launch {
//                mapPointList?.nearestSign(p1)?.let {
//                    nearestSign?.let {
//                        mapView.removePOIItem(it)
//                    }
//                    nearestSign = MapPOIItem().apply {
//                        itemName = "near"
//                        markerType = MapPOIItem.MarkerType.BluePin
//                        mapPoint = it.coordinate
//                    }.also {
//                        mapView.addPOIItem(it)
//                    }
//                    binding.nearTextView.text =
//                        "가장 가까운 신호등 lat: ${p1.mapPointGeoCoord.latitude} lng ${p1.mapPointGeoCoord.longitude}"
//
//                }
//            }
//        }
//    }

    fun addMarkersWithInBound(bound: LatLngBounds?) {
        job?.let {
            if (it.isActive) it.cancel()
        }
        job = CoroutineScope(Dispatchers.Main).launch {
            binding.loadingView.visibility = View.VISIBLE
            withContext(Dispatchers.Default) {
                Log.d(TAG, "addMarkersWithInBound")
                if (bound == null) return@withContext
                val selectedType = binding.numberPicker.value
                removeAllMarker()
                mapPointList?.forEach {
                    if (bound.contains(it.marker.position) && (!binding.filterSwitch.isChecked || it.signalType == selectedType)) {
                        withContext(Dispatchers.Main) {
                            it.marker.map = naverMap
                        }
                    }
                }
                if (naverMap.cameraPosition.zoom > 16) {
                    val temp = repository.getRoadInBound(bound)
                    removeAllPolygon()
                    temp?.features?.forEach {
                        it.geometry?.coordinates?.forEach {
                            val list = mutableListOf<LatLng>()
                            it.forEach {
                                Log.d(
                                    TAG,
                                    "addMarkersWithInBound: ${
                                        ProjCoordinate(
                                            it[0],
                                            it[1]
                                        ).toLatLng()
                                    }"
                                )
                                list.add(ProjCoordinate(it[0], it[1]).toLatLng())
                            }
                            if (list.size > 2) {
                                polygonList.add(PolygonOverlay().apply {
                                    coords = list; color = Color.WHITE
                                    outlineWidth = 5; outlineColor = Color.GREEN
                                })
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        polygonList.forEach {
                            Log.d(TAG, "addMarkersWithInBound: Polygon foreach")
                            it.map = naverMap
                        }
                    }
                }
            }
            binding.loadingView.visibility = View.GONE
        }

    }

    suspend fun removeAllMarker() = withContext(Dispatchers.Main) {
        mapPointList?.forEach {
            it.marker.map = null
        }
    }

    suspend fun removeAllPolygon() = withContext(Dispatchers.Main) {
        polygonList.forEach {
            it.map = null
        }
    }

    val polygonList = mutableListOf<PolygonOverlay>()

    fun readAsset() = CoroutineScope(Dispatchers.IO).launch {
        val br = BufferedReader(InputStreamReader(assets.open("df1.csv")))
        var line: String? = br.readLine()
        val list = LinkedList<TrafficSign>()
        while (br.readLine().also { line = it } != null) {
            line!!.split(",").also {
                list.add(
                    TrafficSign(
                        it[1],
                        it[2],
                        it[3],
                        it[4],
                        it[5],
                        it[6],
                        it[7],
                        it[8],
                        it[9].toDouble().toInt(),
                        it[10],
                        it[11],
                        it[12],
                        it[13],
                        it[14],
                        it[15],
                        it[16],
                        it[17],
                        it[18],
                        it[19],
                        it[20],
                        it[21],
                        it[22],
                        it[23].toDouble(),
                        it[24].toDouble(),
                        it[25],
                        Marker(LatLng(it[27].toDouble(), it[26].toDouble()))
                    ).also { trafficSign ->
                        trafficSign.marker.setOnClickListener {
                            binding.infoTextView.text = trafficSign.toString()
                            binding.infoTextView.visibility = View.VISIBLE
                            true
                        }
                    }
                )
            }
        }
        val temp = Gson().fromJson(json, TrafficSafetyResponse::class.java)
        Log.d(TAG, "readAsset: " + temp.toString())
        temp?.features?.forEach {
            it.geometry?.coordinates?.forEach {
                if (it.size > 2) {
                    val list = mutableListOf<LatLng>()
                    it.forEach {
                        list.add(LatLng(it[0], it[1]))
                    }
                    polygonList.add(PolygonOverlay().apply { coords = list })
                }
            }
        }
        withContext(Dispatchers.Main) {
            polygonList.forEach {
                it.map = naverMap
            }
        }
        Log.d(TAG, "readAsset: loaded")
        mapPointList = list
    }


    fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startTracking(fusedLocationClient)
        } else {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking(fusedLocationProviderClient: FusedLocationProviderClient) {
        binding.trackingButton.setBackgroundColor(Color.RED)
        requesting = true
        fusedLocationProviderClient.requestLocationUpdates(
            createLocationRequest(),
            locationCallback,
            mainLooper
        )
    }

    fun stopTracking() {
        requesting = false
        stopLocationUpdates()
    }

    fun createLocationRequest() = LocationRequest.create().apply {
        interval = 1000
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }


    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(REQUESTING_CODE, requesting)
        super.onSaveInstanceState(outState)
    }

    companion object {
        val REQUESTING_CODE = "100"
        val json =
            "{\"type\":\"FeatureCollection\",\"totalFeatures\":5,\"features\":[{\"type\":\"Feature\",\"id\":\"A004_A.fid-3bd876ce_18012dc9851_5eb6\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[197862.96856089,552012.63273323],[197857.67889288,552007.06434112],[197861.11297481,552003.81610001],[197866.21685151,552009.2920792],[197862.96856089,552012.63273323]]]},\"geometry_name\":\"XGEO\",\"properties\":{\"MGRNU\":\"06-0000025948\",\"STAT_CDE\":\"001\",\"A004_KND_CDE\":\"002\",\"HOL\":0,\"VEL\":0,\"AW_SN_QUA\":0,\"AW_SN_LENX_CDE\":\"001\",\"EVE_CDE\":\"001\",\"OD_PE_CDE\":\"130\",\"GU_CDE\":\"140\",\"DONG_CDE\":\"16700\",\"JIBUN\":\"3-8도\",\"NW_PE_CDE\":\"130\",\"WORK_CDE\":\"001\",\"VIEW_CDE\":\"002\",\"ROD_GBN_CDE\":\"002\",\"TFC_BSS_CDE\":\"108\",\"SIXID\":null,\"ESB_YMD\":null,\"CAE_YMD\":null,\"HISID\":23316,\"CTK_MGRNU\":\"2005-0108-166\",\"CRS_MGRNU\":\"06-025948\",\"FRM_CDE\":\"005\",\"CSS_CDE\":null,\"RN_CDE\":null,\"MNG_AGEN\":null}},{\"type\":\"Feature\",\"id\":\"A004_A.fid-3bd876ce_18012dc9851_5eb7\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[197711.94408936,552102.44687716],[197714.88172106,552106.66249563],[197709.95039139,552109.8601609],[197707.29210268,552105.66083167],[197711.94408936,552102.44687716]]]},\"geometry_name\":\"XGEO\",\"properties\":{\"MGRNU\":\"06-0000002177\",\"STAT_CDE\":\"001\",\"A004_KND_CDE\":\"001\",\"HOL\":15,\"VEL\":8,\"AW_SN_QUA\":2,\"AW_SN_LENX_CDE\":\"002\",\"EVE_CDE\":\"001\",\"OD_PE_CDE\":\"130\",\"GU_CDE\":\"140\",\"DONG_CDE\":\"16700\",\"JIBUN\":\"1-7도\",\"NW_PE_CDE\":\"130\",\"WORK_CDE\":\"001\",\"VIEW_CDE\":\"002\",\"ROD_GBN_CDE\":\"002\",\"TFC_BSS_CDE\":\"108\",\"SIXID\":null,\"ESB_YMD\":null,\"CAE_YMD\":null,\"HISID\":79342,\"CTK_MGRNU\":\"2010-0108-076\",\"CRS_MGRNU\":\"06-002177\",\"FRM_CDE\":\"005\",\"CSS_CDE\":null,\"RN_CDE\":null,\"MNG_AGEN\":null}},{\"type\":\"Feature\",\"id\":\"A004_A.fid-3bd876ce_18012dc9851_5eb8\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[197711.94408936,552102.44687716],[197714.88172106,552106.66249563],[197709.95039139,552109.8601609],[197707.29210268,552105.66083167],[197711.94408936,552102.44687716]]]},\"geometry_name\":\"XGEO\",\"properties\":{\"MGRNU\":\"06-0000002177\",\"STAT_CDE\":\"001\",\"A004_KND_CDE\":\"001\",\"HOL\":15,\"VEL\":8,\"AW_SN_QUA\":2,\"AW_SN_LENX_CDE\":\"002\",\"EVE_CDE\":\"001\",\"OD_PE_CDE\":\"130\",\"GU_CDE\":\"140\",\"DONG_CDE\":\"16700\",\"JIBUN\":\"1-7도\",\"NW_PE_CDE\":\"130\",\"WORK_CDE\":\"004\",\"VIEW_CDE\":\"001\",\"ROD_GBN_CDE\":\"002\",\"TFC_BSS_CDE\":\"108\",\"SIXID\":null,\"ESB_YMD\":null,\"CAE_YMD\":null,\"HISID\":47963,\"CTK_MGRNU\":\"2010-0108-076\",\"CRS_MGRNU\":\"06-002177\",\"FRM_CDE\":\"005\",\"CSS_CDE\":null,\"RN_CDE\":null,\"MNG_AGEN\":null}},{\"type\":\"Feature\",\"id\":\"A004_A.fid-3bd876ce_18012dc9851_5eb9\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[197712.10784812,552102.57875062],[197714.88172106,552106.66249563],[197709.95039139,552109.8601609],[197707.29210268,552105.66083167],[197712.10784812,552102.57875062]]]},\"geometry_name\":\"XGEO\",\"properties\":{\"MGRNU\":\"06-0000002177\",\"STAT_CDE\":\"001\",\"A004_KND_CDE\":\"001\",\"HOL\":15,\"VEL\":8,\"AW_SN_QUA\":2,\"AW_SN_LENX_CDE\":\"002\",\"EVE_CDE\":\"001\",\"OD_PE_CDE\":\"130\",\"GU_CDE\":\"140\",\"DONG_CDE\":\"16700\",\"JIBUN\":\"1-7도\",\"NW_PE_CDE\":\"130\",\"WORK_CDE\":\"004\",\"VIEW_CDE\":\"001\",\"ROD_GBN_CDE\":\"002\",\"TFC_BSS_CDE\":\"108\",\"SIXID\":null,\"ESB_YMD\":null,\"CAE_YMD\":null,\"HISID\":47885,\"CTK_MGRNU\":\"2010-0108-076\",\"CRS_MGRNU\":\"06-002177\",\"FRM_CDE\":\"005\",\"CSS_CDE\":null,\"RN_CDE\":null,\"MNG_AGEN\":null}},{\"type\":\"Feature\",\"id\":\"A004_A.fid-3bd876ce_18012dc9851_5eba\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[197711.24885109,552103.16565807],[197713.35793524,552106.13463046],[197708.27990346,552109.07198985],[197706.65508975,552106.32175857],[197711.24885109,552103.16565807]]]},\"geometry_name\":\"XGEO\",\"properties\":{\"MGRNU\":\"06-0000002177\",\"STAT_CDE\":\"001\",\"A004_KND_CDE\":\"001\",\"HOL\":15,\"VEL\":8,\"AW_SN_QUA\":2,\"AW_SN_LENX_CDE\":\"002\",\"EVE_CDE\":\"001\",\"OD_PE_CDE\":\"130\",\"GU_CDE\":\"140\",\"DONG_CDE\":\"16700\",\"JIBUN\":\"1-7도\",\"NW_PE_CDE\":\"130\",\"WORK_CDE\":\"004\",\"VIEW_CDE\":\"001\",\"ROD_GBN_CDE\":\"002\",\"TFC_BSS_CDE\":\"108\",\"SIXID\":null,\"ESB_YMD\":null,\"CAE_YMD\":null,\"HISID\":8295,\"CTK_MGRNU\":\"2010-0108-076\",\"CRS_MGRNU\":\"06-002177\",\"FRM_CDE\":\"005\",\"CSS_CDE\":null,\"RN_CDE\":null,\"MNG_AGEN\":null}}],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"urn:ogc:def:crs:EPSG::5186\"}}}"
    }


    //
    override fun onBackPressed() {
//        val builder = AlertDialog.Builder(this)
//        initTextToSpeech()
//        ttsSpeak("우리어플평가")
        val dig = FeedBackDialog(this)
        dig.show(this)
    }
//        builder.setTitle("종료 알림")    // 제목
//        builder.setMessage("우리어플을평가해주세요")    // 내용
//        ttsSpeak("어플평가해줘")
//        // 긍정 버튼 추가
//        builder.setPositiveButton("긍정") { dialog, which ->
//        }
//        // 부정 버튼 추가
//        builder.setNegativeButton("부정") { dialog, which ->
//            ActivityCompat.finishAffinity(this)
//        }
//        // 중립 버튼 추가
//        builder.setNeutralButton("읽어") { dialog, which ->
//
//
//        }
//        // 뒤로 가기 or 바깥 부분 터치
//        builder.setOnCancelListener {
//        }
//
//        builder.show()
//    }
//    private fun makeDialog(){
//        AlertDialog(
//            onDismissRequest = {}
//            title = {
//
//            }
//        )
//    }


//    private fun ttsSpeak(strTTS: String) {
//            tts?.speak(strTTS, TextToSpeech.QUEUE_ADD, null, null)
//        }
//    }

//        fun initTextToSpeech() {
//            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
//                Toast.makeText(this, "버전이 너무 낮아", Toast.LENGTH_SHORT).show()
//                return
//            }
//            tts = TextToSpeech(this) {
//                if (it == TextToSpeech.SUCCESS) {
//                    val result = tts?.setLanguage(Locale.ENGLISH)
//                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
//                        Toast.makeText(this, "언어지원 X", Toast.LENGTH_SHORT).show()
//                        return@TextToSpeech
//                    }
//                    Toast.makeText(this, "성공", Toast.LENGTH_SHORT).show()
//                } else {
//                    Toast.makeText(this, "망함", Toast.LENGTH_SHORT).show()
//                }
//
//            }
//        }
//
//        fun ttsSpeak(strTTS: String) {
//            tts?.speak(strTTS, TextToSpeech.QUEUE_ADD, null, null)
//        }



    fun AssetManager.readAssetsFile(fileName: String): String =
        open(fileName).bufferedReader().use { it.readText() } }

