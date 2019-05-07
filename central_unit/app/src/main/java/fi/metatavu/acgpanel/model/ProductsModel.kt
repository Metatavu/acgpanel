package fi.metatavu.acgpanel.model

import android.arch.persistence.room.*
import android.util.Log
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
    var productInfo: String,
    var unit: String,
    var line: String,
    var barcode: String,
    var removed: Boolean,
    var empty: Boolean,
    var serverStock: Int
)

@Entity(primaryKeys = ["productId", "safetyCard"])
data class ProductSafetyCard(
    var productId: Long,
    var safetyCard: String,
    var removed: Boolean
)

data class ProductPage(val products: List<Product>)

@Dao
interface ProductDao {

    @Query("UPDATE product SET removed = 1")
    fun markAllProductsRemoved()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(vararg products: Product): List<Long>

    @Update(onConflict = OnConflictStrategy.IGNORE)
    fun updateAll(vararg products: Product)

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

    @Query("SELECT * FROM product WHERE externalId=:externalId AND line=:line")
    fun findProductByExternalIdAndLine(externalId: Long, line: String): Product?

    @Query("DELETE FROM product")
    fun clearProducts()

    @Query("UPDATE productsafetycard SET removed = 1")
    fun markAllSafetyCardsRemoved()

    @Query("SELECT * FROM productsafetycard WHERE removed=0 AND productId=:productId")
    fun listSafetyCardsByProductId(productId: Long): List<ProductSafetyCard>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(vararg safetyCards: ProductSafetyCard): List<Long>

    @Update(onConflict = OnConflictStrategy.IGNORE)
    fun updateAll(vararg safetyCards: ProductSafetyCard)

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
    var stock: Int? = null
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
                    id = it,
                    externalId = it,
                    name = "Tuote $it",
                    description = "Kuvaus $it",
                    image = "",
                    code = "$it",
                    productInfo = "",
                    unit = "kpl",
                    line = "1%02d".format((it - 1) % 12 + 1),
                    barcode = "",
                    removed = false,
                    empty = false,
                    serverStock = 0
                )
            }
            productDao.insertAll(*products.toTypedArray())
        } else {
            val result = productsService
                .listProducts(vendingMachineId)
                .execute()
            if (!(result.isSuccessful && result.body() != null)) {
                Log.e(javaClass.name, "Error while syncing products: ${result.errorBody()}")
                return
            }
            val giptoolProducts = result
                .body()!!
                .products
            val products = giptoolProducts
                .map {
                    Product(
                        id = null,
                        externalId = it.externalId!!,
                        name = it.name?.trim() ?: "",
                        description = it.description?.trim() ?: "",
                        image = it.picture ?: "",
                        code = it.code?.trim() ?: "",
                        productInfo = it.productInfo ?: "",
                        unit = it.unit ?: "",
                        line = it.line?.trim() ?: "",
                        barcode = it.barcode?.trim() ?: "",
                        removed = false,
                        empty = false,
                        serverStock = it.stock ?: 0
                    )
                }
                .toTypedArray()
            // TODO delete removed
            transaction {
                productDao.markAllProductsRemoved()
                for (product in products) {
                    val existing = productDao.findProductByExternalIdAndLine(
                        product.externalId, product.line)
                    if (existing != null) {
                        product.id = existing.id
                        if (product.serverStock > existing.serverStock) {
                            product.empty = false
                        } else {
                            product.empty = existing.empty
                        }
                        productDao.updateAll(product)
                    } else {
                        productDao.insertAll(product)
                    }
                }
                for (giptoolProduct in giptoolProducts) {
                    val safetyCardFiles = giptoolProduct
                        .safetyCard?.split(",") ?: emptyList()
                    val product = productDao.findProductByExternalId(
                        giptoolProduct.externalId ?: continue) ?: continue
                    for (fileName in safetyCardFiles) {
                        if (fileName != "") {
                            productDao.insertAll(
                                ProductSafetyCard(
                                    product.id!!,
                                    fileName,
                                    false
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun refreshProductPages(callback: () -> Unit) {
        thread(start = true) {
            unsafeRefreshProductPages()
            schedule(Runnable { callback() }, 0)
        }
    }

    protected open fun unsafeRefreshProductPages() {
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

    fun listProductSafetyCards(product: Product, callback: (List<String>) -> Unit) {
        thread(start = true) {
            val productId = product.id
            if (productId != null) {
                callback(
                    productDao
                        .listSafetyCardsByProductId(productId)
                        .map {
                            it.safetyCard
                        }
                )
            }
        }
    }

    fun markNotEmpty(it: Product, callback: () -> Unit) {
        thread(start = true) {
            it.empty = false
            productDao.updateAll(it)
            unsafeRefreshProductPages()
            callback()
        }
    }

}
