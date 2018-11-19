package fi.metatavu.acgpanel.model

import android.arch.persistence.room.*
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
    var description: String
)

data class ProductPage(val products: List<Product>)

@Dao
interface ProductDao {
    @Query("SELECT * FROM product LIMIT 6 OFFSET :offset")
    fun getProductPage(offset: Long): List<Product>

    @Query("SELECT COUNT(*) FROM product")
    fun getProductCount(): Long

    @Query("SELECT * FROM product WHERE UPPER(name) LIKE UPPER(:searchTerm) LIMIT 6 OFFSET :offset")
    fun getProductPageSearch(searchTerm: String, offset: Long): List<Product>

    @Query("SELECT COUNT(*) FROM product WHERE UPPER(name) LIKE UPPER(:searchTerm)")
    fun getProductCountSearch(searchTerm: String): Long

    @Insert
    fun insertAll(vararg products: Product)
}

private const val SESSION_TIMEOUT_MS = 5*60*1000L

abstract class PanelModel {

    abstract fun schedule(callback: Runnable, timeout: Long)
    abstract fun unSchedule(callback: Runnable)
    abstract val productDao: ProductDao

    private val logoutTimerCallback: Runnable = Runnable { logOut() }
    private val logoutEventListeners: MutableList<() -> Unit> = mutableListOf()
    private val loginEventListeners: MutableList<() -> Unit> = mutableListOf()

    var canLogInViaRfid = false
    var currentProduct: Product? = null
    var searchTerm = ""

    init {
        thread(start = true) {
            productDao.insertAll(
                Product(null, "Lapio", "Lorem"),
                Product(null, "Kirves", "Ipsum"),
                Product(null, "Saha", "Dolor"),
                Product(null, "Vasara", "Sit"),
                Product(null, "Kuokka", "Amet"),
                Product(null, "Tuote 6", "Lorem"),
                Product(null, "Tuote 7", "Ipsum"),
                Product(null, "Tuote 8", "Dolor")
            )
            unsafeRefreshProductPages()
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

    var currentUser: User? = null

    fun logIn(cardCode: String, usingRfid: Boolean = false) {
        if (usingRfid && !canLogInViaRfid) {
            return;
        }
        currentUser = User(
            1,
            "Matti Meikäläinen",
            cardCode
        )
        for (listener in loginEventListeners) {
            listener()
        }
        refresh()
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
    }

    var productPages: List<ProductPage> = listOf()

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
}
