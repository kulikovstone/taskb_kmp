package com.mkulikov.taskb_kmp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform