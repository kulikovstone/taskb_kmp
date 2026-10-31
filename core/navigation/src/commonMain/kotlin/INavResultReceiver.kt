package com.mkulikov.taskb.core.navigation

import kotlinx.coroutines.flow.Flow

/** Retain with rememberNavEntryResource so results live as long as the calling entry. */
interface INavResultReceiver<T : Any> : AutoCloseable {
    val key: NavResultKey<T>
    val results: Flow<T>
}
