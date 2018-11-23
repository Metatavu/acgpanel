package fi.metatavu.acgpanel

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.support.v4.content.res.ResourcesCompat
import android.widget.ImageView
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import fi.metatavu.acgpanel.model.Product
import okhttp3.internal.cache.DiskLruCache
import java.io.File
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

// TODO configurable
const val PRODUCT_IMAGE_PREFIX = "http://ilmoeuro-local.metatavu.io:5001"

fun drawProduct(product: Product, view: ImageView) {
    drawProducts(listOf(Pair(product, view)))
}

// TODO share Picasso instances
fun drawProducts(pairs: List<Pair<Product, ImageView>>) {
    val executor = Executors.newFixedThreadPool(1)
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
            .placeholder(R.drawable.image_placeholder)
            .error(R.drawable.image_placeholder)
            .noFade()
            .into(view)
    }
}
