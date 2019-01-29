package fi.metatavu.acgpanel.model

import android.arch.persistence.room.*
import android.util.Log
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.io.IOException
import java.lang.Exception
import java.util.concurrent.ArrayBlockingQueue
import kotlin.concurrent.thread

@Entity
data class User(
    @PrimaryKey var id: Long?,
    var userName: String,
    var cardCode: String
)

@Entity
data class Product(
    @PrimaryKey var id: Long?,
    var externalId: Long,
    var name: String,
    var description: String,
    var image: String,
    var code: String,
    var safetyCard: String,
    var productInfo: String,
    var unit: String,
    var line: String
)

@Entity
data class ProductTransactionItem(
    @PrimaryKey var id: Long?,
    var transactionId: Long,
    var productId: Long,
    var count: Int,
    var expenditure: String,
    var reference: String
)

@Entity
data class ProductTransaction(
    @PrimaryKey var id: Long?,
    var userId: Long,
    var uploaded: Boolean = false
)

@Entity
data class LogInAttempt(
    @PrimaryKey var id: Long?,
    var userId: Long?,
    var cardCode: String,
    var successful: Boolean,
    var uploaded: Boolean
)

@Entity
data class SystemProperties(
    @PrimaryKey var id: Long? = null,
    var containsDemoData: Boolean
)

data class BasketItem(
    val product: Product,
    val count: Int,
    val expenditure: String,
    val reference: String) {

    fun withCount(count: Int): BasketItem {
        return BasketItem(product, count, expenditure, reference)
    }

    fun withExpenditure(expenditure: String): BasketItem {
        return BasketItem(product, count, expenditure, reference)
    }

    fun withReference(reference: String): BasketItem {
        return BasketItem(product, count, expenditure, reference)
    }

}

data class ProductPage(val products: List<Product>)

class GiptoolProduct {
    var externalId: Long? = null
    var name: String? = null
    var description: String? = null
    var picture: String? = null
    var code: String? = null
    var safetyCard: String? = null
    var productInfo: String? = null
    var unit: String? = null
    var line: String? = null
}

class GiptoolProducts {
    var products: MutableList<GiptoolProduct> = mutableListOf()
}

class GiptoolUser {
    var id: Long? = null
    var name: String = ""
    var cardCode: String? = null
}

class GiptoolUsers {
    var users: MutableList<GiptoolUser> = mutableListOf()
}

class GiptoolProductTransactionItem {
    var productId: Long? = null
    var line: String = ""
    var count: Int = 0
    var expenditure: String = ""
    var reference: String = ""
}

class GiptoolProductTransaction {
    var transactionNumber: Long? = null
    var userId: Long? = null
    var vendingMachineId: String? = null
    val items: MutableList<GiptoolProductTransactionItem> = mutableListOf()
}

class GiptoolLogInAttempt {
    var attemptNumber: Long? = null
    var vendingMachineId: String? = null
    var cardCode: String = ""
    var successful: Boolean = false
}

interface GiptoolService {
    @GET("products/vendingMachine/{vendingMachineId}")
    fun listProducts(@Path("vendingMachineId") vendingMachineId: String): Call<GiptoolProducts>

    @GET("users/vendingMachine/{vendingMachineId}")
    fun listUsers(@Path("vendingMachineId") vendingMachineId: String): Call<GiptoolUsers>

    @POST("productTransactions/")
    fun sendProductTransaction(@Body productTransaction: GiptoolProductTransaction): Call<GiptoolProductTransaction>

    @POST("logInAttempts/")
    fun sendLogInAttempt(@Body productTransaction: GiptoolLogInAttempt): Call<GiptoolLogInAttempt>
}

@Dao
interface ProductDao {

    @Insert
    fun insertAll(vararg products: Product)

    @Query("SELECT * FROM product LIMIT 6 OFFSET :offset")
    fun getProductPage(offset: Long): List<Product>

