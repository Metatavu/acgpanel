package fi.metatavu.acgpanel

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import fi.metatavu.acgpanel.model.getLockModel
import fi.metatavu.acgpanel.model.getServerSyncModel
import kotlinx.android.synthetic.main.activity_hardware_test.*
import kotlin.concurrent.thread

class HardwareTestActivity : Activity() {

    private val lockModel = getLockModel()
    private val serverSyncModel = getServerSyncModel()
    private var lockerOpenerThread: Thread? = null
    private var shelfNumber: Int? = null
    private val lineListeners = mutableMapOf<Int, TextWatcher>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hardware_test)
        ArrayAdapter.createFromResource(
            this,
            R.array.shelves_array,
            R.layout.spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            shelf_input.adapter = adapter
        }
        shelf_input.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                shelfNumber = null
            }

            override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    pos: Int,
                    id: Long) {
                shelfNumber = (parent.getItemAtPosition(pos) as String).toIntOrNull()
                assignLineInputs()
            }
        }
        assignLineInputs()
    }

    private fun assignLineInputs() {
        for (i in 1..12) {
            val lineInput = root.findViewWithTag<EditText>("line-$i")
            if (lineListeners.containsKey(i)) {
                lineInput.removeTextChangedListener(lineListeners[i])
            }
        }
        lineListeners.clear()
        for (i in 1..12) {
            val shelf = shelfNumber ?: 1
            val lineInput = root.findViewWithTag<EditText>("line-$i")
            var enabled = false
            thread(start = true) {
                val lines = lockModel.calibrationGetLineAssignments(shelf, i).joinToString(",")
                runOnUiThread {
                    lineInput.text.clear()
                    lineInput.text.insert(0, lines)
                    enabled = true
                }
            }
            val listener = object : TextWatcher {
                override fun onTextChanged(text: CharSequence?, p1: Int, p2: Int, p3: Int) {
                    if (!enabled) {
                        return
                    }
                    thread(start = true) {
                        lockModel.calibrationAssignLines(text.toString().split(","), shelf, i)
                    }
                }

                override fun afterTextChanged(p0: Editable?) {
                }

                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }
            }
            lineListeners[i] = listener
            lineInput.addTextChangedListener(listener)
        }
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
        val shelf = shelfNumber
        if (shelf == null) {
            message.text = getString(R.string.test_invalid_shelf_id)
            return
        }
        lockModel.calibrationAssignShelf(shelf)
    }

    fun openSingleLocker(view: View) {
        val shelf = shelfNumber
        val compartment = (view.tag as String).toInt()
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
                val shelf = shelfNumber
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
            Thread.sleep(6000)
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
