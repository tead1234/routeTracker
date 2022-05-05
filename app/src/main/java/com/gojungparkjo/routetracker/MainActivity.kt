package com.gojungparkjo.routetracker

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PointF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
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
import com.gojungparkjo.routetracker.model.FeedBackDialog
import com.gojungparkjo.routetracker.model.TTS_Module
import com.gojungparkjo.routetracker.model.crosswalk.CrossWalkResponse
import com.gojungparkjo.routetracker.model.trafficlight.TrafficLightResponse
import com.google.android.gms.location.*
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
import kotlinx.coroutines.tasks.await
import org.locationtech.proj4j.ProjCoordinate
import kotlin.math.atan2


class MainActivity : AppCompatActivity(), SensorEventListener,
    OnMapReadyCallback {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // 네이버지도 fusedLocationSource
    private lateinit var locationSource: FusedLocationSource
    private val repository = RoadRepository()
    private var currentSteps = 0
    private val db = Firebase.firestore
    private lateinit var tts: TTS_Module
    private lateinit var compass: Compass
    private val locationPermissionRequest = registerForActivityResult(
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

    // locationSource 퍼미션 ㅇ더어오기 추후 통합해야할듯??
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (locationSource.onRequestPermissionsResult(
                requestCode, permissions,
                grantResults
            )
        ) {
            if (!locationSource.isActivated) { // //권한 거부됨
                naverMap.locationTrackingMode = LocationTrackingMode.None
            }
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    val stepPermissionRequest = fun() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
            0
        )
        Toast.makeText(this, "걸음수 권환획득.", Toast.LENGTH_SHORT).show()
    }


    private val locationCallback by lazy {
        object : LocationCallback() {
            @SuppressLint("SetTextI18n")
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                for (location in locationResult.locations) {
                    naverMap.let {
                        val coordinate = LatLng(location)

                        it.locationOverlay.isVisible = true
                        it.locationOverlay.position = coordinate

                        if (binding.trackingSwitch.isChecked) it.moveCamera(
                            CameraUpdate.scrollTo(
                                coordinate
                            )
                        )
                    }
                }
            }
        }
    }


    lateinit var binding: ActivityMainBinding

    private lateinit var naverMap: NaverMap

    private var requesting = false

    private var addShapeJob = Job().job

    private lateinit var sensorManager: SensorManager

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            if (it.keySet().contains(REQUESTING_CODE)) {
                requesting = it.getBoolean(REQUESTING_CODE)
            }
        }
        tts = TTS_Module(this)

        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationSource = FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        bindView()
        initMap()
        setupCompass()

    }

    private fun setupCompass() {
        compass = Compass(this)
        compass.setListener(object : Compass.CompassListener {
            override fun onNewAzimuth(azimuth: Float) {
                binding.compassTextView.text = azimuth.toInt().toString()
                paintTrafficSignAndCrosswalkInSight(azimuth.toDouble())
            }
        })
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

    override fun onMapReady(naverMap: NaverMap) {
        naverMap.addOnCameraIdleListener {
            fetchDataWithInBound(naverMap.contentBounds)
            addShapesWithInBound(naverMap.contentBounds)
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


    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            if (event.values[0] == 1.0f) {
                currentSteps++
                binding.trackingSteps.text = currentSteps.toString()
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}

    private var colorJob = Job().job

    private fun paintTrafficSignAndCrosswalkInSight(degree: Double) {
        if (fetchAndMakeJob.isActive) return
        if (this::naverMap.isInitialized) {
            if (colorJob.isActive) return
            colorJob = MainScope().launch {
                naverMap.let { map ->
                    map.locationOverlay.bearing = degree.toFloat()
                    trafficLightMap.forEach { (_, trafficLight) ->
                        val temp = atan2(
                            trafficLight.position.longitude - map.locationOverlay.position.longitude,
                            trafficLight.position.latitude - map.locationOverlay.position.latitude
                        ).toDegree()
                        val diff = temp.toInt() - degree.toInt()
                        val dist = trafficLight.position.distanceTo(map.locationOverlay.position)
                        trafficLight.captionText = "diff: $diff"
                        trafficLight.iconTintColor =
                            if (diff in -20..20 && dist < 10) Color.BLACK else Color.GREEN
                        trafficLight.icon = MarkerIcons.BLACK
                        if (diff in -20..20 && dist < 10 && tts.tts.isSpeaking.not()) {
                            tts.speakOut(trafficLight.tag.toString())
                        }
                    }
                    polygonMap.forEach { (_, polygon) ->
                        var nearest = Double.MAX_VALUE
                        var flag = false
                        polygon.coords.forEach {
                            val temp = it.distanceTo(map.locationOverlay.position)
                            if (temp < nearest && temp < 10) {
                                nearest = temp
                                val temp2 = atan2(
                                    it.longitude - map.locationOverlay.position.longitude,
                                    it.latitude - map.locationOverlay.position.latitude
                                ).toDegree()
                                val diff = temp2.toInt() - degree.toInt()
                                flag = diff in -20..20
                            }
                        }
                        polygon.color = if (flag) Color.RED else Color.WHITE
                    }
                }

            }
        }
    }

    private val polygonMap = HashMap<String, PolygonOverlay>()
    private val trafficLightMap = HashMap<String, Marker>()

    private var fetchAndMakeJob = Job().job

    private fun fetchDataWithInBound(bound: LatLngBounds?) {
        if (naverMap.cameraPosition.zoom < 16) return
        if (fetchAndMakeJob.isActive) fetchAndMakeJob.cancel()
        if (addShapeJob.isActive) addShapeJob.cancel()
        MainScope().launch {
            binding.loadingView.visibility = View.VISIBLE
            fetchAndMakeJob = CoroutineScope(Dispatchers.IO).launch fetch@ {
                if (bound == null) return@fetch
                val crossWalkResponse = repository.getRoadInBound(bound)
                crossWalkResponse?.let { addPolygonFromCrossWalkResponse(it) }
                val trafficLights = repository.getTrafficLightInBound(bound)
                trafficLights?.let { addMarkerFromTrafficLightResponse(trafficLights) }
            }
            fetchAndMakeJob.join()
            addShapesWithInBound(bound)
            binding.loadingView.visibility = View.INVISIBLE
        }
    }

    private suspend fun addPolygonFromCrossWalkResponse(response: CrossWalkResponse) =
        withContext(Dispatchers.Default) {
            response.features?.forEach { feature ->
                if (feature.properties?.vIEWCDE != "002" || feature.properties.eVECDE != "001" || polygonMap.containsKey(
                        feature.properties.mGRNU
                    )
                ) return@forEach
                feature.geometry?.coordinates?.forEach {
                    val list = mutableListOf<LatLng>()
                    it.forEach { point ->
                        list.add(ProjCoordinate(point[0], point[1]).toLatLng())
                    }
                    if (list.size > 2) {
                        polygonMap[feature.properties.mGRNU] = PolygonOverlay().apply {
                            coords = list; color = Color.WHITE
                            outlineWidth = 5; outlineColor = Color.GREEN
                            tag = feature.properties.toString()
                            setOnClickListener {
                                binding.infoTextView.text = it.tag.toString()
                                binding.infoTextView.visibility = View.VISIBLE
                                true
                            }
                        }
                            .also {
                                polygonMap[feature.properties.mGRNU + "X"] =
                                    it.orientedMinimumBoundingBox().apply {
                                        color = Color.TRANSPARENT
                                        outlineColor = Color.RED
                                        outlineWidth = 2
                                        zIndex = 20000
                                        setOnClickListener { it.map = null; true }
                                    }
                            }
                    }
                }
            }
        }


    private suspend fun addMarkerFromTrafficLightResponse(response: TrafficLightResponse) =
        withContext(Dispatchers.Default) {
            response.features?.forEach { feature ->
                if (feature.properties?.vIEWCDE != "002" || feature.properties.eVECDE != "001" || trafficLightMap.containsKey(
                        feature.properties.mGRNU
                    )
                ) return@forEach
                feature.properties.let { property ->
                    if (property.sNLPKNDCDE == "007" && property.xCE != null && property.yCE != null) {
                        feature.geometry?.coordinates?.first {
                            val info =
                                db.collection("trafficSignGuideInfo").document(property.mGRNU).get()
                                    .await()
                            trafficLightMap[feature.properties.mGRNU] =
                                Marker(ProjCoordinate(it[0], it[1]).toLatLng()).apply {
                                    tag = info.get("line") ?: "신호등 정보가 없습니다."
                                }
                            true
                        }
                    }
                }
            }
        }

    private fun addShapesWithInBound(bound: LatLngBounds?) {
        if (bound == null) return
        if (fetchAndMakeJob.isActive) return
        if (addShapeJob.isActive) addShapeJob.cancel()
        if (colorJob.isActive) colorJob.cancel()
        addShapeJob = CoroutineScope(Dispatchers.Main).launch {
            binding.loadingView.visibility = View.VISIBLE

            if (naverMap.cameraPosition.zoom < 16) {
                binding.loadingView.visibility = View.GONE
                return@launch
            }

            polygonMap.forEach { (_, crosswalk) ->
                crosswalk.map = if (bound.contains(crosswalk.bounds)) naverMap else null
            }
            trafficLightMap.forEach { (_, trafficLight) ->
                trafficLight.map = if (bound.contains(trafficLight.position)) naverMap else null
            }

            binding.loadingView.visibility = View.GONE
        }
    }

    private fun checkPermissions() {
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

    private fun stopTracking() {
        requesting = false
        stopLocationUpdates()
    }

    private fun createLocationRequest() = LocationRequest.create().apply {
        interval = 1000
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }


    override fun onResume() {
        super.onResume()
        compass.start()
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.also { stepDetect ->
            sensorManager.registerListener(
                this,
                stepDetect,
                SensorManager.SENSOR_DELAY_FASTEST
            )
        }
    }

    override fun onStart() {
        super.onStart()
        compass.start()
    }

    override fun onPause() {
        super.onPause()
        compass.stop()
        sensorManager.unregisterListener(this)
    }

    override fun onStop() {
        super.onStop()
        compass.stop()
    }


    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(REQUESTING_CODE, requesting)
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val REQUESTING_CODE = "100"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1000
        private const val TAG = "MainActivity"
    }

    // tts part
    override fun onBackPressed() {
        val dig = FeedBackDialog(this)
        dig.show(this)
        tts.speakOut("우리 어플을 평가해주세요")
    }

}




