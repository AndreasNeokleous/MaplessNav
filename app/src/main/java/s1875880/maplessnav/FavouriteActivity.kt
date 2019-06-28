package s1875880.maplessnav

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_favourite.*

class FavouriteActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favourite)

        addFav.setOnClickListener {
            intent = Intent(this,AddFavActivity::class.java)
            startActivity(intent)
        }
    }
}
