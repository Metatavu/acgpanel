package fi.metatavu.acgpanel.cotio

import android.arch.persistence.room.*
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import java.time.Instant
import kotlin.concurrent.thread

@Entity(primaryKeys = ["driver", "compartment"])
private data class Locker(
    val driver: Int,
    val compartment: Int
)

@Entity(foreignKeys = [
    ForeignKey(
        entity = LockerCodeState::class,
        childColumns = ["state"],
        parentColumns = ["state"]),
    ForeignKey(
        entity = Locker::class,
        childColumns = ["lockerDriver", "lockerCompartment"],
        parentColumns = ["driver", "compartment"])
], indices = [
    Index("state"),
    Index("lockerDriver", "lockerCompartment")
])
private data class LockerCode(
    @PrimaryKey val code: String,
    val state: String,
    val lockerDriver: Int?,
    val lockerCompartment: Int?,
    val created: Instant? = null,
    val checkedIn: Instant? = null,
    val checkedOut: Instant? = null
)

@Entity
private data class LockerCodeState(
    @PrimaryKey val state: String
) {
    companion object {
        const val FREE = "FREE"
        const val ACTIVE = "ACTIVE"
        const val USED = "USED"
    }
}

@Dao
private interface CotioDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(vararg locker: Locker)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(vararg lockerCode: LockerCode)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(vararg lockerCodeState: LockerCodeState)

    @Query("""SELECT locker.*
              FROM locker
              WHERE compartment || '/' || driver
                NOT IN
                    (SELECT lockerCompartment || '/' || lockerDriver
                     FROM lockerCode
                     WHERE state = 'ACTIVE')
              ORDER BY locker.driver, locker.compartment
              LIMIT 1""")
    fun nextFreeLocker(): Locker?

    @Query("""SELECT *
              FROM lockerCode
              WHERE code = :code""")
    fun findLockerCode(code: String): LockerCode?

    @Update(onConflict = OnConflictStrategy.IGNORE)
    fun updateAll(vararg locker: Locker)

    @Update(onConflict = OnConflictStrategy.IGNORE)
    fun updateAll(vararg lockerCode: LockerCode)

    @Update(onConflict = OnConflictStrategy.IGNORE)
    fun updateAll(vararg lockerCodeState: LockerCodeState)

    @Delete
    fun deleteAll(vararg lockerCode: LockerCode)

    @Delete
    fun deleteAll(vararg locker: Locker)

    @Query("""DELETE FROM lockerCode
              WHERE lockerDriver = :driver
                AND lockerCompartment = :compartment
                AND state = 'ACTIVE'""")
    fun clearLocker(driver: Int, compartment: Int)

}

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? =
        if (value != null) Instant.ofEpochMilli(value) else null

    @TypeConverter
    fun instantToTimestamp(value: Instant?): Long? =
        value?.toEpochMilli()
}

