package com.trialtracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trialtracker.app.ServiceLocator
import com.trialtracker.app.data.Settings
import com.trialtracker.app.data.model.DealUi
import com.trialtracker.app.data.model.Deal
import com.trialtracker.app.data.model.InstalledApp
import com.trialtracker.app.data.remote.DealFeedSource
import com.trialtracker.app.work.CatalogSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One data source as shown in Settings. */
data class SourceStatus(
    val label: String,
    val detail: String,
    val count: Int,
    val ok: Boolean,
)

data class UiState(
    val deals: List<DealUi> = emptyList(),
    val installed: List<InstalledApp> = emptyList(),
    val settings: Settings = Settings(),
    val syncing: Boolean = false,
    val message: String? = null,
) {
    val totalOffers: Int get() = deals.size
    val greetingName: String
        get() = settings.userName.ifBlank { "друг" }

    fun count(category: Category): Int = deals.forCategory(category).size
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ServiceLocator.deals(application)
    private val settingsRepo = ServiceLocator.settings(application)

    private val syncing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<UiState> = combine(
        repo.dealsUi,
        repo.installedApps,
        settingsRepo.settings,
        syncing,
        message,
    ) { deals, installed, settings, isSyncing, msg ->
        UiState(
            deals = deals,
            installed = installed,
            settings = settings,
            syncing = isSyncing,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    val catalogNotice: String get() = repo.notice

    /**
     * Source health for the Settings screen: how many offers each source
     * contributed on the last refresh, and why it produced none if it failed.
     */
    fun sourceStatuses(): List<SourceStatus> {
        val deals = state.value.deals
        val catalogCount = deals.count { it.deal.source == Deal.SOURCE_CATALOG }
        val catalog = SourceStatus(
            label = "Проверенный каталог",
            detail = "Пробные подписки, проверяются вручную",
            count = catalogCount,
            ok = catalogCount > 0,
        )
        val live = DealFeedSource.DEFAULT_FEEDS.map { feed ->
            val result = repo.feedResults[feed.sourceKey]
            val stored = deals.count { it.deal.source == feed.sourceKey }
            SourceStatus(
                label = feed.label,
                detail = result?.error?.let { error -> "Ошибка: " + error }
                    ?: "Скидки Google Play, разбор Atom-фида",
                count = stored,
                ok = result?.error == null && stored > 0,
            )
        }
        return listOf(catalog) + live
    }

    init {
        viewModelScope.launch {
            repo.seedFromAssetsIfEmpty()
            refresh()
        }
    }

    fun refresh() {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            val result = repo.refresh()
            syncing.value = false
            message.value = result.fold(
                onSuccess = { null },
                onFailure = { "Не удалось обновить каталог. Показаны сохранённые данные." },
            )
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    fun toggleFavorite(deal: DealUi) {
        viewModelScope.launch { repo.toggleFavorite(deal.deal.id, !deal.favorite) }
    }

    fun setUserName(value: String) {
        viewModelScope.launch { settingsRepo.setUserName(value) }
    }

    fun completeOnboarding(name: String) {
        viewModelScope.launch {
            settingsRepo.setUserName(name)
            settingsRepo.setOnboarded(true)
        }
    }

    fun setShowSystemApps(value: Boolean) {
        viewModelScope.launch {
            settingsRepo.setShowSystemApps(value)
            refresh()
        }
    }

    fun setNotificationsEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsRepo.setNotificationsEnabled(value)
            val hours = state.value.settings.checkIntervalHours
            if (value) {
                CatalogSyncWorker.schedule(getApplication(), hours)
            } else {
                CatalogSyncWorker.cancel(getApplication())
            }
        }
    }

    fun setCheckInterval(hours: Int) {
        viewModelScope.launch {
            settingsRepo.setCheckIntervalHours(hours)
            if (state.value.settings.notificationsEnabled) {
                CatalogSyncWorker.schedule(getApplication(), hours)
            }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppViewModel(application) as T
    }
}
