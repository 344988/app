package com.bus.app.data.session

object SessionRuntime {
    @Volatile
    var token: String? = null

    @Volatile
    var onUnauthorized: (() -> Unit)? = null
}
