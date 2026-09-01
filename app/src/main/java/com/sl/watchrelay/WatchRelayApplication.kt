package com.sl.watchrelay

import android.app.Application
import com.sl.watchrelay.sync.SyncWorkScheduler

class WatchRelayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncWorkScheduler.schedule(this)
    }
}
