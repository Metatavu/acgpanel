package fi.metatavu.acgpanel

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import fi.metatavu.acgpanel.model.getLockModel
import fi.metatavu.acgpanel.model.getServerSyncModel
import kotlinx.android.synthetic.main.activity_hardware_test.*
import kotlin.concurrent.thread

class HardwareTestActivity : Activity() {

    private val lockModel = getLockModel()
    private val serverSyncModel = getServerSyncModel()
    private var lockerOpenerThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hardware_test)
        shelf_input.transformationMethod = null
    }

    override fun onResume() {
        super.onResume()
        lockModel.isCalibrationMode = true
    }

    override fun onPause() {
        super.onPause()
        cancelOpenLockers(null)
        lockModel.isCalibrationMode = false
    }

    fun programShelf(@Suppress("UNUSED_PARAMETER") view: View?) {
        val shelf = shelf_input.text.toString().toIntOrNull()
        if (shelf == null) {
            message.text = getString(R.string.test_invalid_shelf_id)
            return
        }
        lockModel.calibrationAssignShelf(shelf)
    }

    fun openSingleLocker(view: View) {
        val compartment = (view.tag as String).toInt()
        val shelf = shelf_input.text.toString().toIntOrNull()
        if (shelf == null) {
            message.text = getString(R.string.test_invalid_shelf_id)
            return
        }
        lockModel.openSpecificLock(shelf, compartment, reset = true)
    }

    fun openAllLockers(@Suppress("UNUSED_PARAMETER") view: View?) {
        if (lockerOpenerThread != null) {
            return
        }
        lockerOpenerThread = thread(start = true) {
            try {
                for (shelf in 1..15) {
                    unsafeOpenLockersInShelf(shelf)
                }
            } catch (e: InterruptedException) {
            } finally {
                lockerOpenerThread = null
            }
        }
    }

    fun openCurrentShelfLockers(@Suppress("UNUSED_PARAMETER") view: View?) {
        if (lockerOpenerThread != null) {
            return
        }
        lockerOpenerThread = thread(start = true) {
            try {
                val shelf = shelf_input.text.toString().toIntOrNull()
                if (shelf == null) {
                    message.text = getString(R.string.test_invalid_shelf_id)
                    return@thread
                }
                unsafeOpenLockersInShelf(shelf)
            } catch (e: InterruptedException) {
            } finally {
                lockerOpenerThread = null
            }
        }
    }

    fun cancelOpenLockers(@Suppress("UNUSED_PARAMETER") view: View?) {
        lockerOpenerThread?.interrupt()
        thread(start=true) {
            lockerOpenerThread?.join()
            runOnUiThread {
                message.text = getString(R.string.test_open_canceled)
            }
        }
    }

    private fun unsafeOpenLockersInShelf(shelf: Int) {
        for (compartment in 1..12) {
            runOnUiThread {
                message.text = getString(
                    R.string.test_open_locker_message,
                    shelf,
                    compartment
                )
            }
            lockModel.openSpecificLock(shelf, compartment, reset = true)
            Thread.sleep(4000)
        }
    }

    fun testServerConnection(@Suppress("UNUSED_PARAMETER") view: View?) {
        serverSyncModel.testServerConnection {
            runOnUiThread {
                if (it == null) {
                    server_connection_result.text = getString(R.string.test_connection_succeeded)
                } else {
                    server_connection_result.text = getString(R.string.test_connection_failed)
                    message.text = it
                }
            }
        }
    }

}
