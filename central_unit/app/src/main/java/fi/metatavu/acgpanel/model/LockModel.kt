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

    private val lockOpenTimerCallback = Runnable {
    }
    private val lockOpenedListeners: MutableList<() -> Unit> = mutableListOf()
    private val lockOpenRequestListeners: MutableList<(LockOpenRequest) -> Unit> = mutableListOf()
    private val assignShelfRequestListeners: MutableList<(AssignShelfRequest) -> Unit> = mutableListOf()
    private var numInitialLinesToOpen = 0
    private val linesToOpen: MutableList<String> = mutableListOf()

    val currentLock
        get() = numInitialLinesToOpen - linesToOpen.size
    val numLocks
        get() = numInitialLinesToOpen

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

    fun mapLockNumber(lineNumber: Int): Pair<Int, Int> {
        val shelf = lineNumber.div(100)
        val aux = lineNumber.rem(100)
        if (aux.rem(2) == 0) {
            return Pair(shelf, aux.div(2))
        } else {
            return Pair(shelf, 7 + aux.div(2))
        }
    }

    fun openSpecificLock(shelf: Int, compartment: Int, reset: Boolean = false) {
        if (lockOpenRequestListeners.size != 1) {
            throw IllegalStateException("Exactly one request listener must be present")
        }
        for (listener in lockOpenRequestListeners) {
            listener(LockOpenRequest(shelf, compartment, reset))
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

    private fun linePosition(line: String): Pair<Int, Int> {
        val mapping = compartmentMappingDao.getCompartmentMapping(line)
        var shelf: Int
        var compartment: Int
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

    fun openLock(first: Boolean = true) {
        Log.d(javaClass.name, "linesToOpen: $linesToOpen")
        if (linesToOpen.isEmpty()) {
            locksOpen = false
            unSchedule(lockOpenTimerCallback)
            schedule(Runnable {
                completeProductTransaction {
                    logOut()
                    thread(start = true) {
                        syncProductTransactions()
                    }
                }
            }, 0)
        } else {
            val line = linesToOpen.removeAt(0)
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
        private const val BUFFER_SIZE = 1024
        private const val LOCK_TIMEOUT_MS = 60L*1000L
    }

}
