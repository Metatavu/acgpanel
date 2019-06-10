package fi.metatavu.acgpanel.model

import android.arch.persistence.room.*
import android.util.Log
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import java.time.Instant
import java.util.*
import kotlin.concurrent.thread

/**
 * One item (row) in a batch of items an user purchases
 * from the vending machine.
 */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = ProductTransactionItemType::class,
            childColumns = ["type"],
            parentColumns = ["type"]
        )
    ],
    indices = [
        Index("type")
    ]
)
data class ProductTransactionItem(
    @PrimaryKey var id: Long?,
    var transactionId: Long,
    var productId: Long,
    var count: Int,
    var expenditure: String,
    var reference: String,
    var type: String
)

@Entity(
    primaryKeys = ["type"]
)
data class ProductTransactionItemType(
    val type: String
) {
    companion object {
        val PURCHASE = "PURCHASE"
        val BORROW = "BORROW"
    }
}

@Entity
data class ProductTransaction(
    @PrimaryKey var id: Long?,
    var userId: Long,
    var uploaded: Boolean = false
)

@Entity
data class ProductBorrow(
    @PrimaryKey var id: Long?,
    var productId: Long,
    var userId: Long,
    var expenditure: String,
    var reference: String,
    var started: Instant,
    var ended: Instant?
)

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
interface ProductBorrowDao {

    @Insert
    fun insertAll(vararg borrows: ProductBorrow)

    @Query("""
        SELECT *
        FROM ProductBorrow
        WHERE ended IS NULL
          AND productId = :productId
        ORDER BY started DESC
        LIMIT 1
    """)
    fun getActiveBorrowForProduct(productId: Long)
}

class GiptoolProductTransactionItem {
    var productId: Long? = null
    var line: String = ""
    var count: Int = 0
    var expenditure: String = ""
    var reference: String = ""

    override fun toString(): String {
        return "GiptoolProductTransactionItem(" +
                "productId=$productId," +
                "line=$line," +
                "count=$count," +
                "expenditure=$expenditure," +
                "reference=$reference)"
    }
}

class GiptoolProductTransaction {
    var transactionNumber: Long? = null
    var userId: Long? = null
    var vendingMachineId: String? = null
    val items: MutableList<GiptoolProductTransactionItem> = mutableListOf()

    override fun toString(): String {
        return "GiptoolProductTransaction(" +
                "transactionNumber=$transactionNumber," +
                "userId=$userId," +
                "vendingMachineId=$vendingMachineId," +
                "items=$items)"
    }
}

interface GiptoolProductTransactionsService {
    @POST("productTransactions/")
    fun sendProductTransaction(@Body productTransaction: GiptoolProductTransaction): Call<GiptoolProductTransaction>
}

data class BasketItem(
    val product: Product,
    val count: Int,
    val expenditure: String,
    val reference: String,
    val enabled: Boolean = true) {

    fun withCount(count: Int): BasketItem {
        return BasketItem(product, count, expenditure, reference, enabled)
    }

    fun withExpenditure(expenditure: String): BasketItem {
        return BasketItem(product, count, expenditure, reference, enabled)
    }

    fun withReference(reference: String): BasketItem {
        return BasketItem(product, count, expenditure, reference, enabled)
    }

    fun disabled(): BasketItem {
        return BasketItem(product, count, expenditure, reference, false)
    }

    fun enabled(): BasketItem {
        return BasketItem(product, count, expenditure, reference, true)
    }

}

abstract class BasketModel {

    private sealed class SelectedBasketItem {
        class New(val product: Product) : SelectedBasketItem()
        class Existing(val index: Int): SelectedBasketItem()
    }

