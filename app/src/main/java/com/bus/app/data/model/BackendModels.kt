package com.bus.app.data.model

import com.google.gson.annotations.SerializedName

data class UserDto(
    val id: Int? = null,
    val login: String,
    val role: String,
    @SerializedName("company_id") val companyId: Int? = null,
    @SerializedName("company_name") val companyName: String? = null,
    @SerializedName("vehicle_id") val vehicleId: Int? = null,
    @SerializedName("vehicle_model") val vehicleModel: String? = null,
    @SerializedName("license_plate") val licensePlate: String? = null,
    @SerializedName("is_active") val isActive: Boolean? = null
)

data class VehicleDto(
    val id: Int,
    @SerializedName("company_id") val companyId: Int? = null,
    val model: String? = null,
    @SerializedName("license_plate") val licensePlate: String,
    val capacity: Int? = null,
    val status: String? = null,
    @SerializedName("wialon_unit_id") val wialonUnitId: String? = null
)

data class StopPointDto(
    val id: Int? = null,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    @SerializedName("order_index") val orderIndex: Int? = null,
    @SerializedName("route_template_id") val routeTemplateId: Int? = null
)

data class RouteTemplateDto(
    val id: Int,
    val name: String,
    @SerializedName("company_id") val companyId: Int? = null,
    val description: String? = null,
    @SerializedName("stop_points") val stopPoints: List<StopPointDto> = emptyList(),
    @SerializedName("is_active") val isActive: Boolean? = null
)

data class TripDto(
    val id: Int,
    @SerializedName("route_template_id") val routeTemplateId: Int? = null,
    @SerializedName("driver_id") val driverId: Int? = null,
    @SerializedName("vehicle_id") val vehicleId: Int? = null,
    @SerializedName("company_id") val companyId: Int? = null,
    val status: String,
    @SerializedName("start_time") val startTime: String? = null,
    @SerializedName("end_time") val endTime: String? = null,
    @SerializedName("started_at") val startedAt: String? = null,
    @SerializedName("finished_at") val finishedAt: String? = null
)

data class DriverShiftDto(
    val id: Int,
    @SerializedName("driver_id") val driverId: Int,
    @SerializedName("vehicle_id") val vehicleId: Int? = null,
    val status: String,
    @SerializedName("started_at") val startedAt: String? = null,
    @SerializedName("ended_at") val endedAt: String? = null
)

data class InspectionDto(
    val id: Int,
    @SerializedName("vehicle_id") val vehicleId: Int,
    @SerializedName("driver_id") val driverId: Int? = null,
    @SerializedName("shift_id") val shiftId: Int? = null,
    val type: String? = null,
    val status: String,
    val notes: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class DefectReportDto(
    val id: Int,
    @SerializedName("vehicle_id") val vehicleId: Int,
    @SerializedName("driver_id") val driverId: Int? = null,
    val description: String,
    val severity: String? = null,
    val status: String,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("resolved_at") val resolvedAt: String? = null
)

data class TrackingEventDto(
    val id: Int? = null,
    @SerializedName("vehicle_id") val vehicleId: Int? = null,
    @SerializedName("driver_id") val driverId: Int? = null,
    val latitude: Double,
    val longitude: Double,
    val speed: Double? = null,
    val heading: Double? = null,
    @SerializedName("recorded_at") val recordedAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class LiveMapVehicleDto(
    val id: Int? = null,
    @SerializedName("vehicle_id") val vehicleId: Int? = null,
    @SerializedName("driver_id") val driverId: Int? = null,
    @SerializedName("driver_login") val driverLogin: String? = null,
    @SerializedName("vehicle_model") val vehicleModel: String? = null,
    @SerializedName("license_plate") val licensePlate: String? = null,
    val latitude: Double,
    val longitude: Double,
    val speed: Double? = null,
    @SerializedName("company_id") val companyId: Int? = null,
    val status: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)


data class DispatcherRequestDto(
    val id: Int,
    @SerializedName("user_id") val userId: Int? = null,
    @SerializedName("passenger_id") val passengerId: Int? = null,
    val status: String,
    @SerializedName("start_name") val startName: String? = null,
    @SerializedName("end_name") val endName: String? = null,
    val comment: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class DispatcherNotificationDto(
    val id: Int,
    val title: String? = null,
    val message: String,
    val kind: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("is_read") val isRead: Boolean? = null
)

data class User(
    val id: Int?,
    val login: String,
    val role: String,
    val companyId: Int?,
    val companyName: String?,
    val vehicleId: Int?,
    val vehicleModel: String?,
    val licensePlate: String?,
    val isActive: Boolean?
)

data class Vehicle(
    val id: Int,
    val companyId: Int?,
    val model: String?,
    val licensePlate: String,
    val capacity: Int?,
    val status: String?,
    val wialonUnitId: String?
)

data class StopPoint(
    val id: Int?,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val orderIndex: Int?,
    val routeTemplateId: Int?
)

data class RouteTemplate(
    val id: Int,
    val name: String,
    val companyId: Int?,
    val description: String?,
    val stopPoints: List<StopPoint>,
    val isActive: Boolean?
)

data class Trip(
    val id: Int,
    val routeTemplateId: Int?,
    val driverId: Int?,
    val vehicleId: Int?,
    val companyId: Int?,
    val status: String,
    val startTime: String?,
    val endTime: String?,
    val startedAt: String?,
    val finishedAt: String?
)

data class DriverShift(
    val id: Int,
    val driverId: Int,
    val vehicleId: Int?,
    val status: String,
    val startedAt: String?,
    val endedAt: String?
)

data class Inspection(
    val id: Int,
    val vehicleId: Int,
    val driverId: Int?,
    val shiftId: Int?,
    val type: String?,
    val status: String,
    val notes: String?,
    val createdAt: String?
)

data class DefectReport(
    val id: Int,
    val vehicleId: Int,
    val driverId: Int?,
    val description: String,
    val severity: String?,
    val status: String,
    val createdAt: String?,
    val resolvedAt: String?
)

data class TrackingEvent(
    val id: Int?,
    val vehicleId: Int?,
    val driverId: Int?,
    val latitude: Double,
    val longitude: Double,
    val speed: Double?,
    val heading: Double?,
    val recordedAt: String?,
    val createdAt: String?
)


data class DispatcherRequest(
    val id: Int,
    val userId: Int?,
    val passengerId: Int?,
    val status: String,
    val startName: String?,
    val endName: String?,
    val comment: String?,
    val createdAt: String?
)

data class DispatcherNotification(
    val id: Int,
    val title: String?,
    val message: String,
    val kind: String?,
    val createdAt: String?,
    val isRead: Boolean?
)

data class LiveMapVehicle(
    val id: Int?,
    val vehicleId: Int?,
    val driverId: Int?,
    val driverLogin: String?,
    val vehicleModel: String?,
    val licensePlate: String?,
    val latitude: Double,
    val longitude: Double,
    val speed: Double?,
    val companyId: Int?,
    val status: String?,
    val updatedAt: String?
)

data class LiveMapVehicleUi(
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val status: String?,
    val speed: Double?
)
