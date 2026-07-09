package com.bus.app.data

import com.bus.app.data.session.SessionRuntime
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertTrue
import org.junit.Test

class UnauthorizedInterceptorTest {
    @Test
    fun `calls unauthorized callback on 401`() {
        var called = false
        SessionRuntime.onUnauthorized = { called = true }

        val interceptor = UnauthorizedInterceptor()
        interceptor.intercept(FakeChain(401))

        assertTrue(called)
        SessionRuntime.onUnauthorized = null
    }
}

private class FakeChain(private val code: Int) : Interceptor.Chain {
    private val req = Request.Builder().url("https://example.com/").build()

    override fun request(): Request = req
    override fun proceed(request: Request): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .build()
    }

    override fun call() = throw UnsupportedOperationException()
    override fun connectTimeoutMillis(): Int = 0
    override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    override fun readTimeoutMillis(): Int = 0
    override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    override fun writeTimeoutMillis(): Int = 0
    override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    override fun connection() = null
}
