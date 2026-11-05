package com.mkulikov.taskb.feature.event.api.database

import androidx.room3.Embedded
import androidx.room3.Relation

data class EventRecord(
    @Embedded val event: EventEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["eventId"]) val schedules: List<EventScheduleEntity>,
    @Relation(parentColumns = ["id"], entityColumns = ["eventId"]) val occurrences: List<EventOccurrenceEntity>,
)
