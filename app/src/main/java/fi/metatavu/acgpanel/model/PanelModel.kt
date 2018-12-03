package fi.metatavu.acgpanel.model

import android.arch.persistence.room.*
import android.database.sqlite.SQLiteConstraintException
import retrofit2.Call
import retrofit2.http.GET
import java.io.IOException
import java.lang.NullPointerException
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
    var image: String
)

data class BasketItem(
    val product: Product,
    val count: Int,
    val expenditure: String,
    val reference: String) {

    fun withCount(count: Int): BasketItem {
        return BasketItem(product, count, expenditure, reference)
    }

}


data class ProductPage(val products: List<Product>)

class GiptoolProduct {
    var productId: Long? = null
    var name: String = ""
    var description: String = ""
    var picture: String = ""
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
    @GET("products/page/1")
    fun listProducts(): Call<GiptoolProducts>

    @GET("users/page/1")
    fun listUsers(): Call<GiptoolUsers>
}

@Dao
interface ProductDao {

    @Insert
    fun insertAll(vararg products: Product)

    @Query("SELECT * FROM product LIMIT 6 OFFSET :offset")
    fun getProductPage(offset: Long): List<Product>

    @Query("SELECT COUNT(*) FROM product")
    fun getProductCount(): Long

    @Query("SELECT * FROM product WHERE UPPER(name) LIKE UPPER(:searchTerm) LIMIT 6 OFFSET :offset")
    fun getProductPageSearch(searchTerm: String, offset: Long): List<Product>

    @Query("SELECT COUNT(*) FROM product WHERE UPPER(name) LIKE UPPER(:searchTerm)")
    fun getProductCountSearch(searchTerm: String): Long

    @Query("DELETE FROM product")
    fun nukeProducts()

}

@Dao
interface UserDao {

    @Insert
    fun insertAll(vararg users: User)

    @Query("SELECT * FROM user WHERE cardCode = :cardCode")
    fun findUserByCardCode(cardCode: String): User?

    @Query("DELETE FROM user")
    fun nukeUsers()

}

sealed class Action

data class OpenLockAction(val shelf: Int, val compartment: Int) : Action()

private const val SESSION_TIMEOUT_MS = 5*60*1000L

abstract class PanelModel {

    abstract fun schedule(callback: Runnable, timeout: Long)
    abstract fun unSchedule(callback: Runnable)
    abstract fun transaction(tx: () -> Unit)
    abstract val productDao: ProductDao
    abstract val userDao: UserDao
    abstract val giptoolService: GiptoolService

    private val logoutTimerCallback: Runnable = Runnable { logOut() }
    private val logoutEventListeners: MutableList<() -> Unit> = mutableListOf()
    private val loginEventListeners: MutableList<() -> Unit> = mutableListOf()
    private val actionQueue = ArrayBlockingQueue<Action>(BUFFER_SIZE)

    var canLogInViaRfid = false
    var currentProductIndex = 0
    var searchTerm = ""
    val basket: MutableList<BasketItem> = mutableListOf()

    init {
        thread(start = true) {
            unsafeRefreshProductPages()
        }
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
        currentProductIndex = 0
        basket.clear()
    }

    fun openLock() {
        actionQueue.add(OpenLockAction(0, 0))
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
        if (searchTerm == "") {
            val productCount = productDao.getProductCount()
            productPages = (0 until productCount step 6).map {
                ProductPage(productDao.getProductPage(it))
            }
        } else {
            val productCount = productDao.getProductCountSearch("%$searchTerm%")
            productPages = (0 until productCount step 6).map {
                ProductPage(productDao.getProductPageSearch("%$searchTerm%", it))
            }
        }
    }

    private fun syncProducts() {
        val products = giptoolService
            .listProducts()
            .execute()
            .body()!!
            .products
            .map {
                Product(it.productId,
                    it.name.trim(),
                    it.description.trim(),
                    it.picture)
            }
            .toTypedArray()
        productDao.nukeProducts()
        productDao.insertAll(*products)
    }

    private fun syncUsers() {
        val users = giptoolService
            .listUsers()
            .execute()
            .body()!!
            .users
            .map {
                User(it.id,
                    it.name.trim(),
                    it.cardCode.trim())
            }
            .toTypedArray()
        userDao.nukeUsers()
        userDao.insertAll(*users)
    }

    companion object {
        private const val BUFFER_SIZE = 1024
    }

}
