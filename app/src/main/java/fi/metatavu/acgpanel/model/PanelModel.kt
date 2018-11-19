package fi.metatavu.acgpanel.model

data class User(
    val userName: String,
    val cardCode: String
)

data class Product(val name: String = "")

data class ProductPage(val products: List<Product> = listOf(
    Product("Tuote 1"),
    Product("Tuote 2"),
    Product("Tuote 3"),
    Product("Tuote 4"),
    Product("Tuote 5"),
    Product("Tuote 6")
))

private const val SESSION_TIMEOUT_MS = 5*60*1000L

abstract class PanelModel {

    abstract fun schedule(callback: Runnable, timeout: Long)
    abstract fun unSchedule(callback: Runnable)

    val logoutTimerCallback: Runnable = Runnable { logOut() }
    var canLogInViaRfid = false
    private val logoutEventListeners: MutableList<() -> Unit> = mutableListOf()
    private val loginEventListeners: MutableList<() -> Unit> = mutableListOf()

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

    var currentUser: User? = null

    fun logIn(cardCode: String, usingRfid: Boolean = false) {
        if (usingRfid && !canLogInViaRfid) {
            return;
        }
        currentUser = User(
            "Matti Meikäläinen",
            cardCode
        )
        for (listener in loginEventListeners) {
            listener()
        }
        refresh()
    }

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
    }

}
