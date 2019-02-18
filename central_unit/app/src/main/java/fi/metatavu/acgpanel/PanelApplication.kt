package fi.metatavu.acgpanel

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import eu.chainfire.libsuperuser.Shell
import kotlin.concurrent.thread

class PanelApplication : Application() {
    init {
        instance = this
    }

    companion object {
        lateinit var instance: PanelApplication
    }
}