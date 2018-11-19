package fi.metatavu.acgpanel.model

import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.os.Handler
import android.os.Looper
import fi.metatavu.acgpanel.PanelApplication
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Database(entities = arrayOf(Product::class), version = 1)
abstract class AndroidPanelDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
}

object PanelModelImpl : PanelModel() {
    val db: AndroidPanelDatabase =
        Room.inMemoryDatabaseBuilder(
            PanelApplication.instance,
            AndroidPanelDatabase::class.java
        ).build()

    override val giptoolProductsService: GiptoolProductsService
            = Retrofit.Builder()
        .baseUrl("http://rfidpaikannus.metatavu.io/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GiptoolProductsService::class.java)

    override val productDao: ProductDao
        get() = db.productDao()

    val handler = Handler(Looper.getMainLooper())

    override fun schedule(callback: Runnable, timeout: Long) {
        handler.postDelayed(callback, timeout)
    }

    override fun unSchedule(callback: Runnable) {
        handler.removeCallbacks(callback)
    }
}