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
import fi.metatavu.acgpanel.PanelApplication
import fi.metatavu.acgpanel.R
import okhttp3.Credentials
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Database(entities = [
    User::class,
    Product::class,
    ProductTransaction::class,
    ProductTransactionItem::class
], version = 4)
abstract class AndroidPanelDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun userDao(): UserDao
    abstract fun productTransactionDao(): ProductTransactionDao
}

internal const val DATABASE_NAME = "acgpanel.db"

object PanelModelImpl : PanelModel() {
    private val db: AndroidPanelDatabase =
        Room.databaseBuilder(
            PanelApplication.instance,
            AndroidPanelDatabase::class.java,
            DATABASE_NAME
        )
        .addMigrations(

            object: Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.query("""CREATE TABLE ProductTransaction(
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        vendingMachineId VARCHAR(255) NOT NULL,
                        userId INTEGER NOT NULL,
                        FOREIGN KEY (userId) REFERENCES User(id)
                    )""")
                    database.query("""CREATE TABLE ProductTransactionItem(
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        transactionId INTEGER NOT NULL,
                        productId INTEGER NOT NULL,
                        count INTEGER NOT NULL,
                        expenditure VARCHAR(4096) NOT NULL,
                        reference VARCHAR(4096) NOT NULL,
                        FOREIGN KEY (transactionId) REFERENCES ProductTransaction(id),
                        FOREIGN KEY (productId) REFERENCES Product(id)
                    )""")
                }
            },

            object: Migration(2, 3) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.query("ALTER TABLE Product ADD COLUMN safetyCard VARCHAR(255)")
                    database.query("ALTER TABLE Product ADD COLUMN productInfo VARCHAR(4096)")
                }
            },

            object: Migration(3, 4) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.query("ALTER TABLE Product ADD COLUMN unit VARCHAR(255)")
                }
            }

        )
        .build()

    override val giptoolService: GiptoolService
            = Retrofit.Builder()
        // TODO configurable URL
        .baseUrl("http://ilmoeuro-local.metatavu.io:5001/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(
            OkHttpClient.Builder()
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