package com.gojungparkjo.routetracker

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
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
import net.daum.mf.map.api.MapPOIItem
import net.daum.mf.map.api.MapPoint
import net.daum.mf.map.api.MapPointBounds
import net.daum.mf.map.api.MapView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.coroutines.CoroutineContext


class MainActivity : AppCompatActivity(), MapView.MapViewEventListener {

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
                    mapView.setMapCenterPoint(MapPoint.mapPointWithGeoCoord(location.latitude, location.longitude), true);
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

    var mapPointList: List<MapPoint>? = null

    private var requesting = false

    var job :Job? = null

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
        binding.mapView.addView(mapView)
        mapView.setMapViewEventListener(this)
        readAsset()

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
        job?.let{
            if(it.isActive) it.cancel()
        }
        job = CoroutineScope(Dispatchers.Main).launch {
            binding.loadingView.visibility = View.VISIBLE
            withContext(Dispatchers.Default){
                if (mapView.zoomLevel > 2 || bound == null) return@withContext
                Log.d(TAG, "addMarkersWithInBound: " + mapView.zoomLevel)
                mapView.poiItems.forEach {
                    if (!bound.contains(it.mapPoint)) {
                        mapView.removePOIItem(it)
                    }
                }
                mapPointList?.forEach {
                    if (bound.contains(it)) {
                        MapPOIItem().apply {
                            itemName = "aa"
                            markerType = MapPOIItem.MarkerType.RedPin
                            mapPoint = it
                        }.also {
                            mapView.addPOIItem(it)
                        }
                    }

                }
            }
            binding.loadingView.visibility = View.GONE
        }

    }

    fun readAsset() = CoroutineScope(Dispatchers.IO).launch{
        val br = BufferedReader(InputStreamReader(assets.open("df.csv")))
        var line: String? = br.readLine()
        val list = LinkedList<Pair<Double, Double>>()
        while (br.readLine().also { line = it } != null) {
            line!!.split(",").takeLast(2).also {
                list.add(Pair(it[1].toDouble(), it[0].toDouble()))
            }
        }
        // 아래가 정렬

        mapPointList = list.sortedWith(compareBy({ it.first }, { it.second })).map {
            MapPoint.mapPointWithGeoCoord(it.first, it.second)
        }
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
//    fun startTracking() {
//        binding.trackingButton.setBackgroundColor(Color.RED)
//        requesting = true
//        mapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading)
//    }
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