package com.bus.app.domain.usecase

import com.bus.app.data.ActiveBus
import com.bus.app.data.LocationUpdate
import com.bus.app.data.repository.BusRepository

class SyncActiveRoutesUseCase(private val repository: BusRepository) {
    suspend operator fun invoke(
        token: String,
        role: String,
        companyId: Int?,
        userLocation: LocationUpdate?
    ): List<ActiveBus>? {
        if (role == "driver" && userLocation != null) {
            repository.updateLocation(token, userLocation)
        }
        val buses = repository.getActiveRoutes(token) ?: return null
        return if (role == "admin") buses else buses.filter { it.companyId == companyId }
    }
}
