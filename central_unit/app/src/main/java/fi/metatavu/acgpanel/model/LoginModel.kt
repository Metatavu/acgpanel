@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS")

package fi.metatavu.acgpanel.model

import android.arch.persistence.room.*
import android.util.Log
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.lang.IllegalStateException
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.concurrent.thread

@Entity
data class User(
    @PrimaryKey var id: Long?,
    var userName: String,
    var cardCode: String,
    var expenditure: String,
    var reference: String,
    var canShelve: Boolean,
    var removed: Boolean
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
data class Expenditure(
    @PrimaryKey var code: String,
    var description: String
)

@Entity(
    primaryKeys = ["userId", "customerCode"],
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            childColumns = ["userId"],
            parentColumns = ["id"]
        )
    ]
)
data class UserCustomer(
    var userId: Long,
    var customerCode: String
)

@Entity(
    primaryKeys = ["code", "customerCode"]
)
data class CustomerExpenditure(
    var code: String,
    var customerCode: String,
    var description: String
)

enum class CardReaderType {
    ACCESS_7C,
    ACCESS_7AH,
    SCHNEIDER_HID,
    IR6090B
}

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(vararg users: User): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(vararg userCustomers: UserCustomer): List<Long>

    @Query("SELECT * FROM user WHERE removed = 0 AND cardCode IN (:cardCodes)")
    fun findUserByCardCode(cardCodes: List<String>): User?

    @Query("SELECT * FROM user WHERE id = :id")
    fun findUserById(id: Long?): User?

    @Query("SELECT DISTINCT customerCode FROM userCustomer")
    fun listCustomerCodes(): List<String>

    @Query("UPDATE user SET removed = 1")
    fun markAllRemoved()

    @Update(onConflict = OnConflictStrategy.IGNORE)
    fun updateAll(vararg users: User)

    @Query("DELETE FROM user")
    fun clearUsers()

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
interface ExpenditureDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(vararg expenditures: Expenditure)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(vararg customerExpenditures: CustomerExpenditure)

    @Query("SELECT * FROM expenditure")
    fun listAll(): List<Expenditure>

    @Query("""SELECT DISTINCT CustomerExpenditure.*
              FROM CustomerExpenditure
              INNER JOIN UserCustomer
                  ON UserCustomer.customerCode = CustomerExpenditure.customerCode
              WHERE UserCustomer.userId = :userId""")
    fun listUserExpenditures(userId: Long): List<CustomerExpenditure>

    @Update(onConflict = OnConflictStrategy.IGNORE)
    fun updateAll(vararg expenditures: Expenditure)

    @Update(onConflict = OnConflictStrategy.IGNORE)
    fun updateAll(vararg customerExpenditures: CustomerExpenditure)


    @Query("DELETE FROM expenditure")
    fun clearExpenditures()

}

class GiptoolUser {
    var id: Long? = null
    var name: String = ""
    var cardCode: String? = null
    var expenditure: String? = null
    var reference: String? = null
    var canShelve: Boolean? = null
    var customerCodes: List<String> = listOf()
}

class GiptoolUsers {
    var users: MutableList<GiptoolUser> = mutableListOf()
}

class GiptoolLogInAttempt {
    var attemptNumber: Long? = null
    var vendingMachineId: String? = null
    var cardCode: String = ""
    var successful: Boolean = false
}

class GiptoolExpenditure {
    var code: String = ""
    var name: String = ""
}

class GiptoolExpenditures {
    var expenditures: MutableList<GiptoolExpenditure> = mutableListOf()
}

interface GiptoolUsersService {
    @GET("users/vendingMachine/{vendingMachineId}")
    fun listUsers(@Path("vendingMachineId") vendingMachineId: String): Call<GiptoolUsers>

    @POST("logInAttempts/")
    fun sendLogInAttempt(@Body productTransaction: GiptoolLogInAttempt): Call<GiptoolLogInAttempt>
}

interface GiptoolExpendituresService {
    @GET("expenditures/vendingMachine/{vendingMachineId}")
    fun listExpenditures(@Path("vendingMachineId") vendingMachineId: String): Call<GiptoolExpenditures>

