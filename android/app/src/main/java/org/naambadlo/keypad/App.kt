package org.naambadlo.keypad

import android.app.Application

/** Loads the bundled fonts once for the app, keyboard and floating button. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Ui.init(this)
    }
}
