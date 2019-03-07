package fi.metatavu.acgpanel.model

import android.arch.persistence.room.*
import android.util.Log
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
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

@Dao
interface UserDao {

    @Query("UPDATE user SET removed = 1")
    fun markAllRemoved()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(vararg users: User): List<Long>

    @Update(onConflict = OnConflictStrategy.IGNORE)
    fun updateAll(vararg users: User)

    @Query("SELECT * FROM user WHERE removed = 0 AND cardCode = :cardCode")
    fun findUserByCardCode(cardCode: String): User?

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

interface GiptoolUsersService {
    @GET("users/vendingMachine/{vendingMachineId}")
    fun listUsers(@Path("vendingMachineId") vendingMachineId: String): Call<GiptoolUsers>

    @POST("logInAttempts/")
    fun sendLogInAttempt(@Body productTransaction: GiptoolLogInAttempt): Call<GiptoolLogInAttempt>
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
    protected abstract val usersService: GiptoolUsersService
    protected abstract val vendingMachineId: String
    protected abstract val demoMode: Boolean

    private val logoutTimerCallback = Runnable {
        if (!isLocksOpen()) {
            logOut()
        }
    }

    private val logoutEventListeners: MutableList<() -> Unit> = mutableListOf()
    private val loginEventListeners: MutableList<() -> Unit> = mutableListOf()
    private val failedLoginEventListeners: MutableList<() -> Unit> = mutableListOf()

    var canLogInViaRfid = false
    var currentUser: User? = null

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

    fun logIn(cardCode: String, usingRfid: Boolean = false) {
        val truncatedCode = cardCode.take(15)
        if (usingRfid && !canLogInViaRfid) {
            return
        }
        if (truncatedCode == "" || truncatedCode.length < 5) {
            return
        }
        thread(start = true) {
            val user = userDao.findUserByCardCode(truncatedCode)
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
                            truncatedCode,
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
                            truncatedCode,
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
        clearBasket()
        clearProductSearch()
        clearLocks()
    }

    protected open fun syncUsers() {
        if (demoMode) {
            userDao.clearUsers()
            userDao.insertAll(
                User(1, "Harri Hyllyttäjä", "123456789012345", "123", "123", true, false),
                User(
                    2,
                    "Teppo Testikäyttäjä",
                    "4BA9ACED00000000",
                    "000",
                    "000",
                    false,
                    false
                )
            )
        } else {
            val body = usersService
                .listUsers(vendingMachineId)
                .execute()
                .body()
            if (body != null) {
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
                Log.e(javaClass.name, "Got empty response from server")
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

    companion object {
        private const val SESSION_TIMEOUT_MS = 5*60*1000L
    }

}

