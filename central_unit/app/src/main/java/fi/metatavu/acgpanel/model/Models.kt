package fi.metatavu.acgpanel.model

import android.arch.persistence.room.Database as RoomDatabase
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase as RoomRoomDatabase
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
import java.io.IOException
import java.time.Duration
import kotlin.concurrent.thread

@RoomDatabase(entities = [
    User::class,
    Product::class,
    ProductTransaction::class,
    ProductTransactionItem::class,
    LogInAttempt::class,
    SystemProperties::class,
    CompartmentMapping::class
], version = 5, exportSchema = false)
private abstract class AndroidPanelDatabase : RoomRoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun userDao(): UserDao
    abstract fun productTransactionDao(): ProductTransactionDao
    abstract fun logInAttemptDao(): LogInAttemptDao
    abstract fun systemPropertiesDao(): SystemPropertiesDao
    abstract fun compartmentMappingDao(): CompartmentMappingDao
}

private const val DATABASE_NAME = "acgpanel.db"

private object Database {

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

    val productDao: ProductDao
        get() = db.productDao()

    val userDao: UserDao
        get() = db.userDao()

    val productTransactionDao: ProductTransactionDao
        get() = db.productTransactionDao()

    val logInAttemptDao: LogInAttemptDao
        get() = db.logInAttemptDao()

    val systemPropertiesDao: SystemPropertiesDao
        get() = db.systemPropertiesDao()

    val compartmentMappingDao: CompartmentMappingDao
        get() = db.compartmentMappingDao()

    fun transaction(tx: () -> Unit) {
        db.runInTransaction(tx)
    }

}

private object Services {

    private fun <T>makeRetrofitService(intf: Class<T>): T =
         Retrofit.Builder()
        .baseUrl(Preferences.serverAddress)
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
                            .header(
                                "Authorization", Credentials.basic(
                                    Preferences.vendingMachineId,
                                    Preferences.password
                                )
                            )
                            .build()
                    )
                }
                .build()
        )
        .build()
        .create(intf)

    val productsService: GiptoolProductsService =
        makeRetrofitService(GiptoolProductsService::class.java)

    val usersService: GiptoolUsersService =
        makeRetrofitService(GiptoolUsersService::class.java)

    val productTransactionsService: GiptoolProductTransactionsService =
        makeRetrofitService(GiptoolProductTransactionsService::class.java)

}

private object Preferences {

    private fun preferences(): SharedPreferences {
        return PreferenceManager
            .getDefaultSharedPreferences(PanelApplication.instance)
    }

    private fun getString(resId: Int): String {
        return PanelApplication.instance.getString(resId)
    }

    val vendingMachineId: String
        get() = preferences()
            .getString(getString(R.string.pref_key_vending_machine_id), "")

    val password: String
        get() = preferences()
            .getString(getString(R.string.pref_key_password), "")

    val demoMode: Boolean
        get() = preferences().getBoolean(getString(R.string.pref_key_demo_mode), false)

    val lockUserExpenditure: Boolean
        get() = preferences().getBoolean(getString(R.string.pref_key_user_expenditure), false)

    val lockUserReference: Boolean
        get() = preferences().getBoolean(getString(R.string.pref_key_user_reference), false)

    val maintenancePasscode: String
        get() {
            val code = preferences().getString(getString(R.string.pref_key_maintenance_passcode), "")
            return if (code != "") {
                code
            } else {
                "0000"
            }
        }

    val serverAddress: String
        get() = preferences()
            .getString(getString(R.string.pref_key_server_address), "")
}

private object PanelScheduling {

    private val handler = Handler(Looper.getMainLooper())

    fun schedule(callback: Runnable, timeout: Long) {
        handler.postDelayed(callback, timeout)
    }

    fun unSchedule(callback: Runnable) {
        handler.removeCallbacks(callback)
    }

}

private object ProductsModelImpl: ProductsModel() {

    override val productDao: ProductDao
        get() = Database.productDao

    override val demoMode: Boolean
        get() = Preferences.demoMode

    override val productsService: GiptoolProductsService
        get() = Services.productsService

    override val vendingMachineId: String
        get() = Preferences.vendingMachineId

    override fun schedule(callback: Runnable, timeout: Long) =
        PanelScheduling.schedule(callback, timeout)

    override fun transaction(tx: () -> Unit) =
        Database.transaction(tx)

    public override fun syncProducts() {
        super.syncProducts()
    }

}

fun getProductsModel(): ProductsModel = ProductsModelImpl

private object BasketModelImpl: BasketModel() {

    override val productDao: ProductDao
        get() = Database.productDao

    override val productTransactionDao: ProductTransactionDao
        get() = Database.productTransactionDao

    override val productTransactionsService: GiptoolProductTransactionsService
        get() = Services.productTransactionsService

    override val vendingMachineId: String
        get() = Preferences.vendingMachineId