    @GET("expenditures/customer/{customerId}")
    fun listUserExpenditures(@Path("customerId") customerId: String): Call<GiptoolExpenditures>
}

abstract class LoginModel {

    protected abstract fun schedule(callback: Runnable, timeout: Long)
    protected abstract fun unSchedule(callback: Runnable)
    protected abstract fun transaction(tx: () -> Unit)
    protected abstract fun isLocksOpen(): Boolean
    protected abstract fun clearLocks()
    protected abstract fun clearProductSearch()
    protected abstract fun clearBasket()
    protected abstract val userDao: UserDao
    protected abstract val logInAttemptDao: LogInAttemptDao
    protected abstract val expenditureDao: ExpenditureDao
    protected abstract val usersService: GiptoolUsersService
    protected abstract val expendituresService: GiptoolExpendituresService
    protected abstract val demoMode: Boolean
    protected abstract val timeoutInSeconds: Long
    protected abstract val cardReaderType: CardReaderType

    abstract val vendingMachineId: String
    abstract var password: String
    abstract val shouldPickUserExpenditure: Boolean

    private val logoutTimerCallback = Runnable {
        if (!isLocksOpen()) {
            logOut()
        }
    }

    private val logoutEventListeners: MutableList<() -> Unit> = mutableListOf()
    private val loginEventListeners: MutableList<() -> Unit> = mutableListOf()
    private val failedLoginEventListeners: MutableList<() -> Unit> = mutableListOf()
    private var lastRefresh = Instant.now()
    private var lastLogInAttempt = Instant.now()

    val shouldPollCardReader: Boolean
        get() = cardReaderType == CardReaderType.IR6090B
                && !loggedIn
                && Instant.now().isAfter(lastLogInAttempt.plusSeconds(LOGIN_COOLDOWN_IN_SECONDS))

    var currentUser: User? = null
        private set

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

    fun loginLessShelving() {
        currentUser = User(
            id = null,
            userName = "Ei käyttäjää",
            cardCode = "",
            expenditure = "",
            reference = "",
            canShelve = true,
            removed = false
        )
        for (listener in loginEventListeners) {
            listener()
        }
        refresh()
    }

    fun logIn(cardCode: String) {
        lastLogInAttempt = Instant.now()
        if (loggedIn) {
            return
        }
        val convertedCodes: List<String> = processCardCode(cardReaderType, cardCode)
        Log.d(javaClass.name, "Converted codes: $convertedCodes")
        if (convertedCodes.isEmpty()) {
            return
        }
        refresh()
        thread(start = true) {
            val user = userDao.findUserByCardCode(convertedCodes)
            if (user != null) {
                currentUser = user
                schedule(Runnable {
                    for (listener in loginEventListeners) {
                        listener()
                    }
                    refresh()
                }, 0)
                transaction {
                    logInAttemptDao.insertAll(
                        LogInAttempt(
                            null,
                            user.id,
                            user.cardCode,
                            successful = true,
                            uploaded = false
                        )
                    )
                }
                try {
                    syncLogInAttempts()
                } catch (e: Exception) {
                    Log.e(javaClass.name, "${e.javaClass.name}: ${e.message}")
                }
            } else {
                schedule(Runnable {
                    for (listener in failedLoginEventListeners) {
                        listener()
                    }
                }, 0)
                transaction {
                    logInAttemptDao.insertAll(
                        LogInAttempt(
                            null,
                            null,
                            convertedCodes[0],
                            successful = false,
                            uploaded = false
                        )
                    )
                }
                try {
                    syncLogInAttempts()
                } catch (e: Exception) {
                    Log.e(javaClass.name, "${e.javaClass.name}: ${e.message}")
                }
            }
        }
    }

    val loggedIn: Boolean
        get() = currentUser != null

    val timeLeft: Long
        get() = Duration.between(
                    Instant.now(),
                    lastRefresh.plusMillis(timeoutInSeconds * 1000L)
                ).get(ChronoUnit.SECONDS)

    fun refresh() {
        lastRefresh = Instant.now()
        unSchedule(logoutTimerCallback)
        schedule(logoutTimerCallback, timeoutInSeconds * 1000L)
    }

