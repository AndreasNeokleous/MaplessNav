package s1875880.maplessnav

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
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
import java.lang.Exception
import java.lang.ref.WeakReference
import java.util.*
import java.util.Collections.replaceAll


class MapBoxActivity : AppCompatActivity(), OnMapReadyCallback, PermissionsListener , TextToSpeech.OnInitListener {


    private var permissionsManager: PermissionsManager = PermissionsManager(this)
    private lateinit var mapboxMap: MapboxMap
    private lateinit var mapView: MapView
    private val RESULT_GEOJSON_SOURCE_ID = "RESULT_GEOJSON_SOURCE_ID"
    val CLOSES_POI_SOURCE_ID = "CLOSES_POI_SOURCE_ID"
    private val LAYER_ID = "LAYER_ID"
    private lateinit var locationEngine: LocationEngine
    private val callback = MapBoxLocationCallback(this)
    private var curPoI  = arrayListOf<PoI>()
    private var lastLocation: LatLng? = null

    //TTS
    private val ACT_CHECK_TTS_DATA = 12345
    private var mTTS: TextToSpeech? = null
    private var imm: InputMethodManager? = null;


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(this, "pk.eyJ1IjoiYW5kcmVhc25lb2tlbG91cyIsImEiOiJjanR5NXdoNHEwZW9kM3lwbnRobXNxdGFmIn0.LkYWF5avM-_JVB27lS25zg")
        // called after access token
        setContentView(R.layout.activity_map_box)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)



        //TTS

        imm = this.getSystemService(Service.INPUT_METHOD_SERVICE) as InputMethodManager?
        if ( mTTS == null) {
            var ttsIntent: Intent? = Intent()
            ttsIntent!!.action = TextToSpeech.Engine.ACTION_CHECK_TTS_DATA
            startActivityForResult(ttsIntent, ACT_CHECK_TTS_DATA)
        }else{
            // TTs ready
        }



    //    Handler().postDelayed({

    //    }, 5000)


    }


    override fun onMapReady(mapboxMap: MapboxMap) {
        this.mapboxMap = mapboxMap
     //   mapboxMap.setStyle(Style.Builder().fromUrl(
     //        "mapbox://styles/mapbox/cjerxnqt3cgvp2rmyuxbeqme7")){
        // Show compass
        mapboxMap.uiSettings.isCompassEnabled = true
        // Disable fading animation
        mapboxMap.uiSettings.setCompassFadeFacingNorth(false)

        mapboxMap.setStyle(Style.MAPBOX_STREETS, object:  Style.OnStyleLoaded {
            override fun onStyleLoaded(style: Style) {
             //  addClickLayer(style)
            //  mapboxMap.addOnMapClickListener(this@MapBoxActivity)
                addResultLayer(style)
                enableLocationComponent(style)
                addClosesPoILayer(style)


            }


        })
           //

    //    }
    }


    fun makeTilequeryApiCall(style: Style, point: LatLng){
        var tilequery: MapboxTilequery = MapboxTilequery.builder()
            .accessToken("pk.eyJ1IjoiYW5kcmVhc25lb2tlbG91cyIsImEiOiJjanR5NXdoNHEwZW9kM3lwbnRobXNxdGFmIn0.LkYWF5avM-_JVB27lS25zg")
            .mapIds("mapbox.mapbox-streets-v8")
            .query(Point.fromLngLat(point.longitude, point.latitude))
            .radius(50)
            .limit(5)
            .geometry("point")
            .dedupe(true)
            .layers("poi_label")
            .build()

        tilequery.enqueueCall(object : retrofit2.Callback<FeatureCollection> {
            override fun onResponse(
                call: retrofit2.Call<FeatureCollection>,
                response: retrofit2.Response<FeatureCollection>
            ) {
                val resultSource: GeoJsonSource ?= style.getSourceAs(RESULT_GEOJSON_SOURCE_ID)
                if (resultSource != null && response.body()?.features() != null) {
                    val featureCollection = response.body()?.features()
                    resultSource?.setGeoJson(FeatureCollection.fromFeatures(featureCollection!!))
                  //  val toJsonResponse = response.body()?.toJson()
                  //  Log.v("RESPONSE",toJsonResponse )
                  //  val distance = featureCollection!![0].getProperty("tilequery").asJsonObject.get("distance").toString()
                 //   Log.v("RESPONSE", distance)



                    val featureSize = featureCollection?.size
                    if (curPoI!=null)
                        curPoI!!.clear()
                    if (featureSize!! > 0){
                        for (feature in featureCollection){
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

                                curPoI!!.add(poI)

                            }

                        }



                    }
                     //   Log.v("RESPONSE", "5s past")
                  //  Handler().postDelayed({
                   // }, 5000)

                }


            }

            override fun onFailure(call: retrofit2.Call<FeatureCollection>, t: Throwable) {
               // Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show()
            }

        })
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
     * Closes PoI layer
     */
    private fun addClosesPoILayer(loadedMapStyle: Style){
        loadedMapStyle.addImage("CLOSES-POI-ID", BitmapFactory.decodeResource(
            this.resources, R.drawable.red_marker_s))

    //    var geoJsonSource: GeoJsonSource = GeoJsonSource("poi-geo", Feature.fromGeometry(
    //       Point.fromLngLat(latLng.longitude,latLng.latitude)
    //    ))
    //    loadedMapStyle.addSource(geoJsonSource)

        loadedMapStyle.addSource(
            GeoJsonSource(
                CLOSES_POI_SOURCE_ID, FeatureCollection.fromFeatures(arrayOf<Feature>())
            )
        )

        loadedMapStyle.addLayer(
            SymbolLayer("closes-poi-layer",CLOSES_POI_SOURCE_ID).withProperties(
                iconImage("CLOSES-POI-ID"),
                iconOffset(arrayOf(0f, -12f)),
                iconIgnorePlacement(true),
                iconAllowOverlap(true)
            )
        )

       // var symbolLayer: SymbolLayer = SymbolLayer("closes-poi-layer","poi-geo" )
        // symbolLayer.withProperties(PropertyFactory.iconImage("closes-poi-marker"))

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
            val locationComponent = mapboxMap.locationComponent

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

        var request : LocationEngineRequest ?= LocationEngineRequest.Builder(5000)
            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .setMaxWaitTime(5000).build()

        if (request != null) {
            locationEngine.requestLocationUpdates(request, callback, mainLooper)
        }
        locationEngine.getLastLocation(callback)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onExplanationNeeded(permissionsToExplain: List<String>) {
        Toast.makeText(this, R.string.user_location_permission_explanation, Toast.LENGTH_LONG).show()
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            enableLocationComponent(mapboxMap.style!!)
        } else {
            Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onStart() {
        mapView.onStart()
        super.onStart()
    }

    override fun onResume() {
        mapView.onResume()
        super.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {

        mapView.onLowMemory()
        super.onLowMemory()
    }

    override fun onDestroy() {
        // Prevent leaks
        if (locationEngine != null) {
            locationEngine.removeLocationUpdates(callback);
        }
        mapView.onDestroy()
        if (mTTS !=null){
            mTTS!!.stop()
            mTTS!!.shutdown()
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    private class MapBoxLocationCallback : LocationEngineCallback<LocationEngineResult> {
        override fun onFailure(exception: Exception) {
            var activity : MapBoxActivity = activityWeakReference?.get()!!

            if (activity != null) {
                Toast.makeText(activity, exception.localizedMessage,
                    Toast.LENGTH_SHORT).show()
            }
        }

        override fun onSuccess(result: LocationEngineResult?) {
            var activity : MapBoxActivity = activityWeakReference?.get()!!
            if (activity != null) {

                var point : LatLng ?= LatLng(result?.lastLocation?.latitude!!, result.lastLocation?.longitude!!)

                // Make tile query
                activity.makeTilequeryApiCall(activity.mapboxMap.style!!,point!!)



                if (activity.mapboxMap != null && result.lastLocation != null){
                    activity.mapboxMap.locationComponent.forceLocationUpdate(result.lastLocation)
                    val position = CameraPosition.Builder()
                        .target(point)
                        .zoom(18.0)
                        .tilt(0.0)
                        .build()

                    activity.mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(position))



                }

                // Initialise lastLocation, update and announce every 10 meters
                if (activity.lastLocation == null){
                    activity.lastLocation = point
                }else if (point.distanceTo(activity.lastLocation!!)>=10){
                    Log.v("RESPONSE", "\n")
                    Log.v("RESPONSE", "Last call distance: " +point.distanceTo(activity.lastLocation!!).toString())

                    // Closes PoI
                    var minDistancePoI = activity.curPoI.minBy { it ->  it.distance }

                    var poiSource: GeoJsonSource?  = activity.mapboxMap.style!!.getSourceAs(activity.CLOSES_POI_SOURCE_ID)
                    poiSource?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(minDistancePoI!!.long,minDistancePoI!!.lat)))
               //     activity.addClosesPoILayer(activity.mapboxMap.style!!, latLngMinDistancePoI)

                    if (activity.curPoI !=null ){
                        for (poi in activity.curPoI!!){
                            val output = poi.category_en + ": " + poi.name + ", " + String.format("%.2f", poi.distance) + " meters away"
                            Log.v("RESPONSE", output)
                        }
                        activity.lastLocation = point

                    }

                    Log.v("RESPONSE", "Closest: "+ minDistancePoI?.name.toString())

                    // User bearing in range (0.0, 360.0]
                    var userBearing = result.lastLocation?.bearing
                    Log.v("BEARING", "User Heading: " + userBearing.toString())

                    // User LatLng
                    var lat1 = result.lastLocation?.latitude
                    var lng1 = result.lastLocation?.longitude

                    // Closes PoI LatLng
                    var lat2  = minDistancePoI?.lat
                    var lng2 = minDistancePoI?.long

                    // Calculate bearing between User and PoI
                    var dLon = (lng2!! - lng1!!)
                /**    var y = Math.sin(dLon) * Math.cos(lat2!!)
                    var x = Math.cos(lat1!!)*Math.sin(lat2) - Math.sin(lat1)*Math.cos(lat2)*Math.cos(dLon)
                    var poIbring  =Math.toDegrees((Math.atan2(y, x)))

                    // Calculate closes PoI bearing in relation to user's bearing
                    poIbring = (360 - ((poIbring + 360) % 360))
                    */
                    var dPhi = Math.log(Math.tan(lat2!!/2.0+Math.PI/4.0)/Math.tan(lat1!!/2.0+Math.PI/4.0))
                    if (Math.abs(dLon) > Math.PI){
                        if (dLon > 0.0){
                            dLon = -(2.0 * Math.PI - dLon)
                        }else{
                            dLon = (2.0 * Math.PI + dLon)
                        }
                    }
                    var poIbring = (Math.toDegrees(Math.atan2(dLon, dPhi))+ 360) % 360
                    if (userBearing!!>90 && userBearing!!<270 ){
                        poIbring = 360-poIbring
                    }
                    var closesPoIPoistion = "NaN"
                    if (poIbring!=null &&  poIbring<= 180){
                        closesPoIPoistion = "Right"
                    }else{
                        closesPoIPoistion = "Left"
                    }

                    Log.v("BEARING", "Closest PoI: " + closesPoIPoistion +" " + poIbring.toString() )
                    Log.v("RESPONSE", "---10m--- ")
                    var announcement: String = minDistancePoI?.name + " " + minDistancePoI?.category_en + " to your " + closesPoIPoistion
                    // Trim " "
                    announcement = announcement.replace("\"", "")
                    Log.v("ANNOUNCE", announcement)

                    activity.speakText(announcement)


                }






            }
        }


        private var activityWeakReference: WeakReference<MapBoxActivity>?

        constructor(activity: MapBoxActivity)
        {
            this.activityWeakReference = WeakReference<MapBoxActivity>(activity)
        }
    }

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
                val result = mTTS!!.setLanguage(Locale.UK)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "TTS language is not supported", Toast.LENGTH_LONG).show()
                } else {
                    // Do smth
                }
            }
        } else {
            Toast.makeText(this, "TTS initialization failed", Toast.LENGTH_LONG).show()
        }
    }

    private fun speakText(text: String ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (mTTS!=null)
            mTTS!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            if (mTTS!=null)
            mTTS!!.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    /**
     * Add icon on map click
    private fun addClickLayer(loadedMapStyle: Style){
    loadedMapStyle.addImage("CLICK-ICON-ID",BitmapFactory.decodeResource(
    this.resources, R.drawable.red_marker_s))

    loadedMapStyle.addSource(
    GeoJsonSource(
    CLICK_CENTER_GEOJSON_SOURCE_ID ,
    FeatureCollection.fromFeatures(arrayOf<Feature>())
    )
    )

    loadedMapStyle.addLayer(
    SymbolLayer("click-layer", CLICK_CENTER_GEOJSON_SOURCE_ID).withProperties(
    iconImage("CLICK-ICON-ID"),
    iconOffset(arrayOf(0f, -12f)),
    iconIgnorePlacement(true),
    iconAllowOverlap(true)
    )
    )
    }
     */


    /**
     * On map click listener

    override fun onMapClick(point: LatLng): Boolean {
    var style: Style? = mapboxMap.style
    if (style != null){
    var clickLocationSource: GeoJsonSource?  = style.getSourceAs(CLICK_CENTER_GEOJSON_SOURCE_ID )
    if (clickLocationSource != null) {
    clickLocationSource.setGeoJson(Feature.fromGeometry(Point.fromLngLat(point.longitude,
    point.latitude
    )))
    }

    makeTilequeryApiCall(style, point)
    return true
    }
    return false
    }
     */

}
