package s1875880.maplessnav

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favourite)

//        favouritesTV = findViewById(R.id.favouritesTV)

        favourites_rv.layoutManager = LinearLayoutManager(this)


        addFav.setOnClickListener {
            intent = Intent(this,AddFavActivity::class.java)
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
            favourites_rv.adapter = FavouriteAdapter(applicationContext, favouritesNameList, favouritesPointList)

        }

        cursor.close()

    }

    private fun getIncomingIntent(){
        if (intent.hasExtra("placeName") && intent.hasExtra("placeCentre") ) {
            val placeName = intent.getStringExtra("placeName")
            val placeCentre =  intent.getSerializableExtra("placeCentre") as? Point

          //  Toast.makeText(applicationContext,placeName + " " +placeCentre,Toast.LENGTH_SHORT).show()

        }
    }


    override fun onBackPressed() {
        super.onBackPressed()
        intent = Intent(this,MapBoxActivity::class.java)
        startActivity(intent)
    }

}
