package com.gojungparkjo.routetracker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gojungparkjo.routetracker.ProjUtil.toLatLng
import com.gojungparkjo.routetracker.data.RoadRepository
import com.gojungparkjo.routetracker.databinding.ActivityMainBinding
import com.gojungparkjo.routetracker.model.*
import com.google.android.gms.location.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.MapFragment
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.Overlay
import com.naver.maps.map.overlay.PolygonOverlay
import kotlinx.coroutines.*
import org.locationtech.proj4j.ProjCoordinate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


class MainActivity : AppCompatActivity(), SensorEventListener,
    OnMapReadyCallback {
    private val TAG = "MainActivity"
    private var tts: TextToSpeech? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val repository = RoadRepository()
    var currentSteps = 0
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
    val stepPermissionRequest = fun (){
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),0)
        Toast.makeText(this, "걸음수 권환획득.", Toast.LENGTH_SHORT).show()
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
                                "속도 ${location.speed}" +
                                // 걸음수 임시로 추가
                                "걸음수 ${currentSteps}"

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

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        val fm = supportFragmentManager
        val mapFragment = fm.findFragmentById(R.id.map) as MapFragment?
            ?: MapFragment.newInstance().also {
                fm.beginTransaction().add(R.id.map, it).commit()
            }
        mapFragment.getMapAsync(this)

    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
        }else if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            if(event.values[0]==1.0f){
                currentSteps ++;
            }
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
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.also { stepDetect ->
            sensorManager.registerListener(
                this,
                stepDetect,
                SensorManager.SENSOR_DELAY_GAME
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
                if (naverMap.cameraPosition.zoom > 16) {
                    val temp = repository.getRoadInBound(bound)
                    removeAllPolygon()
                    temp?.features?.forEach { feature ->
                        if(feature.properties?.vIEWCDE != "002" || feature.properties.eVECDE != "001") return@forEach
                        feature.geometry?.coordinates?.forEach {
                            val list = mutableListOf<LatLng>()
                            it.forEach {
                                list.add(ProjCoordinate(it[0], it[1]).toLatLng())
                            }
                            if (list.size > 2) {
                                polygonList.add(PolygonOverlay().apply {
                                    coords = list; color = Color.WHITE
                                    outlineWidth = 5; outlineColor = Color.GREEN
                                    tag = feature.properties.toString()
                                    setOnClickListener {
                                        binding.infoTextView.text = it.tag.toString()
                                        binding.infoTextView.visibility=View.VISIBLE
                                        Log.d(TAG, "addMarkersWithInBound: ${it.tag.toString()}")
                                        true
                                    }
                                })
                            }
                        }
                    }
                    val trafficLights = repository.getTrafficLightInBound(bound)
                    removeTrafficLightMarker()
                    trafficLights?.features?.forEach {feature ->
                        if(feature.properties?.vIEWCDE != "002" || feature.properties.eVECDE != "001") return@forEach
                        feature.properties.let{
                            if (it.sNLPKNDCDE == "007" && it.xCE !=null && it.yCE!=null) {
                                trafficLightMarkerList.add(Marker(ProjCoordinate(it.xCE,it.yCE).toLatLng()).apply { iconTintColor = Color.RED
                                    tag = it.toString()
                                    onClickListener = Overlay.OnClickListener { binding.infoTextView.text = it.tag.toString(); binding.infoTextView.visibility=View.VISIBLE; true }})
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        polygonList.forEach {
                            it.map = naverMap
                        }
                        trafficLightMarkerList.forEach {
                            it.map = naverMap
                        }
                    }
                }
            }
            binding.loadingView.visibility = View.GONE
        }

    }

    suspend fun removeAllPolygon() = withContext(Dispatchers.Main) {
        polygonList.forEach {
            it.map = null
        }
    }

    suspend fun removeTrafficLightMarker() = withContext(Dispatchers.Main) {
        trafficLightMarkerList.forEach {
            it.map = null
        }
        trafficLightMarkerList.clear()
    }

    val polygonList = mutableListOf<PolygonOverlay>()
    val trafficLightMarkerList = mutableListOf<Marker>()

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
        if(ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_DENIED){
// 요청 문구 수정
            stepPermissionRequest()
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
        }
    override fun onBackPressed() {
        val dig = FeedBackDialog(this)
        dig.show(this)
    }
    }


    //

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


