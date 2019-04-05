package s1875880.maplessnav

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.telecom.Call
import android.util.Log
import android.widget.Toast
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.Response
import com.mapbox.android.core.location.*
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.Style.OnStyleLoaded
import com.mapbox.mapboxsdk.maps.Style.MAPBOX_STREETS
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import kotlinx.android.synthetic.main.activity_map_box.*
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
import javax.security.auth.callback.Callback


class MapBoxActivity : AppCompatActivity(), OnMapReadyCallback, PermissionsListener, MapboxMap.OnMapClickListener {

    private var permissionsManager: PermissionsManager = PermissionsManager(this)
    private lateinit var mapboxMap: MapboxMap
    private lateinit var mapView: MapView
    private val CLICK_CENTER_GEOJSON_SOURCE_ID = "CLICK_CENTER_GEOJSON_SOURCE_ID"
    private val RESULT_GEOJSON_SOURCE_ID = "RESULT_GEOJSON_SOURCE_ID"
    private val LAYER_ID = "LAYER_ID"
    private lateinit var locationEngine: LocationEngine
    private val callback = MapBoxLocationCallback(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(this, "pk.eyJ1IjoiYW5kcmVhc25lb2tlbG91cyIsImEiOiJjanR5NXdoNHEwZW9kM3lwbnRobXNxdGFmIn0.LkYWF5avM-_JVB27lS25zg")
        // called after access token
        setContentView(R.layout.activity_map_box)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)


    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        this.mapboxMap = mapboxMap
     //   mapboxMap.setStyle(Style.Builder().fromUrl(
     //        "mapbox://styles/mapbox/cjerxnqt3cgvp2rmyuxbeqme7")){
        mapboxMap.setStyle(Style.MAPBOX_STREETS, object:  Style.OnStyleLoaded {
            override fun onStyleLoaded(style: Style) {
                addClickLayer(style)
                addResultLayer(style)
                enableLocationComponent(style)
                mapboxMap.addOnMapClickListener(this@MapBoxActivity)

            }


        })
           //

    //    }
    }

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
                //if (resultSource != null && response.body()?.features() != null){
                val featureCollection = response.body()?.features()
                resultSource?.setGeoJson(FeatureCollection.fromFeatures(featureCollection!!))

                //}
            }

            override fun onFailure(call: retrofit2.Call<FeatureCollection>, t: Throwable) {
               // Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show()
            }

        })
    }

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
            locationComponent.renderMode = RenderMode.COMPASS

            mapboxMap.animateCamera(
                CameraUpdateFactory
                    .newCameraPosition(
                        CameraPosition.Builder()
                            .zoom(22.0)
                            .build()
                    ), 2000
            )

            initLocationEngine()
        } else{
            permissionsManager = PermissionsManager(this)
            permissionsManager.requestLocationPermissions(this)

        }
    }

    @SuppressLint("MissingPermission")
    private fun initLocationEngine(){
        locationEngine = LocationEngineProvider.getBestLocationEngine(this)

        var request : LocationEngineRequest ?= LocationEngineRequest.Builder(1000)
            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .setMaxWaitTime(5000).build()

        if (request != null) {
            locationEngine.requestLocationUpdates(request, callback, getMainLooper())
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

    public override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    public override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    public override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    public override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    private class MapBoxLocationCallback : LocationEngineCallback<LocationEngineResult> {
        override fun onFailure(exception: Exception) {
            var activity : MapBoxActivity = activityWeakReference?.get()!!

            if (activity != null) {
                Toast.makeText(activity, exception.getLocalizedMessage(),
                    Toast.LENGTH_SHORT).show();
            }
        }

        override fun onSuccess(result: LocationEngineResult?) {
            var activity : MapBoxActivity = activityWeakReference?.get()!!
            if (activity != null) {
                var location: Location? = result?.lastLocation
                if ( location == null ){
                    return;
                }

              //  Toast.makeText(activity, "New location" + " " +
               //     result?.getLastLocation()?.getLatitude().toString() + " " + result?.getLastLocation()?.getLongitude().toString(),
              //      Toast.LENGTH_SHORT).show()
                var point : LatLng ?= LatLng(result?.getLastLocation()?.getLatitude()!!, result?.getLastLocation()?.getLongitude()!!)
                activity.makeTilequeryApiCall(activity.mapboxMap.style!!,point!!)

                if (activity.mapboxMap != null && result?.lastLocation != null){
                    activity.mapboxMap.locationComponent.forceLocationUpdate(result.lastLocation)
                }
            }
        }

        private var activityWeakReference: WeakReference<MapBoxActivity>?

        constructor(activity: MapBoxActivity)
        {
            this.activityWeakReference = WeakReference<MapBoxActivity>(activity)
        }
    }
}
