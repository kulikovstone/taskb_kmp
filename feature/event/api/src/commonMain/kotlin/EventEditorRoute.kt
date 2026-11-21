package com.mkulikov.taskb.feature.event.api

import androidx.navigation3.runtime.NavKey
import com.mkulikov.taskb.feature.event.api.model.EventOccurrenceId

data class EventEditorRoute(val eventId: Int, val occurrenceId: EventOccurrenceId? = null) : NavKey
