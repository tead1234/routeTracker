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
import com.gojungparkjo.routetracker.data.TmapLabelRepository
import com.gojungparkjo.routetracker.databinding.ActivityMainBinding
import com.gojungparkjo.routetracker.model.FeedBackDialog
import com.gojungparkjo.routetracker.model.TTS_Module
import com.gojungparkjo.routetracker.model.crosswalk.CrossWalkResponse
import com.gojungparkjo.routetracker.model.intersection.InterSectionResponse
import com.gojungparkjo.routetracker.model.pedestrianroad.PedestrianRoadResponse
import com.gojungparkjo.routetracker.model.trafficlight.TrafficLightResponse
import com.google.android.gms.location.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.*
import com.naver.maps.map.overlay.*
import com.naver.maps.map.util.FusedLocationSource
import com.naver.maps.map.util.MarkerIcons
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineSegment
import org.locationtech.proj4j.ProjCoordinate
import kotlin.math.atan2


class MainActivity : AppCompatActivity(), SensorEventListener,
    OnMapReadyCallback {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // 네이버지도 fusedLocationSource
    private lateinit var locationSource: FusedLocationSource
    private val tmapLabelRepository = TmapLabelRepository()
    private val roadRepository = RoadRepository()
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

                        if (binding.trackingSwitch.isChecked) {
                            it.moveCamera(
                            CameraUpdate.scrollTo(
                                coordinate
                                )
                            )
                            naverMap.moveCamera(CameraUpdate.zoomTo(18.0))
                        }
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

        binding.trackingSwitch.isChecked = true

        binding.mapButton.setOnClickListener(View.OnClickListener {
            if(binding.mapButton.text.equals("지도 모드")){
                binding.trackingButton.visibility = View.INVISIBLE
                binding.mapButton.setText("일반 모드")
            }
            else{
                binding.trackingButton.visibility = View.VISIBLE
                binding.mapButton.setText("지도 모드")
            }


        })


    }

    private fun setupCompass() {
        compass = Compass(this)
        compass.setListener(object : Compass.CompassListener {
            override fun onNewAzimuth(azimuth: Float) {
                binding.compassTextView.text = azimuth.toInt().toString()
                findTrafficSignAndCrosswalkInSight(azimuth.toDouble())
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
                binding.trackingButton.setText("안내 시작")
                it.setBackgroundColor(ContextCompat.getColor(this, R.color.light_green))


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

    private fun findTrafficSignAndCrosswalkInSight(degree: Double) {
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
                   // var nearestInterSection:Triple<PolygonOverlay,Pair<LatLng,LatLng>,Pair<String,String>>? = null
                    polygonMap.forEach { (_, polygon) ->
                        var nearest = Double.MAX_VALUE
                        var flag = false
                        var direct: String = ""
                        polygon.first.coords.forEach {
                            val temp = it.distanceTo(map.locationOverlay.position)
                            if (temp < nearest && temp < 10) {
                                nearest = temp
                                val temp2 = atan2(
                                    it.longitude - map.locationOverlay.position.longitude,
                                    it.latitude - map.locationOverlay.position.latitude
                                ).toDegree()
                                val diff = temp2.toInt() - degree.toInt()
                                flag = diff in -20..20
                                if(flag) {
                                   var nearestInterSection = polygon
                                    var temp3 = atan2(nearestInterSection.second.first.longitude - map.locationOverlay.position.longitude,
                                        nearestInterSection.second.first.latitude - map.locationOverlay.position.latitude).toDouble()
                                    var temp4 = atan2(nearestInterSection.second.second.longitude - map.locationOverlay.position.longitude,
                                        nearestInterSection.second.second.latitude - map.locationOverlay.position.latitude).toDouble()
                                    if(temp3 >= temp4){
                                        direct = nearestInterSection.third.first.toString()
                                    }else{
                                        direct = nearestInterSection.third.second.toString()
                                    }
                                }
                                if (polygon.first.tag != null){
                                    // csscde
                                    var crossWalk = interSectionMap[polygon.first.tag]
                                    Log.d(TAG, "사거리: $crossWalk")
                                    var anonunce = "${direct}방면 ${crossWalk}교차로 횡단보도입니다."
                                    tts.speakOut(anonunce)

                                }
                                // 지금 이게 가장 가까운 횡단보도를 캐치한거니깐
                                // 여기에서 폴리곤의 second와 나의  거리를 캐치하고 가장 가까운 seond를 찾아서
                                // 그값이 second의 첫번쨰인지 두번쨰인지를 확인
                                // 첫번째면 third의 두번쨰를
                                // 두번째면 third의 첫번쨰를 return

                            }
                        }
                        polygon.first.color = if (flag) Color.RED else Color.WHITE
                        // 교차


                        // 랜드마크
                        // 만약에 second의 첫번째면 third의 두번째를

                    }
                }

            }
        }
    }

    private val polygonMap = HashMap<String, Triple<PolygonOverlay,Pair<LatLng,LatLng>,Pair<String,String>>>()
    private val pedestrianRoadMap = HashMap<String, PolygonOverlay>()
    private val polylineMap = HashMap<String, PolylineOverlay>()
    private val trafficLightMap = HashMap<String, Marker>()
    private val interSectionMap = HashMap<String, String>()
    private var fetchAndMakeJob = Job().job

    private fun fetchDataWithInBound(bound: LatLngBounds?) {
        if (naverMap.cameraPosition.zoom < 16) return
        if (fetchAndMakeJob.isActive) fetchAndMakeJob.cancel()
        if (addShapeJob.isActive) addShapeJob.cancel()
        MainScope().launch {
            binding.loadingView.visibility = View.VISIBLE
            fetchAndMakeJob = CoroutineScope(Dispatchers.IO).launch fetch@{
                if (bound == null) return@fetch
                val trafficLights = roadRepository.getTrafficLightInBound(bound)
                trafficLights?.let { addMarkerFromTrafficLightResponse(it) }
                val pedestrianRoadResponse = roadRepository.getPedestrianRoadInBound(bound)
                pedestrianRoadResponse?.let { addPolygonFromPedestrianRoadResponse(it) }
                val trafficIslandResponse = roadRepository.getTrafficIslandInBound(bound)
                trafficIslandResponse?.let { addPolygonFromPedestrianRoadResponse(it) }
                val crossWalkResponse = roadRepository.getRoadInBound(bound)
                crossWalkResponse?.let { addPolygonFromCrossWalkResponse(it) }
                val InterSectionResponse = roadRepository.getIntersectionInBound(bound)
                InterSectionResponse?.let{getInterSectionNameInBound(it)}
            }
            fetchAndMakeJob.join()
            addShapesWithInBound(bound)
            binding.loadingView.visibility = View.INVISIBLE
        }
    }

    private suspend fun addPolygonFromPedestrianRoadResponse(response: PedestrianRoadResponse) =
        withContext(Dispatchers.Default) {
            response.features?.forEach { feature ->
                if (feature.properties?.vIEWCDE != "002" || pedestrianRoadMap.containsKey(
                        feature.properties.mGRNU
                    )
                ) return@forEach
                feature.geometry?.coordinates?.forEach {
                    val list = mutableListOf<LatLng>()
                    it.forEach { point ->
                        list.add(ProjCoordinate(point[0], point[1]).toLatLng())
                    }
                    if (list.size > 2) {
                        pedestrianRoadMap[feature.properties.mGRNU] = PolygonOverlay().apply {
                            coords = list; color = Color.WHITE
                            outlineWidth = 5; outlineColor = Color.RED
                            tag = feature.properties.toString()
                            setOnClickListener {
                                binding.infoTextView.text = it.tag.toString()
                                binding.infoTextView.visibility = View.VISIBLE
                                true
                            }
                        }
                    }
                }
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
                        val crossWalkPolygon = PolygonOverlay().apply {
                            coords = list; color = Color.WHITE
                            outlineWidth = 5; outlineColor = Color.GREEN
                            tag = feature.properties.cSSCDE.toString()
                            setOnClickListener {
                                binding.infoTextView.text = it.tag.toString()
                                binding.infoTextView.visibility = View.VISIBLE
                                true
                            }
                        }

                        // 최소 사각형
                        val rectanglePolygon = crossWalkPolygon.minimumRectangle()
                        // 사각형 꼭짓점
                        val rectangleCoord = rectanglePolygon.coords.map { it.toCoordinate() }
                        // 각 변의 중점 찾기
                        val middlePoints = mutableListOf<LatLng>()
                        for (i in 0 until rectangleCoord.size - 1) {
                            val middlePoint =
                                LineSegment.midPoint(rectangleCoord[i], rectangleCoord[i + 1])
                            middlePoints.add(middlePoint.toLatLng())

                        }
                        // 중심선 잇고 늘이기
                        val delta = 0.00003
                        val midLine1 =
                            lengthenLine(middlePoints[0], middlePoints[2], delta)
                        val midLine2 =
                            lengthenLine(middlePoints[1], middlePoints[3], delta)

                        val geometryFactory = GeometryFactory()
                        val midLine1Geometry = LineSegment(
                            midLine1[0].toCoordinate(),
                            midLine1[1].toCoordinate()
                        ).toGeometry(geometryFactory)
                        val midLine2Geometry = LineSegment(
                            midLine2[0].toCoordinate(),
                            midLine2[1].toCoordinate()
                        ).toGeometry(geometryFactory)

                        // 중심선과 도보 교차 계산
                        var midLine1Intersect = 0
                        var midLine2Intersect = 0

                        withContext(Dispatchers.Main) {
                            pedestrianRoadMap.forEach { _, polygon ->
                                val polygonBoundary =
                                    geometryFactory.createPolygon(polygon.coords.map { it.toCoordinate() }
                                        .toTypedArray()).boundary
                                if (polygonBoundary.intersects(midLine1Geometry)) midLine1Intersect++
                                if (polygonBoundary.intersects(midLine2Geometry)) midLine2Intersect++
                            }
                        }

                        var mainMidLine:List<LatLng>
                        var label1 :String
                        var label2 :String

                        if(midLine1Intersect>midLine2Intersect){
                            mainMidLine = lengthenLine(middlePoints[0], middlePoints[2], 0.00020)

                        }else if(midLine1Intersect<midLine2Intersect){
                            mainMidLine = lengthenLine(middlePoints[1], middlePoints[3], 0.00020)
                        }else{
                            val midPoint = LineSegment.midPoint(middlePoints[0].toCoordinate(),middlePoints[2].toCoordinate()).toLatLng()
                            mainMidLine = listOf(midPoint,midPoint)
                        }

                        label1 = tmapLabelRepository.getLabelFromLatLng(mainMidLine[0])?.poiInfo?.name?:""
                        label2 = tmapLabelRepository.getLabelFromLatLng(mainMidLine[1])?.poiInfo?.name?:""
                        Log.d(TAG, "addPolygonFromCrossWalkResponse: $label1")
                        Log.d(TAG, "addPolygonFromCrossWalkResponse: $label2")
                        Log.d(TAG, "addPolygonFromCrossWalkResponse: #######")

                        polygonMap[feature.properties.mGRNU] = Triple(crossWalkPolygon,Pair(mainMidLine[0],mainMidLine[1]),Pair(label1,label2))

                        polylineMap[feature.properties.mGRNU + "L1"] =
                            PolylineOverlay(midLine1).apply {
                                zIndex = 20003
                                if (midLine1Intersect > midLine2Intersect) color =
                                    Color.MAGENTA
                                setOnClickListener { it.map = null; true }
                            }

                        polylineMap[feature.properties.mGRNU + "L2"] =
                            PolylineOverlay(midLine2).apply {
                                zIndex = 20003
                                if (midLine1Intersect < midLine2Intersect) color =
                                    Color.MAGENTA
                                setOnClickListener { it.map = null; true }
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
    private fun getInterSectionNameInBound(response: InterSectionResponse){
        // 각 피쳐를 가져온다음 css 네임을
        response.features.forEach{ feature ->
            if(interSectionMap.containsKey(feature.properties.CSS_NUM.toString()))
                return@forEach
            feature.properties.let{ property ->
                // 지금 문제가 tag에 들어있는 값은 csscde
                interSectionMap[property.CSS_NUM.toString()] = property.CSS_NAM
                Log.d(TAG,"잡히나: $interSectionMap[property.CSS_NUM.toString()]")
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
                crosswalk.first.map = if (bound.contains(crosswalk.first.bounds)) naverMap else null
            }
            pedestrianRoadMap.forEach { (_, pedestrianRoad) ->
                pedestrianRoad.map =
                    if (bound.contains(pedestrianRoad.bounds) || bound.intersects(pedestrianRoad.bounds)) naverMap else null
            }
            trafficLightMap.forEach { (_, trafficLight) ->
                trafficLight.map = if (bound.contains(trafficLight.position)) naverMap else null
            }
            polylineMap.forEach { (_, polyline) ->
                polyline.map = if (bound.contains(polyline.bounds)) naverMap else null
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
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_DENIED
        ) {
            stepPermissionRequest()
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking(fusedLocationProviderClient: FusedLocationProviderClient) {
        binding.trackingButton.setBackgroundColor(R.color.light_red)
        binding.trackingButton.setText("안내 중")
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


    @SuppressLint("MissingPermission")
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




