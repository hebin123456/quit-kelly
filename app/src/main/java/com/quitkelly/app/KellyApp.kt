package com.quitkelly.app

import android.app.Application
import com.quitkelly.app.util.NotificationHelper
import com.quitkelly.app.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KellyApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannel(this)

        appScope.launch {
            container.settings.migrateSourceDefaults()
            val settings = container.settings.snapshot()
            WorkScheduler.schedule(this@KellyApp, settings)
        }
    }
}
