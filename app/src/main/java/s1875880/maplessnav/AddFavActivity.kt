package s1875880.maplessnav

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import com.mapbox.api.geocoding.v5.MapboxGeocoding
import com.mapbox.api.geocoding.v5.models.GeocodingResponse
import kotlinx.android.synthetic.main.activity_add_fav.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AddFavActivity : AppCompatActivity() {

    var address: EditText? = null
    var mapboxGeocoding: MapboxGeocoding?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_fav)
        address = findViewById(R.id.add_fav_address)

        // Add TextWatcher listener.
        //address!!.addTextChangedListener()

        find_address.setOnClickListener {
            if (address!!.text.toString().trim().isNotEmpty()) {
                mapboxGeocoding = MapboxGeocoding.builder()
                    .accessToken(getString(R.string.access_token))
                    .query(address!!.text.toString())
                    .build()


                mapboxGeocoding!!.enqueueCall(object : Callback<GeocodingResponse> {
                    override fun onResponse(call: Call<GeocodingResponse>, response: Response<GeocodingResponse>) {
                        val results = response.body()!!.features()
                        if (results.size > 0) {
                            // Log the first results Point.
                            val firstResultPoint = results[0].center()

                            //Response: Log.v("GEOCODE", results[0].toString())
                            for (result in results){
                                Log.v("GEOCODE", result.placeName())
                            }

                        } else {
                            Toast.makeText(applicationContext,"Address not found", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<GeocodingResponse>, throwable: Throwable) {
                        throwable.printStackTrace()
                    }
                })

            }else{
                Toast.makeText(applicationContext,"Enter an address", Toast.LENGTH_SHORT).show()
            }


        }


    }



    override fun onDestroy() {
        //mapboxGeocoding!!.cancelCall()

        super.onDestroy()
    }
}

