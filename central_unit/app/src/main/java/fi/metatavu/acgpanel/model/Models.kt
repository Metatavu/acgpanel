package fi.metatavu.acgpanel.model

import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.room.Database as RoomDatabase
import android.arch.persistence.room.Room
import android.arch.persistence.room.TypeConverter
import android.arch.persistence.room.TypeConverters
import android.arch.persistence.room.migration.Migration
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
import java.time.Instant
import kotlin.concurrent.thread

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? =
        if (value != null) Instant.ofEpochMilli(value) else null

    @TypeConverter
    fun instantToTimestamp(value: Instant?): Long? =
        value?.toEpochMilli()
}

// TODO: Add foreign keys to all tables
@RoomDatabase(entities = [
    User::class,
    Product::class,
    ProductTransaction::class,
    ProductTransactionItem::class,
    ProductDocument::class,
    LogInAttempt::class,
    SystemProperties::class,
    CompartmentMapping::class,
    Expenditure::class,
    UserCustomer::class,
    CustomerExpenditure::class,
    ProductTransactionItemType::class
], version = 13, exportSchema = false)
@TypeConverters(Converters::class)
private abstract class AndroidPanelDatabase : RoomRoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun userDao(): UserDao
    abstract fun productTransactionDao(): ProductTransactionDao
    abstract fun logInAttemptDao(): LogInAttemptDao
    abstract fun systemPropertiesDao(): SystemPropertiesDao
    abstract fun compartmentMappingDao(): CompartmentMappingDao
    abstract fun expenditureDao(): ExpenditureDao
}

private const val DATABASE_NAME = "acgpanel.db"

private object Database {

    private val db: AndroidPanelDatabase =
        Room.databaseBuilder(
            PanelApplication.instance,
            AndroidPanelDatabase::class.java,
            DATABASE_NAME
        )
            .addMigrations(
                object: Migration(6, 7) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("DELETE FROM ProductDocument")
                    }
                },
                object: Migration(7, 8) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("""
                            ALTER TABLE product
                            ADD COLUMN empty INTEGER NOT NULL DEFAULT 0
                        """)
                    }
                },
                object: Migration(8, 9) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("""
                            ALTER TABLE product
                            ADD COLUMN serverStock INTEGER NOT NULL DEFAULT 0
                        """)
                    }
                },
                object: Migration(9, 10) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("""
                            DROP TABLE ProductSafetyCard
                        """)
                        database.execSQL("""
                            CREATE TABLE ProductDocument (
                                productId INTEGER NOT NULL,
                                url TEXT NOT NULL,
                                removed INTEGER NOT NULL,
                                PRIMARY KEY (productId, url)
                            )
                        """)
                    }
                },
                object: Migration(10, 11) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("""
                            CREATE TABLE IF NOT EXISTS `Expenditure` (
                                `code` TEXT NOT NULL,
                                `description` TEXT NOT NULL,
                                PRIMARY KEY(`code`)
                            )
                            """
                        )
                    }
                },
                object: Migration(11, 12) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("""
                            CREATE TABLE IF NOT EXISTS `UserCustomer` (
                                `userId` INTEGER NOT NULL,
                                `customerCode` TEXT NOT NULL,
                                PRIMARY KEY(`userId`, `customerCode`),
                                FOREIGN KEY(`userId`) REFERENCES `User`(`id`)
                                ON UPDATE NO ACTION ON DELETE NO ACTION
                            )
                            """
                        )
                        database.execSQL("""
                            CREATE TABLE IF NOT EXISTS `CustomerExpenditure` (
                                `code` TEXT NOT NULL,
                                `customerCode` TEXT NOT NULL,
                                 `description` TEXT NOT NULL,
                                 PRIMARY KEY(`code`, `customerCode`)
                            )
                            """
                        )
                        database.execSQL("""
                            ALTER TABLE `ProductTransactionItem`
                                ADD COLUMN `type` TEXT NOT NULL DEFAULT ''
                            """
                        )
                        database.execSQL("""
                            CREATE TABLE IF NOT EXISTS `ProductTransactionItemType` (
                                `type` TEXT NOT NULL DEFAULT '',
                                PRIMARY KEY (`type`)
                            )
                            """
                        )
                        database.execSQL("""
                            ALTER TABLE `Product`
                                ADD COLUMN `borrowable` INTEGER NOT NULL DEFAULT 0
                            """
                        )
                    }
                },
                object: Migration(11, 12) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                    }
                }
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

    val expenditureDao: ExpenditureDao
        get() = db.expenditureDao()

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

    val messagingService: GiptoolMessagingService =
        makeRetrofitService(GiptoolMessagingService::class.java)

    val expendituresService: GiptoolExpendituresService =
        makeRetrofitService(GiptoolExpendituresService::class.java)

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

    var password: String
        get() = preferences()
            .getString(getString(R.string.pref_key_password), "")
        set(value) {
            preferences()
                .edit()
                .putString(
                    getString(R.string.pref_key_password),
                    value
                )
                .apply()
        }

    val demoMode: Boolean
        get() = preferences().getBoolean(getString(R.string.pref_key_demo_mode), true)

    val lockUserExpenditure: Boolean
        get() = preferences().getBoolean(getString(R.string.pref_key_user_expenditure), false)
        
    val pickUserExpenditure: Boolean
        get() = preferences().getBoolean(getString(R.string.pref_key_pick_user_expenditure), false)

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
            .getString(getString(R.string.pref_key_server_address), "http://localhost/")

    val useWiegandProfile1: Boolean
        get() = preferences()
            .getBoolean(getString(R.string.pref_key_wiegand_profile_1), false)

    val useWiegandProfile2: Boolean
        get() = preferences()
            .getBoolean(getString(R.string.pref_key_wiegand_profile_2), false)

    val timeoutInSeconds: Long
        get() = preferences()
            .getString(getString(R.string.pref_key_timeout_in_s), "").toLongOrNull() ?: 60

    val lightsTimeoutInSeconds: Int
        get() = preferences()
            .getString(getString(R.string.pref_key_lights_timeout_in_s), "").toIntOrNull() ?: 60

    val lightsLowIntensity: Int
        get() = preferences()
            .getString(getString(R.string.pref_key_lights_low_intensity), "").toIntOrNull() ?: 60

    val lightsHighIntensity: Int
        get() = preferences()
            .getString(getString(R.string.pref_key_lights_high_intensity), "").toIntOrNull() ?: 60

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

    public override fun unsafeRefreshProductPages() {
        super.unsafeRefreshProductPages()
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

    override fun unsafeUpdateProducts() {
        ProductsModelImpl.unsafeRefreshProductPages()
    }

}

