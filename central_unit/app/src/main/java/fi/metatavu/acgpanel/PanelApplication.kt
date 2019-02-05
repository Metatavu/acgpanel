package fi.metatavu.acgpanel

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.github.anrwatchdog.ANRWatchDog
import eu.chainfire.libsuperuser.Shell
import kotlin.concurrent.thread

class PanelApplication : Application() {
    init {
        instance = this
    }

    override fun onCreate() {
        super.onCreate()
        ANRWatchDog().start()
    }

    companion object {
        lateinit var instance: PanelApplication
    }
}