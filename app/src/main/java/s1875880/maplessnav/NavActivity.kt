package s1875880.maplessnav

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.NonNull
import com.mapbox.android.core.location.*
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.tilequery.MapboxTilequery
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions
import com.mapbox.services.android.navigation.ui.v5.listeners.NavigationListener
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute
import com.mapbox.services.android.navigation.v5.location.replay.ReplayRouteLocationEngine
import com.mapbox.services.android.navigation.v5.milestone.Milestone
import com.mapbox.services.android.navigation.v5.milestone.MilestoneEventListener
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigation
import com.mapbox.services.android.navigation.v5.navigation.NavigationEventListener
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute
import com.mapbox.services.android.navigation.v5.offroute.OffRouteListener
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress
import org.jetbrains.annotations.NotNull
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.lang.Exception
import java.lang.ref.WeakReference
import java.util.*

class NavActivity : AppCompatActivity(), OnMapReadyCallback, PermissionsListener, NavigationEventListener,
    OffRouteListener, ProgressChangeListener, MilestoneEventListener {

    // Mapbox
    var mapView: MapView ?=null
    var mapboxMap: MapboxMap ?=null
    val callback = MapBoxLocationCallback(this)
    // Current Location an permissions
    var locationComponent : LocationComponent?= null
    var locationEngine: LocationEngine?=null
    var currentLocation:Point?=null
    var lastLocation: Location?=null
    var permissionsManager:PermissionsManager?=null
    // Navigation
    var currentRoute: DirectionsRoute?=null
    var navigationMapRoute: NavigationMapRoute ?= null
    var destination: Point?=null
    var origin: Point?=null
    var navigationOptions:NavigationLauncherOptions?=null
    // Reroute
    var mockLocationEngine: ReplayRouteLocationEngine ?=null
    var navigation : MapboxNavigation ?= null
    var running: Boolean = false
    var tracking: Boolean = false
    var wasInTunnel: Boolean = false
    // Points of Interest - Tilequery
    val RESULT_GEOJSON_SOURCE_ID = "RESULT_GEOJSON_SOURCE_ID"
    val CLOSES_POI_SOURCE_ID = "CLOSES_POI_SOURCE_ID"
    val LAYER_ID = "LAYER_ID"
    var curPoI  = arrayListOf<PoI>()
    var lastQueryLocation: LatLng? = null
    var tilequery: MapboxTilequery?=null
    //Text-To-Speech
    private val ACT_CHECK_TTS_DATA = 12345
    private var mTTS: TextToSpeech? = null
    private var imm: InputMethodManager? = null;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(this,getString(R.string.access_token))
        setContentView(R.layout.activity_nav)
        // Initialize Mapbox
        mapView = findViewById(R.id.mapViewNav)
        mapView!!.onCreate(savedInstanceState)
        mapView!!.getMapAsync(this)
        // Setup Text-to-Speech
        imm = this.getSystemService(Service.INPUT_METHOD_SERVICE) as InputMethodManager?
        if ( mTTS == null) {
            var ttsIntent: Intent? = Intent()
            ttsIntent!!.action = TextToSpeech.Engine.ACTION_CHECK_TTS_DATA
            startActivityForResult(ttsIntent, ACT_CHECK_TTS_DATA)
        }
    }

    override fun onMapReady(@NonNull mapboxMap: MapboxMap) {
        this.mapboxMap = mapboxMap
        mapboxMap.setStyle(Style.MAPBOX_STREETS, object:  Style.OnStyleLoaded {
            override fun onStyleLoaded(@NonNull style: Style) {
                getIncomingIntent()
                enableLocationComponent(style)
                getRoute(origin, destination)
                addPoILayer(style)
            }
        })
    }

    @SuppressLint("RestrictedApi")
    private fun getIncomingIntent(){
        if (intent.hasExtra("placeName") && intent.hasExtra("placePoint")  && intent.hasExtra("currentLocation")) {
            val placeName = intent.getStringExtra("placeName")
            destination = Point.fromJson(intent.getSerializableExtra("placePoint").toString())
            origin = Point.fromJson(intent.getStringExtra("currentLocation"))
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableLocationComponent(@NotNull loadedMapStyle: Style?){
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            // Location  Component
            locationComponent = mapboxMap!!.locationComponent
            locationComponent!!.activateLocationComponent(this, loadedMapStyle!!)
            locationComponent!!.isLocationComponentEnabled = true

            // Location Engine
            locationEngine = LocationEngineProvider.getBestLocationEngine(this)
            var request : LocationEngineRequest?= LocationEngineRequest.Builder(5000)
                .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                .setMaxWaitTime(5000).build()
            if (request != null)
                locationEngine!!.requestLocationUpdates(request, callback, mainLooper)
            locationEngine!!.getLastLocation(callback)
        } else{
            permissionsManager = PermissionsManager(this)
            permissionsManager!!.requestLocationPermissions(this)
        }
    }

    private fun getRoute(origin: Point?, destination: Point?){
        if (destination!=null && origin!=null) {
            NavigationRoute.builder(this)
                .accessToken(getString(R.string.access_token))
                .origin(origin)
                .destination(destination)
                .profile(DirectionsCriteria.PROFILE_WALKING)
                .build()
                .getRoute(object : Callback<DirectionsResponse> {
                    override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                        Log.v("NAVIGATION", "currentRoute " + response.body()!!.toString())
                        if (response.body() == null) {
                            Log.v("NAVIGATION", "Wrong access token.")
                            return
                        }else if (response!!.body()!!.routes().size<1){
                            Log.v("NAVIGATION", "No routes found.")
                            return
                        }
                        currentRoute = response.body()!!.routes().get(0)

                        if (navigationMapRoute !=null){
                            navigationMapRoute!!.removeRoute()
                        }else{
                            navigationMapRoute =
                                NavigationMapRoute(null, mapView!!, mapboxMap!!, R.style.NavigationMapRoute)
                        }
                        navigationMapRoute!!.addRoute(currentRoute)
                        navigationOptions = NavigationLauncherOptions.builder()
                            .directionsRoute(currentRoute)
                            .shouldSimulateRoute(false)
                            .build()
                        NavigationLauncher.startNavigation(this@NavActivity, navigationOptions)
                    }
                    override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                        Log.e("Navigation", "Error: " + t.message)
                    }
                })
        }
    }

    private fun addPoILayer(loadedMapStyle: Style){
        // 5 PoIs
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
                PropertyFactory.iconImage("RESULT-ICON-ID"),
                PropertyFactory.iconOffset(arrayOf(0f, -12f)),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconAllowOverlap(true)
            )
        )

        // Closest PoI to be announced
        loadedMapStyle.addImage("CLOSES-POI-ID", BitmapFactory.decodeResource(
            this.resources, R.drawable.red_marker_s))
        loadedMapStyle.addSource(
            GeoJsonSource(
                CLOSES_POI_SOURCE_ID, FeatureCollection.fromFeatures(arrayOf<Feature>())
            )
        )
        loadedMapStyle.addLayer(
            SymbolLayer("closes-poi-layer",CLOSES_POI_SOURCE_ID).withProperties(
                PropertyFactory.iconImage("CLOSES-POI-ID"),
                PropertyFactory.iconOffset(arrayOf(0f, -12f)),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconAllowOverlap(true)
            )
        )
    }

    override fun onRunning(running: Boolean) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun userOffRoute(location: Location?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onProgressChange(location: Location?, routeProgress: RouteProgress?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onMilestoneEvent(routeProgress: RouteProgress?, instruction: String?, milestone: Milestone?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    fun makeTilequeryApiCall(@NonNull style: Style,@NonNull point: LatLng){
        tilequery = MapboxTilequery.builder()
            .accessToken(getString(R.string.access_token))
            .mapIds("mapbox.mapbox-streets-v8")
            .query(Point.fromLngLat(point.longitude, point.latitude))
            .radius(50)
            .limit(5)
            .geometry("point")
            .dedupe(true)
            .layers("poi_label")
            .build()

        tilequery!!.enqueueCall(object : retrofit2.Callback<FeatureCollection> {
            override fun onResponse(
                call: retrofit2.Call<FeatureCollection>,
                response: retrofit2.Response<FeatureCollection>
            ) {
                if (style.isFullyLoaded) {
                    var resultSource: GeoJsonSource? = null
                    resultSource = style.getSourceAs(RESULT_GEOJSON_SOURCE_ID)//}
                    if (resultSource != null && response.body()?.features() != null) {
                        val featureCollection = response.body()?.features()
                        resultSource?.setGeoJson(FeatureCollection.fromFeatures(featureCollection!!))
                        val featureSize = featureCollection?.size
                        if (curPoI != null)
                            curPoI!!.clear()
                        if (featureSize!! > 0) {
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
                                    curPoI!!.add(poI)
                                }
                            }
                        }
                    }
                }
            }
            override fun onFailure(call: retrofit2.Call<FeatureCollection>, t: Throwable) {
            }
        })
    }

    private fun speakText(text: String ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (mTTS!=null && !mTTS!!.isSpeaking)
                mTTS!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            if (mTTS!=null &&!mTTS!!.isSpeaking)
                mTTS!!.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    // Location Permission
    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        Toast.makeText(this, R.string.user_location_permission_explanation, Toast.LENGTH_SHORT).show()
    }
    override fun onPermissionResult(granted: Boolean) {
        if (granted){
            enableLocationComponent(mapboxMap!!.style)
        }else{
            Toast.makeText(this, R.string.user_location_permission_not_granted,Toast.LENGTH_SHORT).show()
            finish()
        }
    }



    override fun onStart() {
        super.onStart()
        mapView!!.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView!!.onResume()
        mTTS = TextToSpeech(applicationContext, object : TextToSpeech.OnInitListener {
            override fun onInit(p0: Int) {
                if (p0 != TextToSpeech.ERROR) {
                    mTTS!!.language = Locale.UK

                }
            }
        })
    }

    override fun onPause() {
        super.onPause()
        mapView!!.onPause()
        /*if (tilequery!=null)
            tilequery!!.cancelBatchCall()*/
    }

    override fun onStop() {
        super.onStop()
        mapView!!.onStop()
        /*if (tilequery!=null)
            tilequery!!.cancelBatchCall()
        if (locationEngine != null)
            locationEngine!!.removeLocationUpdates(callback)
        if (mTTS !=null){
            mTTS!!.stop()
            if (mapboxMap!!.locationComponent.isLocationComponentActivated)
                mapboxMap!!.locationComponent.onStop()
        }*/
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        mapView!!.onSaveInstanceState(outState!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView!!.onDestroy()
        mapboxMap!!.locationComponent.onDestroy()
        if (tilequery!=null)
            tilequery!!.cancelBatchCall()
        if (locationEngine != null)
            locationEngine!!.removeLocationUpdates(callback)
        if (mTTS !=null)
            mTTS!!.shutdown()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView!!.onLowMemory()
        mTTS!!.shutdown()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        if (mTTS !=null){
            mTTS!!.shutdown()
        }
    }

    class MapBoxLocationCallback : LocationEngineCallback<LocationEngineResult> {
        override fun onFailure(exception: Exception) {
            var activity : NavActivity = activityWeakReference?.get()!!
            if (activity != null) {
                Toast.makeText(activity, exception.localizedMessage,
                    Toast.LENGTH_SHORT).show()
            }
        }
        @SuppressLint("RestrictedApi")
        override fun onSuccess(result: LocationEngineResult?) {
            var activity : NavActivity = activityWeakReference?.get()!!
            if (activity != null) {

                var point : LatLng ?= LatLng(result?.lastLocation?.latitude!!, result.lastLocation?.longitude!!)
                if (point!=null){
                    activity.currentLocation = Point.fromLngLat(point!!.longitude,point!!.latitude)
                }
                activity.makeTilequeryApiCall(activity.mapboxMap!!.style!!,point!!)
                if (activity.mapboxMap != null && result.lastLocation != null){
                    activity.mapboxMap!!.locationComponent.forceLocationUpdate(result.lastLocation)
                    val position = CameraPosition.Builder()
                        .target(point)
                        .zoom(18.0)
                        .tilt(0.0)
                        .build()
                    activity.mapboxMap!!.animateCamera(CameraUpdateFactory.newCameraPosition(position))
                }
                // Initialise lastQueryLocation, update and announce every 20 meters
                if (activity.lastQueryLocation == null){
                    activity.lastQueryLocation = point
                }else if (point.distanceTo(activity.lastQueryLocation!!)>=20 && activity.mapboxMap!!.style!!!=null){
                    // Closes PoI
                    var minDistancePoI = activity.curPoI.minBy { it ->  it.distance }
                    var poiSource: GeoJsonSource?  = activity.mapboxMap!!.style!!.getSourceAs(activity.CLOSES_POI_SOURCE_ID)
                    poiSource?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(minDistancePoI!!.long,minDistancePoI!!.lat)))
                    if (activity.curPoI !=null )
                        activity.lastQueryLocation = point
                    // User bearing in range (0.0, 360.0]
                    var userBearing = result.lastLocation?.bearing
                    // User LatLng
                    var lat1 = result.lastLocation?.latitude
                    var lng1 = result.lastLocation?.longitude
                    // Closes PoI LatLng
                    var lat2  = minDistancePoI?.lat
                    var lng2 = minDistancePoI?.long
                    // Calculate bearing between User and PoI
                    var dLon = (lng2!! - lng1!!)
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
                    var announcement: String = minDistancePoI?.name + " " + minDistancePoI?.category_en + " to your " + closesPoIPoistion
                    // Trim " "
                    announcement = announcement.replace("\"", "")
                    activity.speakText(announcement)
                }
            }
        }
        private var activityWeakReference: WeakReference<NavActivity>?
        constructor(activity: NavActivity)
        {
            this.activityWeakReference = WeakReference<NavActivity>(activity)
        }
    }
}