    override val demoMode: Boolean
        get() = Preferences.demoMode

    override val lockUserExpenditure: Boolean
        get() = Preferences.lockUserExpenditure

    override val lockUserReference: Boolean
        get() = Preferences.lockUserReference

    override val currentUser: User?
        get() = LoginModelImpl.currentUser

    public override fun syncProductTransactions() {
        super.syncProductTransactions()
    }

    override fun schedule(callback: Runnable, timeout: Long) =
        PanelScheduling.schedule(callback, timeout)

    override fun transaction(tx: () -> Unit) =
        Database.transaction(tx)

    override fun acceptBasket() {
        LockModelImpl.openLines(basket.map{it.product.line})
    }

}

fun getBasketModel(): BasketModel = BasketModelImpl

private object LockModelImpl: LockModel() {

    override val compartmentMappingDao: CompartmentMappingDao
        get() = Database.compartmentMappingDao

    override fun schedule(callback: Runnable, timeout: Long) =
        PanelScheduling.schedule(callback, timeout)

    override fun unSchedule(callback: Runnable) =
        PanelScheduling.unSchedule(callback)

    override fun transaction(tx: () -> Unit) =
        Database.transaction(tx)

    override fun syncProductTransactions() {
        BasketModelImpl.syncProductTransactions()
    }

    override fun logOut() {
        LoginModelImpl.logOut()
    }

    override fun completeProductTransaction(callback: () -> Unit) {
        BasketModelImpl.completeProductTransaction(callback)
    }

}

fun getLockModel(): LockModel = LockModelImpl

private object LoginModelImpl: LoginModel() {

    override val userDao: UserDao
        get() = Database.userDao

    override val logInAttemptDao: LogInAttemptDao
        get() = Database.logInAttemptDao

    override val usersService: GiptoolUsersService
        get() = Services.usersService

    override val vendingMachineId: String
        get() = Preferences.vendingMachineId

    override val demoMode: Boolean
        get() = Preferences.demoMode

    override fun schedule(callback: Runnable, timeout: Long) =
        PanelScheduling.schedule(callback, timeout)

    override fun unSchedule(callback: Runnable) =
        PanelScheduling.unSchedule(callback)

    override fun transaction(tx: () -> Unit) =
        Database.transaction(tx)

    override fun isLocksOpen(): Boolean =
        LockModelImpl.locksOpen

    override fun clearLocks() {
        LockModelImpl.clearLines()
    }

    override fun clearProductSearch() {
        ProductsModelImpl.searchTerm = ""
        ProductsModelImpl.refreshProductPages {  }
    }

    override fun clearBasket() {
        BasketModelImpl.clearBasket()
    }

    public override fun syncUsers() {
        super.syncUsers()
    }

    public override fun syncLogInAttempts() {
        super.syncLogInAttempts()
    }


}

fun getLoginModel(): LoginModel = LoginModelImpl

private object DemoModelImpl: DemoModel() {

    override val productDao: ProductDao
        get() = Database.productDao

    override val userDao: UserDao
        get() = Database.userDao

    override val productTransactionDao: ProductTransactionDao
        get() = Database.productTransactionDao

    override val logInAttemptDao: LogInAttemptDao
        get() = Database.logInAttemptDao

    override val systemPropertiesDao: SystemPropertiesDao
        get() = Database.systemPropertiesDao

    override val demoMode: Boolean
        get() = Preferences.demoMode

    override fun schedule(callback: Runnable, timeout: Long) =
        PanelScheduling.schedule(callback, timeout)

    override fun unSchedule(callback: Runnable) =
        PanelScheduling.unSchedule(callback)

    override fun transaction(tx: () -> Unit) =
        Database.transaction(tx)

    public override fun demoModeCleanup() {
        super.demoModeCleanup()
    }

}

fun getDemoModel(): DemoModel = DemoModelImpl

private object MaintenanceModelImpl: MaintenanceModel() {

    override val maintenancePasscode: String
        get() = Preferences.maintenancePasscode

    override fun schedule(callback: Runnable, timeout: Long) =
        PanelScheduling.schedule(callback, timeout)

}

fun getMaintenanceModel(): MaintenanceModel = MaintenanceModelImpl

private object ServerSyncModelImpl: ServerSyncModel() {

    override fun serverSync() {
         thread(start=true) {
            try {
                DemoModelImpl.demoModeCleanup()
                LoginModelImpl.syncUsers()
                ProductsModelImpl.syncProducts()
                BasketModelImpl.syncProductTransactions()
                LoginModelImpl.syncLogInAttempts()
            } catch (ex: IOException) {
                // device offline, do nothing
            } catch (ex: Exception) {
                Log.e(javaClass.name, "Error while syncing to server: $ex")
            }
        }
    }

}

fun getServerSyncModel(): ServerSyncModel = ServerSyncModelImpl
