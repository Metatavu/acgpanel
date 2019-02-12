package fi.metatavu.acgpanel.model

import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.arch.persistence.room.migration.Migration
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.util.Log
import fi.metatavu.acgpanel.PanelApplication
import fi.metatavu.acgpanel.R
import okhttp3.Credentials
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Duration

@Database(entities = [
    User::class,
    Product::class,
    ProductTransaction::class,
    ProductTransactionItem::class,
    LogInAttempt::class,
    SystemProperties::class,
    CompartmentMapping::class
], version = 4, exportSchema = false)
abstract class AndroidPanelDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun userDao(): UserDao
    abstract fun productTransactionDao(): ProductTransactionDao
    abstract fun logInAttemptDao(): LogInAttemptDao
    abstract fun systemPropertiesDao(): SystemPropertiesDao
}

internal const val DATABASE_NAME = "acgpanel.db"

object PanelModelImpl : PanelModel() {
    private val db: AndroidPanelDatabase =
        Room.databaseBuilder(
            PanelApplication.instance,
            AndroidPanelDatabase::class.java,
            DATABASE_NAME
        )
        .fallbackToDestructiveMigration()
        .addMigrations(
        )
        .build()

    override val giptoolService: GiptoolService
            = Retrofit.Builder()
            //http://ilmoeuro-local.metatavu.io:5001/api/
        .baseUrl(serverAddress)
        .addConverterFactory(GsonConverterFactory.create())
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .client(
            OkHttpClient.Builder()
                .readTimeout(Duration.ofMinutes(30))
                .connectTimeout(Duration.ofMinutes(30))
                .callTimeout(Duration.ofMinutes(30))
                .addInterceptor {
                    it.proceed(
                        it.request()
                            .newBuilder()
                            .header("Authorization", Credentials.basic(
                                vendingMachineId,
                                password
                            ))
                            .build()
                    )
                }
                .build()
        )
        .build()
        .create(GiptoolService::class.java)

    override val productDao: ProductDao
        get() = db.productDao()

    override val userDao: UserDao
        get() = db.userDao()

    override val productTransactionDao: ProductTransactionDao
        get() = db.productTransactionDao()

    override val logInAttemptDao: LogInAttemptDao
        get() = db.logInAttemptDao()

    override val systemPropertiesDao: SystemPropertiesDao
        get() = db.systemPropertiesDao()

    private fun preferences(): SharedPreferences {
        return PreferenceManager
            .getDefaultSharedPreferences(PanelApplication.instance)
    }

    private fun getString(resId: Int): String {
        return PanelApplication.instance.getString(resId)
    }

    override val vendingMachineId: String
        get() = preferences()
                    .getString(getString(R.string.pref_key_vending_machine_id), "")

    override val password: String
        get() = preferences()
            .getString(getString(R.string.pref_key_password), "")

    override val demoMode: Boolean
        get() = preferences().getBoolean(getString(R.string.pref_key_demo_mode), false)

    override val lockUserExpenditure: Boolean
        get() = preferences().getBoolean(getString(R.string.pref_key_user_expenditure), false)

    override val lockUserReference: Boolean
        get() = preferences().getBoolean(getString(R.string.pref_key_user_reference), false)

    override val maintenancePasscode: String
        get() {
            val code = preferences().getString(getString(R.string.pref_key_maintenance_passcode), "")
            if (code != "") {
                return code
            } else {
                return "0000"
            }
        }
    
    private val serverAddress: String
        get() = preferences()
            .getString(getString(R.string.pref_key_server_address), "")

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