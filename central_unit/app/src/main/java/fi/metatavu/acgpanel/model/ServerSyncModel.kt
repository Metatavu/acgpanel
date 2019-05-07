package fi.metatavu.acgpanel.model

abstract class ServerSyncModel {
    abstract fun serverSync()
    abstract fun testServerConnection(callback: (String?) -> Unit)
}