    protected abstract fun schedule(callback: Runnable, timeout: Long)
    protected abstract fun transaction(tx: () -> Unit)
    protected abstract fun unsafeUpdateProducts()
    protected abstract val productDao: ProductDao
    protected abstract val productTransactionDao: ProductTransactionDao
    protected abstract val productTransactionsService: GiptoolProductTransactionsService
    protected abstract val vendingMachineId: String
    protected abstract val demoMode: Boolean
    abstract fun acceptBasket()
    abstract val lockUserExpenditure: Boolean
    abstract val lockUserReference: Boolean

    private var selectedBasketItem: SelectedBasketItem? = null
    private val mutableBasket: MutableList<BasketItem> = mutableListOf()

    protected abstract val currentUser: User?
    val basket: List<BasketItem>
        get() = mutableBasket

    val currentBasketItem: BasketItem?
        get() {
            val item = selectedBasketItem
            return when (item) {
                is SelectedBasketItem.Existing -> basket[item.index]
                is SelectedBasketItem.New -> BasketItem(
                    item.product,
                    1,
                    currentUser?.expenditure ?: "",
                    currentUser?.reference ?: ""
                )
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
                val existingIndex = mutableBasket.indexOfFirst {
                    it.product.id == item.product.id &&
                            it.expenditure == expenditure &&
                            it.reference == reference
                }
                if (existingIndex >= 0) {
                    val old = mutableBasket[existingIndex]
                    mutableBasket[existingIndex] = old
                        .withCount(old.count + (count ?: 1))
                } else {
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
    }

    fun completeProductTransaction(callback: () -> Unit) {
        thread(start = true) {
            val user = currentUser
            if (user != null) {
                transaction {
                    val tx = ProductTransaction(
                        null,
                        user.id!!
                    )
                    val id = productTransactionDao.insertProductTransaction(tx)
                    Log.d(javaClass.name, "ProductTransaction: $tx")
                    val items = basket.map {
                        ProductTransactionItem(
                            null,
                            id,
                            it.product.id!!,
                            it.count,
                            it.expenditure,
                            it.reference,
                            ProductTransactionItemType.PURCHASE
                        )
                    }.toTypedArray()
                    productTransactionDao.insertProductTransactionItems(*items)
                    Log.d(javaClass.name, "ProductTransactionItems: ${Arrays.toString(items)}")
                    Log.d(javaClass.name, "Completed product transaction")
                    schedule(Runnable { callback() }, 100)
                }
            }
        }
    }

    fun deleteBasketItem(index: Int) {
        mutableBasket.removeAt(index)
    }

    fun markProductEmpty(index: Int) {
        val product = basket[index].product
        product.empty = true
        thread(start = true) {
            productDao.updateAll(product)
            unsafeUpdateProducts()
        }
    }

    fun markCurrentProductEmpty() {
        val item = selectedBasketItem
        if (item is SelectedBasketItem.Existing) {
            val product = basket[item.index].product
            product.empty = true
            thread(start = true) {
                productDao.updateAll(product)
                unsafeUpdateProducts()
            }
        }
    }


    fun removeZeroCountItems() {
        mutableBasket.removeIf { it.count < 1 }
    }

    fun clearBasket() {
        mutableBasket.clear()
    }

    fun disableAll() {
        mutableBasket.replaceAll { it.disabled() }
    }

    fun disableItemsInLine(line: String) {
        mutableBasket.replaceAll {
            if (it.product.line == line) {
                it.disabled()
            } else {
                it
            }
        }
    }

    fun enableItemsInLine(line: String) {
        mutableBasket.replaceAll {
            if (it.product.line == line) {
                it.enabled()
            } else {
                it
            }
        }
    }


    protected open fun syncProductTransactions() {
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
                Log.d(javaClass.name, "Uploading product transaction: $giptoolTx")
                val res = productTransactionsService.sendProductTransaction(giptoolTx)
                    .execute()
                if (res.isSuccessful) {
                    productTransactionDao.markUploaded(tx.id!!)
                } else {
                    Log.e(javaClass.name, "Error when uploading product transaction: ${res.message()}")
                }
            }
        }
    }

}
