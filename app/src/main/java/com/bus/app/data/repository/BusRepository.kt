package com.bus.app.data.repository

import com.bus.app.data.ActiveBus
import com.bus.app.data.Company
import com.bus.app.data.DriverAcceptVehicleRequest
import com.bus.app.data.DriverInspectionCreateRequest
import com.bus.app.data.MechanicAssignRepairRequest
import com.bus.app.data.MechanicCloseDefectRequest
import com.bus.app.data.LocationUpdate
import com.bus.app.data.RejectDispatcherRequest
import com.bus.app.data.MapConfigDto
import com.bus.app.data.AdminTripCreateRequest
import com.bus.app.data.AdminTripUpdateRequest
import com.bus.app.data.AssignDriverRequest
import com.bus.app.data.AssignVehicleRequest
import com.bus.app.data.AuthResult
import com.bus.app.data.CurrentUserDto
import com.bus.app.data.LoginRequest
import com.bus.app.data.RouteRequest
import com.bus.app.data.RouteResponse
import com.bus.app.data.UserCreateRequest
import com.bus.app.data.UserDto
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
import com.bus.app.data.model.RouteTemplate
import com.bus.app.data.model.Trip
import com.bus.app.data.model.TrackingEvent
import okhttp3.MultipartBody
import okhttp3.RequestBody

data class HealthSnapshot(
    val isReachable: Boolean,
    val avgPingMs: Long?,
    val packetLossPercent: Int
)

interface BusRepository {
    suspend fun getHealthSnapshot(): HealthSnapshot
    suspend fun login(request: LoginRequest): AuthResult
    suspend fun getCurrentUser(token: String): CurrentUserDto?
    suspend fun getActiveRoutes(token: String): List<ActiveBus>?
    suspend fun updateLocation(token: String, location: LocationUpdate): Boolean
    suspend fun startRoute(token: String, route: RouteRequest): RouteResponse?
    suspend fun getCompanies(token: String): List<Company>?
    suspend fun createCompany(token: String, name: String): Boolean
    suspend fun getUsers(token: String): List<UserDto>?
    suspend fun createUser(token: String, request: UserCreateRequest): Boolean
    suspend fun getWialonAccounts(token: String): List<WialonAccount>?
    suspend fun createWialonAccount(token: String, request: WialonAccountCreateRequest): Boolean
    suspend fun testWialonAccount(token: String, accountId: Int): Boolean
    suspend fun syncWialonUnits(token: String, accountId: Int): Boolean
    suspend fun getWialonUnits(token: String): List<WialonUnit>?
    suspend fun getCurrentDriverShift(token: String): DriverShift?
    suspend fun startDriverShift(token: String): DriverShift?
    suspend fun acceptDriverVehicle(token: String, request: DriverAcceptVehicleRequest): DriverShift?
    suspend fun createDriverInspection(token: String, request: DriverInspectionCreateRequest): Inspection?
    suspend fun getDriverInspections(token: String): List<Inspection>?
    suspend fun getDriverTrips(token: String): List<Trip>?
    suspend fun startDriverTrip(token: String, tripId: Int): Trip?
    suspend fun completeDriverTrip(token: String, tripId: Int): Trip?
    suspend fun finishDriverShift(token: String): DriverShift?
    suspend fun getAdminTrips(token: String): List<Trip>?
    suspend fun createAdminTrip(token: String, request: AdminTripCreateRequest): Trip?
    suspend fun updateAdminTrip(token: String, tripId: Int, request: AdminTripUpdateRequest): Trip?
    suspend fun assignTripDriver(token: String, tripId: Int, request: AssignDriverRequest): Trip?
    suspend fun assignTripVehicle(token: String, tripId: Int, request: AssignVehicleRequest): Trip?
    suspend fun startAdminTrip(token: String, tripId: Int): Trip?
    suspend fun completeAdminTrip(token: String, tripId: Int): Trip?
    suspend fun cancelAdminTrip(token: String, tripId: Int): Trip?
    suspend fun getDispatcherRequests(token: String): List<DispatcherRequest>?
    suspend fun approveDispatcherRequest(token: String, requestId: Int): DispatcherRequest?
    suspend fun rejectDispatcherRequest(token: String, requestId: Int, request: RejectDispatcherRequest): DispatcherRequest?
    suspend fun getAdminTrackingEvents(token: String): List<TrackingEvent>?
    suspend fun getDispatcherNotifications(token: String): List<DispatcherNotification>?
    suspend fun getAdminStops(token: String): List<StopPoint>?
    suspend fun getRouteTemplates(token: String): List<RouteTemplate>?
    suspend fun getRouteTemplateStops(token: String, routeTemplateId: Int): List<StopPoint>?
    suspend fun getTripRouteTemplate(token: String, tripId: Int): RouteTemplate?
    suspend fun getMapConfig(token: String): MapConfigDto?
    suspend fun getLiveMapVehicles(token: String): List<LiveMapVehicle>?
    suspend fun getAdminMapVehicles(token: String): List<LiveMapVehicle>?
    suspend fun getAdminMapVehicle(token: String, vehicleId: Int): LiveMapVehicle?
    suspend fun getMapStops(token: String): List<StopPoint>?
    suspend fun getMapIcon(token: String, iconKind: String): ByteArray?
    suspend fun createDriverDefect(
        token: String,
        vehicleId: RequestBody,
        description: RequestBody,
        severity: RequestBody?,
        photo: MultipartBody.Part?
    ): DefectReport?
    suspend fun getDriverDefects(token: String): List<DefectReport>?
    suspend fun getMechanicDefects(token: String): List<DefectReport>?
    suspend fun acceptMechanicDefect(token: String, defectId: Int): DefectReport?
    suspend fun assignMechanicRepair(token: String, defectId: Int, request: MechanicAssignRepairRequest): DefectReport?
    suspend fun closeMechanicDefect(token: String, defectId: Int, request: MechanicCloseDefectRequest): DefectReport?
    suspend fun getVehicleRepairHistory(token: String, vehicleId: Int): List<DefectReport>?
}
