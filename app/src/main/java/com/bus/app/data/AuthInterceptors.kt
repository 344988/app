package com.bus.app.data

import com.bus.app.data.session.SessionRuntime
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = SessionRuntime.token
        val request = chain.request()
        if (token.isNullOrBlank() || request.header("Authorization") != null) {
            return chain.proceed(request)
        }
        val withAuth = request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(withAuth)
    }
}

class UnauthorizedInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 401) {
            SessionRuntime.onUnauthorized?.invoke()
        }
        return response
    }
}
