package fi.metatavu.acgpanel.model

import android.arch.persistence.room.*
import android.util.Log
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
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

@Dao
interface UserDao {

    @Query("UPDATE user SET removed = 1")
    fun markAllRemoved()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(vararg users: User): List<Long>

    @Update(onConflict = OnConflictStrategy.IGNORE)
    fun updateAll(vararg users: User)

    @Query("SELECT * FROM user WHERE removed = 0 AND cardCode IN (:cardCodes)")
    fun findUserByCardCode(cardCodes: List<String>): User?

    @Query("DELETE FROM user")
    fun clearUsers()

    @Query("SELECT * FROM user WHERE id = :id")
    fun findUserById(id: Long?): User?
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(vararg expenditures: Expenditure)

    @Query("SELECT * FROM expenditure")
    fun listAll(): List<Expenditure>

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
    protected abstract val useWiegandProfile1: Boolean
    protected abstract val useWiegandProfile2: Boolean
    protected abstract val timeoutInSeconds: Long

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
        if (loggedIn) {
            return
        }
        val convertedCodes: List<String>
        if (useWiegandProfile1) {
            // 24 data bits + 2 (discarded) parity bits
            convertedCodes = listOf(cardCode
                .drop(1).take(24)
                .toInt(2).toString()
                .padEnd(CODE_LENGTH, '0'))
        } else if (useWiegandProfile2) {
            var binary = cardCode.padEnd(35, '0').toULong(2)
            binary = binary xor 0x400000000UL;
            val hex = binary
                .toString(16)
                .toUpperCase()
            convertedCodes = listOf(
                hex
                    .padStart(10, '0')
                    .padEnd(CODE_LENGTH, '0'),
                hex
                    .padStart(9, '0')
                    .padEnd(CODE_LENGTH, '0'))
        } else {
            convertedCodes = listOf(cardCode.take(CODE_LENGTH))
        }
        Log.d(javaClass.name, "Converted codes: $convertedCodes");
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
                            true,
                            false
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
                            false,
                            false
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
        return expenditureDao.listAll().map {it.code ?: ""}
    }

    protected open fun syncUsers() {
        if (demoMode) {
            userDao.clearUsers()
            userDao.insertAll(
                User(1, "Harri Hyllyttäjä", "123456789012345", "123", "123", true, false),
                User(
                    2,
                    "Teppo Testikäyttäjä",
                    "4BA9ACED0000000",
                    "000",
                    "000",
                    false,
                    false
                )
            )
        } else {
            val result = usersService
                .listUsers(vendingMachineId)
                .execute()
            val body = result.body()
            if (result.isSuccessful && body != null) {
                val users = body.users.map {
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
            expenditureDao.upsertAll(
                Expenditure("KP1", "Kustannuspaikka 1"),
                Expenditure("KP2", "Kustannuspaikka 2"),
                Expenditure("KP3", "Kustannuspaikka 3"),
                Expenditure("KP4", "Kustannuspaikka 4")
            )
        } else {
            val result = expendituresService
                .listExpenditures(vendingMachineId)
                .execute()
            val body = result.body()
            if (result.isSuccessful && body != null) {
                val expenditures = body.expenditures.map {
                    Expenditure(it.code, it.name)
                }
                expenditureDao.clearExpenditures()
                expenditureDao.upsertAll(*expenditures.toTypedArray())
            }
        }
    }

    companion object {
        private const val CODE_LENGTH = 15
    }

}

