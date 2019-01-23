package fi.metatavu.acgpanel.model

import android.arch.persistence.room.*
import android.util.Log
import retrofit2.Call
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
    var name: String,
    var description: String,
    var image: String,
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
    var vendingMachineId: String,
    var userId: Long
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
    var productId: Long? = null
    var name: String = ""
    var description: String = ""
    var picture: String = ""
    var safetyCard: String = ""
    var productInfo: String = ""
    var unit: String = ""
    var line: String = ""
}

class GiptoolProducts {
    var products: MutableList<GiptoolProduct> = mutableListOf()
}

class GiptoolUser {
    var id: Long? = null
    var name: String = ""
    var cardCode: String = ""
}

class GiptoolUsers {
    var users: MutableList<GiptoolUser> = mutableListOf()
}

interface GiptoolService {
    @GET("products/vendingMachine/{vendingMachineId}")
    fun listProducts(@Path("vendingMachineId") vendingMachineId: String): Call<GiptoolProducts>

    @GET("users/page/1")
    fun listUsers(): Call<GiptoolUsers>

    @POST("productTransactions/")
    fun sendProductTransaction(productTransaction: ProductTransaction): Call<Void>
}

@Dao
interface ProductDao {

    @Insert
    fun insertAll(vararg products: Product)

    @Query("SELECT * FROM product LIMIT 6 OFFSET :offset")
    fun getProductPage(offset: Long): List<Product>

    @Query("SELECT COUNT(*) FROM product")
    fun getProductCount(): Long

    @Query("SELECT * FROM product WHERE UPPER(name) LIKE UPPER(:searchTerm) OR UPPER(line) LIKE UPPER(:searchTerm) LIMIT 6 OFFSET :offset")
    fun getProductPageSearch(searchTerm: String, offset: Long): List<Product>

    @Query("SELECT COUNT(*) FROM product WHERE UPPER(name) LIKE UPPER(:searchTerm)")
    fun getProductCountSearch(searchTerm: String): Long

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

}

@Dao
interface ProductTransactionDao {

    @Insert
    fun insertProductTransaction(transactions: ProductTransaction): Long

    @Insert
    fun insertProductTransactionItems(vararg items: ProductTransactionItem)

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
    abstract val giptoolService: GiptoolService
    abstract val vendingMachineId: String
    abstract val password: String
    abstract val demoMode: Boolean

    private val logoutTimerCallback: Runnable = Runnable { logOut() }
    private val logoutEventListeners: MutableList<() -> Unit> = mutableListOf()
    private val loginEventListeners: MutableList<() -> Unit> = mutableListOf()
    private val actionQueue = ArrayBlockingQueue<Action>(BUFFER_SIZE)

    var searchTerm = ""
    var canLogInViaRfid = false

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

    fun completeProductTransaction() {
        thread(start = true) {
            val user = currentUser
            if (user != null) {
                val tx = ProductTransaction(
                    null,
                    vendingMachineId,
                    user.id!!
                )
                val id = productTransactionDao.insertProductTransaction(tx)
                val items = basket.map {
                    ProductTransactionItem(
                        null,
                        id,
                        it.product.id!!,
                        it.count,
                        it.expenditure,
                        it.reference
                    )
                }.toTypedArray()
                productTransactionDao.insertProductTransactionItems(*items)
                schedule(Runnable {mutableBasket.clear()}, 0)
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
                syncUsers()
                syncProducts()
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

    fun nextAction(): Action? {
        return actionQueue.poll()
    }

    var currentUser: User? = null

    fun logIn(cardCode: String, usingRfid: Boolean = false) {
        if (usingRfid && !canLogInViaRfid) {
            return
        }
        thread(start = true) {
            val user = userDao.findUserByCardCode(cardCode)
            if (user != null) {
                currentUser = user
                schedule(Runnable {
                    for (listener in loginEventListeners) {
                        listener()
                    }
                    refresh()
                }, 0)
            }
        }
    }

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
                    completeProductTransaction()
                    logOut()
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

    private fun unsafeRefreshProductPages() {
        productPages = if (searchTerm == "") {
            val productCount = productDao.getProductCount()
            (0 until productCount step 6).map {
                ProductPage(productDao.getProductPage(it))
            }
        } else {
            val productCount = productDao.getProductCountSearch("%$searchTerm%")
            (0 until productCount step 6).map {
                ProductPage(productDao.getProductPageSearch("%$searchTerm%", it))
            }
        }
    }

    private fun syncProducts() {
        if (demoMode) {
            productDao.clearProducts()
            val products = (1L..20L).map {
                Product(it,
                    "Tuote $it",
                    "Kuvaus $it",
                    "",
                    "",
                    "",
                    "kpl",
                    "1%02d".format(it))
            }
            productDao.insertAll(*products.toTypedArray())
        } else {
            val products = giptoolService
                .listProducts(vendingMachineId)
                .execute()
                .body()!!
                .products
                .map {
                    Product(it.productId,
                        it.name.trim(),
                        it.description.trim(),
                        it.picture,
                        it.safetyCard,
                        it.productInfo,
                        it.unit,
                        it.line)
                }
                .toTypedArray()
            productDao.clearProducts()
            productDao.insertAll(*products)
        }
    }

    private fun syncUsers() {
        if (demoMode) {
        } else {
            val users = giptoolService
                .listUsers()
                .execute()
                .body()!!
                .users
                .map {
                    User(
                        it.id,
                        it.name.trim(),
                        it.cardCode.trim()
                    )
                }
                .toTypedArray()
            userDao.clearUsers()
            userDao.insertAll(*users)
        }
    }

    private fun syncProductTransactions() {

    }

    companion object {
        private const val BUFFER_SIZE = 1024
    }

}
