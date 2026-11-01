package com.ivy.wallet

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ivy.base.legacy.appContext
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import timber.log.Timber.DebugTree
import javax.inject.Inject

/**
 * Created by iliyan on 24.02.18.
 */
@HiltAndroidApp
class IvyAndroidApp : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        appContext = this

        // Planted in EVERY build type, not just debug. A release APK that logs
        // nothing cannot be diagnosed from the device: the only evidence left is
        // the sync server's request log, which cannot say which code path gave up
        // or why. The cost is logcat noise; the cost of the alternative is a bug
        // that has to be reproduced on a developer machine that is not the device
        // that failed. Nothing secret is logged - no credential reaches a Timber
        // call - so this is a diagnosability choice, not a privacy one.
        Timber.plant(DebugTree())
    }
}
