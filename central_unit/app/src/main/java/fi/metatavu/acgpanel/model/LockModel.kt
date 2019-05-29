package fi.metatavu.acgpanel.model

import android.arch.persistence.room.*
import android.util.Log
import java.lang.IllegalStateException
import kotlin.concurrent.thread

@Entity
data class CompartmentMapping(
    @PrimaryKey var line: String,
    var shelf: Int,
    var compartment: Int
)

@Dao
interface CompartmentMappingDao {

    @Query("SELECT * FROM compartmentmapping WHERE line=:line")
    fun getCompartmentMapping(line: String): CompartmentMapping?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun mapCompartments(vararg compartmentMappings: CompartmentMapping)

    @Query("SELECT * FROM compartmentmapping WHERE shelf=:shelf AND compartment=:compartment")
    fun getCompartmentMappingsInverse(shelf: Int, compartment: Int): List<CompartmentMapping>

    @Query("DELETE FROM compartmentmapping WHERE shelf=:shelf AND compartment=:compartment")
    fun clearCompartmentMappings(shelf: Int, compartment: Int)

}

data class LockOpenRequest(
    val shelf: Int,
    val compartment: Int,
    val reset: Boolean
)

data class AssignShelfRequest(
    val shelf: Int
)

abstract class LockModel {

    protected abstract fun schedule(callback: Runnable, timeout: Long)
    protected abstract fun unSchedule(callback: Runnable)
    protected abstract fun transaction(tx: () -> Unit)
    protected abstract fun syncProductTransactions()
    protected abstract fun logOut()
    protected abstract fun completeProductTransaction(function: () -> Unit)
    protected abstract val compartmentMappingDao: CompartmentMappingDao
    abstract val isShelvingMode: Boolean

    private val lockOpenTimerCallback = Runnable {
    }
    private val lockOpenedListeners: MutableList<() -> Unit> = mutableListOf()
    private val lockOpenRequestListeners: MutableList<(LockOpenRequest) -> Unit> = mutableListOf()
    private val assignShelfRequestListeners: MutableList<(AssignShelfRequest) -> Unit> = mutableListOf()
    private var numInitialLinesToOpen = 0
    private val linesToOpen: MutableList<String> = mutableListOf()
    private var reOpenLock = Runnable {}

    val currentLock
        get() = numInitialLinesToOpen - linesToOpen.size
    val numLocks
        get() = numInitialLinesToOpen
    var currentLine: String? = null
        get
        private set

    var isCalibrationMode = false

    fun addLockOpenedListener(listener: () -> Unit) {
        lockOpenedListeners.add(listener)
    }

    fun removeLockOpenedListener(listener: () -> Unit) {
        lockOpenedListeners.remove(listener)
    }

    fun addLockOpenRequestListener(listener: (LockOpenRequest) -> Unit) {
        lockOpenRequestListeners.add(listener)
    }

    fun removeLockOpenRequestListener(listener: (LockOpenRequest) -> Unit) {
        lockOpenRequestListeners.remove(listener)
    }

    fun addAssignShelfRequestListener(listener: (AssignShelfRequest) -> Unit) {
        assignShelfRequestListeners.add(listener)
    }

    fun removeAssignShelfRequestListener(listener: (AssignShelfRequest) -> Unit) {
        assignShelfRequestListeners.remove(listener)
    }

    private fun mapLockNumber(lineNumber: Int): Pair<Int, Int> {
        val shelf = lineNumber.div(100)
        val aux = lineNumber.rem(100)
        return if (aux.rem(2) == 0) {
            Pair(shelf, aux.div(2))
        } else {
            Pair(shelf, 7 + aux.div(2))
        }
    }

    fun openSpecificLock(shelf: Int, compartment: Int, reset: Boolean = false, reopen: Boolean = false) {
        if (lockOpenRequestListeners.size != 1) {
            throw IllegalStateException("Exactly one request listener must be present")
        }
        for (listener in lockOpenRequestListeners) {
            listener(LockOpenRequest(shelf, compartment, reset))
            if (reopen) {
                reOpenLock = Runnable {
                    listener(LockOpenRequest(shelf, compartment, true))
                    schedule(reOpenLock, 58_000)
                }
                schedule(reOpenLock, 58_000)
            }
        }
    }

