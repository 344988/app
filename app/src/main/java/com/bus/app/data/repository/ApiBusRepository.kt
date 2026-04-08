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

class ApiBusRepository : BusRepository {
    override suspend fun login(username: String, password: String): LoginResponse? {
        val response = ApiClient.api.login(username, password)
        return if (response.isSuccessful) response.body() else null
    }

    override suspend fun getActiveRoutes(token: String): List<ActiveBus>? {
        val response = ApiClient.api.getActiveRoutes(token)
        return if (response.isSuccessful) response.body() else null
    }

    override suspend fun updateLocation(token: String, location: LocationUpdate): Boolean {
        return ApiClient.api.updateLocation(token, location).isSuccessful
    }

    override suspend fun startRoute(token: String, route: RouteRequest): RouteResponse? {
        val response = ApiClient.api.startRoute(token, route)
        return if (response.isSuccessful) response.body() else null
    }

    override suspend fun getCompanies(token: String): List<Company>? {
        val response = ApiClient.api.getCompanies(token)
        return if (response.isSuccessful) response.body() else null
    }

    override suspend fun createCompany(token: String, name: String): Boolean {
        return ApiClient.api.createCompany(token, mapOf("name" to name)).isSuccessful
    }

    override suspend fun getUsers(token: String): List<UserDto>? {
        val response = ApiClient.api.getUsers(token)
        return if (response.isSuccessful) response.body() else null
    }

    override suspend fun createUser(token: String, request: UserCreateRequest): Boolean {
        return ApiClient.api.createUser(token, request).isSuccessful
    }
}