fun getBasketModel(): BasketModel = BasketModelImpl

private object LockModelImpl: LockModel() {
    override fun enableItemsInLine(line: String) {
        BasketModelImpl.enableItemsInLine(line)
    }

    override fun disableAllItemsInBasket() {
        BasketModelImpl.disableAll()
    }

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

    override fun completeProductTransaction(function: () -> Unit) {
        BasketModelImpl.completeProductTransaction(function)
    }

    override val isShelvingMode: Boolean
        get() = LoginModelImpl.currentUser?.canShelve == true

}

fun getLockModel(): LockModel = LockModelImpl

private object LoginModelImpl: LoginModel() {
    override val expenditureDao: ExpenditureDao
        get() = Database.expenditureDao

    override val expendituresService: GiptoolExpendituresService
        get() = Services.expendituresService

    override val shouldPickUserExpenditure: Boolean
        get() = Preferences.pickUserExpenditure

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

    override val useWiegandProfile1: Boolean
        get() = Preferences.useWiegandProfile1

    override val useWiegandProfile2: Boolean
        get() = Preferences.useWiegandProfile2

    override val timeoutInSeconds: Long
        get() = Preferences.timeoutInSeconds

    override var password: String
        get() = Preferences.password
        set(value) {Preferences.password = value}

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

    public override fun syncExpenditures() {
        super.syncExpenditures()
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
        try {
            DemoModelImpl.demoModeCleanup()
            LoginModelImpl.syncUsers()
            ProductsModelImpl.syncProducts()
            BasketModelImpl.syncProductTransactions()
            LoginModelImpl.syncLogInAttempts()
            LoginModelImpl.syncExpenditures()
        } catch (ex: IOException) {
            Log.d(javaClass.name, "IOException: $ex")
            // device offline, do nothing
        } catch (ex: Exception) {
            Log.e(javaClass.name, Log.getStackTraceString(ex))
            Log.e(javaClass.name, "Error while syncing to server: $ex")
        }
    }

    override fun testServerConnection(callback: (String?) -> Unit) {
        thread(start = true) {
            try {
                val result = Services.productsService.listProducts(Preferences.vendingMachineId)
                    .execute()
                if (!result.isSuccessful) {
                    callback("Result not successful: ${result.code()}: ${result.errorBody()}")
                }
                else if (result.body() == null) {
                    callback("Response was empty")
                }
                else {
                    callback(null)
                }
            } catch (e: IOException) {
                callback("IOException: $e")
            }
        }
    }

}

fun getServerSyncModel(): ServerSyncModel = ServerSyncModelImpl

private object NotificationModelImpl: NotificationModel()

fun getNotificationModel(): NotificationModel = NotificationModelImpl

private object LightsModelImpl: LightsModel() {

    override fun schedule(callback: Runnable, timeout: Long) =
        PanelScheduling.schedule(callback, timeout)

    override fun unSchedule(callback: Runnable) =
        PanelScheduling.unSchedule(callback)

    override val lightsTimeoutInSeconds: Int
        get() = Preferences.lightsTimeoutInSeconds

    override val lightsLowIntensity: Int
        get() = Preferences.lightsLowIntensity

    override val lightsHighIntensity: Int
        get() = Preferences.lightsHighIntensity

}

fun getLightsModel(): LightsModel = LightsModelImpl

private object MessagingModelImpl: MessagingModel() {
    override val emptyVendingMachineMessageTemplate: String
        get() = ""

    override val messagingService: GiptoolMessagingService
        get() = Services.messagingService

    override val vendingMachineId: String
        get() = Preferences.vendingMachineId

}

fun getMessagingModel(): MessagingModel = MessagingModelImpl