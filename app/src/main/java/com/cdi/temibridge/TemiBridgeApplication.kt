package com.cdi.temibridge

import android.app.Application
import android.util.Log

class TemiBridgeApplication : Application() {

    companion object {
        private const val TAG = "TemiBridgeApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "TemiBridge application started")
    }
}
