package s1875880.maplessnav

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat.startActivity
import androidx.recyclerview.widget.RecyclerView
import com.mapbox.geojson.Point
import kotlinx.android.synthetic.main.geocode_result.view.*

/**
 * Created by Andreas Neokleous.
 */

class GeocodeResultAdapter(val context: Context, val placeNames: ArrayList<String>?, val placeCenters: ArrayList<Point>?,val currentLocation: String) : RecyclerView.Adapter<ViewHolder>() {

    // Gets the number of places in the list
    override fun getItemCount(): Int {
        return placeNames!!.size
    }

    // Inflates the item views
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(context).inflate(R.layout.geocode_result, parent, false))
    }

    // Binds each place in the ArrayList to a view
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.geocodeTV?.text = placeNames!!.get(position)
        holder.parentLayout.setOnClickListener {

            if (placeExists(placeNames.get(position))){
                Toast.makeText(context,"Already added.", Toast.LENGTH_SHORT).show()
            }else {
                // add to database on favourite click
                val dbHandler = DBHelper(context, null)
                val fav = Favourite(placeNames.get(position), placeCenters?.get(position)!!.toJson())
                dbHandler.addFavourite(fav)
                Toast.makeText(context, "Added to favourites", Toast.LENGTH_SHORT).show()

                // switch to previous activity
                val intent = Intent(context, FavouriteActivity::class.java)
                intent.putExtra("placeName", placeNames.get(position))
                intent.putExtra("placeCentre", placeCenters.get(position))
                intent.putExtra("currentLocation", currentLocation)
                context.startActivity(intent)
            }
        }
    }

    fun placeExists(placeName: String) : Boolean {
        val dbHandler = DBHelper(context, null)
        val cursor = dbHandler.getAllFavourite()
        cursor!!.moveToFirst()
        if (cursor.count > 0) {
            do {
                if (placeName == cursor.getString(cursor.getColumnIndex(DBHelper.COLUMN_NAME))){
                        cursor.close()
                        return true
                    }
            } while (cursor.moveToNext())
        }

        cursor.close()
        return false
    }
}

class ViewHolder (view: View) : RecyclerView.ViewHolder(view) {
    // Holds the TextView that will add each animal to
    val geocodeTV = view.geocode_result_tv
    val parentLayout = view.parent_layout

}