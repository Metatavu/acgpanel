package fi.metatavu.acgpanel

import android.widget.ImageView
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import fi.metatavu.acgpanel.model.Product
import java.io.File
import java.util.concurrent.Executors

// TODO make RemoteImageView
// TODO configurable
const val PRODUCT_IMAGE_PREFIX = "http://tuotetiedot.metatavu.io"

fun drawProduct(product: Product, view: ImageView) {
    drawProducts(listOf(Pair(product, view)))
}

// TODO share Picasso instances
fun drawProducts(pairs: List<Pair<Product, ImageView>>) {
    val executor = Executors.newFixedThreadPool(3)
    val context = PanelApplication.instance.applicationContext
    val cacheDir = File(context.cacheDir.path + File.separator + "productImages")
    val cacheSize = 512L*1024L*1024L // 512 MB
    val picasso = Picasso
        .Builder(context)
        .executor(executor)
        .downloader(OkHttp3Downloader(cacheDir, cacheSize))
        .build()
    for ((product, view) in pairs) {
        picasso
            .load("$PRODUCT_IMAGE_PREFIX${product.image}")
            //.placeholder(R.drawable.image_placeholder)
            .error(R.drawable.image_placeholder)
            .noFade()
            .into(view)
    }
}

