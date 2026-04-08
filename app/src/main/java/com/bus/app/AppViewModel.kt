package com.bus.app

import androidx.lifecycle.ViewModel
import com.bus.app.data.ActiveBus
import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppUiState(
    val token: String? = null,
    val userRole: String = "",
    val userLogin: String = "",
    val companyId: Int? = null,
    val companyName: String? = null,
    val userLocation: GeoPoint? = null,
    val activeBuses: List<ActiveBus> = emptyList(),
    val startPoint: GeoPoint? = null,
    val endPoint: GeoPoint? = null,
    val routePoints: List<GeoPoint> = emptyList(),
    val travelTimeInfo: String = "Расчет..."
)

class AppViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    fun setAuthenticatedUser(
        token: String,
        role: String,
        login: String,
        companyId: Int?,
        companyName: String?
    ) {
        _uiState.update {
            it.copy(
                token = token,
                userRole = role,
                userLogin = login,
                companyId = companyId,
                companyName = companyName
            )
        }
    }

    fun logout() {
        _uiState.update { AppUiState() }
    }

    fun setUserLocation(location: GeoPoint) {
        _uiState.update { it.copy(userLocation = location) }
    }

    fun setActiveBuses(buses: List<ActiveBus>) {
        _uiState.update { it.copy(activeBuses = buses) }
    }

    fun setStartPoint(point: GeoPoint?) {
        _uiState.update { it.copy(startPoint = point) }
    }

    fun setEndPoint(point: GeoPoint?) {
        _uiState.update { it.copy(endPoint = point) }
    }

    fun setRoutePoints(points: List<GeoPoint>) {
        _uiState.update { it.copy(routePoints = points) }
    }
}
