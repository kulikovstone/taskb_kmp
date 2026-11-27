package com.mkulikov.taskb.feature.item.api.model

enum class ItemType(internal val keyPrefix: String) {
    Task(keyPrefix = ""),
    Event(keyPrefix = "event:"),
}
