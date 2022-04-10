package com.gojungparkjo.routetracker

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gojungparkjo.routetracker.databinding.ActivityMainBinding
import com.google.android.gms.location.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.*
import net.daum.mf.map.api.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*


class MainActivity : AppCompatActivity(), MapView.MapViewEventListener,
    MapView.CurrentLocationEventListener, MapView.POIItemEventListener, SensorEventListener {

    private val TAG = "MainActivity"

    private lateinit var fusedLocationClient: FusedLocationProviderClient

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

    private val mapView: MapView by lazy {
        MapView(this)
    }

    var mapPointList: List<TrafficSign>? = null

    var nearestSign: MapPOIItem? = null

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
                mapView.removeAllPOIItems()
                addMarkersWithInBound(mapView.mapPointBounds)
            }
        }
        binding.filterSwitch.setOnCheckedChangeListener { _, _ ->
            mapView.removeAllPOIItems()
            addMarkersWithInBound(mapView.mapPointBounds)
        }
        binding.mapView.addView(mapView)
        mapView.setMapViewEventListener(this)
        mapView.setCurrentLocationEventListener(this)
        mapView.setPOIItemEventListener(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager


        readAsset()

    }

    override fun onSensorChanged(event: SensorEvent?) {
        if(event==null) return
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
        val degree = ((Math.toDegrees(orientationAngles[0].toDouble()) + 360)%360).toInt()
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

    override fun onPOIItemSelected(p0: MapView?, p1: MapPOIItem?) {
        binding.infoTextView.text = p1?.itemName ?: return
        binding.infoTextView.visibility = View.VISIBLE
    }

    override fun onCalloutBalloonOfPOIItemTouched(p0: MapView?, p1: MapPOIItem?) {
    }

    override fun onCalloutBalloonOfPOIItemTouched(
        p0: MapView?,
        p1: MapPOIItem?,
        p2: MapPOIItem.CalloutBalloonButtonType?
    ) {
    }

    override fun onDraggablePOIItemMoved(p0: MapView?, p1: MapPOIItem?, p2: MapPoint?) {
    }

    override fun onCurrentLocationUpdate(p0: MapView?, p1: MapPoint?, p2: Float) {
        p1?.let {
            CoroutineScope(Dispatchers.Main).launch {
                mapPointList?.nearestSign(p1)?.let {
                    nearestSign?.let {
                        mapView.removePOIItem(it)
                    }
                    nearestSign = MapPOIItem().apply {
                        itemName = "near"
                        markerType = MapPOIItem.MarkerType.BluePin
                        mapPoint = it.coordinate
                    }.also {
                        mapView.addPOIItem(it)
                    }
                    binding.nearTextView.text =
                        "가장 가까운 신호등 lat: ${p1.mapPointGeoCoord.latitude} lng ${p1.mapPointGeoCoord.longitude}"

                }
            }
        }
    }

    override fun onCurrentLocationDeviceHeadingUpdate(p0: MapView?, p1: Float) {
    }

    override fun onCurrentLocationUpdateFailed(p0: MapView?) {
    }

    override fun onCurrentLocationUpdateCancelled(p0: MapView?) {
    }

    override fun onMapViewInitialized(p0: MapView?) {

    }

    override fun onMapViewCenterPointMoved(p0: MapView?, p1: MapPoint?) {
    }

    override fun onMapViewZoomLevelChanged(mapView: MapView?, p1: Int) {
    }

    override fun onMapViewSingleTapped(p0: MapView?, p1: MapPoint?) {
    }

    override fun onMapViewDoubleTapped(p0: MapView?, p1: MapPoint?) {
    }

    override fun onMapViewLongPressed(p0: MapView?, p1: MapPoint?) {
    }

    override fun onMapViewDragStarted(p0: MapView?, p1: MapPoint?) {
    }

    override fun onMapViewDragEnded(p0: MapView?, p1: MapPoint?) {
    }

    override fun onMapViewMoveFinished(mapView: MapView?, p1: MapPoint?) {
        addMarkersWithInBound(mapView?.mapPointBounds)
    }

    fun addMarkersWithInBound(bound: MapPointBounds?) {
        job?.let {
            if (it.isActive) it.cancel()
        }
        job = CoroutineScope(Dispatchers.Main).launch {
            binding.loadingView.visibility = View.VISIBLE
            withContext(Dispatchers.Default) {
                if (mapView.zoomLevel > 2 || bound == null) return@withContext
                Log.d(TAG, "addMarkersWithInBound: " + mapView.zoomLevel)
                val temp = mapView.poiItems
                val selectedType = binding.numberPicker.value
                mapPointList?.forEach {
                    if (bound.contains(it.coordinate) && (!binding.filterSwitch.isChecked || it.signalType == selectedType)) {
                        MapPOIItem().apply {
                            itemName = it.toString()
                            markerType = MapPOIItem.MarkerType.RedPin
                            mapPoint = it.coordinate
                        }.also {
                            mapView.addPOIItem(it)
                        }
                    }
                }
                mapView.removePOIItems(temp)
            }
            binding.loadingView.visibility = View.GONE
        }

    }

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
                        MapPoint.mapPointWithGeoCoord(it[27].toDouble(), it[26].toDouble())
                    )
                )
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
//        mapView.currentLocationTrackingMode = MapView.CurrentLocationTrackingMode.TrackingModeOnWithHeading
        requesting = true
        fusedLocationProviderClient.requestLocationUpdates(
            createLocationRequest(),
            locationCallback,
            mainLooper
        )
    }

    fun stopTracking() {
        requesting = false
//        mapView.currentLocationTrackingMode = MapView.CurrentLocationTrackingMode.TrackingModeOff
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
