package s1875880.maplessnav

import android.annotation.SuppressLint
import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigation
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class Navigation : AppCompatActivity(), OnMapReadyCallback {


    var navigation: MapboxNavigation?=null
    var destination: Point?=null
    var origin: Point?=null
  //  var navigationOptions: NavigationLauncherOptions?=null
    var directionsRoute: DirectionsRoute?=null
 //   var navigationMapRoute: NavigationMapRoute?=null
    var mapboxMap: MapboxMap ?=null
    var mapView: MapView ?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation)

        mapView = findViewById(R.id.mapViewNav)
        mapView!!.onCreate(savedInstanceState)
        mapView!!.getMapAsync(this)

        getIncomingIntent()
        navigation = MapboxNavigation(this, getString(R.string.access_token))

    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        this.mapboxMap = mapboxMap
        mapboxMap.setStyle(Style.MAPBOX_STREETS, object:  Style.OnStyleLoaded {
            override fun onStyleLoaded(style: Style) {
                var navigationOptions = NavigationLauncherOptions.builder()
                    .directionsRoute(directionsRoute)
                    .shouldSimulateRoute(true)
                    .build()
                NavigationLauncher.startNavigation(applicationContext as Activity?, navigationOptions)
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

        if (destination!=null && origin!=null) {
            NavigationRoute.builder(this)
                .accessToken(getString(R.string.access_token))
                .origin(origin!!)
                .destination(destination!!)
                .build()
                .getRoute(object : Callback<DirectionsResponse> {
                    override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                        directionsRoute = response.body()!!.routes()[0]
                        Log.v("NAVIGATION", "directionsRoute " + directionsRoute.toString())
                    }
                    override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                    }
                })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        navigation!!.onDestroy()
    }
}
