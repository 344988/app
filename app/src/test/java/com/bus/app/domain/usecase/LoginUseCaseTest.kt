package com.bus.app.domain.usecase

import com.bus.app.data.LocationUpdate
import com.bus.app.data.LoginResponse
import com.bus.app.data.RouteRequest
import com.bus.app.data.RouteResponse
import com.bus.app.data.UserCreateRequest
import com.bus.app.data.repository.BusRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoginUseCaseTest {
    @Test
    fun `returns login response from repository`() = runBlocking {
        val expected = LoginResponse(
            accessToken = "token-1",
            role = "driver",
            login = "ivan",
            companyId = 1,
            companyName = "Bus Co"
        )
        val useCase = LoginUseCase(FakeBusRepository(loginResponse = expected))

        val result = useCase("ivan", "pass")

        assertEquals(expected, result)
    }

    @Test
    fun `returns null when repository returns null`() = runBlocking {
        val useCase = LoginUseCase(FakeBusRepository(loginResponse = null))

        val result = useCase("ivan", "wrong")

        assertNull(result)
    }
}

private class FakeBusRepository(
    private val loginResponse: LoginResponse?
) : BusRepository {
    override suspend fun getHealth() = true
    override suspend fun login(username: String, password: String): LoginResponse? = loginResponse
    override suspend fun getActiveRoutes(token: String) = emptyList<com.bus.app.data.ActiveBus>()
    override suspend fun updateLocation(token: String, location: LocationUpdate) = true
    override suspend fun startRoute(token: String, route: RouteRequest): RouteResponse? = null
    override suspend fun getCompanies(token: String) = emptyList<com.bus.app.data.Company>()
    override suspend fun createCompany(token: String, name: String) = true
    override suspend fun getUsers(token: String) = emptyList<com.bus.app.data.UserDto>()
    override suspend fun createUser(token: String, request: UserCreateRequest) = true
}