    @Query("SELECT COUNT(*) FROM product")
    fun getProductCount(): Long

    @Query("SELECT * FROM product WHERE code = :searchTerm OR line = :searchTerm LIMIT 6 OFFSET :offset")
    fun getProductPageSearch(searchTerm: String, offset: Long): List<Product>

    @Query("SELECT COUNT(*) FROM product WHERE code = :searchTerm OR line = :searchTerm")
    fun getProductCountSearch(searchTerm: String): Long

    @Query("SELECT * FROM product WHERE id=:id")
    fun findProductById(id: Long): Product?

    @Query("SELECT * FROM product WHERE externalId=:externalId")
    fun findProductByExternalId(externalId: Long): Product?

    @Query("DELETE FROM product")
    fun clearProducts()

}

@Dao
interface UserDao {

    @Insert
    fun insertAll(vararg users: User)

    @Query("SELECT * FROM user WHERE cardCode = :cardCode")
    fun findUserByCardCode(cardCode: String): User?

    @Query("DELETE FROM user")
    fun clearUsers()

    @Query("SELECT * FROM user WHERE id = :id")
    fun findUserById(id: Long?): User?
}

@Dao
interface ProductTransactionDao {

    @Insert
    fun insertProductTransaction(transactions: ProductTransaction): Long

    @Insert
    fun insertProductTransactionItems(vararg items: ProductTransactionItem)

    @Query("SELECT * FROM producttransaction WHERE uploaded=0")
    fun listNonUploadedTransactions(): List<ProductTransaction>

    @Query("SELECT * FROM producttransactionitem WHERE transactionId=:transactionId")
    fun listItemsByTransaction(transactionId: Long): List<ProductTransactionItem>

    @Query("UPDATE producttransaction SET uploaded=1 WHERE id=:id")
    fun markUploaded(id: Long)

    @Query("DELETE FROM producttransaction")
    fun clearProductTransactions()

    @Query("DELETE FROM producttransactionitem")
    fun clearProductTransactionsItems()

}

@Dao
interface LogInAttemptDao {

    @Insert
    fun insertAll(vararg loginAttempts: LogInAttempt)

    @Query("SELECT * FROM loginattempt WHERE uploaded=0")
    fun listNonUploadedAttempts(): List<LogInAttempt>

    @Query("UPDATE loginattempt SET uploaded=1 WHERE id=:id")
    fun markUploaded(id: Long)

    @Query("DELETE FROM loginattempt")
    fun clearLogInAttempts()

}

@Dao
interface SystemPropertiesDao {

    @Insert
    fun insert(systemProperties: SystemProperties)

    @Query("DELETE FROM systemproperties")
    fun clear()

    @Query("SELECT * FROM systemproperties WHERE id=1")
    fun getSystemProperties(): SystemProperties?

}

sealed class Action

data class OpenLockAction(val shelf: Int, val compartment: Int) : Action()

private const val SESSION_TIMEOUT_MS = 5*60*1000L

abstract class PanelModel {

    private sealed class SelectedBasketItem {
        class New(val product: Product) : SelectedBasketItem()
        class Existing(val index: Int): SelectedBasketItem()
    }

    abstract fun schedule(callback: Runnable, timeout: Long)
    abstract fun unSchedule(callback: Runnable)
    abstract fun transaction(tx: () -> Unit)
    abstract val productDao: ProductDao
    abstract val userDao: UserDao
    abstract val productTransactionDao: ProductTransactionDao
    abstract val logInAttemptDao: LogInAttemptDao
    abstract val systemPropertiesDao: SystemPropertiesDao
    abstract val giptoolService: GiptoolService
    abstract val vendingMachineId: String
    abstract val password: String
    abstract val demoMode: Boolean
    abstract val maintenancePasscode: String

