package com.mkulikov.taskb

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform