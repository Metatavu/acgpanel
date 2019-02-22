package fi.metatavu.acgpanel.model

import android.arch.persistence.room.*

@Entity
data class SystemProperties(
    @PrimaryKey var id: Long? = null,
    var containsDemoData: Boolean
)

@Dao
interface SystemPropertiesDao {

    @Insert
    fun insert(systemProperties: SystemProperties)

    @Query("DELETE FROM systemproperties")
    fun clear()

    @Query("SELECT * FROM systemproperties WHERE id=1")
    fun getSystemProperties(): SystemProperties?

}

abstract class DemoModel {

    protected abstract fun schedule(callback: Runnable, timeout: Long)
    protected abstract fun unSchedule(callback: Runnable)
    protected abstract fun transaction(tx: () -> Unit)
    protected abstract val productDao: ProductDao
    protected abstract val userDao: UserDao
    protected abstract val productTransactionDao: ProductTransactionDao
    protected abstract val logInAttemptDao: LogInAttemptDao
    protected abstract val systemPropertiesDao: SystemPropertiesDao
    abstract val demoMode: Boolean

    protected open fun demoModeCleanup() {
        transaction {
            val props = systemPropertiesDao.getSystemProperties()
            if (props != null) {
                if (!demoMode && props.containsDemoData) {
                    productTransactionDao.clearProductTransactionsItems()
                    productTransactionDao.clearProductTransactions()
                    logInAttemptDao.clearLogInAttempts()
                    productDao.clearProducts()
                    userDao.clearUsers()
                }
                systemPropertiesDao.clear()
            }
            val newProps = SystemProperties(1, demoMode)
            systemPropertiesDao.insert(newProps)
       }
    }

}
