package s1875880.maplessnav

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.NonNull
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.android.core.location.*
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.geojson.FeatureCollection
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.*
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource

import com.mapbox.api.tilequery.MapboxTilequery
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.Exception
import java.lang.ref.WeakReference
import java.util.*


class MainActivity : AppCompatActivity(), OnMapReadyCallback, PermissionsListener , TextToSpeech.OnInitListener {

    // Mapbox
    private var mapboxMap: MapboxMap?=null
    private var mapView: MapView?=null
    private val RESULT_GEOJSON_SOURCE_ID = "RESULT_GEOJSON_SOURCE_ID"
    private val CLOSEST_POI_SOURCE_ID = "CLOSEST_POI_SOURCE_ID"
    private val LAYER_ID = "LAYER_ID"
    // Location
    private var locationEngine: LocationEngine?=null
    private val callback = MapBoxLocationCallback(this)
    private var currentLocation:Point?=null
    private var permissionsManager: PermissionsManager = PermissionsManager(this)
    private var locManager: LocationManager?=null

    // Tilequery API
    private var tilequeryResponsePoI  = arrayListOf<PoI>()
    private var lastQueryLocation: Location? = null
    // Text-to-speech engine
    private val ACT_CHECK_TTS_DATA = 12345
    private var mTTS: TextToSpeech? = null
    private var imm: InputMethodManager? = null
    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(this, getString(R.string.access_token))
        setContentView(R.layout.activity_main)
        // Mapbox
        mapView = findViewById(R.id.mapView)
        mapView!!.onCreate(savedInstanceState)
        mapView!!.getMapAsync(this)
        // TTS
        imm = this.getSystemService(Service.INPUT_METHOD_SERVICE) as InputMethodManager?
        if ( mTTS == null) {
            val ttsIntent: Intent? = Intent()
            ttsIntent!!.action = TextToSpeech.Engine.ACTION_CHECK_TTS_DATA
            startActivityForResult(ttsIntent, ACT_CHECK_TTS_DATA)
        }
        // Layout
        favouritesFab.setOnClickListener {
            if (locManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER) && currentLocation!=null){
                val intent = Intent(this, FavouriteActivity::class.java)
                intent.putExtra("currentLocation",currentLocation!!.toJson())
                if (mTTS!=null) mTTS!!.shutdown()
                startActivity(intent)
            }else{
                Toast.makeText(this,"Please enable location services and restart the app.", Toast.LENGTH_LONG).show()
            }
        }
        locManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER) && PermissionsManager.areLocationPermissionsGranted(this)){
            Toast.makeText(this,"Please enable location services and restart the app.", Toast.LENGTH_LONG).show()
        }
    }


    override fun onMapReady(mapboxMap: MapboxMap) {
        this.mapboxMap = mapboxMap
        mapboxMap.uiSettings.isCompassEnabled = true
        mapboxMap.uiSettings.setCompassFadeFacingNorth(false)
        mapboxMap.setStyle(Style.MAPBOX_STREETS) { style ->
            addResultLayer(style)
            enableLocationComponent(style)
            addClosesPoILayer(style)
        }
    }

    /**
     * Result layer: Add PoI icon
     */
    private fun addResultLayer(loadedMapStyle: Style){
        loadedMapStyle.addImage("RESULT-ICON-ID", BitmapFactory.decodeResource(
            this.resources, R.drawable.blue_marker_s))
        loadedMapStyle.addSource(
            GeoJsonSource(
                RESULT_GEOJSON_SOURCE_ID,
                FeatureCollection.fromFeatures(arrayOf())
            )
        )
        loadedMapStyle.addLayer(
            SymbolLayer(LAYER_ID, RESULT_GEOJSON_SOURCE_ID).withProperties(
                iconImage("RESULT-ICON-ID"),
                iconOffset(arrayOf(0f, -12f)),
                iconIgnorePlacement(true),
                iconAllowOverlap(true)
            )
        )
    }

    /**
     * Closest PoI layer
     */
    private fun addClosesPoILayer(loadedMapStyle: Style){
        loadedMapStyle.addImage("CLOSES-POI-ID", BitmapFactory.decodeResource(
            this.resources, R.drawable.red_marker_s))
        loadedMapStyle.addSource(
            GeoJsonSource(
                CLOSEST_POI_SOURCE_ID, FeatureCollection.fromFeatures(arrayOf<Feature>())
            )
        )
        loadedMapStyle.addLayer(
            SymbolLayer("closes-poi-layer",CLOSEST_POI_SOURCE_ID).withProperties(
                iconImage("CLOSES-POI-ID"),
                iconOffset(arrayOf(0f, -12f)),
                iconIgnorePlacement(true),
                iconAllowOverlap(true)
            )
        )
    }

    /**
     * Location component and permissions
     */
    @SuppressLint("MissingPermission")
    private fun enableLocationComponent(loadedMapStyle: Style){
        if (PermissionsManager.areLocationPermissionsGranted(this)){
            val options = LocationComponentOptions.builder(this)
                .trackingGesturesManagement(true)
                .accuracyColor(Color.GREEN)
                .build()
            // Get an instance of the component
            val locationComponent = mapboxMap!!.locationComponent
            // Activate the component
            locationComponent.activateLocationComponent(this, loadedMapStyle)
            // Apply the options to the LocationComponent
            locationComponent.applyStyle(options)
            // Enable to make component visible
            locationComponent.isLocationComponentEnabled = true
            // Set the component's camera mode
            locationComponent.cameraMode = CameraMode.TRACKING
            // Current location icon retrieved by bearing
            locationComponent.renderMode = RenderMode.GPS
            initLocationEngine()
        } else{
            permissionsManager = PermissionsManager(this)
            permissionsManager.requestLocationPermissions(this)
        }
    }
    @SuppressLint("MissingPermission")
    private fun initLocationEngine(){
        locationEngine = LocationEngineProvider.getBestLocationEngine(this)
        val request : LocationEngineRequest ?= LocationEngineRequest.Builder(5000)
            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .setMaxWaitTime(5000).build()
        if (request != null) {
            locationEngine!!.requestLocationUpdates(request, callback, mainLooper)
        }
        locationEngine!!.getLastLocation(callback)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
    override fun onExplanationNeeded(permissionsToExplain: List<String>) {
        Toast.makeText(this, R.string.user_location_permission_explanation, Toast.LENGTH_LONG).show()
    }
    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            if (!locManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                Toast.makeText(this,"Please enable location services and restart the app.", Toast.LENGTH_LONG).show()
            }else{
                enableLocationComponent(mapboxMap!!.style!!)
            }
        } else {
            Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show()
            finishAfterTransition()
        }
    }


    /**
     * Make Tilequery call to get the closest PoI and announce it.
     */
    private fun makeTilequeryCall(@NonNull  location: Location?){
        val tilequery = MapboxTilequery.builder()
            .accessToken(getString(R.string.access_token))
            .mapIds("mapbox.mapbox-streets-v8")
            .query(Point.fromLngLat(location!!.longitude, location.latitude))
            .radius(50)
            .limit(5)
            .geometry("point")
            .layers("poi_label")
            .build()
        tilequery!!.enqueueCall(object : retrofit2.Callback<FeatureCollection> {
            override fun onResponse(
                call: retrofit2.Call<FeatureCollection>,
                response: retrofit2.Response<FeatureCollection>
            ) {
                if (mapboxMap!!.style!!.isFullyLoaded) {
                    val resultSource: GeoJsonSource? = mapboxMap!!.style!!.getSourceAs(RESULT_GEOJSON_SOURCE_ID)
                    if (resultSource != null && response.body()?.features() != null) {
                        val featureCollection = response.body()?.features()
                        val featureSize = featureCollection!!.size
                        // Mark five closest PoIs with blue marker
                        resultSource.setGeoJson(FeatureCollection.fromFeatures(featureCollection))
                        tilequeryResponsePoI.clear()
                        if (featureSize > 0) {
                            for (feature in featureCollection) {
                                if (feature != null) {
                                    //Adding PoI to list
                                    var distance = 0.0
                                    var category_en = " "
                                    var name = " "
                                    var lat = 0.0
                                    var long = 0.0
                                    val poI = PoI(distance, category_en, name, lat, long)
                                    // distance from user
                                    if (feature.hasProperty("tilequery")) {
                                        distance =
                                            feature.getProperty("tilequery").asJsonObject.get("distance").toString()
                                                .toDouble()
                                        poI.distance = distance
                                    }
                                    // categories e.g. shop, cafe, casino
                                    if (feature.hasProperty("category_en")) {
                                        category_en = feature.getProperty("category_en").toString()
                                        poI.category_en = category_en
                                    }
                                    if (feature.hasProperty("name")) {
                                        name = feature.getProperty("name").toString()
                                        poI.name = name
                                    }
                                    val position1 = feature.geometry() as Point
                                    lat = position1.latitude()
                                    long = position1.longitude()
                                    poI.lat = lat
                                    poI.long = long
                                    tilequeryResponsePoI.add(poI)
                                }
                            }
                            val closestPoI : PoI? = tilequeryResponsePoI.minBy { it.distance }
                            val leftOrRight = getRelativeLocation(closestPoI, location )
                            var announcement: String = closestPoI?.name + " " + closestPoI?.category_en + " to your " + leftOrRight
                            announcement = announcement.replace("\"", "")
                            speakText(announcement)
                            // Mark closest PoI with red marker
                            val poiSource: GeoJsonSource?  = mapboxMap!!.style!!.getSourceAs(CLOSEST_POI_SOURCE_ID)
                            poiSource?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(closestPoI!!.long,closestPoI.lat)))
                        }
                    }
                }
            }
            override fun onFailure(call: retrofit2.Call<FeatureCollection>, t: Throwable) {
            }
        })
    }

    /**
     * Is PoI Left/Right of User
     */
    private fun getRelativeLocation(closestPoI: PoI?, location: Location? ) : String{
        val res: String
        // User LatLng
        val lat1 = location?.latitude
        val lng1 = location?.longitude
        val userBearing = location?.bearing
        // Closes PoI LatLng
        val lat2  = closestPoI?.lat
        val lng2 = closestPoI?.long
        // Calculate difference of longitude  between User and PoI
        var dLon = (lng2!! - lng1!!)
        val dPhi = Math.log(Math.tan(lat2!!/2.0+Math.PI/4.0)/Math.tan(lat1!!/2.0+Math.PI/4.0))
        if (Math.abs(dLon) > Math.PI){
            if (dLon > 0.0){
                dLon = -(2.0 * Math.PI - dLon)
            }else{
                dLon = (2.0 * Math.PI + dLon)
            }
        }
        var poIbring = (Math.toDegrees(Math.atan2(dLon, dPhi))+ 360) % 360
        if (userBearing!!>90 && userBearing <270 ){
            poIbring = 360-poIbring
        }
        if (poIbring<= 180){
            res = "Right"
        }else{
            res = "Left"
        }
        return res
    }

    /**
     * Text-to-Speech Methods: onActivityResult, onInit, speakText
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == ACT_CHECK_TTS_DATA){
            if (resultCode === TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                // Data exists, so we instantiate the TTS engine
                mTTS = TextToSpeech(this, this)
            } else {
                // Data is missing, so we start the TTS installation process
                val installIntent = Intent()
                installIntent.action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
                startActivity(installIntent)
            }
        }
        else{
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
    override fun onInit(status: Int) {
        if (status === TextToSpeech.SUCCESS) {
            if (mTTS != null) {
                mTTS!!.language = Locale.getDefault()
            }
        } else {
            Toast.makeText(this, "TTS initialization failed", Toast.LENGTH_LONG).show()
        }
    }
    @SuppressLint("ObsoleteSdkInt")
    private fun speakText(text: String ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (mTTS!=null && !mTTS!!.isSpeaking)
                mTTS!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            if (mTTS!=null &&!mTTS!!.isSpeaking)
                mTTS!!.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    /**
     * Location Updates
     */
    private class MapBoxLocationCallback(activity: MainActivity) : LocationEngineCallback<LocationEngineResult> {
        private var activityWeakReference: WeakReference<MainActivity>? = WeakReference(activity)

        @SuppressLint("RestrictedApi")
        override fun onSuccess(result: LocationEngineResult?) {
            // Reference to MapBoxActivity
            val activity : MainActivity = activityWeakReference?.get()!!
            if (result!!.lastLocation!=null){
                activity.currentLocation = Point.fromLngLat(result.lastLocation!!.longitude,result.lastLocation!!.latitude)
                // Centre map to user's location
                if (activity.mapboxMap != null){
                    activity.mapboxMap!!.locationComponent.forceLocationUpdate(result.lastLocation)
                    val position = CameraPosition.Builder()
                        .target(LatLng(result.lastLocation!!.latitude,result.lastLocation!!.longitude))
                        .zoom(18.0)
                        .tilt(0.0)
                        .build()
                    activity.mapboxMap!!.animateCamera(CameraUpdateFactory.newCameraPosition(position))
                }
            }

            // Initialise lastQueryLocation, update and announce every 20 meters
            if (result.lastLocation!=null) {
                if (activity.lastQueryLocation == null) {
                    activity.lastQueryLocation = result.lastLocation
                    activity.makeTilequeryCall(result.lastLocation)
                } else if (result.lastLocation!!.distanceTo(activity.lastQueryLocation!!) >= 20) {
                    activity.lastQueryLocation = result.lastLocation
                    activity.makeTilequeryCall(result.lastLocation)
                }
            }

        }
        override fun onFailure(exception: Exception) {
            val activity : MainActivity = activityWeakReference?.get()!!
            Toast.makeText(activity, exception.localizedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        mapView!!.onStart()
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        mapView!!.onResume()
        if (locationEngine !=null) {
            val request: LocationEngineRequest? = LocationEngineRequest.Builder(5000)
                .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                .setMaxWaitTime(5000).build()
            locationEngine!!.requestLocationUpdates(request!!, callback, null)
        }
        // Resume text-to-speech after onDestory
        mTTS = null
        if (mTTS==null){
            mTTS = TextToSpeech(applicationContext, TextToSpeech.OnInitListener { p0 ->
                if (p0 != TextToSpeech.ERROR) {
                    mTTS!!.language = Locale.UK
                }
            })}
    }

    override fun onPause() {
        super.onPause()
        if (mapView!=null){
            mapView!!.onPause()
        }
    }

    override fun onStop() {
        mapView!!.onStop()
        super.onStop()
    }

    override fun onBackPressed() {
        if (mTTS !=null){
            mTTS!!.stop()
            mTTS!!.shutdown()
        }
        moveTaskToBack(true)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView!!.onLowMemory()
        mTTS!!.stop()
        mTTS!!.shutdown()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (locationEngine != null) {
            locationEngine!!.removeLocationUpdates(callback)
        }
        mapView!!.onDestroy()
        if (mTTS !=null){
            mTTS!!.stop()
            mTTS!!.shutdown()
        }
        mapboxMap!!.locationComponent.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView!!.onSaveInstanceState(outState)
    }
}