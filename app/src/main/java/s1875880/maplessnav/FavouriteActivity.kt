package s1875880.maplessnav

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import com.mapbox.geojson.Point
import kotlinx.android.synthetic.main.activity_favourite.*
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.geocode_result.*


class FavouriteActivity : AppCompatActivity() {

    /*var favouritesTV : TextView ?=null*/
    var favouritesNameList: ArrayList<String>? = ArrayList<String>()
    var favouritesPointList: ArrayList<String>? =  ArrayList<String>()
    var currentLocation: Point ?= null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favourite)

//        favouritesTV = findViewById(R.id.favouritesTV)

        favourites_rv.layoutManager = LinearLayoutManager(this)


        addFav.setOnClickListener {
            intent = Intent(this,AddFavActivity::class.java)
            intent.putExtra("currentLocation",currentLocation!!.toJson())
            startActivity(intent)
        }



        getIncomingIntent()

//        favouritesTV!!.text =""
        val dbHandler = DBHelper(this,null)

        val cursor = dbHandler.getAllFavourite()
        cursor!!.moveToFirst()
        if (cursor!!.count > 0) {
//            favouritesTV!!.append((cursor.getString(cursor.getColumnIndex(DBHelper.COLUMN_NAME))))
//            favouritesTV!!.append((cursor.getString(cursor.getColumnIndex(DBHelper.COLUMN_POINT))))
            while (cursor.moveToNext()) {
        /*        favouritesTV!!.append((cursor.getString(cursor.getColumnIndex(DBHelper.COLUMN_NAME))))
                favouritesTV!!.append((cursor.getString(cursor.getColumnIndex(DBHelper.COLUMN_POINT))))
                favouritesTV!!.append("\n")
                favouritesTV!!.append("\n")*/

                favouritesNameList!!.add((cursor.getString(cursor.getColumnIndex(DBHelper.COLUMN_NAME))))
                favouritesPointList!!.add((cursor.getString(cursor.getColumnIndex(DBHelper.COLUMN_POINT))))

            }

            if (favourites_rv.adapter==null)
                Log.v("NULLFINDER", "adapter")
            if (currentLocation == null)
                Log.v("NULLFINDER", "currentLocation")
            favourites_rv.adapter = FavouriteAdapter(applicationContext, favouritesNameList, favouritesPointList, currentLocation!!.toJson())
        }
        cursor.close()
    }

    private fun getIncomingIntent(){
        if (intent.hasExtra("currentLocation")) {
            currentLocation = Point.fromJson(intent.getStringExtra("currentLocation"))
            Log.v("CURRENT_LOCATION",currentLocation!!.toJson())
        }
    }


    override fun onBackPressed() {
        super.onBackPressed()
        intent = Intent(this,MapBoxActivity::class.java)
        startActivity(intent)
    }

}
