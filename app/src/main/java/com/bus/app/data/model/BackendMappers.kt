package com.bus.app.data.model

fun UserDto.toDomain(): User = User(
    id = id,
    login = login,
    role = role,
    companyId = companyId,
    companyName = companyName,
    vehicleId = vehicleId,
    vehicleModel = vehicleModel,
    licensePlate = licensePlate,
    isActive = isActive
)

fun VehicleDto.toDomain(): Vehicle = Vehicle(
    id = id,
    companyId = companyId,
    model = model,
    licensePlate = licensePlate,
    capacity = capacity,
    status = status,
    wialonUnitId = wialonUnitId
)

fun StopPointDto.toDomain(): StopPoint = StopPoint(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    orderIndex = orderIndex,
    routeTemplateId = routeTemplateId
)

fun RouteTemplateDto.toDomain(): RouteTemplate = RouteTemplate(
    id = id,
    name = name,
    companyId = companyId,
    description = description,
    stopPoints = stopPoints.map { it.toDomain() },
    isActive = isActive
)

fun TripDto.toDomain(): Trip = Trip(
    id = id,
    routeTemplateId = routeTemplateId,
    driverId = driverId,
    vehicleId = vehicleId,
    companyId = companyId,
    status = status,
    startTime = startTime,
    endTime = endTime,
    startedAt = startedAt,
    finishedAt = finishedAt
)

fun DriverShiftDto.toDomain(): DriverShift = DriverShift(
    id = id,
    driverId = driverId,
    vehicleId = vehicleId,
    status = status,
    startedAt = startedAt,
    endedAt = endedAt
)

fun InspectionDto.toDomain(): Inspection = Inspection(
    id = id,
    vehicleId = vehicleId,
    driverId = driverId,
    shiftId = shiftId,
    type = type,
    status = status,
    notes = notes,
    createdAt = createdAt
)

fun DefectReportDto.toDomain(): DefectReport = DefectReport(
    id = id,
    vehicleId = vehicleId,
    driverId = driverId,
    description = description,
    severity = severity,
    status = status,
    createdAt = createdAt,
    resolvedAt = resolvedAt
)

fun TrackingEventDto.toDomain(): TrackingEvent = TrackingEvent(
    id = id,
    vehicleId = vehicleId,
    driverId = driverId,
    latitude = latitude,
    longitude = longitude,
    speed = speed,
    heading = heading,
    recordedAt = recordedAt,
    createdAt = createdAt
)


fun DispatcherRequestDto.toDomain(): DispatcherRequest = DispatcherRequest(
    id = id,
    userId = userId,
    passengerId = passengerId,
    status = status,
    startName = startName,
    endName = endName,
    comment = comment,
    createdAt = createdAt
)

fun DispatcherNotificationDto.toDomain(): DispatcherNotification = DispatcherNotification(
    id = id,
    title = title,
    message = message,
    kind = kind,
    createdAt = createdAt,
    isRead = isRead
)

fun LiveMapVehicleDto.toDomain(): LiveMapVehicle = LiveMapVehicle(
    id = id,
    vehicleId = vehicleId,
    driverId = driverId,
    driverLogin = driverLogin,
    vehicleModel = vehicleModel,
    licensePlate = licensePlate,
    latitude = latitude,
    longitude = longitude,
    speed = speed,
    companyId = companyId,
    status = status,
    updatedAt = updatedAt
)

fun LiveMapVehicle.toUiModel(): LiveMapVehicleUi = LiveMapVehicleUi(
    title = vehicleModel ?: "Автобус",
    subtitle = licensePlate ?: driverLogin ?: "Без номера",
    latitude = latitude,
    longitude = longitude,
    status = status,
    speed = speed
)
