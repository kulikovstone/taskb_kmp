package com.mkulikov.taskb.feature.hello.api

import androidx.navigation3.runtime.NavKey

data class HelloRoute(val version: String, val initialError: String? = null) : NavKey
