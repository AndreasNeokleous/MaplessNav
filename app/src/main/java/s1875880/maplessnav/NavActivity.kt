package s1875880.maplessnav

import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.mapbox.services.android.navigation.ui.v5.listeners.NavigationListener
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.geojson.Point
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute
import retrofit2.Call
import retrofit2.Response
import retrofit2.Callback
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.BannerInstructions
import com.mapbox.api.tilequery.MapboxTilequery
import com.mapbox.geojson.FeatureCollection
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.services.android.navigation.ui.v5.*
import com.mapbox.services.android.navigation.ui.v5.listeners.BannerInstructionsListener
import com.mapbox.services.android.navigation.ui.v5.listeners.InstructionListListener
import com.mapbox.services.android.navigation.ui.v5.listeners.SpeechAnnouncementListener
import com.mapbox.services.android.navigation.ui.v5.voice.SpeechAnnouncement
import java.util.*
import kotlin.math.roundToInt


class NavActivity : AppCompatActivity(), OnNavigationReadyCallback,
    NavigationListener, ProgressChangeListener, InstructionListListener, SpeechAnnouncementListener,
    BannerInstructionsListener, TextToSpeech.OnInitListener  {

    // Navigation Options
    private var ORIGIN = Point.fromLngLat(-3.183719, 55.944481)
    private var DESTINATION = Point.fromLngLat(-3.186766, 55.944625)
    private val INITIAL_ZOOM = 16.0
    // Navigation Layout
    private var navigationView: NavigationView? = null
    private var spacer: View? = null
    private var speedWidget: TextView ?= null
    private var bottomSheetVisible = true
    private var instructionListShown = false
    // Tilequery API
    private var tilequeryResponsePoI  = arrayListOf<PoI>()
    private var lastQueryLocation : Location? = null
    // Text-to-speech engine
    private val ACT_CHECK_TTS_DATA = 12345
    private var mTTS: TextToSpeech? = null
    private var imm: InputMethodManager? = null

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_AppCompat_Light_NoActionBar)
        getIncomingIntent()
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(this,getString(R.string.access_token))
        setContentView(R.layout.activity_nav)
        navigationView = findViewById(R.id.navigationView)
        speedWidget = findViewById(R.id.speed_limit)
        spacer = findViewById(R.id.spacer)
        setSpeedWidgetAnchor(R.id.summaryBottomSheet)

        val initialPosition : CameraPosition =  CameraPosition.Builder()
            .target(LatLng(ORIGIN.latitude(),ORIGIN.longitude()))
            .zoom(INITIAL_ZOOM)
            .build()
        navigationView!!.onCreate(savedInstanceState)
        navigationView!!.initialize(this, initialPosition)
        val soundButton = (navigationView!!.retrieveSoundButton() as SoundButton)
        val soundFab =
            soundButton.findViewById<FloatingActionButton>(com.mapbox.services.android.navigation.ui.v5.R.id.soundFab)
        soundFab!!.visibility = View.GONE
        val feedbackButton = (navigationView!!.retrieveFeedbackButton() as FeedbackButton)
        val feedbackFab =
            feedbackButton.findViewById<FloatingActionButton>(com.mapbox.services.android.navigation.ui.v5.R.id.feedbackFab)
        feedbackFab!!.visibility = View.GONE

        imm = this.getSystemService(Service.INPUT_METHOD_SERVICE) as InputMethodManager?
        val ttsIntent: Intent? = Intent()
        ttsIntent!!.action = TextToSpeech.Engine.ACTION_CHECK_TTS_DATA
        startActivityForResult(ttsIntent, ACT_CHECK_TTS_DATA)
        val locManager: LocationManager  = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            Toast.makeText(this,"Please enable location services", Toast.LENGTH_LONG).show()
        }
    }


    override fun onNavigationReady(isRunning: Boolean) {
        fetchRoute()
    }

    override fun onNavigationFinished() {
        mTTS!!.stop()
        mTTS!!.shutdown()
        finish()
    }

    override fun onNavigationRunning() {
    }

    override fun onCancelNavigation() {
        mTTS!!.stop()
        mTTS!!.shutdown()
        finish()
    }

    override fun onProgressChange(location: Location?, routeProgress: RouteProgress?) {
        setSpeed(location!!)
        if (lastQueryLocation == null) {
            lastQueryLocation = location
            makeTilequeryCall(location)
        }else if (location.distanceTo(lastQueryLocation!!)>=20){
            lastQueryLocation = location
            makeTilequeryCall(location)
        }
    }

    override fun onInstructionListVisibilityChanged(visible: Boolean) {
        instructionListShown = visible
        speedWidget!!.visibility = if (visible) View.GONE else View.VISIBLE
    }

    override fun willVoice(announcement: SpeechAnnouncement?): SpeechAnnouncement {
        return  announcement!!
    }

    override fun willDisplay(instructions: BannerInstructions?): BannerInstructions {
        return instructions!!
    }


    private fun startNavigation(directionsRoute: DirectionsRoute ) {
        val options: NavigationViewOptions.Builder  =
      NavigationViewOptions.builder()
        .navigationListener(this)
        .directionsRoute(directionsRoute)
        .shouldSimulateRoute(false)
        .progressChangeListener(this)
        .instructionListListener(this)
        .speechAnnouncementListener(this)
        .bannerInstructionsListener(this)

    setBottomSheetCallback(options)
    navigationView!!.startNavigation(options.build())
    }
    private fun fetchRoute() {
        NavigationRoute.builder(this)
          .accessToken(getString(R.string.access_token))
          .origin(ORIGIN)
          .destination(DESTINATION)
          .alternatives(true)
          .profile(DirectionsCriteria.PROFILE_WALKING)
          .build()
          .getRoute(object : Callback<DirectionsResponse> {
              override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
              }
              override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                  val directionsRoute: DirectionsRoute = response.body()!!.routes()[0]
                  startNavigation(directionsRoute)
              }
          })
    }

    private fun setSpeedWidgetAnchor(@IdRes res : Int) {
        val layoutParams : CoordinatorLayout.LayoutParams  = spacer!!.layoutParams as  (CoordinatorLayout.LayoutParams)
        layoutParams.anchorId = res
        spacer!!.layoutParams = layoutParams
    }
    private fun setBottomSheetCallback(options: NavigationViewOptions.Builder)
    {
        options.bottomSheetCallback(object: BottomSheetBehavior.BottomSheetCallback () {
            override fun onSlide(p0: View, p1: Float) {
            }
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when(newState) {
                    BottomSheetBehavior.STATE_HIDDEN ->
                        {
                        bottomSheetVisible = false
                        setSpeedWidgetAnchor(R.id.recenterBtn)

                        }
                    BottomSheetBehavior.STATE_EXPANDED ->
                    {
                        bottomSheetVisible = true
                    }
                    BottomSheetBehavior.STATE_SETTLING ->
                    {
                        if (!bottomSheetVisible) {
                            setSpeedWidgetAnchor(R.id.summaryBottomSheet)
                        }
                    }
                    else -> return
                }
            }
        })
    }


    private fun setSpeed(location: Location){
        val string: String = String.format("%d\nMPH",(location.speed * 2.2369).roundToInt())
        val mphTextSize : Int  = resources!!.getDimensionPixelSize(R.dimen.mph_text_size)
        val speedTextSize: Int = resources!!.getDimensionPixelSize(R.dimen.speed_text_size)

        val spannableString = SpannableString(string)
        spannableString.setSpan(AbsoluteSizeSpan(mphTextSize),
            string.length - 4, string.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)

        spannableString.setSpan(AbsoluteSizeSpan(speedTextSize),
          0, string.length - 3, Spanned.SPAN_INCLUSIVE_INCLUSIVE)

        speedWidget!!.text = spannableString
        if (!instructionListShown) {
            speedWidget!!.visibility = View.VISIBLE
        }
    }

    @SuppressLint("RestrictedApi")
    private fun getIncomingIntent(){
        if (intent.hasExtra("placeName") && intent.hasExtra("placePoint")  && intent.hasExtra("currentLocation")) {
            DESTINATION = Point.fromJson(intent.getSerializableExtra("placePoint").toString())
            ORIGIN = Point.fromJson(intent.getStringExtra("currentLocation"))
        }
    }

    /**
     * Make Tilequery call to get the closest PoI and announce it.
     */
    private fun makeTilequeryCall(location: Location?) {
        val tilequery : MapboxTilequery = MapboxTilequery.builder()
            .accessToken(getString(R.string.access_token))
            .mapIds("mapbox.mapbox-streets-v8")
            .query(Point.fromLngLat(location!!.longitude, location.latitude))
            .radius(50)
            .limit(5)
            .geometry("point")
            .dedupe(true)
            .layers("poi_label")
            .build()
        tilequery.enqueueCall(object : Callback<FeatureCollection> {
            override fun onResponse(
                call: Call<FeatureCollection>,
                response: Response<FeatureCollection>
            ) {
                    if (response.body()?.features() != null) {
                        val featureCollection = response.body()?.features()
                        val featureSize = featureCollection?.size
                        tilequeryResponsePoI.clear()
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
                                    tilequeryResponsePoI.add(poI)
                                }
                            }
                            val closestPoI : PoI?= tilequeryResponsePoI.minBy { it.distance }
                            val leftOrRight = getRelativeLocation(closestPoI, location )
                            var announcement: String = closestPoI?.name + " " + closestPoI?.category_en + " to your " + leftOrRight
                            announcement = announcement.replace("\"", "")
                            speakText(announcement)
                        }
                    }
            }
            override fun onFailure(call: Call<FeatureCollection>, t: Throwable) {
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
        // Calculate bearing between User and PoI
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

    private fun speakText(text: String ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (mTTS!=null && !mTTS!!.isSpeaking)
                mTTS!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            if (mTTS!=null &&!mTTS!!.isSpeaking)
                mTTS!!.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    public override fun onStart() {
        super.onStart()
        navigationView!!.onStart()
    }

    public override fun onResume() {
        super.onResume()
        navigationView!!.onResume()
    }

    override fun onLowMemory() {
        navigationView!!.onLowMemory()
        mTTS!!.stop()
        mTTS!!.shutdown()
        super.onLowMemory()
    }

    override fun onBackPressed() {
        mTTS!!.stop()
        mTTS!!.shutdown()
        // If the navigation view didn't need to do anything, call super
        if (!navigationView!!.onBackPressed()) {
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        navigationView!!.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        navigationView!!.onRestoreInstanceState(savedInstanceState)
    }

    public override fun onPause() {
        super.onPause()
        navigationView!!.onPause()
    }

    public override fun onStop() {
        navigationView!!.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        navigationView!!.onDestroy()
        mTTS!!.stop()
        mTTS!!.shutdown()
        super.onDestroy()
    }
}
