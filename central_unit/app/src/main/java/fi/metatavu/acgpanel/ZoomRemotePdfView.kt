package fi.metatavu.acgpanel

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.util.Base64
import android.util.Log
import com.otaliastudios.zoom.ZoomImageView
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Okio
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

class ZoomRemotePdfView: ZoomImageView, AutoCloseable {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet): super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int): super(context, attrs, defStyleAttr)

    private val cacheDir = File(context.cacheDir.path + File.separator + CACHE_SUBDIR)
    private var renderer: PdfRenderer? = null

    private fun file(): File? {
        val url = this.url
        return if (url != null) {
            File(
                cacheDir,
                Base64.encodeToString(url.toByteArray(StandardCharsets.UTF_8), Base64.URL_SAFE)
            )
        } else {
            null
        }
    }

    private fun ensureFileExists(): Boolean {
        val file = file() ?: return false
        cacheDir.mkdirs()
        try {
            if (!file.exists()) {
                OkHttpClient().newCall(
                    Request.Builder()
                        .url(url!!)
                        .get()
                        .build()
                )
                    .execute()
                    .use { request ->
                        file.createNewFile()
                        Okio.buffer(Okio.sink(file)).use {
                            val body = request.body()
                            if (body != null) {
                                it.writeAll(body.source())
                            } else {
                                return false
                            }
                        }
                    }
            }
            return true
        } catch (e: Exception) {
            if (e !is IOException) {
                Log.e(javaClass.name, e.toString())
                Log.e(javaClass.name, Log.getStackTraceString(e))
            }
            return false
        }
    }

    private fun newUrl() {
        val exists = ensureFileExists()
        Handler(context.mainLooper).post {
            if (!exists) {
                renderer = null
                numPages = 0
                page = 0
            } else {
                val fd = ParcelFileDescriptor.open(file(), ParcelFileDescriptor.MODE_READ_ONLY)
                if (renderer != null) {
                    renderer!!.close()
                }
                try {
                    renderer = PdfRenderer(fd)
                    numPages = renderer!!.pageCount
                    if (page == 0) {
                        newPage()
                    } else {
                        page = 0
                    }
                } catch (ex: Exception) {
                    numPages = 0
                    page = 0
                }
            }
        }
    }

    private fun newPage() {
        val renderer = this.renderer
        val page = this.page
        if (page < 0 || page >= numPages) {
            return
        }
        if (renderer == null) {
            return
        }
        renderer.openPage(page).use {
            val aspect = it.width.toFloat() / it.height.toFloat()
            val width = (aspect * IMAGE_HEIGHT).toInt()
            val bitmap = Bitmap.createBitmap(
                width,
                IMAGE_HEIGHT,
                Bitmap.Config.ARGB_8888
            )
            bitmap.eraseColor(Color.WHITE)
            it.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            Handler(context.mainLooper).post {
                setImageBitmap(bitmap)
            }
        }
    }

    var url: String? = null
        set(value) {
            if (field != value) {
                field = value
                thread(start=true) {newUrl()}
            }
        }

    var page: Int = 0
        set(value) {
            if (field != value) {
                field = value
                thread(start=true) {newPage()}
            }
        }

    var numPages: Int = 0
        private set

    override fun close() {
        if (renderer != null) {
            renderer!!.close()
        }
    }

    companion object {
        private const val CACHE_SUBDIR = "pdfs"
        private val DP = Resources.getSystem().displayMetrics.density
        private val IMAGE_HEIGHT = (2000*DP).toInt()
    }

}