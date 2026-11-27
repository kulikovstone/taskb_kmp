package com.mkulikov.taskb.feature.settings.screen.settings

import com.mkulikov.taskb.core.mvi.CoroutineStore
import com.mkulikov.taskb.feature.settings.api.IItemGroupSettings
import com.mkulikov.taskb.feature.settings.api.IThemeSettings
import com.mkulikov.taskb.feature.settings.api.model.AppLocation
import com.mkulikov.taskb.feature.settings.api.model.ThemeMode
import com.mkulikov.taskb.feature.settings.api.repository.IAppLocationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal class SettingsStore(
    private val locations: IAppLocationRepository,
    private val groupSettings: IItemGroupSettings,
    private val themeSettings: IThemeSettings,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CoroutineStore<SettingsIntent, SettingsState, SettingsMessage>(
    initialState = SettingsState(themeMode = themeSettings.themeMode.value),
    reducer = ::reduceSettings,
    context = dispatcher,
) {
    private var search: Job? = null
    private var observation: Job? = null

    init {
        collectEffect(messages = themeSettings.themeMode.map(transform = SettingsMessage::ThemeChanged))
        accept(intent = SettingsIntent.LoadGroupSetting)
        accept(intent = SettingsIntent.RetryLoad)
    }

    override fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.RetryLoad -> if (observation?.isActive != true) {
                reduce(message = SettingsMessage.Loading)
                observation = collectEffect(
                    messages = locations
                        .observeLocation()
                        .map<AppLocation?, SettingsMessage> { location ->
                            SettingsMessage.Location(value = location)
                        }
                        .catch { exception ->
                            if (exception is CancellationException) throw exception
                            emit(value = SettingsMessage.LoadFailed)
                        }
                )
            }
            is SettingsIntent.Search -> if (!state.value.isSaving) {
                search?.cancel()
                reduce(message = SettingsMessage.Query(value = intent.query))
                if (intent.query.trim().length >= 2)
                    search = launchEffect {
                        delay(timeMillis = 350)
                        try {
                            SettingsMessage.SearchResult(
                                query = intent.query,
                                results = locations.searchLocations(query = intent.query),
                            )
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (_: Exception) {
                            SettingsMessage.SearchResult(
                                query = intent.query,
                                results = emptyList(),
                                failed = true,
                            )
                        }
                    }
            }
            is SettingsIntent.SelectLocation ->
                if (intent.location in state.value.results) save(location = intent.location)
            SettingsIntent.LoadGroupSetting -> updateGroupSetting(enabled = null)
            is SettingsIntent.SetRememberLastGroup -> if (state.value.rememberLastGroup != null) {
                updateGroupSetting(enabled = intent.enabled)
            }
            SettingsIntent.ClearLocation -> save(location = null)
            is SettingsIntent.SetThemeMode -> setThemeMode(mode = intent.mode)
        }
    }

    private fun setThemeMode(mode: ThemeMode) {
        if (state.value.isSavingTheme || themeSettings.themeMode.value == mode) return
        reduce(message = SettingsMessage.SavingTheme)
        launchEffect {
            try {
                themeSettings.setThemeMode(mode = mode)
                SettingsMessage.ThemeSaveFinished(failed = false)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                SettingsMessage.ThemeSaveFinished(failed = true)
            }
        }
    }

    private fun updateGroupSetting(enabled: Boolean?) {
        if (state.value.isUpdatingGroupSetting) return
        reduce(message = SettingsMessage.UpdatingGroupSetting)
        launchEffect {
            try {
                if (enabled != null) groupSettings.setRememberLastGroup(enabled = enabled)
                SettingsMessage.GroupSetting(enabled = enabled ?: groupSettings.rememberLastGroupEnabled())
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                SettingsMessage.GroupSettingFailed
            }
        }
    }

    private fun save(location: AppLocation?) {
        if (state.value.isSaving || state.value.isLoading) return
        search?.cancel()
        reduce(message = SettingsMessage.Saving)
        launchEffect {
            try {
                locations.setLocation(location = location)
                SettingsMessage.Saved(failed = false)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                SettingsMessage.Saved(failed = true)
            }
        }
    }
}
