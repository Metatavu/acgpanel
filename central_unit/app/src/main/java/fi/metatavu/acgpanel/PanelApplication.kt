package fi.metatavu.acgpanel

import android.app.Application

class PanelApplication : Application() {

    init {
        instance = this
    }

    companion object {
        lateinit var instance: PanelApplication
    }

}