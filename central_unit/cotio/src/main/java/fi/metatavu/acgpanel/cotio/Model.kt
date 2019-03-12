package fi.metatavu.acgpanel.cotio

import android.arch.persistence.room.*
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import java.time.Instant

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
    val lockerCompartment: Int?
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
              LEFT JOIN lockerCode
              ON driver = lockerDriver
                AND compartment = lockerCompartment
              WHERE lockerCode.state = 'USED'
              OR lockerCode.state IS NULL
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

}

@Database(entities = [
    Locker::class,
    LockerCode::class,
    LockerCodeState::class
], version = 1, exportSchema = false)
private abstract class CotioDatabase: RoomDatabase() {
    abstract fun cotioDao(): CotioDao
}

private data class LockerCoordinates(
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
                    pair.second.map { compartment ->
                        LockerCoordinates(pair.first, compartment.toInt())
                    }
                }
        }
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
}

class CotioModel(private val context: Context) {

    private var lastCodeReadTime = Instant.MIN;

    private val db = Room.inMemoryDatabaseBuilder(
            context,
            CotioDatabase::class.java)
        .fallbackToDestructiveMigration()
        .addMigrations()
        .build()

    private val preferences = Preferences(context)

    private val cotioDao: CotioDao = db.cotioDao()

    private fun <V>transaction(function: () -> V): V = db.runInTransaction(function)

    private val lockOpenedListeners: MutableList<() -> Unit> = mutableListOf()

    private val lockOpenRequestListeners: MutableList<(LockerOpenRequest) -> Unit> = mutableListOf()

    fun addLockOpenedListener(listener: () -> Unit) {
        lockOpenedListeners.add(listener)
    }

    fun removeLockOpenedListener(listener: () -> Unit) {
        lockOpenedListeners.remove(listener)
    }

    fun addLockOpenRequestListener(listener: (LockerOpenRequest) -> Unit) {
        lockOpenRequestListeners.add(listener)
    }

    fun removeLockOpenRequestListener(listener: (LockerOpenRequest) -> Unit) {
        lockOpenRequestListeners.remove(listener)
    }

    private fun openLocker(driver: Int, compartment: Int, reset: Boolean = false) {
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
            for ((driver, compartment) in preferences.enabledLockers) {
                cotioDao.insertAll(Locker(driver, compartment))
            }
            cotioDao.insertAll(
                LockerCode("0001", LockerCodeState.FREE, null, null),
                LockerCode("0002", LockerCodeState.FREE, null, null),
                LockerCode("0003", LockerCodeState.FREE, null, null),
                LockerCode("0004", LockerCodeState.FREE, null, null),
                LockerCode("0005", LockerCodeState.FREE, null, null),
                LockerCode("0006", LockerCodeState.FREE, null, null),
                LockerCode("0007", LockerCodeState.FREE, null, null),
                LockerCode("ABCD", LockerCodeState.FREE, null, null),
                LockerCode("XYZ0", LockerCodeState.FREE, null, null),
                LockerCode("FFFF", LockerCodeState.FREE, null, null),
                LockerCode("9876", LockerCodeState.FREE, null, null)
            )
        }
    }

    fun readCode(code: String): CodeReadResult {
        val now = Instant.now()
        if (now.isBefore(lastCodeReadTime.plusSeconds(10))) {
            CodeReadResult.TooFrequentReads
        }
        lastCodeReadTime = now
        return transaction {
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
                        cotioDao.updateAll(
                            LockerCode(
                                code,
                                LockerCodeState.ACTIVE,
                                locker!!.driver,
                                locker!!.compartment
                            )
                        )
                        openLocker(locker!!.driver, locker!!.compartment)
                        CodeReadResult.CheckIn
                    }
                    LockerCodeState.ACTIVE -> {
                        cotioDao.updateAll(
                            LockerCode(
                                code,
                                LockerCodeState.USED,
                                lockerCode.lockerDriver!!,
                                lockerCode.lockerCompartment!!
                            )
                        )
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

}