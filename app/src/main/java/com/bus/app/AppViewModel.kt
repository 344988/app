package com.bus.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bus.app.data.ActiveBus
import com.bus.app.data.AuthErrorType
import com.bus.app.data.AuthResult
import com.bus.app.data.Company
import com.bus.app.data.CurrentUserDto
import com.bus.app.data.DriverAcceptVehicleRequest
import com.bus.app.data.DriverInspectionCreateRequest
import com.bus.app.data.LocationUpdate
import com.bus.app.data.MechanicAssignRepairRequest
import com.bus.app.data.MechanicCloseDefectRequest
import com.bus.app.data.RouteRequest
import com.bus.app.data.UserCreateRequest
import com.bus.app.data.UserDto
import com.bus.app.data.WialonAccount
import com.bus.app.data.WialonAccountCreateRequest
import com.bus.app.data.WialonUnit
import com.bus.app.data.repository.ApiBusRepository
import com.bus.app.data.repository.BusRepository
import com.bus.app.data.repository.HealthSnapshot
import com.bus.app.data.session.SessionDataStore
import com.bus.app.data.session.SessionRuntime
import com.bus.app.data.model.DefectReport
import com.bus.app.data.model.DriverShift
import com.bus.app.data.model.Inspection
import com.bus.app.data.model.Trip
import com.bus.app.domain.usecase.LoginUseCase
import com.bus.app.domain.usecase.SyncActiveRoutesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.osmdroid.util.GeoPoint

