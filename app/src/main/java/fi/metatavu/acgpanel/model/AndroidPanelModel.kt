package fi.metatavu.acgpanel.model

import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.os.Handler
import android.os.Looper
import fi.metatavu.acgpanel.PanelApplication

@Database(entities = arrayOf(Product::class), version = 1)
abstract class AndroidPanelDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
}

object AndroidPanelModel : PanelModel() {
    val db: AndroidPanelDatabase =
        Room.inMemoryDatabaseBuilder(
            PanelApplication.instance,
            AndroidPanelDatabase::class.java
        ).build()

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