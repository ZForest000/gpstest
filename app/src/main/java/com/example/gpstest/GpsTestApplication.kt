package com.example.gpstest

import android.app.Application
import android.util.Log
import timber.log.Timber

class GpsTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }
    }

    private class ReleaseTree : Timber.Tree() {
        override fun log(
            priority: Int,
            tag: String?,
            message: String,
            t: Throwable?,
        ) {
            if (priority == Log.VERBOSE || priority == Log.DEBUG) {
                return
            }
            if (t != null) {
                Log.println(priority, tag, message + '\n' + Log.getStackTraceString(t))
            } else {
                Log.println(priority, tag, message)
            }
        }
    }
}
