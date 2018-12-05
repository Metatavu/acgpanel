package fi.metatavu.acgpanel

import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import fi.metatavu.acgpanel.model.Product
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Okio
import java.io.File
import java.util.concurrent.Executors
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

const val PAGE_HEIGHT = 2000
const val PAGE_SEPARATION = 10

fun drawProductSafetyCard(
    product: Product,
    view: ImageView
) {
    val dp = Resources.getSystem().displayMetrics.density
    val client = OkHttpClient()
    val context = PanelApplication.instance.applicationContext
    val cacheDir = File(context.cacheDir.path + File.separator + "productSafetyCards")
    cacheDir.mkdirs()
    val targetFile = File(cacheDir, product.safetyCard)
    thread(start = true) process@{
        if (!targetFile.exists()) {
            client.newCall(
                Request.Builder()
                    // TODO put UserAssets in server
                    .url("$PRODUCT_IMAGE_PREFIX/UserAssets/${product.safetyCard}")
                    .get()
                    .build()
            )
                .execute()
                .use {request ->
                    targetFile.createNewFile()
                    val sink = Okio.buffer(Okio.sink(targetFile)).use {
                        val body = request.body()
                        if (body != null) {
                            it.writeAll(body.source())
                        } else {
                            return@process
                        }
                    }
                }
        }
        val fd = ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(fd).use {renderer ->
            // TODO render pages on demand
            var totalWidth = 0
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use {page ->
                    val width = pageWidth(page, PAGE_HEIGHT)
                    totalWidth += width + PAGE_SEPARATION
                }
            }
            totalWidth -= PAGE_SEPARATION
            val bitmap = Bitmap.createBitmap(
                totalWidth,
                PAGE_HEIGHT,
                Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            val canvas = Canvas(bitmap)
            val separationPaint = Paint()
            separationPaint.style = Paint.Style.FILL
            separationPaint.color = Color.rgb(0x80, 0x80, 0x80)
            var x = 0
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use {page ->
                    val width = pageWidth(page, PAGE_HEIGHT)
                    if (i != 0) {
                        canvas.drawRect(
                            Rect(x, 0, x + PAGE_SEPARATION, PAGE_HEIGHT),
                            separationPaint
                        )
                        x += PAGE_SEPARATION
                    }
                    page.render(
                        bitmap,
                        Rect(x, 0, x+width, PAGE_HEIGHT),
                        null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    x += width
                }
            }
            val drawable = BitmapDrawable(context.resources, bitmap)
            Handler(context.mainLooper).post {
                view.setImageDrawable(drawable)
            }
        }

    }
}

private fun pageWidth(page: PdfRenderer.Page, height: Int): Int {
    val aspect = page.width.toDouble() / page.height.toDouble()
    val width = (height * aspect).toInt()
    return width
}
