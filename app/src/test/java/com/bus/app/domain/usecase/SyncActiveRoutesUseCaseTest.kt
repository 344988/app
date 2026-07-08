package com.bus.app.domain.usecase

import com.bus.app.data.ActiveBus
import com.bus.app.data.AdminTripCreateRequest
import com.bus.app.data.AdminTripUpdateRequest
import com.bus.app.data.AssignDriverRequest
import com.bus.app.data.AssignVehicleRequest
import com.bus.app.data.AuthResult
import com.bus.app.data.CurrentUserDto
import com.bus.app.data.DriverAcceptVehicleRequest
import com.bus.app.data.DriverInspectionCreateRequest
import com.bus.app.data.MechanicAssignRepairRequest
import com.bus.app.data.MechanicCloseDefectRequest
import com.bus.app.data.Company
import com.bus.app.data.LocationUpdate
import com.bus.app.data.MapConfigDto
import com.bus.app.data.LoginRequest
import com.bus.app.data.RejectDispatcherRequest
import com.bus.app.data.RouteRequest
import com.bus.app.data.RouteResponse
import com.bus.app.data.UserCreateRequest
import com.bus.app.data.WialonAccount
import com.bus.app.data.WialonAccountCreateRequest
import com.bus.app.data.WialonUnit
import com.bus.app.data.model.DefectReport
import com.bus.app.data.model.DispatcherNotification
import com.bus.app.data.model.DispatcherRequest
import com.bus.app.data.model.DriverShift
import com.bus.app.data.model.Inspection
import com.bus.app.data.model.LiveMapVehicle
import com.bus.app.data.model.StopPoint
import com.bus.app.data.model.Trip
import com.bus.app.data.model.TrackingEvent
import com.bus.app.data.repository.BusRepository
import com.bus.app.data.repository.HealthSnapshot
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import okhttp3.RequestBody
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
    override suspend fun login(request: LoginRequest): AuthResult = AuthResult.Failure(com.bus.app.data.AuthErrorType.INVALID_CREDENTIALS, 401)
    override suspend fun getCurrentUser(token: String): CurrentUserDto? = null
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
    override suspend fun getAdminTrips(token: String): List<Trip> = emptyList()
    override suspend fun createAdminTrip(token: String, request: AdminTripCreateRequest): Trip? = null
    override suspend fun updateAdminTrip(token: String, tripId: Int, request: AdminTripUpdateRequest): Trip? = null
    override suspend fun assignTripDriver(token: String, tripId: Int, request: AssignDriverRequest): Trip? = null
    override suspend fun assignTripVehicle(token: String, tripId: Int, request: AssignVehicleRequest): Trip? = null
    override suspend fun startAdminTrip(token: String, tripId: Int): Trip? = null
    override suspend fun completeAdminTrip(token: String, tripId: Int): Trip? = null
    override suspend fun cancelAdminTrip(token: String, tripId: Int): Trip? = null
    override suspend fun getDispatcherRequests(token: String): List<DispatcherRequest> = emptyList()
    override suspend fun approveDispatcherRequest(token: String, requestId: Int): DispatcherRequest? = null
    override suspend fun rejectDispatcherRequest(token: String, requestId: Int, request: RejectDispatcherRequest): DispatcherRequest? = null
    override suspend fun getAdminTrackingEvents(token: String): List<TrackingEvent> = emptyList()
    override suspend fun getDispatcherNotifications(token: String): List<DispatcherNotification> = emptyList()
    override suspend fun getMapConfig(token: String): MapConfigDto? = null
    override suspend fun getLiveMapVehicles(token: String): List<LiveMapVehicle> = emptyList()
    override suspend fun getAdminMapVehicles(token: String): List<LiveMapVehicle> = emptyList()
    override suspend fun getAdminMapVehicle(token: String, vehicleId: Int): LiveMapVehicle? = null
    override suspend fun getMapStops(token: String): List<StopPoint> = emptyList()
    override suspend fun getMapIcon(token: String, iconKind: String): ByteArray? = null
    override suspend fun createDriverDefect(
        token: String,
        vehicleId: RequestBody,
        description: RequestBody,
        severity: RequestBody?,
        photo: MultipartBody.Part?
    ): DefectReport? = null
    override suspend fun getDriverDefects(token: String): List<DefectReport> = emptyList()
    override suspend fun getMechanicDefects(token: String): List<DefectReport> = emptyList()
    override suspend fun acceptMechanicDefect(token: String, defectId: Int): DefectReport? = null
    override suspend fun assignMechanicRepair(token: String, defectId: Int, request: MechanicAssignRepairRequest): DefectReport? = null
    override suspend fun closeMechanicDefect(token: String, defectId: Int, request: MechanicCloseDefectRequest): DefectReport? = null
    override suspend fun getVehicleRepairHistory(token: String, vehicleId: Int): List<DefectReport> = emptyList()
}
