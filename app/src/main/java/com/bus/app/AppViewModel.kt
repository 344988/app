package com.bus.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bus.app.data.ActiveBus
import com.bus.app.data.Company
import com.bus.app.data.LocationUpdate
import com.bus.app.data.RouteRequest
import com.bus.app.data.UserCreateRequest
import com.bus.app.data.UserDto
import com.bus.app.data.repository.ApiBusRepository
import com.bus.app.data.repository.BusRepository
import com.bus.app.data.session.SessionDataStore
import com.bus.app.data.session.SessionRuntime
import com.bus.app.domain.usecase.LoginUseCase
import com.bus.app.domain.usecase.SyncActiveRoutesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

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
    val travelTimeInfo: String = "Расчет...",
    val errorMessage: String? = null,
    val apiHealthy: Boolean? = null
)

class AppViewModel(
    application: Application,
    private val repository: BusRepository = ApiBusRepository()
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, ApiBusRepository())

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private val loginUseCase = LoginUseCase(repository)
    private val syncActiveRoutesUseCase = SyncActiveRoutesUseCase(repository)
    private val sessionDataStore = SessionDataStore(application.applicationContext)

    init {
        viewModelScope.launch {
            sessionDataStore.sessionFlow.collect { session ->
                SessionRuntime.token = session.token
                if (!session.token.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            token = session.token,
                            userRole = session.role ?: "unknown",
                            userLogin = session.login ?: ""
                        )
                    }
                }
            }
        }
    }

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
        SessionRuntime.token = null
        viewModelScope.launch { sessionDataStore.clearSession() }
        _uiState.update { AppUiState() }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
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
        val response = try {
            loginUseCase(username, password)
        } catch (_: Exception) {
            _uiState.update { it.copy(errorMessage = "Ошибка сети при входе") }
            null
        } ?: return false
        setAuthenticatedUser(
            token = response.accessToken,
            role = response.role,
            login = response.login,
            companyId = response.companyId,
            companyName = response.companyName
        )
        SessionRuntime.token = response.accessToken
        viewModelScope.launch {
            sessionDataStore.saveSession(
                token = response.accessToken,
                role = response.role,
                userId = null,
                login = response.login
            )
        }
        return true
    }

    suspend fun refreshActiveRoutes() {
        try {
            val state = _uiState.value
            val token = state.token ?: return
            _uiState.update { it.copy(apiHealthy = repository.getHealth()) }
            val location = state.userLocation?.let { LocationUpdate(it.latitude, it.longitude) }
            val buses = syncActiveRoutesUseCase(
                token = "Bearer $token",
                role = state.userRole,
                companyId = state.companyId,
                userLocation = location
            ) ?: return
            setActiveBuses(buses)
        } catch (_: Exception) {
            _uiState.update { it.copy(errorMessage = "Не удалось обновить данные маршрутов") }
        }
    }

    suspend fun getAdminData(): Pair<List<Company>, List<UserDto>> {
        return try {
            val token = _uiState.value.token ?: return emptyList<Company>() to emptyList()
            val auth = "Bearer $token"
            val companies = repository.getCompanies(auth) ?: emptyList()
            val users = repository.getUsers(auth) ?: emptyList()
            companies to users
        } catch (_: Exception) {
            _uiState.update { it.copy(errorMessage = "Не удалось загрузить данные админ-панели") }
            emptyList<Company>() to emptyList()
        }
    }

    suspend fun createCompany(name: String): Boolean {
        return try {
            val token = _uiState.value.token ?: return false
            repository.createCompany("Bearer $token", name)
        } catch (_: Exception) {
            _uiState.update { it.copy(errorMessage = "Ошибка сети при создании компании") }
            false
        }
    }

    suspend fun createUser(request: UserCreateRequest): Boolean {
        return try {
            val token = _uiState.value.token ?: return false
            repository.createUser("Bearer $token", request)
        } catch (_: Exception) {
            _uiState.update { it.copy(errorMessage = "Ошибка сети при создании пользователя") }
            false
        }
    }

    suspend fun startRoute(route: RouteRequest): Boolean {
        return try {
            val token = _uiState.value.token ?: return false
            repository.startRoute("Bearer $token", route) != null
        } catch (_: Exception) {
            _uiState.update { it.copy(errorMessage = "Ошибка сети при запуске рейса") }
            false
        }
    }
}
