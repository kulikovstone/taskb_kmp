package com.mkulikov.taskb.feature.event.api

import androidx.navigation3.runtime.NavKey
import com.mkulikov.taskb.feature.event.api.model.EventOccurrenceId
import kotlinx.datetime.LocalDate

/** Opens an event editor independently of the events list. Defaults apply only to a new event. */
sealed interface EventEditorRoute : NavKey {
    data class Create(
        val initialDate: LocalDate? = null,
        val initialGroupId: Int? = null,
        /** Reuse the last explicitly selected group for the current app session. */
        val rememberGroupSelection: Boolean = false,
    ) : EventEditorRoute

    data class Edit(val eventId: Int, val occurrenceId: EventOccurrenceId? = null) : EventEditorRoute
}
