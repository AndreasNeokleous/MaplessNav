package s1875880.maplessnav

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
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

import com.mapbox.api.tilequery.MapboxTilequery;
import javax.security.auth.callback.Callback


class MapBoxActivity : AppCompatActivity(), OnMapReadyCallback, PermissionsListener, MapboxMap.OnMapClickListener {
    private var permissionsManager: PermissionsManager = PermissionsManager(this)
    private lateinit var mapboxMap: MapboxMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(this, "pk.eyJ1IjoiYW5kcmVhc25lb2tlbG91cyIsImEiOiJjanR5NXdoNHEwZW9kM3lwbnRobXNxdGFmIn0.LkYWF5avM-_JVB27lS25zg")
        // called after access token
        setContentView(R.layout.activity_map_box)

      //  mapView = findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this)


    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        this.mapboxMap = mapboxMap
        mapboxMap.setStyle(Style.Builder().fromUrl(
            "mapbox://styles/mapbox/cjerxnqt3cgvp2rmyuxbeqme7")){
            enableLocationComponent(it)
           // addClickLayer(it)
           // addResultLayer(it)
          //  mapboxMap.addOnMapClickListener(this)


        } }

    override fun onMapClick(point: LatLng): Boolean {
        var style: Style? = mapboxMap.getStyle();
        if (style != null){
            var clickLocationSource: GeoJsonSource?  = style.getSourceAs("CLICK_CENTER_GEOJSON_SOURCE_ID");
            if (clickLocationSource != null) {
                clickLocationSource.setGeoJson(Feature.fromGeometry(Point.fromLngLat(point.getLongitude(),
                    point.getLatitude())));
            }

            makeTilequeryApiCall(style, point);
            return true
        }
        return false
    }

    private fun makeTilequeryApiCall(style: Style, point: LatLng){
        var tilequery: MapboxTilequery = MapboxTilequery.builder()
            .accessToken("pk.eyJ1IjoiYW5kcmVhc25lb2tlbG91cyIsImEiOiJjanR5NXdoNHEwZW9kM3lwbnRobXNxdGFmIn0.LkYWF5avM-_JVB27lS25zg")
            .mapIds("mapbox.mapbox-streets-v8")
            .query(Point.fromLngLat(point.getLongitude(), point.getLatitude()))
            .radius(50)
            .limit(20)
            .geometry("point")
            .dedupe(true)
            .layers("poi_label")
            .build()

    //    tilequery.enqueueCall()
    }

    private fun addClickLayer(loadedMapStyle: Style){
        loadedMapStyle.addImage("Click",BitmapFactory.decodeResource(
            this.getResources(), R.drawable.abc_ab_share_pack_mtrl_alpha))

        loadedMapStyle.addSource(
            GeoJsonSource(
                "CLICK_CENTER_GEOJSON_SOURCE_ID",
                FeatureCollection.fromFeatures(arrayOf<Feature>())
            )
        )

        loadedMapStyle.addLayer(
            SymbolLayer("click-layer", "CLICK_CENTER_GEOJSON_SOURCE_ID").withProperties(
                iconImage("CLICK-ICON-ID"),
                iconOffset(arrayOf(0f, -12f)),
                iconIgnorePlacement(true),
                iconAllowOverlap(true)
            )
        )
    }

    private fun addResultLayer(loadedMapStyle: Style){
        loadedMapStyle.addImage("RESULT", BitmapFactory.decodeResource(
            this.getResources(), R.drawable.abc_btn_colored_material))

        loadedMapStyle.addSource(
            GeoJsonSource(
                "RESULT_GEOJSON_SOURCE_ID",
                FeatureCollection.fromFeatures(arrayOf())
            )
        )

        loadedMapStyle.addLayer(
            SymbolLayer("LAYER_ID", "RESULT_GEOJSON_SOURCE_ID").withProperties(
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


        } else{
            permissionsManager = PermissionsManager(this)
            permissionsManager.requestLocationPermissions(this)

        }
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
        mapView?.onStart()
    }

    public override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    public override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    public override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }
}
