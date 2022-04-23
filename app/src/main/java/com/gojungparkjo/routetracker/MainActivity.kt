package com.gojungparkjo.routetracker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PointF
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
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gojungparkjo.routetracker.ProjUtil.toLatLng
import com.gojungparkjo.routetracker.data.RoadRepository
import com.gojungparkjo.routetracker.databinding.ActivityMainBinding
import com.gojungparkjo.routetracker.model.FeedBackDialog
import com.gojungparkjo.routetracker.model.crosswalk.CrossWalkResponse
import com.gojungparkjo.routetracker.model.trafficlight.TrafficLightResponse
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.*
import com.naver.maps.map.overlay.LocationOverlay
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PolygonOverlay
import com.naver.maps.map.util.FusedLocationSource
import com.naver.maps.map.util.MarkerIcons
import kotlinx.coroutines.*
import org.locationtech.proj4j.ProjCoordinate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.atanh


class MainActivity : AppCompatActivity(), SensorEventListener,
    OnMapReadyCallback {
    private val TAG = "MainActivity"
    private var tts: TextToSpeech? = null
    private var text = ""
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    // 네이버지도 fusedlocationsourece
    private lateinit var locationSource: FusedLocationSource
    private val repository = RoadRepository()
    private var buttonSpeak: Button? = null
    private var tv: TextView? = null
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
    // locationsource 퍼미션 ㅇ더어오기 추후 통합해야할듯??
    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        if (locationSource.onRequestPermissionsResult(requestCode, permissions,
                grantResults)) {
            if (!locationSource.isActivated) { // /권한 거부됨
                naverMap.locationTrackingMode = LocationTrackingMode.None
            }
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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
                                "속도 ${location.speed}"
                    )
//                    db.collection(android.os.Build.MODEL)
//                        .document(LocalDateTime.now(ZoneId.of("JST")).format(dateTimeFormatter))
//                        .set(mapOf(Pair("lat", location.latitude), Pair("lng", location.longitude)))
//                        .addOnSuccessListener {
//                            Toast.makeText(applicationContext, "위치 정보 기록됨", Toast.LENGTH_SHORT)
//                                .show()
//                        }
                }
                Log.d(TAG, "onLocationResult: ${locationResult.locations.size}")
                for (location in locationResult.locations) {
                    Log.d(
                        TAG, "onLocationResult: " + "위도 ${location.latitude}" +
                                "경도 ${location.longitude}" +
                                "속도 ${location.speed}"
                    )
                    naverMap.let {
                        val coord = LatLng(location)

                        it.locationOverlay.isVisible = true
                        it.locationOverlay.position = coord

                        it.moveCamera(CameraUpdate.scrollTo(coord))
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

    lateinit var cancellationTokenSource: CancellationTokenSource

    private val polygonList = mutableListOf<PolygonOverlay>()
    private val trafficLightMarkerList = mutableListOf<Marker>()

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            if (it.keySet().contains(REQUESTING_CODE)) {
                requesting = it.getBoolean(REQUESTING_CODE)
            }
        }
        // 그지같은놈 초기화
        tts = TextToSpeech(this, TextToSpeech.OnInitListener {
            fun onInit(status: Int) {
                if (status == TextToSpeech.SUCCESS) {
                    // set US English as language for tts
                    val result = tts!!.setLanguage(Locale.US)

                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS","The Language specified is not supported!")
                    } else {
                        buttonSpeak!!.isEnabled = true
                    }

                } else {
                    Log.e("TTS", "Initilization Failed!")
                }
            }
        })


        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        // 로케이션 관련 서비스 객체들
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        bindView()
        initMap()

    }
    private fun bindView() {
        binding.compassTextView.setOnClickListener {
            naverMap.locationOverlay.position = naverMap.cameraPosition.target
        }


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
    }

    private fun initMap() {
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
                binding.trackingSteps.setText(currentSteps.toString())
            }
        }
        updateOrientationAngles()
    }
    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        Log.d(TAG, "onAccuracyChanged: " + p0.toString())
        Log.d(TAG, "onAccuracyChanged: $p1")
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
        val degree = ((Math.toDegrees(orientationAngles[0].toDouble()) + 360) % 360)
        binding.compassTextView.text = degree.toInt().toString()

        if (this::naverMap.isInitialized) {
            naverMap.let { map ->
                map.locationOverlay.bearing = degree.toFloat()
                trafficLightMarkerList.forEach {
                    val temp = atan2(
                        it.position.longitude - map.locationOverlay.position.longitude,
                        it.position.latitude - map.locationOverlay.position.latitude
                    ).toDegree()
                    val diff = temp.toInt() - degree.toInt()
                    it.captionText = "diff: $diff"
                    it.iconTintColor = if (diff in -20..20) Color.BLACK else Color.GREEN
                    it.icon = MarkerIcons.BLACK
                }
                polygonList.forEach { polygon ->
                    var nearest = Double.MAX_VALUE
                    var flag = false
                    polygon.coords.forEach{
                        val temp =it.distanceTo(map.locationOverlay.position)
                        if(temp < nearest && temp < 10){
                            nearest = temp
                            val temp2 = atan2(
                                it.longitude - map.locationOverlay.position.longitude,
                                it.latitude - map.locationOverlay.position.latitude
                            ).toDegree()
                            val diff = temp2.toInt() - degree.toInt()
                            flag = diff in -20..20
                        }
                    }
                    polygon.color = if(flag) Color.RED else Color.WHITE
                }
            }

        }

    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensorManager.registerListener(
                this,
                magneticField,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.also { stepDetect ->
            sensorManager.registerListener(
                this,
                stepDetect,
                SensorManager.SENSOR_DELAY_FASTEST
            )
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onMapReady(naverMap: NaverMap) {

        naverMap.addOnCameraIdleListener {
            addMarkersWithInBound(naverMap.contentBounds)
        }
        naverMap.apply {
            locationOverlay.icon =
                OverlayImage.fromResource(com.naver.maps.map.R.drawable.navermap_default_location_overlay_sub_icon_cone)
            locationOverlay.iconWidth = LocationOverlay.SIZE_AUTO
            locationOverlay.iconHeight = LocationOverlay.SIZE_AUTO
            locationOverlay.anchor = PointF(0.5f, 1f)
        }
        this.naverMap = naverMap

    }

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
                    val response = repository.getRoadInBound(bound)
                    removeAllPolygon()
                    response?.let { addPolygonFromCrossWalkResponse(it) }
                    val trafficLights = repository.getTrafficLightInBound(bound)
                    removeTrafficLightMarker()
                    trafficLights?.let { addMarkerFromTrafficLightResponse(trafficLights) }
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

    fun addPolygonFromCrossWalkResponse(response: CrossWalkResponse) {
        response.features?.forEach { feature ->
            if (feature.properties?.vIEWCDE != "002" || feature.properties.eVECDE != "001") return@forEach
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
                            binding.infoTextView.visibility = View.VISIBLE
                            true
                        }
                    })
                }
            }
        }
    }

    fun addMarkerFromTrafficLightResponse(response: TrafficLightResponse) {
        response.features?.forEach { feature ->
            if (feature.properties?.vIEWCDE != "002" || feature.properties.eVECDE != "001") return@forEach
            feature.properties.let {
                if (it.sNLPKNDCDE == "007" && it.xCE != null && it.yCE != null) {
                    feature.geometry?.coordinates?.first {
                        trafficLightMarkerList.add(
                            Marker(
                                ProjCoordinate(
                                    it[0],
                                    it[1]
                                ).toLatLng()
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun removeAllPolygon() = withContext(Dispatchers.Main) {
        polygonList.forEach {
            it.map = null
        }
        polygonList.clear()
    }

    suspend fun removeTrafficLightMarker() = withContext(Dispatchers.Main) {
        trafficLightMarkerList.forEach {
            it.map = null
        }
        trafficLightMarkerList.clear()
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
    // tts part
    override fun onBackPressed() {
        val dig = FeedBackDialog(this)
        dig.show(this)
        speakOut()
    }
    private fun speakOut() {
        text = "우리어플을 평가해주세요"
        tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, null,"")
    }
}