    private val logoutTimerCallback: Runnable = Runnable { logOut() }
    private val logoutEventListeners: MutableList<() -> Unit> = mutableListOf()
    private val loginEventListeners: MutableList<() -> Unit> = mutableListOf()
    private val failedLoginEventListeners: MutableList<() -> Unit> = mutableListOf()
    private val deviceErrorListeners: MutableList<(String) -> Unit> = mutableListOf()
    private val actionQueue = ArrayBlockingQueue<Action>(BUFFER_SIZE)

    var searchTerm = ""
    var canLogInViaRfid = false
    var isDeviceErrorMode = false

    var currentUser: User? = null
    private var nextLockToOpen = -1
    private var selectedBasketItem: SelectedBasketItem? = null
    private val mutableBasket: MutableList<BasketItem> = mutableListOf()
    val basket: List<BasketItem>
        get() = mutableBasket

    init {
        thread(start = true) {
            unsafeRefreshProductPages()
        }
    }

    val currentBasketItem: BasketItem?
        get() {
            val item = selectedBasketItem
            return when (item) {
                is SelectedBasketItem.Existing -> basket[item.index]
                is SelectedBasketItem.New -> BasketItem(item.product, 1, "", "")
                null -> null
            }
        }

    fun selectNewBasketItem(product: Product) {
        selectedBasketItem = SelectedBasketItem.New(product)
    }

    fun selectExistingBasketItem(index: Int) {
        selectedBasketItem = SelectedBasketItem.Existing(index)
    }

    fun saveSelectedItem(
        count: Int?,
        expenditure: String,
        reference: String
    ) {
        val item = selectedBasketItem
        when (item) {
            is SelectedBasketItem.Existing -> {
                mutableBasket[item.index] = mutableBasket[item.index]
                    .withCount(count ?: 1)
                    .withExpenditure(expenditure)
                    .withReference(reference)
            }
            is SelectedBasketItem.New -> {
                mutableBasket.add(
                    BasketItem(
                        item.product,
                        count ?: 1,
                        expenditure,
                        reference
                    )
                )
            }
        }
    }

    fun completeProductTransaction(callback: () -> Unit) {
        thread(start = true) {
            val user = currentUser
            if (user != null) {
                val tx = ProductTransaction(
                    null,
                    user.id!!
                )
                val id = productTransactionDao.insertProductTransaction(tx)
                val items = basket.map {
                    ProductTransactionItem(
                        null,
                        id,
                        it.product.externalId,
                        it.count,
                        it.expenditure,
                        it.reference
                    )
                }.toTypedArray()
                productTransactionDao.insertProductTransactionItems(*items)
                schedule(Runnable {callback()}, 0)
            }
        }
    }

    fun deleteBasketItem(index: Int) {
        mutableBasket.removeAt(index)
    }

    fun clearBasket() {
        mutableBasket.clear()
    }

    fun serverSync() {
        try {
            transaction {
                demoModeCleanup()
                syncUsers()
                syncProducts()
                syncProductTransactions()
                syncLogInAttempts()
                unsafeRefreshProductPages()
            }
        } catch (e: IOException) {
            // device offline, do nothing
        } catch (e: Exception) {
            Log.e(javaClass.name, "Error when syncing to server: $e")
            Log.e(javaClass.name, Log.getStackTraceString(e))
        }
    }

    fun addLogOutListener(listener: () -> Unit) {
        logoutEventListeners.add(listener)
    }

    fun removeLogOutListener(listener: () -> Unit) {
        logoutEventListeners.remove(listener)
    }

    fun addLogInListener(listener: () -> Unit) {
        loginEventListeners.add(listener)
    }

    fun removeLogInListener(listener: () -> Unit) {
        loginEventListeners.remove(listener)
    }

    fun addFailedLogInListener(listener: () -> Unit) {
        failedLoginEventListeners.add(listener)
    }

    fun removeFailedLogInListener(listener: () -> Unit) {
        failedLoginEventListeners.remove(listener)
    }

    fun addDeviceErrorListener(listener: (String) -> Unit) {
        deviceErrorListeners.add(listener)
    }

