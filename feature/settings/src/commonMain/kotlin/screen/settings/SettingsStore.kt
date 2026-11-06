package com.mkulikov.taskb.feature.settings.screen.settings

import com.mkulikov.taskb.core.mvi.CoroutineStore
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
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CoroutineStore<SettingsIntent, SettingsState, SettingsMessage>(SettingsState(), ::reduceSettings, dispatcher) {
    private var search: Job? = null
    private var observation: Job? = null

    init { accept(SettingsIntent.RetryLoad) }

    override fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.RetryLoad -> if (observation?.isActive != true) {
                reduce(SettingsMessage.Loading)
                observation = collectEffect(locations.observeLocation().map<AppLocation?, SettingsMessage> {
                    SettingsMessage.Location(it)
                }.catch {
                    if (it is CancellationException) throw it
                    emit(SettingsMessage.LoadFailed)
                })
            }
            is SettingsIntent.Search -> if (!state.value.isSaving) {
                search?.cancel()
                reduce(SettingsMessage.Query(intent.query))
                if (intent.query.trim().length >= 2) search = launchEffect {
                    delay(350)
                    try {
                        SettingsMessage.SearchResult(intent.query, locations.searchLocations(intent.query))
                    } catch (exception: CancellationException) { throw exception }
                    catch (_: Exception) { SettingsMessage.SearchResult(intent.query, emptyList(), failed = true) }
                }
            }
            is SettingsIntent.SelectLocation -> if (intent.location in state.value.results) save(intent.location)
            SettingsIntent.ClearLocation -> save(null)
        }
    }

    private fun save(location: AppLocation?) {
        if (state.value.isSaving || state.value.isLoading) return
        search?.cancel()
        reduce(SettingsMessage.Saving)
        launchEffect {
            try {
                locations.setLocation(location)
                SettingsMessage.Saved(failed = false)
            } catch (exception: CancellationException) { throw exception }
            catch (_: Exception) { SettingsMessage.Saved(failed = true) }
        }
    }
}
