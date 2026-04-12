package com.bus.app.domain.usecase

import com.bus.app.data.ActiveBus
import com.bus.app.data.Company
import com.bus.app.data.LocationUpdate
import com.bus.app.data.LoginResponse
import com.bus.app.data.RouteRequest
import com.bus.app.data.RouteResponse
import com.bus.app.data.UserCreateRequest
import com.bus.app.data.repository.BusRepository
import com.bus.app.data.repository.HealthSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncActiveRoutesUseCaseTest {
    @Test
    fun `driver flow updates location before loading routes`() = runBlocking {
        val repository = FakeSyncRepository(
            routes = listOf(
                ActiveBus(1, "d1", "Bus 1", "A001AA", 43.0, 131.0, 10),
                ActiveBus(2, "d2", "Bus 2", "A002AA", 43.1, 131.1, 11)
            )
        )
        val useCase = SyncActiveRoutesUseCase(repository)

        val result = useCase(
            token = "Bearer token",
            role = "driver",
            companyId = 10,
            userLocation = LocationUpdate(43.11, 131.88)
        )

        assertTrue(repository.updateLocationCalled)
        assertEquals(1, result?.size)
        assertEquals(10, result?.first()?.companyId)
    }

    @Test
    fun `admin gets unfiltered routes`() = runBlocking {
        val routes = listOf(
            ActiveBus(1, "d1", "Bus 1", "A001AA", 43.0, 131.0, 10),
            ActiveBus(2, "d2", "Bus 2", "A002AA", 43.1, 131.1, 11)
        )
        val useCase = SyncActiveRoutesUseCase(FakeSyncRepository(routes))

        val result = useCase(
            token = "Bearer token",
            role = "admin",
            companyId = 10,
            userLocation = null
        )

        assertEquals(routes, result)
    }
}

private class FakeSyncRepository(
    private val routes: List<ActiveBus>
) : BusRepository {
    var updateLocationCalled: Boolean = false

    override suspend fun getHealthSnapshot() = HealthSnapshot(true, 50, 0)
    override suspend fun login(username: String, password: String): LoginResponse? = null
    override suspend fun getActiveRoutes(token: String): List<ActiveBus> = routes
    override suspend fun updateLocation(token: String, location: LocationUpdate): Boolean {
        updateLocationCalled = true
        return true
    }
    override suspend fun startRoute(token: String, route: RouteRequest): RouteResponse? = null
    override suspend fun getCompanies(token: String): List<Company> = emptyList()
    override suspend fun createCompany(token: String, name: String): Boolean = true
    override suspend fun getUsers(token: String) = emptyList<com.bus.app.data.UserDto>()
    override suspend fun createUser(token: String, request: UserCreateRequest) = true
}