    fun removeDeviceErrorListener(listener: (String) -> Unit) {
        deviceErrorListeners.remove(listener)
    }

    fun nextAction(): Action? {
        return actionQueue.poll()
    }

    fun logIn(cardCode: String, usingRfid: Boolean = false) {
        val truncatedCode = cardCode.takeWhile { it != '=' }
        if (usingRfid && !canLogInViaRfid) {
            return
        }
        if (truncatedCode == "") {
            return
        }
        thread(start = true) {
            val user = userDao.findUserByCardCode(truncatedCode)
            if (user != null) {
                currentUser = user
                schedule(Runnable {
                    for (listener in loginEventListeners) {
                        listener()
                    }
                    refresh()
                }, 0)
            } else {
                schedule(Runnable {
                    for (listener in failedLoginEventListeners) {
                        listener()
                    }
                }, 0)
            }
        }
    }

    val loggedIn: Boolean
        get() = currentUser != null

    fun refresh() {
        unSchedule(logoutTimerCallback)
        schedule(logoutTimerCallback, SESSION_TIMEOUT_MS)
    }

    fun logOut() {
        unSchedule(logoutTimerCallback)
        for (listener in logoutEventListeners) {
            listener()
        }
        currentUser = null
        selectedBasketItem = null
        searchTerm = ""
        mutableBasket.clear()
        refreshProductPages {  }
    }

    fun mapLockNumber(lineNumber: Int): Pair<Int, Int> {
        val shelf = lineNumber.div(100)
        val aux = lineNumber.rem(100)
        if (aux.rem(2) == 0) {
            return Pair(shelf, aux.div(2))
        } else {
            return Pair(shelf, 7 + aux.div(2))
        }
    }

    fun openLock(first: Boolean = true) {
        if (first) {
            nextLockToOpen = 0
        } else {
            if (nextLockToOpen == basket.size) {
                schedule(Runnable {
                    completeProductTransaction {
                        logOut()
                    }
                }, 0)
            }
        }
        val i = nextLockToOpen
        if (i != -1 && i < basket.size) {
            val item = basket[i]
            val (shelf, compartment) = mapLockNumber(
                item.product.line.toInt()
            )
            actionQueue.add(OpenLockAction(shelf, compartment))
            nextLockToOpen++
        }
    }

    var productPages: List<ProductPage> = listOf()
        private set

    fun refreshProductPages(callback: () -> Unit) {
        thread(start = true) {
            unsafeRefreshProductPages()
            schedule(Runnable {callback()}, 0)
        }
    }

    fun triggerDeviceError(message: String) {
        if (!isDeviceErrorMode) {
            schedule(Runnable{
                for (listener in deviceErrorListeners) {
                    listener(message)
                }
            }, 0)
        }
    }

    private fun unsafeRefreshProductPages() {
        productPages = if (searchTerm == "") {
            val productCount = productDao.getProductCount()
            (0 until productCount step 6).map {
                ProductPage(productDao.getProductPage(it))
            }
        } else {
            val productCount = productDao.getProductCountSearch(searchTerm)
            (0 until productCount step 6).map {
                ProductPage(productDao.getProductPageSearch(searchTerm, it))
            }
        }
    }

    private fun syncProducts() {
        if (demoMode) {
            productDao.clearProducts()
            val products = (1L..20L).map {
                Product(it,
                    it,
                    "Tuote $it",
                    "Kuvaus $it",
                    "",
                    "$it",
                    "",
                    "",
                    "kpl",
                    "1%02d".format((it-1)%12+1))
            }
            productDao.insertAll(*products.toTypedArray())
        } else {
            val products = giptoolService
                .listProducts(vendingMachineId)
                .execute()
                .body()!!
                .products
                .map {
                    Product(null,
                        it.externalId!!,
                        it.name?.trim() ?: "",
                        it.description?.trim() ?: "",
                        it.picture ?: "",
                        it.code ?: "",
                        it.safetyCard ?: "",
                        it.productInfo ?: "",
                        it.unit ?: "",
                        it.line?.trim() ?: "")
                }
                .filter {
                    productDao.findProductByExternalId(
                        it.externalId
                    ) == null
                }
                .toTypedArray()
            // TODO delete removed
            productDao.insertAll(*products)
        }
    }

