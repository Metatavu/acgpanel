package fi.metatavu.acgpanel.model

import android.arch.persistence.room.*
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import kotlin.concurrent.thread

@Entity(indices = [Index(
    value = arrayOf(
        "externalId",
        "line"
    ), unique = true
)])
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
    var line: String,
    var barcode: String,
    var removed: Boolean
)

data class ProductPage(val products: List<Product>)

@Dao
interface ProductDao {

    @Query("UPDATE product SET removed = 1")
    fun markAllRemoved()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg products: Product): List<Long>

    @Query("""SELECT * FROM product
              WHERE removed = 0
              ORDER BY line LIMIT 6 OFFSET :offset""")
    fun getProductPage(offset: Long): List<Product>

    @Query("SELECT COUNT(*) FROM product WHERE removed = 0")
    fun getProductCount(): Long

    @Query("""SELECT * FROM product
              WHERE removed = 0 AND
              (code = :searchTerm OR
                  line = :searchTerm OR
                  line = ('0' || :searchTerm) OR
                  line = ('00' || :searchTerm) OR
                  line = ('000' || :searchTerm) OR
                  barcode = :searchTerm)
              ORDER BY line LIMIT 6 OFFSET :offset""")
    fun getProductPageSearch(searchTerm: String, offset: Long): List<Product>

    @Query("""SELECT * FROM product
              WHERE removed = 0 AND
              (LOWER(name) LIKE :searchTerm
                  OR LOWER(description) LIKE :searchTerm)
              ORDER BY line LIMIT 6 OFFSET :offset""")
    fun getProductPageSearchAlphabetic(searchTerm: String, offset: Long): List<Product>

    @Query("""SELECT COUNT(*) FROM product
              WHERE removed = 0 AND
              (code = :searchTerm OR
                  line = :searchTerm OR
                  line = ('0' || :searchTerm) OR
                  line = ('00' || :searchTerm) OR
                  line = ('000' || :searchTerm) OR
                  barcode = :searchTerm)""")
    fun getProductCountSearch(searchTerm: String): Long

    @Query("""SELECT COUNT(*) FROM product
              WHERE removed = 0 AND
              (LOWER(name) LIKE :searchTerm
                  OR LOWER(description) LIKE :searchTerm)""")
    fun getProductCountSearchAlphabetic(searchTerm: String): Long

    @Query("SELECT * FROM product WHERE id=:id")
    fun findProductById(id: Long): Product?

    @Query("SELECT * FROM product WHERE externalId=:externalId")
    fun findProductByExternalId(externalId: Long): Product?

    @Query("DELETE FROM product")
    fun clearProducts()

}

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
    var barcode: String? = null
}

class GiptoolProducts {
    var products: MutableList<GiptoolProduct> = mutableListOf()
}

interface GiptoolProductsService {
    @GET("products/vendingMachine/{vendingMachineId}")
    fun listProducts(@Path("vendingMachineId") vendingMachineId: String): Call<GiptoolProducts>
}

abstract class ProductsModel {
    protected abstract val productDao: ProductDao
    protected abstract val demoMode: Boolean
    protected abstract val productsService: GiptoolProductsService
    protected abstract val vendingMachineId: String
    protected abstract fun schedule(callback: Runnable, timeout: Long)
    protected abstract fun transaction(tx: () -> Unit)

    var searchTerm = ""
    var productPages: List<ProductPage> = listOf()
        private set

    protected open fun syncProducts() {
        if (demoMode) {
            productDao.clearProducts()
            val products = (1L..20L).map {
                Product(
                    it,
                    it,
                    "Tuote $it",
                    "Kuvaus $it",
                    "",
                    "$it",
                    "",
                    "",
                    "kpl",
                    "1%02d".format((it - 1) % 12 + 1),
                    "",
                    false
                )
            }
            productDao.insertAll(*products.toTypedArray())
        } else {
            val products = productsService
                .listProducts(vendingMachineId)
                .execute()
                .body()!!
                .products
                .map {
                    Product(
                        null,
                        it.externalId!!,
                        it.name?.trim() ?: "",
                        it.description?.trim() ?: "",
                        it.picture ?: "",
                        it.code?.trim() ?: "",
                        it.safetyCard ?: "",
                        it.productInfo ?: "",
                        it.unit ?: "",
                        it.line?.trim() ?: "",
                        it.barcode?.trim() ?: "",
                        false
                    )
                }
                .toTypedArray()
            // TODO delete removed
            transaction {
                productDao.markAllRemoved()
                productDao.insertAll(*products)
            }
        }
    }

    fun refreshProductPages(callback: () -> Unit) {
        thread(start = true) {
            unsafeRefreshProductPages()
            schedule(Runnable { callback() }, 0)
        }
    }

    private fun unsafeRefreshProductPages() {
        productPages = when {
            searchTerm == "" -> {
                val productCount = productDao.getProductCount()
                (0 until productCount step 6).map {
                    ProductPage(productDao.getProductPage(it))
                }
            }
            searchTerm.contains(Regex("\\D")) -> {
                val productCount = productDao.getProductCountSearchAlphabetic("%${searchTerm.toLowerCase()}%")
                (0 until productCount step 6).map {
                    ProductPage(
                        productDao.getProductPageSearchAlphabetic(
                            "%${searchTerm.toLowerCase()}%",
                            it
                        )
                    )
                }
            }
            else -> {
                val productCount = productDao.getProductCountSearch(searchTerm)
                (0 until productCount step 6).map {
                    ProductPage(productDao.getProductPageSearch(searchTerm, it))
                }
            }
        }
    }

}
