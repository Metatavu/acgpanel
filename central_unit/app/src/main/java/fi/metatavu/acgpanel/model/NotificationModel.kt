package fi.metatavu.acgpanel.model

abstract class NotificationModel {

    private data class Notification(val name: String, val text: String)
    private val notifications = mutableListOf<Notification>()
    private val notificationListeners = mutableListOf<(String) -> Unit>()

    fun addNotificationListener(listener: (String) -> Unit) {
        notificationListeners.add(listener)
    }

    fun removeNotificationListener(listener: (String) -> Unit) {
        notificationListeners.remove(listener)
    }

    fun showNotification(name: String, content: String) {
        if (notifications.any {it.name == name}) {
            return
        }
        notifications.add(Notification(name, content))
        refreshNotifications()
    }

    fun removeNotification(name: String) {
        notifications.removeIf { it.name == name }
        refreshNotifications()
    }

    fun refreshNotifications() {
        for (listener in notificationListeners) {
            listener(notifications.joinToString("\n") {"⚠️ $it"})
        }
    }

}