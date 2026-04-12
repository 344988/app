package com.bus.app.data.repository

import com.bus.app.data.ActiveBus
import com.bus.app.data.Company
import com.bus.app.data.LocationUpdate
import com.bus.app.data.LoginResponse
import com.bus.app.data.RouteRequest
import com.bus.app.data.RouteResponse
import com.bus.app.data.UserCreateRequest
import com.bus.app.data.UserDto

data class HealthSnapshot(
    val isReachable: Boolean,
    val avgPingMs: Long?,
    val packetLossPercent: Int
)

interface BusRepository {
    suspend fun getHealthSnapshot(): HealthSnapshot
    suspend fun login(username: String, password: String): LoginResponse?
    suspend fun getActiveRoutes(token: String): List<ActiveBus>?
    suspend fun updateLocation(token: String, location: LocationUpdate): Boolean
    suspend fun startRoute(token: String, route: RouteRequest): RouteResponse?
    suspend fun getCompanies(token: String): List<Company>?
    suspend fun createCompany(token: String, name: String): Boolean
    suspend fun getUsers(token: String): List<UserDto>?
    suspend fun createUser(token: String, request: UserCreateRequest): Boolean
}