@Database(entities = [
    Locker::class,
    LockerCode::class,
    LockerCodeState::class
], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
private abstract class CotioDatabase: RoomDatabase() {
    abstract fun cotioDao(): CotioDao
}

data class LockerCoordinates(
    val driver: Int,
    val compartment: Int
)

private class Preferences(private val context: Context) {

    private fun preferences(): SharedPreferences {
        return PreferenceManager
            .getDefaultSharedPreferences(context)
    }

    private fun getString(resId: Int): String {
        return context.getString(resId)
    }

    val enabledLockers: List<LockerCoordinates>
        get() {
            val drivers = listOf(
                preferences().getStringSet(getString(R.string.pref_key_enabled_lockers_1), setOf()),
                preferences().getStringSet(getString(R.string.pref_key_enabled_lockers_2), setOf()),
                preferences().getStringSet(getString(R.string.pref_key_enabled_lockers_3), setOf()),
                preferences().getStringSet(getString(R.string.pref_key_enabled_lockers_4), setOf()))
            return drivers.mapIndexed { driver, compartments ->
                    Pair(driver + 1, compartments)
                }.flatMap { pair ->
                    pair.second!!.map { compartment ->
                        LockerCoordinates(pair.first, compartment.toInt())
                    }
                }
        }

    val clearCodeAfterUse: Boolean
        get() = preferences().getBoolean(getString(R.string.pref_key_clear_code_after_use), false)

    val useDemoCodes: Boolean
        get() = preferences().getBoolean(getString(R.string.pref_key_use_demo_codes), false)

    val maintenancePasscode: String
        get() = preferences().getString(getString(R.string.pref_key_maintenance_passcode), "0000") ?: "0000"
}

data class LockerOpenRequest(
    val driver: Int,
    val compartment: Int,
    val reset: Boolean
)

sealed class CodeReadResult {
    object CheckIn: CodeReadResult()
    object CheckOut: CodeReadResult()
    object NotFound: CodeReadResult()
    object CodeUsed: CodeReadResult()
    object TooFrequentReads: CodeReadResult()
    object NoFreeLockers: CodeReadResult()
}

sealed class CodeAddResult {
    object Success: CodeAddResult()
    object TooFrequentAdds: CodeAddResult()
    data class InvalidCode(val error: String): CodeAddResult()
}


class CotioModel(private val context: Context) {

    private var lastCodeReadTime = Instant.MIN
    private var lastCodeAddTime = Instant.MIN

    private val db = Room.databaseBuilder(
            context,
            CotioDatabase::class.java,
            DATABASE_NAME)
        .fallbackToDestructiveMigration()
        .addMigrations()
        .build()

    private val preferences = Preferences(context)

    private val cotioDao: CotioDao = db.cotioDao()

    private fun <V>transaction(function: () -> V): V = db.runInTransaction(function)

    private val lockOpenedListeners: MutableList<() -> Unit> = mutableListOf()

    private val lockOpenRequestListeners: MutableList<(LockerOpenRequest) -> Unit> = mutableListOf()

    val maintenancePasscode: String
        get() = preferences.maintenancePasscode

    @Suppress("unused")
    fun addLockOpenedListener(listener: () -> Unit) {
        lockOpenedListeners.add(listener)
    }

    @Suppress("unused")
    fun removeLockOpenedListener(listener: () -> Unit) {
        lockOpenedListeners.remove(listener)
    }

    fun addLockOpenRequestListener(listener: (LockerOpenRequest) -> Unit) {
        lockOpenRequestListeners.add(listener)
    }

    @Suppress("unused")
    fun removeLockOpenRequestListener(listener: (LockerOpenRequest) -> Unit) {
        lockOpenRequestListeners.remove(listener)
    }

    fun openLocker(driver: Int, compartment: Int, reset: Boolean = false) {
        if (lockOpenRequestListeners.size != 1) {
            throw IllegalStateException("Exactly one request listener must be present")
        }
        for (listener in lockOpenRequestListeners) {
            listener(LockerOpenRequest(driver, compartment, reset))
        }
    }

    fun init() {
        transaction {
            cotioDao.insertAll(
                LockerCodeState(LockerCodeState.FREE),
                LockerCodeState(LockerCodeState.ACTIVE),
                LockerCodeState(LockerCodeState.USED)
            )
            val lockers = preferences.enabledLockers
            for (driver in 1..4) {
                for (compartment in 1..12) {
                    if (lockers.contains(LockerCoordinates(driver, compartment))) {
                        cotioDao.insertAll(Locker(driver, compartment))
                    } else {
                        cotioDao.deleteAll(Locker(driver, compartment))
                    }
                }
            }
            if (preferences.useDemoCodes) {
                val now = Instant.now()
                val lockerCodes = arrayOf(
                    LockerCode("0001", LockerCodeState.FREE, null, null, now),
                    LockerCode("0002", LockerCodeState.FREE, null, null, now),
                    LockerCode("0003", LockerCodeState.FREE, null, null, now),
                    LockerCode("0004", LockerCodeState.FREE, null, null, now),
                    LockerCode("0005", LockerCodeState.FREE, null, null, now),
                    LockerCode("0006", LockerCodeState.FREE, null, null, now),
                    LockerCode("0007", LockerCodeState.FREE, null, null, now),
                    LockerCode("A9EAE4WM", LockerCodeState.FREE, null, null, now)
                )
                cotioDao.insertAll(*lockerCodes)
                cotioDao.updateAll(*lockerCodes)
            }
        }
    }

    val enabledLockers: List<LockerCoordinates>
        get() = preferences.enabledLockers

    fun addCode(rawCode: String): CodeAddResult {
        val code = rawCode
            .removePrefix(context.getString(R.string.code_prefix_https))
            .removePrefix(context.getString(R.string.code_prefix_http))
            .removePrefix(" ")
        val now = Instant.now()
        val lockerCode = LockerCode(code, LockerCodeState.FREE, null, null, now)
        if (now.isBefore(lastCodeAddTime.plusSeconds(10))) {
            return CodeAddResult.TooFrequentAdds
        }
        lastCodeAddTime = now
        try {
            cotioDao.insertAll(lockerCode)
            cotioDao.updateAll(lockerCode)
        } catch (ex: Exception) {
            return CodeAddResult.InvalidCode(ex.toString())
        }
        return CodeAddResult.Success
    }

    fun clearLocker(driver: Int, compartment: Int) {
        cotioDao.clearLocker(driver, compartment)
    }

    fun readCode(rawCode: String): CodeReadResult {
        val code = rawCode
            .removePrefix(context.getString(R.string.code_prefix_https))
            .removePrefix(context.getString(R.string.code_prefix_http))
            .removePrefix(" ")
        val now = Instant.now()
        if (now.isBefore(lastCodeReadTime.plusSeconds(10))) {
            return CodeReadResult.TooFrequentReads
        }
        lastCodeReadTime = now
        return transaction tx@{
            val lockerCode = cotioDao.findLockerCode(code)
            if (lockerCode == null) {
                CodeReadResult.NotFound
            } else {
                when (lockerCode.state) {
                    LockerCodeState.USED -> {
                        CodeReadResult.CodeUsed
                    }
                    LockerCodeState.FREE -> {
                        val locker = cotioDao.nextFreeLocker()
                            ?: return@tx CodeReadResult.NoFreeLockers
                        cotioDao.updateAll(
                            LockerCode(
                                code,
                                LockerCodeState.ACTIVE,
                                locker.driver,
                                locker.compartment,
                                lockerCode.created,
                                Instant.now(),
                                null
                            )
                        )
                        openLocker(locker.driver, locker.compartment)
                        CodeReadResult.CheckIn
                    }
                    LockerCodeState.ACTIVE -> {
                        if (preferences.clearCodeAfterUse) {
                            cotioDao.deleteAll(lockerCode)
                        } else {
                            cotioDao.updateAll(
                                LockerCode(
                                    code,
                                    LockerCodeState.USED,
                                    lockerCode.lockerDriver!!,
                                    lockerCode.lockerCompartment!!,
                                    lockerCode.created,
                                    lockerCode.checkedIn,
                                    Instant.now()
                                )
                            )
                        }
                        openLocker(
                            lockerCode.lockerDriver!!,
                            lockerCode.lockerCompartment!!
                        )
                        CodeReadResult.CheckOut
                    }
                    else -> {
                        throw IllegalStateException("Invalid locker code state")
                    }
                }
            }
        }
    }

    companion object {
        private const val DATABASE_NAME = "cotio.db"
    }

}