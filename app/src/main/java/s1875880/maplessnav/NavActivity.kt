package s1875880.maplessnav

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.NonNull
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute
import org.jetbrains.annotations.NotNull
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class NavActivity : AppCompatActivity(), OnMapReadyCallback, PermissionsListener {


    var mapView: MapView ?=null
    var mapboxMap: MapboxMap ?=null
    var locationComponent : LocationComponent?= null
    var currentRoute: DirectionsRoute?=null
    val TAG: String = "TAG"
    var navigationMapRoute: NavigationMapRoute ?= null
//    var navigation: MapboxNavigation?=null
    var destination: Point?=null
    var origin: Point?=null
    var navigationOptions:NavigationLauncherOptions?=null
    var permissionsManager:PermissionsManager?=null
  //  var navigationOptions: NavigationLauncherOptions?=null

 //   var navigationMapRoute: NavigationMapRoute?=null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(this,getString(R.string.access_token))
        setContentView(R.layout.activity_nav)
        mapView = findViewById(R.id.mapViewNav)
        mapView!!.onCreate(savedInstanceState)
        mapView!!.getMapAsync(this)


      //  navigation = MapboxNavigation(this, getString(R.string.access_token))

    }

    override fun onMapReady(@NonNull mapboxMap: MapboxMap) {
        this.mapboxMap = mapboxMap
        mapboxMap.setStyle(Style.MAPBOX_STREETS, object:  Style.OnStyleLoaded {
            override fun onStyleLoaded(@NonNull style: Style) {
                getIncomingIntent()
                enableLocationComponent(style)
                getRoute(origin, destination)


            }
        })
    }

    private fun getRoute(origin: Point?, destination: Point?){
        if (destination!=null && origin!=null) {
            NavigationRoute.builder(this)
                .accessToken(getString(R.string.access_token))
                .origin(origin)
                .destination(destination)
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
                        Log.e(TAG, "Error: " + t.message)
                    }
                })
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableLocationComponent(@NotNull loadedMapStyle: Style?){
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            locationComponent = mapboxMap!!.locationComponent
            locationComponent!!.activateLocationComponent(this, loadedMapStyle!!)
            locationComponent!!.isLocationComponentEnabled = true
        } else{
            permissionsManager = PermissionsManager(this)
            permissionsManager!!.requestLocationPermissions(this)
        }
    }

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


    @SuppressLint("RestrictedApi")
    private fun getIncomingIntent(){
        if (intent.hasExtra("placeName") && intent.hasExtra("placePoint")  && intent.hasExtra("currentLocation")) {
            val placeName = intent.getStringExtra("placeName")
            destination = Point.fromJson(intent.getSerializableExtra("placePoint").toString())
            origin = Point.fromJson(intent.getStringExtra("currentLocation"))
        }
    }

    override fun onStart() {
        super.onStart()
        mapView!!.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView!!.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView!!.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView!!.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        mapView!!.onSaveInstanceState(outState!!)
    }
    override fun onDestroy() {
        super.onDestroy()
        mapView!!.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView!!.onLowMemory()
    }
}
