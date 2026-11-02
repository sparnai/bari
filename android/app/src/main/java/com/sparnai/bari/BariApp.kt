package com.sparnai.bari

import android.app.Application
import com.sparnai.bari.data.BariRepository
import com.sparnai.bari.work.Notifier
import com.sparnai.bari.work.PriceCheckWorker

class BariApp : Application() {
    lateinit var repository: BariRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = BariRepository(this)
        Notifier.createChannel(this)
        PriceCheckWorker.schedule(this, repository.state.value.settings.checkIntervalHours)
    }
}
