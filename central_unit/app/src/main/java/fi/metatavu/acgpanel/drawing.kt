package fi.metatavu.acgpanel

import android.preference.PreferenceManager
import android.widget.ImageView
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import fi.metatavu.acgpanel.model.Product
import java.io.File
import java.util.concurrent.Executors

// TODO make RemoteImageView
// TODO configurable
const val DEFAULT_PRODUCT_IMAGE_PREFIX = "http://tuotetiedot.metatavu.io"

fun drawProduct(product: Product, view: ImageView) {
    drawProducts(listOf(Pair(product, view)))
}

// TODO share Picasso instances
fun drawProducts(pairs: List<Pair<Product, ImageView>>) {
    val executor = Executors.newFixedThreadPool(3)
    val context = PanelApplication.instance.applicationContext
    val cacheDir = File(context.cacheDir.path + File.separator + "productImages")
    val cacheSize = 512L*1024L*1024L // 512 MB
    val productImagePrefix = PreferenceManager
        .getDefaultSharedPreferences(PanelApplication.instance)
        .getString(PanelApplication.instance.getString(R.string.pref_key_user_asset_url), DEFAULT_PRODUCT_IMAGE_PREFIX)
    val picasso = Picasso
        .Builder(context)
        .executor(executor)
        .downloader(OkHttp3Downloader(cacheDir, cacheSize))
        .build()
    for ((product, view) in pairs) {
        picasso
            .load("$productImagePrefix${product.image}")
            //.placeholder(R.drawable.image_placeholder)
            .error(R.drawable.image_placeholder)
            .noFade()
            .into(view)
    }
}