enum class ServerStatusLevel { GREEN, YELLOW, RED }

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
    val apiHealthy: Boolean? = null,
    val serverStatus: ServerStatusLevel = ServerStatusLevel.RED,
    val serverPingMs: Long? = null,
    val packetLossPercent: Int = 100,
    val driverShift: DriverShift? = null,
    val driverTrips: List<Trip> = emptyList(),
    val driverInspections: List<Inspection> = emptyList(),
    val driverLoading: Boolean = false,
    val driverErrorMessage: String? = null,
    val driverOffline: Boolean = false,
    val driverDefects: List<DefectReport> = emptyList(),
    val mechanicDefects: List<DefectReport> = emptyList(),
    val selectedMechanicDefect: DefectReport? = null,
    val vehicleRepairHistory: List<DefectReport> = emptyList(),
    val defectLoading: Boolean = false,
    val defectErrorMessage: String? = null,
    val defectOffline: Boolean = false
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
                val savedToken = session.token
                SessionRuntime.token = savedToken
                if (!savedToken.isNullOrBlank()) {
                    val currentUser = repository.getCurrentUser("Bearer $savedToken")
                    if (currentUser != null) {
                        applyAuthenticatedUser(savedToken, currentUser)
                    } else {
                        logout()
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

    private fun applyAuthenticatedUser(token: String, user: CurrentUserDto) {
        setAuthenticatedUser(
            token = token,
            role = user.role,
            login = user.login,
            companyId = user.companyId,
            companyName = user.companyName
        )
    }

    private fun AuthResult.Failure.toUserMessage(): String = when (type) {
        AuthErrorType.INVALID_CREDENTIALS -> "Неверный логин или пароль"
        AuthErrorType.UNAUTHORIZED -> "Сессия недействительна. Войдите снова"
        AuthErrorType.RATE_LIMITED -> "Слишком много попыток входа. Попробуйте позже"
        AuthErrorType.NETWORK -> "Нет сети или сервер недоступен"
        AuthErrorType.SERVER -> "Ошибка сервера при входе"
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
        return when (val result = loginUseCase(username, password)) {
            is AuthResult.Success -> completeLogin(result)
            is AuthResult.Failure -> {
                _uiState.update { it.copy(errorMessage = result.toUserMessage()) }
                false
            }
        }
    }

    private suspend fun completeLogin(result: AuthResult.Success): Boolean {
        val response = result.response
        val token = response.accessToken
        SessionRuntime.token = token
        val currentUser = repository.getCurrentUser("Bearer $token")
        if (currentUser != null) {
            applyAuthenticatedUser(token, currentUser)
            sessionDataStore.saveSession(
                token = token,
                role = currentUser.role,
                userId = currentUser.id,
                login = currentUser.login
            )
            return true
        }

        val fallbackRole = response.role
        val fallbackLogin = response.login
        if (fallbackRole != null && fallbackLogin != null) {
            setAuthenticatedUser(
                token = token,
                role = fallbackRole,
                login = fallbackLogin,
                companyId = response.companyId,
                companyName = response.companyName
            )
            sessionDataStore.saveSession(
                token = token,
                role = fallbackRole,
                userId = null,
                login = fallbackLogin
            )
            return true
        }

        SessionRuntime.token = null
        _uiState.update { it.copy(errorMessage = "Не удалось получить профиль пользователя") }
        return false
    }

    suspend fun refreshActiveRoutes() {
        try {
            val state = _uiState.value
            val token = state.token ?: return
            val health = repository.getHealthSnapshot()
            _uiState.update {
                it.copy(
                    apiHealthy = health.isReachable,
                    serverStatus = health.toStatusLevel(),
                    serverPingMs = health.avgPingMs,
                    packetLossPercent = health.packetLossPercent
                )
            }
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

    suspend fun getAdminData(): List<UserDto> {
        return try {
            val token = _uiState.value.token ?: return emptyList()
            val auth = "Bearer $token"
            val users = repository.getUsers(auth) ?: emptyList()
            users
        } catch (_: Exception) {
            _uiState.update { it.copy(errorMessage = "Не удалось загрузить данные админ-панели") }
            emptyList()
        }
    }

    suspend fun getCompanies(): List<Company>? {
        return try {
            val token = _uiState.value.token ?: return emptyList<Company>()
            repository.getCompanies("Bearer $token") ?: emptyList<Company>()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun createCompany(name: String): Boolean {
        return try {
            val token = _uiState.value.token ?: return false
            repository.createCompany("Bearer $token", name.trim())
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

    suspend fun getWialonAccounts(): List<WialonAccount> {
        return try {
            val token = _uiState.value.token ?: return emptyList()
            repository.getWialonAccounts("Bearer $token") ?: emptyList()
        } catch (_: Exception) {
            _uiState.update { it.copy(errorMessage = "Не удалось загрузить Wialon-аккаунты") }
            emptyList()
        }
    }

    suspend fun createWialonAccount(name: String, baseUrl: String, token: String): Boolean {
        return try {
            val authToken = _uiState.value.token ?: return false
            repository.createWialonAccount(
                token = "Bearer $authToken",
                request = WialonAccountCreateRequest(name.trim(), baseUrl.trim(), token.trim())
            )
        } catch (_: Exception) {
            _uiState.update { it.copy(errorMessage = "Ошибка при создании Wialon-аккаунта") }
            false
        }
    }

    suspend fun testWialonAccount(accountId: Int): Boolean {
        return try {
            val token = _uiState.value.token ?: return false
            repository.testWialonAccount("Bearer $token", accountId)
        } catch (_: Exception) {
            _uiState.update { it.copy(errorMessage = "Ошибка проверки Wialon-аккаунта") }
            false
        }
    }

    suspend fun syncWialonUnits(accountId: Int): Boolean {
        return try {
            val token = _uiState.value.token ?: return false
            repository.syncWialonUnits("Bearer $token", accountId)
        } catch (_: Exception) {
            _uiState.update { it.copy(errorMessage = "Ошибка синхронизации Wialon units") }
            false
        }
    }

    suspend fun getWialonUnits(): List<WialonUnit> {
        return try {
            val token = _uiState.value.token ?: return emptyList()
            repository.getWialonUnits("Bearer $token") ?: emptyList()
        } catch (_: Exception) {
            _uiState.update { it.copy(errorMessage = "Не удалось загрузить Wialon units") }
            emptyList()
        }
    }


    suspend fun loadDriverDashboard() {
        val token = _uiState.value.token ?: return
        _uiState.update { it.copy(driverLoading = true, driverErrorMessage = null, driverOffline = false) }
        try {
            val auth = "Bearer $token"
            val shift = repository.getCurrentDriverShift(auth)
            val trips = repository.getDriverTrips(auth) ?: emptyList()
            val inspections = repository.getDriverInspections(auth) ?: emptyList()
            _uiState.update {
                it.copy(
                    driverShift = shift,
                    driverTrips = trips,
                    driverInspections = inspections,
                    driverLoading = false,
                    driverErrorMessage = null,
                    driverOffline = false
                )
            }
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    driverLoading = false,
                    driverErrorMessage = "Не удалось загрузить данные водителя",
                    driverOffline = true
                )
            }
        }
    }

    suspend fun startDriverShift(): Boolean {
        val token = _uiState.value.token ?: return false
        return runDriverAction("Не удалось начать смену") {
            val shift = repository.startDriverShift("Bearer $token") ?: return@runDriverAction false
            _uiState.update { it.copy(driverShift = shift) }
            loadDriverDashboard()
            true
        }
    }

    suspend fun acceptDriverVehicle(vehicleId: Int): Boolean {
        val token = _uiState.value.token ?: return false
        return runDriverAction("Не удалось принять автобус") {
            val shift = repository.acceptDriverVehicle(
                "Bearer $token",
                DriverAcceptVehicleRequest(vehicleId = vehicleId)
            ) ?: return@runDriverAction false
            _uiState.update { it.copy(driverShift = shift) }
            loadDriverDashboard()
            true
        }
    }

    suspend fun submitDriverInspection(vehicleId: Int, status: String, notes: String): Boolean {
        val token = _uiState.value.token ?: return false
        return runDriverAction("Не удалось отправить осмотр") {
            repository.createDriverInspection(
                "Bearer $token",
                DriverInspectionCreateRequest(
                    vehicleId = vehicleId,
                    status = status.trim(),
                    notes = notes.trim().ifBlank { null },
                    type = "pre_trip"
                )
            ) ?: return@runDriverAction false
            loadDriverDashboard()
            true
        }
    }

    suspend fun startDriverTrip(tripId: Int): Boolean {
        val token = _uiState.value.token ?: return false
        return runDriverAction("Не удалось начать рейс") {
            repository.startDriverTrip("Bearer $token", tripId) ?: return@runDriverAction false
            loadDriverDashboard()
            true
        }
    }

    suspend fun completeDriverTrip(tripId: Int): Boolean {
        val token = _uiState.value.token ?: return false
        return runDriverAction("Не удалось завершить рейс") {
            repository.completeDriverTrip("Bearer $token", tripId) ?: return@runDriverAction false
            loadDriverDashboard()
            true
        }
    }

    suspend fun finishDriverShift(): Boolean {
        val token = _uiState.value.token ?: return false
        return runDriverAction("Не удалось завершить смену") {
            val shift = repository.finishDriverShift("Bearer $token") ?: return@runDriverAction false
            _uiState.update { it.copy(driverShift = shift) }
            loadDriverDashboard()
            true
        }
    }

    private suspend fun runDriverAction(errorMessage: String, action: suspend () -> Boolean): Boolean {
        _uiState.update { it.copy(driverLoading = true, driverErrorMessage = null, driverOffline = false) }
        return try {
            val result = action()
            _uiState.update {
                it.copy(
                    driverLoading = false,
                    driverErrorMessage = if (result) null else errorMessage,
                    driverOffline = false
                )
            }
            result
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    driverLoading = false,
                    driverErrorMessage = errorMessage,
                    driverOffline = true
                )
            }
            false
        }
    }


    suspend fun loadDriverDefects() {
        val token = _uiState.value.token ?: return
        _uiState.update { it.copy(defectLoading = true, defectErrorMessage = null, defectOffline = false) }
        try {
            val defects = repository.getDriverDefects("Bearer $token") ?: emptyList()
            _uiState.update {
                it.copy(
                    driverDefects = defects,
                    defectLoading = false,
                    defectErrorMessage = null,
                    defectOffline = false
                )
            }
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    defectLoading = false,
                    defectErrorMessage = "Не удалось загрузить дефектные карточки",
                    defectOffline = true
                )
            }
        }
    }

    suspend fun createDriverDefect(
        vehicleId: Int,
        description: String,
        severity: String,
        photoUri: Uri?
    ): Boolean {
        val token = _uiState.value.token ?: return false
        return runDefectAction("Не удалось создать дефектную карточку") {
            val descriptionBody = description.toPlainRequestBody()
            val severityBody = severity.trim().ifBlank { null }?.toPlainRequestBody()
            val vehicleIdBody = vehicleId.toString().toPlainRequestBody()
            val photoPart = photoUri?.toMultipartPhotoPart()
            repository.createDriverDefect(
                token = "Bearer $token",
                vehicleId = vehicleIdBody,
                description = descriptionBody,
                severity = severityBody,
                photo = photoPart
            ) ?: return@runDefectAction false
            loadDriverDefects()
            true
        }
    }

    suspend fun loadMechanicDefects() {
        val token = _uiState.value.token ?: return
        _uiState.update { it.copy(defectLoading = true, defectErrorMessage = null, defectOffline = false) }
        try {
            val defects = repository.getMechanicDefects("Bearer $token") ?: emptyList()
            _uiState.update {
                it.copy(
                    mechanicDefects = defects,
                    selectedMechanicDefect = defects.firstOrNull { defect -> defect.id == it.selectedMechanicDefect?.id } ?: defects.firstOrNull(),
                    defectLoading = false,
                    defectErrorMessage = null,
                    defectOffline = false
                )
            }
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    defectLoading = false,
                    defectErrorMessage = "Не удалось загрузить карточки механика",
                    defectOffline = true
                )
            }
        }
    }

    fun selectMechanicDefect(defect: DefectReport) {
        _uiState.update { it.copy(selectedMechanicDefect = defect) }
    }

    suspend fun acceptMechanicDefect(defectId: Int): Boolean {
        val token = _uiState.value.token ?: return false
        return runDefectAction("Не удалось принять карточку") {
            repository.acceptMechanicDefect("Bearer $token", defectId) ?: return@runDefectAction false
            loadMechanicDefects()
            true
        }
    }

    suspend fun assignMechanicRepair(defectId: Int, notes: String): Boolean {
        val token = _uiState.value.token ?: return false
        return runDefectAction("Не удалось назначить ремонт") {
            repository.assignMechanicRepair(
                "Bearer $token",
                defectId,
                MechanicAssignRepairRequest(notes = notes.trim().ifBlank { null })
            ) ?: return@runDefectAction false
            loadMechanicDefects()
            true
        }
    }

    suspend fun closeMechanicDefect(defectId: Int, resolution: String): Boolean {
        val token = _uiState.value.token ?: return false
        return runDefectAction("Не удалось закрыть ремонт") {
            repository.closeMechanicDefect(
                "Bearer $token",
                defectId,
                MechanicCloseDefectRequest(resolution = resolution.trim().ifBlank { null })
            ) ?: return@runDefectAction false
            loadMechanicDefects()
            true
        }
    }

    suspend fun loadVehicleRepairHistory(vehicleId: Int): Boolean {
        val token = _uiState.value.token ?: return false
        return runDefectAction("Не удалось загрузить историю ремонта") {
            val history = repository.getVehicleRepairHistory("Bearer $token", vehicleId) ?: return@runDefectAction false
            _uiState.update { it.copy(vehicleRepairHistory = history) }
            true
        }
    }

    private suspend fun runDefectAction(errorMessage: String, action: suspend () -> Boolean): Boolean {
        _uiState.update { it.copy(defectLoading = true, defectErrorMessage = null, defectOffline = false) }
        return try {
            val result = action()
            _uiState.update {
                it.copy(
                    defectLoading = false,
                    defectErrorMessage = if (result) null else errorMessage,
                    defectOffline = false
                )
            }
            result
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    defectLoading = false,
                    defectErrorMessage = errorMessage,
                    defectOffline = true
                )
            }
            false
        }
    }

    private fun String.toPlainRequestBody(): RequestBody =
        toRequestBody("text/plain".toMediaTypeOrNull())

    private fun Uri.toMultipartPhotoPart(): MultipartBody.Part? {
        val resolver = getApplication<Application>().contentResolver
        val bytes = resolver.openInputStream(this)?.use { it.readBytes() } ?: return null
        val mediaType = (resolver.getType(this) ?: "image/jpeg").toMediaTypeOrNull()
        val fileName = lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "defect-photo.jpg"
        val body = bytes.toRequestBody(mediaType)
        return MultipartBody.Part.createFormData("photo", fileName, body)
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

    private fun HealthSnapshot.toStatusLevel(): ServerStatusLevel {
        if (!isReachable) return ServerStatusLevel.RED
        if ((avgPingMs != null && avgPingMs > 800) || packetLossPercent >= 20) return ServerStatusLevel.YELLOW
        return ServerStatusLevel.GREEN
    }
}
