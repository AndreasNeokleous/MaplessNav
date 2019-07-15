package s1875880.maplessnav

import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.favourite_view.view.*
import kotlinx.android.synthetic.main.geocode_result.view.*

/**
 * Created by Andreas Neokleous.
 */

class FavouriteAdapter(val context: Context, val placeNames: ArrayList<String>?, val placePoints: ArrayList<String>?, val currentLocation: String) : RecyclerView.Adapter<FavViewHolder>(){


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavViewHolder {
        return FavViewHolder(LayoutInflater.from(context).inflate(R.layout.favourite_view, parent, false))
    }

    override fun getItemCount(): Int {
        return placeNames!!.size
    }

    override fun onBindViewHolder(holder: FavViewHolder, position: Int) {
        holder?.favTV?.text = placeNames!!.get(position)

        holder?.parentLayout.setOnClickListener {
            val intent = Intent(context, MapBoxActivity::class.java)
            intent.putExtra("placeName", placeNames?.get(position))
            intent.putExtra("placePoint", placePoints?.get(position))
            intent.putExtra("currentLocation", currentLocation)
            context.startActivity(intent)


        }

        holder?.del_fav.setOnClickListener {
            val dbHandler = DBHelper(context, null)
            dbHandler.deleteFavourite(placeNames!!.get(position))

            Toast.makeText(context,"Favourite Deleted",Toast.LENGTH_SHORT).show()
            placeNames!!.removeAt(position)
            placePoints!!.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, placeNames!!.size)
        }


    }


}

class FavViewHolder (view: View) : RecyclerView.ViewHolder(view){


    val favTV = view.favourites_tv
    val parentLayout = view.fav_parent_layout
    val del_fav = view.del_fav



}