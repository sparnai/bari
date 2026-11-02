package com.sparnai.bari.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sparnai.bari.BariApp
import com.sparnai.bari.core.AppSettings
import com.sparnai.bari.core.AppState
import com.sparnai.bari.core.DayFare
import com.sparnai.bari.core.PriceScope
import com.sparnai.bari.core.RouteOption
import com.sparnai.bari.core.Segment
import com.sparnai.bari.work.PriceCheckWorker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as BariApp).repository

    val state: StateFlow<AppState> = repository.state
    val refreshing: StateFlow<Boolean> = repository.refreshing

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun refresh() {
        viewModelScope.launch {
            val outcome = repository.refreshAll()
            if (outcome.skipped) return@launch
            val drops = outcome.alerts.filterNot { it.startsWith("💶") }
            val message = when {
                drops.isNotEmpty() -> drops.first() + if (drops.size > 1) "  (+${drops.size - 1})" else ""
                outcome.ok == 0 && outcome.failed > 0 -> "Nepavyko atnaujinti kainų – patikrinkite interneto ryšį"
                outcome.failed > 0 -> "Atnaujinta ${outcome.ok}, nepavyko ${outcome.failed}"
                outcome.ok == 0 -> "Nėra automatiškai tikrinamų skrydžių"
                else -> "Kainos atnaujintos – pokyčių nėra"
            }
            _messages.emit(message)
        }
    }

    /** Refresh on app start when the last check is older than 30 minutes. */
    fun refreshIfStale() {
        val last = state.value.lastRefresh
        if (last == null || System.currentTimeMillis() - last > 30 * 60_000L) refresh()
    }

    suspend fun cheapestAround(segment: Segment): Result<List<DayFare>> =
        runCatching { repository.cheapestAround(segment) }

    fun saveSegment(segment: Segment, previousId: String?) {
        viewModelScope.launch {
            repository.saveSegment(segment, previousId)
            _messages.emit("Išsaugota: ${segment.label}")
        }
    }

    fun deleteSegment(id: String) {
        viewModelScope.launch {
            repository.deleteSegment(id)
            _messages.emit("Skrydis pašalintas")
        }
    }

    fun saveRoute(route: RouteOption) {
        viewModelScope.launch { repository.saveRoute(route) }
    }

    fun deleteRoute(id: String) {
        viewModelScope.launch {
            repository.deleteRoute(id)
            _messages.emit("Maršrutas pašalintas")
        }
    }

    fun setPassengers(count: Int) {
        viewModelScope.launch { repository.setPassengers(count) }
    }

    fun setTripName(name: String) {
        viewModelScope.launch { repository.setTripName(name) }
    }

    fun updateSettings(settings: AppSettings) {
        val old = state.value.settings
        viewModelScope.launch {
            repository.updateSettings(settings)
            if (old.checkIntervalHours != settings.checkIntervalHours) {
                PriceCheckWorker.schedule(getApplication<Application>(), settings.checkIntervalHours)
            }
        }
    }

    fun setPriceScope(scope: PriceScope) {
        updateSettings(state.value.settings.copy(priceScope = scope))
    }

    fun resetTrip() {
        viewModelScope.launch {
            repository.resetTrip()
            _messages.emit("Atkurta numatytoji kelionė")
            refresh()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _messages.emit("Kainų istorija išvalyta")
        }
    }

    fun toast(message: String) {
        _messages.tryEmit(message)
    }
}
