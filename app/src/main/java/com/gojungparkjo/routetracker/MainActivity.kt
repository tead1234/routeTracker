package com.gojungparkjo.routetracker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PointF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gojungparkjo.routetracker.ProjUtil.toLatLng
import com.gojungparkjo.routetracker.data.RoadRepository
import com.gojungparkjo.routetracker.databinding.ActivityMainBinding
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
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.atanh


class MainActivity : AppCompatActivity(), SensorEventListener,
    OnMapReadyCallback {

    private val TAG = "MainActivity"

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val repository = RoadRepository()

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

                Log.d(TAG, "onLocationResult: ${locationResult.locations.size}")
                for (location in locationResult.locations) {
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

        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

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
        }
        updateOrientationAngles()
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        Log.d(TAG, "onAccuracyChanged: " + p0.toString())
        Log.d(TAG, "onAccuracyChanged: $p1")
    }

    var colorJob :Job? = null

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

        if (job?.isActive == true) return

        if (this::naverMap.isInitialized) {
            colorJob?.let{
                if(it.isActive) return
            }
            colorJob = MainScope().launch {
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
        colorJob?.let{ if(it.isActive) it.cancel() }
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
}
