package com.bus.app

import androidx.lifecycle.ViewModel
import com.bus.app.data.ActiveBus
import com.bus.app.data.Company
import com.bus.app.data.LocationUpdate
import com.bus.app.data.RouteRequest
import com.bus.app.data.UserCreateRequest
import com.bus.app.data.UserDto
import com.bus.app.data.repository.ApiBusRepository
import com.bus.app.data.repository.BusRepository
import com.bus.app.domain.usecase.LoginUseCase
import com.bus.app.domain.usecase.SyncActiveRoutesUseCase
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

class AppViewModel(
    private val repository: BusRepository = ApiBusRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private val loginUseCase = LoginUseCase(repository)
    private val syncActiveRoutesUseCase = SyncActiveRoutesUseCase(repository)

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

    suspend fun login(username: String, password: String): Boolean {
        val response = loginUseCase(username, password) ?: return false
        setAuthenticatedUser(
            token = response.accessToken,
            role = response.role,
            login = response.login,
            companyId = response.companyId,
            companyName = response.companyName
        )
        return true
    }

    suspend fun refreshActiveRoutes() {
        val state = _uiState.value
        val token = state.token ?: return
        val location = state.userLocation?.let { LocationUpdate(it.latitude, it.longitude) }
        val buses = syncActiveRoutesUseCase(
            token = "Bearer $token",
            role = state.userRole,
            companyId = state.companyId,
            userLocation = location
        ) ?: return
        setActiveBuses(buses)
    }

    suspend fun getAdminData(): Pair<List<Company>, List<UserDto>> {
        val token = _uiState.value.token ?: return emptyList<Company>() to emptyList()
        val auth = "Bearer $token"
        val companies = repository.getCompanies(auth) ?: emptyList()
        val users = repository.getUsers(auth) ?: emptyList()
        return companies to users
    }

    suspend fun createCompany(name: String): Boolean {
        val token = _uiState.value.token ?: return false
        return repository.createCompany("Bearer $token", name)
    }

    suspend fun createUser(request: UserCreateRequest): Boolean {
        val token = _uiState.value.token ?: return false
        return repository.createUser("Bearer $token", request)
    }

    suspend fun startRoute(route: RouteRequest): Boolean {
        val token = _uiState.value.token ?: return false
        return repository.startRoute("Bearer $token", route) != null
    }
}