    fun logOut() {
        unSchedule(logoutTimerCallback)
        for (listener in logoutEventListeners) {
            listener()
        }
        currentUser = null
        clearBasket()
        clearProductSearch()
        clearLocks()
    }

    fun listExpenditures(): List<String> {
        val user = currentUser
        return if (user != null) {
            val expenditures = expenditureDao.listUserExpenditures(user.id ?: -1)
            if (expenditures.isEmpty()) {
                expenditureDao.listAll().map { it.code }
            } else {
                expenditures.map { it.code }
            }
        } else {
            expenditureDao.listAll().map { it.code }
        }
    }

    protected open fun syncUsers() {
        if (demoMode) {
            userDao.insertAll(
                User(1, "Harri Hyllyttäjä", "123456789012345", "123", "123", true, removed = false),
                User(
                    2,
                    "Teppo Testikäyttäjä",
                    "4BA9ACED0000000",
                    "000",
                    "000",
                    canShelve = false,
                    removed = false
                )
            )
        } else {
            val result = usersService
                .listUsers(vendingMachineId)
                .execute()
            val body = result.body()
            if (result.isSuccessful && body != null) {
                val giptoolUsers = body.users
                val users = giptoolUsers.map {
                    User(
                        it.id,
                        it.name.trim(),
                        it.cardCode?.trim() ?: "",
                        it.expenditure?.trim() ?: "",
                        it.reference?.trim() ?: "",
                        it.canShelve ?: false,
                        false
                    )
                }
                    .toTypedArray()
                // TODO delete removed
                transaction {
                    userDao.markAllRemoved()
                    userDao.insertAll(*users)
                    userDao.updateAll(*users)
                    for (giptoolUser in giptoolUsers) {
                        for (code in giptoolUser.customerCodes.filter{it.isBlank()}) {
                            userDao.insertAll(
                                UserCustomer(
                                    giptoolUser.id
                                        ?: throw IllegalStateException("Giptool user with no id"),
                                    code))
                        }
                    }
                }
            } else {
                Log.e(javaClass.name, "Couldn't sync users: ${result.errorBody()}")
            }
        }
    }

    protected open fun syncLogInAttempts() {
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
                val res = usersService.sendLogInAttempt(giptoolAttempt)
                    .execute()
                if (res.isSuccessful) {
                    logInAttemptDao.markUploaded(attempt.id!!)
                } else {
                    Log.e(javaClass.name, "Error when uploading login attempt: ${res.message()}")
                }
            }
        }
    }

    protected open fun syncExpenditures() {
        if (demoMode) {
            expenditureDao.insertAll(
                Expenditure("KP1", "Kustannuspaikka 1"),
                Expenditure("KP2", "Kustannuspaikka 2"),
                Expenditure("KP3", "Kustannuspaikka 3"),
                Expenditure("KP4", "Kustannuspaikka 4")
            )
        } else {
            transaction {
                val expendituresResult = expendituresService
                    .listExpenditures(vendingMachineId)
                    .execute()
                val expendituresBody = expendituresResult.body()
                if (expendituresResult.isSuccessful && expendituresBody != null) {
                    val expenditures = expendituresBody.expenditures.map {
                        Expenditure(it.code, it.name)
                    }
                    expenditureDao.clearExpenditures()
                    expenditureDao.insertAll(*expenditures.toTypedArray())
                    expenditureDao.updateAll(*expenditures.toTypedArray())
                }
                for (customerCode in userDao.listCustomerCodes()) {
                    val customerExpendituresResult = expendituresService
                        .listUserExpenditures(customerCode)
                        .execute()
                    val customerExpendituresBody = customerExpendituresResult.body()
                    if (customerExpendituresResult.isSuccessful &&
                        customerExpendituresBody != null
                    ) {
                        val customerExpenditures = customerExpendituresBody.expenditures.map {
                            CustomerExpenditure(it.code, customerCode, it.name)
                        }.toTypedArray()
                        expenditureDao.insertAll(*customerExpenditures)
                        expenditureDao.updateAll(*customerExpenditures)
                    }
                }
            }
        }
    }

    companion object {
        private const val LOGIN_COOLDOWN_IN_SECONDS = 2L
    }

}
