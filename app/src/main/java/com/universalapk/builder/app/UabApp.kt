package com.universalapk.builder.app

import android.app.Application
import com.universalapk.builder.workers.Notifier

class UabApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Notifier.ensureChannels(this)
    }
}
