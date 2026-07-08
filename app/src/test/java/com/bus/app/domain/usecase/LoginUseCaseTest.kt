package com.bus.app.domain.usecase

import com.bus.app.data.AuthResult
import com.bus.app.data.CurrentUserDto
import com.bus.app.data.DriverAcceptVehicleRequest
import com.bus.app.data.DriverInspectionCreateRequest
import com.bus.app.data.LocationUpdate
import com.bus.app.data.LoginRequest
import com.bus.app.data.LoginResponse
import com.bus.app.data.RouteRequest
import com.bus.app.data.RouteResponse
import com.bus.app.data.UserCreateRequest
import com.bus.app.data.Company
import com.bus.app.data.repository.BusRepository
import com.bus.app.data.repository.HealthSnapshot
import com.bus.app.data.WialonAccount
import com.bus.app.data.WialonAccountCreateRequest
import com.bus.app.data.WialonUnit
import com.bus.app.data.model.DriverShift
import com.bus.app.data.model.Inspection
import com.bus.app.data.model.Trip
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

        assertEquals(AuthResult.Success(expected), result)
    }

    @Test
    fun `returns failure when repository returns failure`() = runBlocking {
        val failure = AuthResult.Failure(com.bus.app.data.AuthErrorType.INVALID_CREDENTIALS, 401)
        val useCase = LoginUseCase(FakeBusRepository(authResult = failure))

        val result = useCase("ivan", "wrong")

        assertEquals(failure, result)
    }
}

private class FakeBusRepository(
    private val authResult: AuthResult
) : BusRepository {
    constructor(loginResponse: LoginResponse) : this(AuthResult.Success(loginResponse))
    override suspend fun getHealthSnapshot() = HealthSnapshot(true, 50, 0)
    override suspend fun login(request: LoginRequest): AuthResult = authResult
    override suspend fun getCurrentUser(token: String): CurrentUserDto? = CurrentUserDto(login = "ivan", role = "driver", companyId = 1, companyName = "Bus Co")
    override suspend fun getActiveRoutes(token: String) = emptyList<com.bus.app.data.ActiveBus>()
    override suspend fun updateLocation(token: String, location: LocationUpdate) = true
    override suspend fun startRoute(token: String, route: RouteRequest): RouteResponse? = null
    override suspend fun getCompanies(token: String): List<Company> = emptyList()
    override suspend fun createCompany(token: String, name: String): Boolean = true
    override suspend fun getUsers(token: String) = emptyList<com.bus.app.data.UserDto>()
    override suspend fun createUser(token: String, request: UserCreateRequest) = true
    override suspend fun getWialonAccounts(token: String): List<WialonAccount> = emptyList()
    override suspend fun createWialonAccount(token: String, request: WialonAccountCreateRequest): Boolean = true
    override suspend fun testWialonAccount(token: String, accountId: Int): Boolean = true
    override suspend fun syncWialonUnits(token: String, accountId: Int): Boolean = true
    override suspend fun getWialonUnits(token: String): List<WialonUnit> = emptyList()
    override suspend fun getCurrentDriverShift(token: String): DriverShift? = null
    override suspend fun startDriverShift(token: String): DriverShift? = null
    override suspend fun acceptDriverVehicle(token: String, request: DriverAcceptVehicleRequest): DriverShift? = null
    override suspend fun createDriverInspection(token: String, request: DriverInspectionCreateRequest): Inspection? = null
    override suspend fun getDriverInspections(token: String): List<Inspection> = emptyList()
    override suspend fun getDriverTrips(token: String): List<Trip> = emptyList()
    override suspend fun startDriverTrip(token: String, tripId: Int): Trip? = null
    override suspend fun completeDriverTrip(token: String, tripId: Int): Trip? = null
    override suspend fun finishDriverShift(token: String): DriverShift? = null
}
