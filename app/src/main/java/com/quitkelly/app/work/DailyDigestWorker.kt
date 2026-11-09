package com.quitkelly.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.quitkelly.app.KellyApp
import com.quitkelly.app.util.NotificationHelper
import java.time.LocalDate

class DailyDigestWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? KellyApp ?: return Result.failure()
        val container = app.container

        if (container.secretStore.apiKey().isBlank()) {
            return Result.success()
        }

        return try {
            val settings = container.settings.snapshot()
            val digest = container.digestGenerator.generate(LocalDate.now())
            if (settings.notificationsEnabled) {
                NotificationHelper.notifyDigest(applicationContext, digest)
            }
            Result.success()
        } catch (t: Throwable) {
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }
}
