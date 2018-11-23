package fi.metatavu.acgpanel.model

import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.arch.persistence.room.migration.Migration
import android.os.Handler
import android.os.Looper
import android.util.Log
import fi.metatavu.acgpanel.PanelApplication
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Database(entities = arrayOf(User::class, Product::class), version = 1)
abstract class AndroidPanelDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun userDao(): UserDao
}

internal const val DATABASE_NAME = "acgpanel.db"

object PanelModelImpl : PanelModel() {
    private val db: AndroidPanelDatabase =
        Room.databaseBuilder(
            PanelApplication.instance,
            AndroidPanelDatabase::class.java,
            DATABASE_NAME
        )
        .build()

    override val giptoolService: GiptoolService
            = Retrofit.Builder()
        // TODO configurable URL
        .baseUrl("http://ilmoeuro-local.metatavu.io:5001/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GiptoolService::class.java)

    override val productDao: ProductDao
        get() = db.productDao()

    override val userDao: UserDao
        get() = db.userDao()

    private val handler = Handler(Looper.getMainLooper())

    override fun schedule(callback: Runnable, timeout: Long) {
        handler.postDelayed(callback, timeout)
    }

    override fun unSchedule(callback: Runnable) {
        handler.removeCallbacks(callback)
    }

    override fun transaction(tx: () -> Unit) {
        db.runInTransaction(tx)
    }

}