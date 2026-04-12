package com.bus.app.data.repository

import com.bus.app.data.ActiveBus
import com.bus.app.data.ApiClient
import com.bus.app.data.Company
import com.bus.app.data.LocationUpdate
import com.bus.app.data.LoginResponse
import com.bus.app.data.RouteRequest
import com.bus.app.data.RouteResponse
import com.bus.app.data.UserCreateRequest
import com.bus.app.data.UserDto
import com.bus.app.config.AppConfig
import kotlinx.coroutines.delay
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import kotlin.system.measureTimeMillis

class ApiBusRepository : BusRepository {
    private interface CompanyAdminApi {
        @GET("/admin/companies")
        suspend fun getCompanies(@Header("Authorization") token: String): Response<List<Company>>

        @POST("/admin/companies")
        suspend fun createCompany(
            @Header("Authorization") token: String,
            @Body name: Map<String, String>
        ): Response<Unit>
    }

    private val companyAdminApi: CompanyAdminApi by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(ApiClient.okHttpClient)
            .build()
            .create(CompanyAdminApi::class.java)
    }

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

    override suspend fun getCompanies(token: String): List<Company>? {
        val response: Response<List<Company>> = retryWithBackoff { companyAdminApi.getCompanies(token) }
        return if (response.isSuccessful) response.body() else null
    }

    override suspend fun createCompany(token: String, name: String): Boolean {
        val response: Response<Unit> =
            retryWithBackoff { companyAdminApi.createCompany(token, mapOf("name" to name)) }
        return response.isSuccessful
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
