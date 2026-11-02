package com.sparnai.bari.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sparnai.bari.BariApp
import java.util.concurrent.TimeUnit

/** Periodic background price check; posts a notification when something got cheaper. */
class PriceCheckWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BariApp
        val outcome = app.repository.refreshAll()
        Notifier.showPriceAlerts(applicationContext, outcome.alerts)
        return if (outcome.ok == 0 && outcome.failed > 0 && runAttemptCount < 2) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "bari-price-check"

        fun schedule(context: Context, intervalHours: Int) {
            val workManager = WorkManager.getInstance(context)
            if (intervalHours <= 0) {
                workManager.cancelUniqueWork(UNIQUE_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<PriceCheckWorker>(intervalHours.toLong(), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