    private fun demoModeCleanup() {
        val props = systemPropertiesDao.getSystemProperties()
        if (props != null) {
            if (!demoMode && props.containsDemoData) {
                productTransactionDao.clearProductTransactionsItems()
                productTransactionDao.clearProductTransactions()
                logInAttemptDao.clearLogInAttempts()
                productDao.clearProducts()
                userDao.clearUsers()
            }
            val newProps = SystemProperties(1, demoMode)
            systemPropertiesDao.clear()
        }
        val newProps = SystemProperties(1, demoMode)
        systemPropertiesDao.insert(newProps)
    }

    private fun syncUsers() {
        if (demoMode) {
            userDao.clearUsers()
            userDao.insertAll(
                User(1, "Matti Meikäläinen", "123456789012345"),
                User(2, "Teppo Testikäyttäjä", "4BA9ACED00000000")
            )
        } else {
            val users = giptoolService
                .listUsers(vendingMachineId)
                .execute()
                .body()!!
                .users
                .map {
                    User(
                        it.id,
                        it.name.trim(),
                        it.cardCode?.trim() ?: ""
                    )
                }
                .filter {
                    userDao.findUserById(
                        it.id
                    ) == null
                }
                .toTypedArray()
            // TODO delete removed
            userDao.insertAll(*users)
        }
    }

    private fun syncProductTransactions() {
        if (demoMode) {
            return
        }
        transaction {
            val txs = productTransactionDao.listNonUploadedTransactions()
            for (tx in txs) {
                val giptoolTx = GiptoolProductTransaction()
                giptoolTx.transactionNumber = tx.id
                giptoolTx.vendingMachineId = vendingMachineId
                giptoolTx.userId = tx.userId
                val items = productTransactionDao.listItemsByTransaction(tx.id!!)
                for (item in items) {
                    val product = productDao.findProductById(item.productId)
                    if (product != null) {
                        val giptoolTxItem = GiptoolProductTransactionItem()
                        giptoolTxItem.productId = product.externalId
                        giptoolTxItem.line = product.line
                        giptoolTxItem.count = item.count
                        giptoolTxItem.expenditure = item.expenditure
                        giptoolTxItem.reference = item.reference
                        giptoolTx.items.add(giptoolTxItem)
                    }
                }
                val res = giptoolService.sendProductTransaction(giptoolTx)
                    .execute()
                if (res.isSuccessful) {
                    productTransactionDao.markUploaded(tx.id!!)
                } else {
                    Log.e(javaClass.name, "Error when uploading product transaction: ${res.message()}")
                }
            }
        }
    }

    private fun syncLogInAttempts() {
        if (demoMode) {
            return
        }
        transaction {
            val attempts = logInAttemptDao.listNonUploadedAttempts()
            for (attempt in attempts) {
                val giptoolAttempt = GiptoolLogInAttempt()
                giptoolAttempt.attemptNumber = attempt.id
                giptoolAttempt.vendingMachineId = vendingMachineId
                giptoolAttempt.cardCode = attempt.cardCode
                giptoolAttempt.successful = attempt.successful
                val res = giptoolService.sendLogInAttempt(giptoolAttempt)
                    .execute()
                if (res.isSuccessful) {
                    logInAttemptDao.markUploaded(attempt.id!!)
                } else {
                    Log.e(javaClass.name, "Error when uploading login attempt: ${res.message()}")
                }
            }
        }
    }

    companion object {
        private const val BUFFER_SIZE = 1024
    }

}
