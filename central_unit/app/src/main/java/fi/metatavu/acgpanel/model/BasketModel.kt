package fi.metatavu.acgpanel.model

import android.arch.persistence.room.*
import android.util.Log
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import kotlin.concurrent.thread

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

interface GiptoolProductTransactionsService {
    @POST("productTransactions/")
    fun sendProductTransaction(@Body productTransaction: GiptoolProductTransaction): Call<GiptoolProductTransaction>
}

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

abstract class BasketModel {

    private sealed class SelectedBasketItem {
        class New(val product: Product) : SelectedBasketItem()
        class Existing(val index: Int): SelectedBasketItem()
    }

    protected abstract fun schedule(callback: Runnable, timeout: Long)
    protected abstract fun transaction(tx: () -> Unit)
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
                val tx = ProductTransaction(
                    null,
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
                schedule(Runnable { callback() }, 0)
            }
        }
    }

    fun deleteBasketItem(index: Int) {
        mutableBasket.removeAt(index)
    }

    fun removeZeroCountItems() {
        mutableBasket.removeIf { it.count < 1 }
    }

    fun clearBasket() {
        mutableBasket.clear()
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
