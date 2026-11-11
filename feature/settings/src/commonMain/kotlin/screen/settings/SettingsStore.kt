package com.mkulikov.taskb.feature.settings.screen.settings

import com.mkulikov.taskb.core.mvi.CoroutineStore
import com.mkulikov.taskb.feature.settings.api.IAppSettings
import com.mkulikov.taskb.feature.settings.api.model.AppLocation
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
    private val settings: IAppSettings,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CoroutineStore<SettingsIntent, SettingsState, SettingsMessage>(
    initialState = SettingsState(),
    reducer = ::reduceSettings,
    context = dispatcher,
) {
    private var search: Job? = null
    private var observation: Job? = null

    init {
        accept(intent = SettingsIntent.LoadWelcomeScreen)
        accept(intent = SettingsIntent.RetryLoad)
    }

    override fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.LoadWelcomeScreen -> if (state.value.showWelcomeScreen == null) {
                updateWelcomeScreen(error = "Couldn't load the welcome screen setting.") {
                    settings.shouldShowWelcomeScreen()
                }
            }
            is SettingsIntent.SetShowWelcomeScreen -> if (state.value.showWelcomeScreen != null) {
                updateWelcomeScreen(error = "Couldn't save the welcome screen setting. Please try again.") {
                    settings.setShowWelcomeScreen(enabled = intent.enabled)
                    intent.enabled
                }
            }
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
            SettingsIntent.ClearLocation -> save(location = null)
        }
    }

    private fun updateWelcomeScreen(error: String, update: suspend () -> Boolean) {
        if (state.value.isUpdatingWelcomeScreen) return
        reduce(message = SettingsMessage.UpdatingWelcomeScreen)
        launchEffect {
            try {
                SettingsMessage.WelcomeScreenUpdated(enabled = update())
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                SettingsMessage.WelcomeScreenUpdateFailed(error = error)
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