    fun calibrationAssignShelf(shelf: Int) {
        if (assignShelfRequestListeners.size != 1) {
            throw IllegalStateException("Exactly one request listener must be present")
        }
        for (listener in assignShelfRequestListeners) {
            listener(AssignShelfRequest(shelf))
        }
    }

    fun calibrationAssignLine(line: String, shelf: Int, compartment: Int) {
        compartmentMappingDao.mapCompartments(CompartmentMapping(line, shelf, compartment))
    }

    fun calibrationAssignLines(lines: List<String>, shelf: Int, compartment: Int) {
        compartmentMappingDao.clearCompartmentMappings(shelf, compartment)
        for (line in lines) {
            if (line == "") {
                continue
            }
            compartmentMappingDao.mapCompartments(CompartmentMapping(line, shelf, compartment))
        }
    }

    fun calibrationGetLineAssignments(shelf: Int, compartment: Int): List<String> =
        compartmentMappingDao
            .getCompartmentMappingsInverse(shelf, compartment)
            .map { it.line }

    fun isLineCalibrated(line: String, callback: (Boolean) -> Unit) {
        thread(start = true) {
            callback(unsafeIsLineCalibrated(line))
        }
    }

    fun unsafeIsLineCalibrated(line: String): Boolean
        = compartmentMappingDao.getCompartmentMapping(line) != null

    private fun linePosition(line: String): Pair<Int, Int> {
        val mapping = compartmentMappingDao.getCompartmentMapping(line)
        val shelf: Int
        val compartment: Int
        if (mapping != null) {
            shelf = mapping.shelf
            compartment = mapping.compartment
        } else {
            val fallback = mapLockNumber(
                line.toInt()
            )
            shelf = fallback.first
            compartment = fallback.second
        }
        return Pair(shelf, compartment)
    }

    private fun unsafeOpenLineLock(line: String, reset: Boolean = false) {
        val (shelf, compartment) = linePosition(line)
        openSpecificLock(shelf, compartment, reset)
    }

    fun openLineLock(line: String, reset: Boolean = false) {
        thread(start = true) {
            unsafeOpenLineLock(line, reset)
        }
    }

    protected abstract fun enableItemsInLine(line: String)
    protected abstract fun disableAllItemsInBasket()

    fun openLock(first: Boolean = true) {
        Log.d(javaClass.name, "linesToOpen: $linesToOpen")
        unSchedule(reOpenLock)
        if (linesToOpen.isEmpty()) {
            locksOpen = false
            unSchedule(lockOpenTimerCallback)
            if (!isShelvingMode) {
                schedule(Runnable {
                    completeProductTransaction {
                        logOut()
                        thread(start = true) {
                            try {
                                syncProductTransactions()
                            } catch (e: Exception) {
                                Log.e(javaClass.name, "${e.javaClass.name}: ${e.message}")
                            }
                        }
                    }
                }, 0)
            }
        } else {
            val line = linesToOpen.removeAt(0)
            currentLine = line
            disableAllItemsInBasket()
            enableItemsInLine(line)
            unSchedule(lockOpenTimerCallback)
            schedule(lockOpenTimerCallback, LOCK_TIMEOUT_MS)
            openLineLock(line, reset = first)
            schedule(Runnable {
                for (listener in lockOpenedListeners) {
                    listener()
                }
            }, 0)
        }
    }

    fun openLines(lines: List<String>) {
        thread(start = true) {
            linesToOpen.clear()
            linesToOpen.addAll(lines.distinct())
            numInitialLinesToOpen = linesToOpen.size
            locksOpen = true
            openLock(first = true)
        }
    }

    fun clearLines() {
        linesToOpen.clear()
        numInitialLinesToOpen = 0
    }

    var locksOpen: Boolean = false
        private set

    companion object {
        private const val LOCK_TIMEOUT_MS = 60L*1000L
    }

}
