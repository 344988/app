package com.bus.app.data.repository

import com.bus.app.data.ActiveBus
import com.bus.app.data.ApiClient
import com.bus.app.data.LocationUpdate
import com.bus.app.data.LoginResponse
import com.bus.app.data.RouteRequest
import com.bus.app.data.RouteResponse
import com.bus.app.data.UserCreateRequest
import com.bus.app.data.UserDto
import kotlinx.coroutines.delay
import kotlin.system.measureTimeMillis

class ApiBusRepository : BusRepository {
    override suspend fun getHealthSnapshot(): HealthSnapshot {
        val probes = 3
        var okCount = 0
        var totalPing = 0L
        repeat(probes) {
            try {
                var responseCode = 0
                val ping = measureTimeMillis {
                    responseCode = ApiClient.api.health().code()
                }
                if (responseCode in 200..299) {
                    okCount++
                    totalPing += ping
                }
            } catch (_: Exception) {
                // ignore failed probe
            }
        }

        val lossPercent = ((probes - okCount) * 100) / probes
        val avgPing = if (okCount > 0) totalPing / okCount else null
        return HealthSnapshot(
            isReachable = okCount > 0,
            avgPingMs = avgPing,
            packetLossPercent = lossPercent
        )
    }

    override suspend fun login(username: String, password: String): LoginResponse? {
        val response = retryWithBackoff { ApiClient.api.login(username = username, pass = password) }
        return if (response.isSuccessful) response.body() else null
    }

    override suspend fun getActiveRoutes(token: String): List<ActiveBus>? {
        val response = retryWithBackoff { ApiClient.api.getActiveRoutes(token) }
        return if (response.isSuccessful) response.body() else null
    }

    override suspend fun updateLocation(token: String, location: LocationUpdate): Boolean {
        return retryWithBackoff { ApiClient.api.updateLocation(token, location) }.isSuccessful
    }

    override suspend fun startRoute(token: String, route: RouteRequest): RouteResponse? {
        val response = retryWithBackoff { ApiClient.api.startRoute(token, route) }
        return if (response.isSuccessful) response.body() else null
    }

    override suspend fun getUsers(token: String): List<UserDto>? {
        val response = retryWithBackoff { ApiClient.api.getUsers(token) }
        return if (response.isSuccessful) response.body() else null
    }

    override suspend fun createUser(token: String, request: UserCreateRequest): Boolean {
        return retryWithBackoff { ApiClient.api.createUser(token, request) }.isSuccessful
    }

    private suspend fun <T> retryWithBackoff(
        maxAttempts: Int = 3,
        initialDelayMillis: Long = 500,
        block: suspend () -> T
    ): T {
        var attempt = 0
        var delayMillis = initialDelayMillis
        var lastError: Exception? = null

        while (attempt < maxAttempts) {
            try {
                return block()
            } catch (e: Exception) {
                lastError = e
                attempt++
                if (attempt >= maxAttempts) break
                delay(delayMillis)
                delayMillis *= 2
            }
        }

        throw lastError ?: IllegalStateException("Network request failed")
    }
}
