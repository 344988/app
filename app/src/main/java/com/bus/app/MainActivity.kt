package com.bus.app

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bus.app.config.AppConfig
import com.bus.app.data.*
import com.bus.app.data.session.SessionRuntime
import com.bus.app.ui.theme.СлужебныйАвтобусTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.URLEncoder
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

private val OSM_HTTPS_TILE_SOURCE = XYTileSource(
    "OSM-HTTPS",
    0,
    19,
    256,
    ".png",
    arrayOf("https://tile.openstreetmap.org/")
)


private fun backendTileSource(config: MapConfigDto?): XYTileSource {
    val baseUrl = config?.tileUrl
        ?.takeIf { it.isNotBlank() && !it.contains("{z}") }
        ?: "${AppConfig.BASE_URL.trimEnd('/')}/admin/map/tiles/"
    return XYTileSource(
        "BackendTiles",
        config?.minZoom ?: 0,
        config?.maxZoom ?: 19,
        256,
        ".png",
        arrayOf(baseUrl.trimEnd('/') + "/")
    )
}

private val LOCAL_STOPS_FALLBACK = listOf(
    BusStop("Луговая", GeoPoint(43.1137, 131.9382)),
    BusStop("Центр", GeoPoint(43.1165, 131.8855)),
    BusStop("Площадь Борцов Революции", GeoPoint(43.1159, 131.8852)),
    BusStop("Ж/д вокзал", GeoPoint(43.1114, 131.8735)),
    BusStop("Вторая Речка", GeoPoint(43.1688, 131.9322)),
    BusStop("Некрасовская", GeoPoint(43.1303, 131.9100)),
    BusStop("Балягина", GeoPoint(43.1267, 131.9194)),
    BusStop("Чуркин", GeoPoint(43.0960, 131.9002)),
    BusStop("Маяк", GeoPoint(43.0988, 131.8578)),
    BusStop("Семеновская", GeoPoint(43.1170, 131.8825))
)

data class BusStop(val name: String, val location: GeoPoint)

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            startLocationUpdates()
        } else {
            Toast.makeText(this, "Разрешение на геолокацию не выдано", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionRuntime.onUnauthorized = {
            runOnUiThread {
                appViewModel.logout()
                Toast.makeText(this, "Сессия завершена (401). Войдите снова.", Toast.LENGTH_SHORT).show()
            }
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        Configuration.getInstance().userAgentValue = packageName
        enableEdgeToEdge()
        setContent {
            СлужебныйАвтобусTheme {
                val navController = rememberNavController()
                val uiState by appViewModel.uiState.collectAsState()

                LaunchedEffect(uiState.errorMessage) {
                    if (uiState.errorMessage != null) {
                        Toast.makeText(this@MainActivity, uiState.errorMessage, Toast.LENGTH_SHORT).show()
                        appViewModel.clearError()
                    }
                }

                LaunchedEffect(uiState.token) {
                    if (uiState.token != null) {
                        while(isActive) {
                            appViewModel.refreshActiveRoutes()
                            delay(15000)
                        }
                    }
                }

                val startDestination = if (uiState.token.isNullOrBlank()) "auth" else "main_map"
                NavHost(navController = navController, startDestination = startDestination) {
                    composable("auth") { AuthScreen(navController, appViewModel) }
                    composable("main_map") { MainMapScreen(navController, appViewModel) }
                    composable("driver_setup") { DriverSetupScreen(navController, appViewModel) }
                    composable("passenger_setup") { PassengerSetupScreen(navController, appViewModel) }
                    composable("admin_panel") { AdminPanelScreen(navController, appViewModel) }
                    composable("mechanic_panel") { MechanicPanelScreen(navController, appViewModel) }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ensureLocationPermissionAndStartUpdates()
    }

    override fun onStop() {
        super.onStop()
        stopLocationUpdates()
    }

    override fun onDestroy() {
        SessionRuntime.onUnauthorized = null
        super.onDestroy()
    }

    private fun ensureLocationPermissionAndStartUpdates() {
        if (hasLocationPermission()) {
            startLocationUpdates()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        if (locationCallback != null) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                appViewModel.setUserLocation(GeoPoint(location.latitude, location.longitude))
            }
        }

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback as LocationCallback,
            Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
    }
}

fun decodePolyline(encoded: String): List<GeoPoint> {
    val poly = ArrayList<GeoPoint>()
    var index = 0; val len = encoded.length; var lat = 0; var lng = 0
    while (index < len) {
        var b: Int; var shift = 0; var result = 0
        do { b = encoded[index++].code - 63; result = result or (b and 0x1f shl shift); shift += 5 } while (b >= 0x20)
        lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
        shift = 0; result = 0
        do { b = encoded[index++].code - 63; result = result or (b and 0x1f shl shift); shift += 5 } while (b >= 0x20)
        lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1
        poly.add(GeoPoint(lat.toDouble() / 1E5, lng.toDouble() / 1E5))
    }
    return poly
}

@Composable
fun AuthScreen(navController: NavController, appViewModel: AppViewModel) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050816)).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Служебный Автобус", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(32.dp))
            NeonTextField(login, { login = it }, "Логин")
            Spacer(modifier = Modifier.height(16.dp))
            NeonTextField(password, { password = it }, "Пароль", isPassword = true)
            Spacer(modifier = Modifier.height(32.dp))
            NeonRunningButton("Войти", "Авторизация") {
                scope.launch {
                    try {
                        if (appViewModel.login(login.trim(), password.trim())) {
                            navController.navigate("main_map")
                        }
                    } catch (e: Exception) { Toast.makeText(context, "Сервер недоступен", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }
}

@Composable
fun MainMapScreen(navController: NavController, appViewModel: AppViewModel) {
    val uiState by appViewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    val tileSource = remember(uiState.mapConfig) { backendTileSource(uiState.mapConfig) }

    LaunchedEffect(uiState.token) {
        if (uiState.token != null) {
            while (isActive) {
                appViewModel.refreshServerMap()
                delay(10000)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapView(context).apply {
                    setTileSource(tileSource)
                    setMultiTouchControls(true)
                    controller.setZoom(uiState.mapConfig?.defaultZoom ?: 12.0)
                    controller.setCenter(GeoPoint(uiState.mapConfig?.centerLat ?: 43.1155, uiState.mapConfig?.centerLng ?: 131.8855))
                }
            },
            update = { view ->
                view.setTileSource(tileSource)
                view.overlays.clear()
                val backendBusIcon = uiState.mapBusIconBytes
                    ?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                    ?.let { bitmap -> BitmapDrawable(view.resources, bitmap) }
                val backendStopIcon = uiState.mapStopIconBytes
                    ?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                    ?.let { bitmap -> BitmapDrawable(view.resources, bitmap) }
                uiState.liveMapVehicles.forEach { bus ->
                    val m = Marker(view)
                    m.position = GeoPoint(bus.latitude, bus.longitude)
                    m.title = "${bus.vehicleModel ?: "Автобус"} (${bus.licensePlate ?: "без номера"})"
                    m.snippet = "Статус: ${bus.status ?: "—"}; скорость: ${bus.speed?.let { "${it} км/ч" } ?: "—"}"
                    m.icon = backendBusIcon ?: view.context.getDrawable(android.R.drawable.ic_menu_compass)
                    view.overlays.add(m)
                }
                uiState.mapStops.forEach { stop ->
                    val m = Marker(view)
                    m.position = GeoPoint(stop.latitude, stop.longitude)
                    m.title = stop.name
                    m.snippet = "Остановка"
                    m.icon = backendStopIcon ?: view.context.getDrawable(android.R.drawable.ic_menu_mylocation)
                    view.overlays.add(m)
                }
                uiState.userLocation?.let { val m = Marker(view); m.position = it; m.title = "Вы"; m.icon = view.context.getDrawable(android.R.drawable.star_on); view.overlays.add(m) }
                if (uiState.routePoints.isNotEmpty()) {
                    val line = Polyline(); line.setPoints(uiState.routePoints); line.outlinePaint.color = android.graphics.Color.GREEN; line.outlinePaint.strokeWidth = 12f; view.overlays.add(line)
                }
                view.invalidate()
            }
        )
        uiState.mapErrorMessage?.let { error ->
            Text(
                text = error,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 44.dp)
                    .background(Color(0xCCB00020), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        IconButton(onClick = { showMenu = !showMenu }, modifier = Modifier.padding(top = 40.dp, start = 16.dp).background(Color(0xFF0A1024).copy(0.8f), RoundedCornerShape(8.dp))) {
            Icon(Icons.Default.Menu, null, tint = Color.White)
        }
        val statusColor = when (uiState.serverStatus) {
            ServerStatusLevel.GREEN -> Color(0xFF00E676)
            ServerStatusLevel.YELLOW -> Color(0xFFFFEA00)
            ServerStatusLevel.RED -> Color(0xFFFF5252)
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp)
                .background(Color(0xFF0A1024).copy(0.85f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(statusColor, RoundedCornerShape(99.dp))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Сервер ${uiState.serverPingMs?.let { "${it}мс" } ?: "--"} / loss ${uiState.packetLossPercent}%",
                color = Color.White,
                fontSize = 11.sp
            )
        }
        if (showMenu) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable { showMenu = false }) {
                Column(modifier = Modifier.width(260.dp).fillMaxSize().background(Color(0xFF050816)).padding(24.dp).clickable(enabled=false){}) {
                    Text("Меню (${uiState.userRole})", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = when (uiState.apiHealthy) {
                            true -> "API: online"
                            false -> "API: offline"
                            null -> "API: проверка..."
                        },
                        color = if (uiState.apiHealthy == true) Color(0xFF5BFF7A) else Color(0xFFFFA0A0),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    if (uiState.userRole == "admin") MenuButton("Админ-панель", Icons.Default.Settings) { navController.navigate("admin_panel"); showMenu = false }
                    if (uiState.userRole == "driver" || uiState.userRole == "admin") MenuButton("Настроить рейс", Icons.Default.Add) { navController.navigate("driver_setup"); showMenu = false }
                    if (uiState.userRole == "mechanic" || uiState.userRole == "admin") MenuButton("Механик", Icons.Default.Build) { navController.navigate("mechanic_panel"); showMenu = false }
                    if (uiState.userRole == "passenger" || uiState.userRole == "admin") MenuButton("Выбрать автобус", Icons.Default.Search) { navController.navigate("passenger_setup"); showMenu = false }
                    Spacer(modifier = Modifier.weight(1f))
                    MenuButton("Выйти", Icons.Default.ExitToApp) { appViewModel.logout(); navController.navigate("auth"); showMenu = false }
                }
            }
        }
    }
}

@Composable
fun AdminPanelScreen(navController: NavController, appViewModel: AppViewModel) {
    var companies by remember { mutableStateOf<List<Company>>(emptyList()) }
    var companiesCrudAvailable by remember { mutableStateOf(true) }
    var newCompanyName by remember { mutableStateOf("") }
    var selectedCompanyId by remember { mutableStateOf<Int?>(null) }
    var users by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var wialonAccounts by remember { mutableStateOf<List<WialonAccount>>(emptyList()) }
    var wialonUnits by remember { mutableStateOf<List<WialonUnit>>(emptyList()) }
    var selectedWialonAccountId by remember { mutableStateOf<Int?>(null) }
    var wialonName by remember { mutableStateOf("") }
    var wialonBaseUrl by remember { mutableStateOf("") }
    var wialonToken by remember { mutableStateOf("") }
    var newUserLogin by remember { mutableStateOf("") }
    var newUserPass by remember { mutableStateOf("") }
    var newUserRole by remember { mutableStateOf("passenger") }
    var vModel by remember { mutableStateOf("") }
    var lPlate by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope(); val scroll = rememberScrollState(); val context = LocalContext.current

    fun refreshData() {
        scope.launch {
            users = appViewModel.getAdminData()
            wialonAccounts = appViewModel.getWialonAccounts()
            wialonUnits = appViewModel.getWialonUnits()
            if (selectedWialonAccountId == null) selectedWialonAccountId = wialonAccounts.firstOrNull()?.id
            val loadedCompanies = appViewModel.getCompanies()
            companiesCrudAvailable = loadedCompanies != null
            companies = loadedCompanies ?: emptyList()
            if (selectedCompanyId == null) {
                selectedCompanyId = companies.firstOrNull()?.id
            }
        }
    }
    LaunchedEffect(Unit) { refreshData() }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF050816)).padding(24.dp).verticalScroll(scroll)) {
        Text("Админ-панель", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Компании", color = Color(0xFF00F5FF))
        if (companiesCrudAvailable) {
            NeonTextField(newCompanyName, { newCompanyName = it }, "Название компании")
            Button(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = {
                    scope.launch {
                        val companyName = newCompanyName.trim()
                        if (companyName.isBlank()) {
                            Toast.makeText(context, "Введите название компании", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        if (appViewModel.createCompany(companyName)) {
                            Toast.makeText(context, "Компания создана", Toast.LENGTH_SHORT).show()
                            newCompanyName = ""
                            refreshData()
                        } else {
                            Toast.makeText(context, "Ошибка создания компании", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            ) { Text("Создать компанию") }
            Spacer(modifier = Modifier.height(12.dp))
            companies.forEach { company ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { selectedCompanyId = company.id }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selectedCompanyId == company.id, { selectedCompanyId = company.id })
                    Text(company.name, color = Color.White)
                }
            }
        } else {
            Text(
                "CRUD компаний временно недоступен: endpoint /admin/companies не отвечает.",
                color = Color(0xFFFFEA00),
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.height(24.dp)); Text("Пользователь", color = Color(0xFF00F5FF))
        NeonTextField(newUserLogin, { newUserLogin = it }, "Логин")
        NeonTextField(newUserPass, { newUserPass = it }, "Пароль")
        Row {
            RadioButton(newUserRole == "passenger", { newUserRole = "passenger" }); Text("Пасс.", color = Color.White)
            RadioButton(newUserRole == "driver", { newUserRole = "driver" }); Text("Вод.", color = Color.White)
        }
        if (newUserRole == "driver") { NeonTextField(vModel, { vModel = it }, "Марка"); NeonTextField(lPlate, { lPlate = it }, "Номер") }
        Button(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), onClick = { scope.launch {
            if (companiesCrudAvailable && selectedCompanyId == null) {
                Toast.makeText(context, "Выберите компанию", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val req = UserCreateRequest(newUserLogin, newUserPass, newUserRole, selectedCompanyId, vModel, lPlate)
            if (appViewModel.createUser(req)) { Toast.makeText(context, "Пользователь создан", Toast.LENGTH_SHORT).show(); newUserLogin = ""; newUserPass = ""; refreshData() }
            else { Toast.makeText(context, "Ошибка", Toast.LENGTH_SHORT).show() }
        } }) { Text("Создать пользователя") }

        Spacer(modifier = Modifier.height(32.dp)); Text("Список пользователей", color = Color(0xFF00F5FF))
        users.forEach { user -> Text("${user.role}: ${user.login} (${user.companyName})", color = Color.Gray, fontSize = 14.sp) }

        Spacer(modifier = Modifier.height(32.dp))
        Text("Интеграции Wialon/ГЛОНАСС", color = Color(0xFF00F5FF))
        NeonTextField(wialonName, { wialonName = it }, "Name")
        NeonTextField(wialonBaseUrl, { wialonBaseUrl = it }, "Base URL")
        NeonTextField(wialonToken, { wialonToken = it }, "Token")
        Button(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), onClick = {
            scope.launch {
                if (wialonName.isBlank() || wialonBaseUrl.isBlank() || wialonToken.isBlank()) {
                    Toast.makeText(context, "Заполните name/baseUrl/token", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                if (appViewModel.createWialonAccount(wialonName, wialonBaseUrl, wialonToken)) {
                    Toast.makeText(context, "Wialon-аккаунт сохранен", Toast.LENGTH_SHORT).show()
                    wialonToken = ""
                    refreshData()
                } else {
                    Toast.makeText(context, "Ошибка создания Wialon-аккаунта", Toast.LENGTH_SHORT).show()
                }
            }
        }) { Text("Сохранить интеграцию") }

        wialonAccounts.forEach { account ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { selectedWialonAccountId = account.id }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selectedWialonAccountId == account.id, { selectedWialonAccountId = account.id })
                Text("${account.name} (${account.baseUrl})", color = Color.White, fontSize = 13.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(modifier = Modifier.weight(1f), onClick = {
                scope.launch {
                    val id = selectedWialonAccountId ?: return@launch
                    val ok = appViewModel.testWialonAccount(id)
                    Toast.makeText(context, if (ok) "Проверка успешна" else "Проверка неуспешна", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Проверить") }
            Button(modifier = Modifier.weight(1f), onClick = {
                scope.launch {
                    val id = selectedWialonAccountId ?: return@launch
                    val ok = appViewModel.syncWialonUnits(id)
                    if (ok) {
                        Toast.makeText(context, "Синхронизация выполнена", Toast.LENGTH_SHORT).show()
                        wialonUnits = appViewModel.getWialonUnits()
                    } else {
                        Toast.makeText(context, "Ошибка синхронизации", Toast.LENGTH_SHORT).show()
                    }
                }
            }) { Text("Синхронизировать") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("Найденные автобусы/units", color = Color(0xFF00F5FF), fontSize = 14.sp)
        wialonUnits.forEach { unit ->
            Text(
                "${unit.name} ${unit.licensePlate?.let { "($it)" } ?: ""}",
                color = Color.Gray,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.height(24.dp)); Button(modifier = Modifier.fillMaxWidth(), onClick = { navController.popBackStack() }) { Text("Назад") }
    }
}

@Composable
fun DriverSetupScreen(navController: NavController, appViewModel: AppViewModel) {
    val uiState by appViewModel.uiState.collectAsState()
    var vehicleIdText by remember { mutableStateOf("") }
    var inspectionStatus by remember { mutableStateOf("ok") }
    var inspectionNotes by remember { mutableStateOf("") }
    var defectVehicleIdText by remember { mutableStateOf("") }
    var defectDescription by remember { mutableStateOf("") }
    var defectSeverity by remember { mutableStateOf("medium") }
    var selectedDefectPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var sName by remember { mutableStateOf("") }
    var eName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedDefectPhotoUri = uri
    }

    LaunchedEffect(Unit) {
        appViewModel.loadDriverDashboard()
        appViewModel.loadDriverDefects()
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF050816)).padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("Кабинет водителя", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.driverLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
        }
        uiState.driverErrorMessage?.let { message ->
            Text(
                if (uiState.driverOffline) "$message. Проверьте интернет." else message,
                color = Color(0xFFFFA0A0),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text("Смена", color = Color(0xFF00F5FF), fontWeight = FontWeight.Bold)
        Text(
            text = uiState.driverShift?.let { "#${it.id}: ${it.status} / автобус ${it.vehicleId ?: "не принят"}" } ?: "Активной смены нет",
            color = Color.White,
            fontSize = 14.sp
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(modifier = Modifier.weight(1f), onClick = { scope.launch { appViewModel.startDriverShift() } }) {
                Text("Начать смену")
            }
            Button(modifier = Modifier.weight(1f), onClick = { scope.launch { appViewModel.finishDriverShift() } }) {
                Text("Завершить")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("Прием автобуса", color = Color(0xFF00F5FF), fontWeight = FontWeight.Bold)
        NeonTextField(vehicleIdText, { vehicleIdText = it.filter { ch -> ch.isDigit() } }, "ID автобуса")
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            scope.launch {
                val vehicleId = vehicleIdText.toIntOrNull() ?: return@launch
                appViewModel.acceptDriverVehicle(vehicleId)
            }
        }) { Text("Принять автобус") }

        Spacer(modifier = Modifier.height(20.dp))
        Text("Осмотр", color = Color(0xFF00F5FF), fontWeight = FontWeight.Bold)
        NeonTextField(inspectionStatus, { inspectionStatus = it }, "Статус осмотра (ok/problem)")
        NeonTextField(inspectionNotes, { inspectionNotes = it }, "Комментарий")
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            scope.launch {
                val vehicleId = vehicleIdText.toIntOrNull() ?: uiState.driverShift?.vehicleId ?: return@launch
                appViewModel.submitDriverInspection(vehicleId, inspectionStatus, inspectionNotes)
            }
        }) { Text("Отправить осмотр") }
        if (uiState.driverInspections.isEmpty()) {
            Text("Осмотров пока нет", color = Color.Gray, fontSize = 13.sp)
        } else {
            uiState.driverInspections.take(3).forEach { inspection ->
                Text("#${inspection.id}: ${inspection.status} ${inspection.createdAt ?: ""}", color = Color.Gray, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("Дефектные карточки", color = Color(0xFF00F5FF), fontWeight = FontWeight.Bold)
        if (uiState.defectLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
        }
        uiState.defectErrorMessage?.let { message ->
            Text(
                if (uiState.defectOffline) "$message. Проверьте интернет." else message,
                color = Color(0xFFFFA0A0),
                fontSize = 13.sp
            )
        }
        NeonTextField(defectVehicleIdText, { defectVehicleIdText = it.filter { ch -> ch.isDigit() } }, "ID автобуса для карточки")
        NeonTextField(defectDescription, { defectDescription = it }, "Описание неисправности")
        NeonTextField(defectSeverity, { defectSeverity = it }, "Критичность (low/medium/high)")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(modifier = Modifier.weight(1f), onClick = { photoPicker.launch("image/*") }) {
                Text("Прикрепить фото")
            }
            Button(modifier = Modifier.weight(1f), onClick = {
                scope.launch {
                    val vehicleId = defectVehicleIdText.toIntOrNull() ?: uiState.driverShift?.vehicleId ?: return@launch
                    if (defectDescription.isBlank()) return@launch
                    if (appViewModel.createDriverDefect(vehicleId, defectDescription, defectSeverity, selectedDefectPhotoUri)) {
                        defectVehicleIdText = ""
                        defectDescription = ""
                        defectSeverity = "medium"
                        selectedDefectPhotoUri = null
                    }
                }
            }) { Text("Создать карточку") }
        }
        selectedDefectPhotoUri?.let { Text("Фото выбрано: ${it.lastPathSegment ?: "image"}", color = Color.Gray, fontSize = 12.sp) }
        if (uiState.driverDefects.isEmpty()) {
            Text("Дефектных карточек пока нет", color = Color.Gray, fontSize = 13.sp)
        } else {
            uiState.driverDefects.forEach { defect ->
                Text(
                    "#${defect.id}: автобус ${defect.vehicleId}, ${defect.status}, ${defect.severity ?: "без критичности"} — ${defect.description}",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("Назначенные рейсы", color = Color(0xFF00F5FF), fontWeight = FontWeight.Bold)
        if (uiState.driverTrips.isEmpty()) {
            Text("Назначенных рейсов нет", color = Color.Gray, fontSize = 13.sp)
        } else {
            uiState.driverTrips.forEach { trip ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF10192F))) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Рейс #${trip.id}: ${trip.status}", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Шаблон: ${trip.routeTemplateId ?: "—"} / автобус: ${trip.vehicleId ?: "—"}", color = Color.Gray, fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(modifier = Modifier.weight(1f), onClick = { scope.launch { appViewModel.startDriverTrip(trip.id) } }) {
                                Text("Старт")
                            }
                            Button(modifier = Modifier.weight(1f), onClick = { scope.launch { appViewModel.completeDriverTrip(trip.id) } }) {
                                Text("Финиш")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("Ручной маршрут", color = Color(0xFF00F5FF), fontWeight = FontWeight.Bold)
        SearchableStopField("Откуда", sName, { sName = it }, { appViewModel.setStartPoint(it.location) })
        Spacer(modifier = Modifier.height(12.dp))
        SearchableStopField("Куда", eName, { eName = it }, { appViewModel.setEndPoint(it.location) })
        if (uiState.startPoint != null && uiState.endPoint != null) {
            NeonRunningButton("Запустить ручной рейс", "Старт") {
                scope.launch {
                    val req = RouteRequest(
                        sName,
                        uiState.startPoint!!.latitude,
                        uiState.startPoint!!.longitude,
                        eName,
                        uiState.endPoint!!.latitude,
                        uiState.endPoint!!.longitude,
                        "Теперь"
                    )
                    if (appViewModel.startRoute(req)) navController.navigate("main_map")
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(modifier = Modifier.fillMaxWidth(), onClick = { navController.popBackStack() }) { Text("Назад") }
    }
}

@Composable
fun MechanicPanelScreen(navController: NavController, appViewModel: AppViewModel) {
    val uiState by appViewModel.uiState.collectAsState()
    var repairNotes by remember { mutableStateOf("") }
    var closeResolution by remember { mutableStateOf("") }
    var historyVehicleIdText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        appViewModel.loadMechanicDefects()
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF050816)).padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("Панель механика", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        if (uiState.defectLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
        }
        uiState.defectErrorMessage?.let { message ->
            Text(
                if (uiState.defectOffline) "$message. Проверьте интернет." else message,
                color = Color(0xFFFFA0A0),
                fontSize = 13.sp
            )
        }

        Text("Карточки неисправностей", color = Color(0xFF00F5FF), fontWeight = FontWeight.Bold)
        if (uiState.mechanicDefects.isEmpty()) {
            Text("Открытых карточек нет", color = Color.Gray, fontSize = 13.sp)
        } else {
            uiState.mechanicDefects.forEach { defect ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { appViewModel.selectMechanicDefect(defect) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10192F))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("#${defect.id}: автобус ${defect.vehicleId} — ${defect.status}", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(defect.description, color = Color.Gray, fontSize = 12.sp)
                        Text("Критичность: ${defect.severity ?: "—"}; создано: ${defect.createdAt ?: "—"}", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }

        val selected = uiState.selectedMechanicDefect
        Spacer(modifier = Modifier.height(20.dp))
        Text("Выбранная карточка", color = Color(0xFF00F5FF), fontWeight = FontWeight.Bold)
        if (selected == null) {
            Text("Выберите карточку из списка", color = Color.Gray, fontSize = 13.sp)
        } else {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF10192F))) {
                Column(Modifier.padding(12.dp)) {
                    Text("Карточка #${selected.id}", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Автобус: ${selected.vehicleId}; водитель: ${selected.driverId ?: "—"}", color = Color.Gray, fontSize = 13.sp)
                    Text("Статус: ${selected.status}; критичность: ${selected.severity ?: "—"}", color = Color.Gray, fontSize = 13.sp)
                    Text(selected.description, color = Color.White, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(modifier = Modifier.fillMaxWidth(), onClick = { scope.launch { appViewModel.acceptMechanicDefect(selected.id) } }) {
                        Text("Принять карточку")
                    }
                    NeonTextField(repairNotes, { repairNotes = it }, "Комментарий к ремонту")
                    Button(modifier = Modifier.fillMaxWidth(), onClick = {
                        scope.launch {
                            if (appViewModel.assignMechanicRepair(selected.id, repairNotes)) repairNotes = ""
                        }
                    }) { Text("Назначить ремонт") }
                    NeonTextField(closeResolution, { closeResolution = it }, "Решение / результат ремонта")
                    Button(modifier = Modifier.fillMaxWidth(), onClick = {
                        scope.launch {
                            if (appViewModel.closeMechanicDefect(selected.id, closeResolution)) closeResolution = ""
                        }
                    }) { Text("Закрыть ремонт") }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("История ремонта автобуса", color = Color(0xFF00F5FF), fontWeight = FontWeight.Bold)
        NeonTextField(historyVehicleIdText, { historyVehicleIdText = it.filter { ch -> ch.isDigit() } }, "ID автобуса")
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            scope.launch {
                val vehicleId = historyVehicleIdText.toIntOrNull() ?: selected?.vehicleId ?: return@launch
                appViewModel.loadVehicleRepairHistory(vehicleId)
            }
        }) { Text("Загрузить историю") }
        if (uiState.vehicleRepairHistory.isEmpty()) {
            Text("История ремонта пуста", color = Color.Gray, fontSize = 13.sp)
        } else {
            uiState.vehicleRepairHistory.forEach { defect ->
                Text(
                    "#${defect.id}: ${defect.status}, ${defect.resolvedAt ?: defect.createdAt ?: "—"} — ${defect.description}",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(modifier = Modifier.fillMaxWidth(), onClick = { navController.popBackStack() }) { Text("Назад") }
    }
}

@Composable
fun PassengerSetupScreen(navController: NavController, appViewModel: AppViewModel) {
    val uiState by appViewModel.uiState.collectAsState()
    Column(Modifier.fillMaxSize().background(Color(0xFF050816)).padding(24.dp)) {
        Text("Ваши автобусы", color = Color.White, fontSize = 20.sp); Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.weight(1f)) { items(uiState.activeBuses) { bus -> Card(Modifier.fillMaxWidth().padding(vertical=4.dp).clickable{navController.navigate("main_map")}, colors=CardDefaults.cardColors(containerColor=Color(0xFF10192F))) {
            Column(Modifier.padding(16.dp)) { Text(bus.vehicleModel ?: "Автобус", color=Color.White, fontWeight=FontWeight.Bold); Text(bus.licensePlate ?: bus.driverLogin, color=Color.Gray, fontSize=12.sp) }
        } } }
        Button(modifier = Modifier.fillMaxWidth(), onClick={navController.popBackStack()}) { Text("Назад") }
    }
}

@Composable
fun SearchableStopField(label: String, query: String, onQueryChange: (String) -> Unit, onStopSelected: (BusStop) -> Unit) {
    var results by remember { mutableStateOf<List<BusStop>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val nominatimClient = remember {
        OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
    LaunchedEffect(query) {
        if (query.length < 3) {
            results = emptyList()
            errorMessage = null
            return@LaunchedEffect
        }
        delay(600); withContext(Dispatchers.IO) {
            try {
                val url = "https://nominatim.openstreetmap.org/search?q=${URLEncoder.encode("$query Приморский край", "UTF-8")}&format=json&addressdetails=1&limit=5"
                val res = nominatimClient.newCall(
                    Request.Builder()
                        .url(url)
                        .header("User-Agent", "ServiceBusApp/1.0 (android-client)")
                        .header("Accept", "application/json")
                        .build()
                ).execute()
                if (!res.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        results = emptyList()
                        errorMessage = "Поиск остановок недоступен (HTTP ${res.code})."
                    }
                    return@withContext
                }
                val jsonArray = JSONArray(res.body?.string() ?: "[]")
                val list = mutableListOf<BusStop>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i); val addr = obj.optJSONObject("address")
                    val city = addr?.optString("city") ?: addr?.optString("town") ?: addr?.optString("village") ?: addr?.optString("suburb") ?: ""
                    val name = obj.getString("display_name").split(",")[0]
                    val fullName = if(city.isNotEmpty()) "остановка $name, г. $city" else "остановка $name"
                    list.add(BusStop(fullName, GeoPoint(obj.getDouble("lat"), obj.getDouble("lon"))))
                }
                withContext(Dispatchers.Main) {
                    results = list
                    errorMessage = if (list.isEmpty()) "Остановки не найдены." else null
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    val fallback = LOCAL_STOPS_FALLBACK.filter {
                        it.name.contains(query, ignoreCase = true)
                    }
                    results = fallback
                    errorMessage = if (fallback.isEmpty()) {
                        "Ошибка сети при поиске остановок."
                    } else {
                        "Сеть недоступна. Показаны локальные остановки."
                    }
                }
            }
        }
    }
    Column { NeonTextField(query, onQueryChange, label)
        if (results.isNotEmpty()) { Column(Modifier.fillMaxWidth().background(Color(0xFF10192F)).border(1.dp, Color(0xFF00F5FF).copy(0.4f))) {
            results.forEach { stop -> Text(stop.name, color=Color.White, modifier=Modifier.fillMaxWidth().clickable { onStopSelected(stop); onQueryChange(stop.name); results=emptyList() }.padding(12.dp)) }
        } }
        if (errorMessage != null) {
            Text(errorMessage!!, color = Color(0xFFFFA0A0), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable fun MenuButton(t: String, i: ImageVector, c: () -> Unit) { Row(Modifier.fillMaxWidth().clickable { c() }.padding(12.dp), verticalAlignment=Alignment.CenterVertically) { Icon(i, null, tint=Color(0xFF00F5FF)); Spacer(Modifier.width(12.dp)); Text(t, color=Color.White) } }
@Composable fun NeonTextField(v: String, o: (String) -> Unit, l: String, isPassword: Boolean = false) { OutlinedTextField(v, o, label={Text(l, color=Color(0xFF9FB3FF))}, modifier=Modifier.fillMaxWidth().padding(vertical=4.dp), visualTransformation=if(isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None, colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Color(0xFF00F5FF), unfocusedBorderColor=Color(0xFF1A2140), cursorColor=Color(0xFF00F5FF)), shape=RoundedCornerShape(12.dp)) }
@Composable fun NeonRunningButton(t: String, s: String, onClick: () -> Unit) {
    val trans = rememberInfiniteTransition(label="")
    val off by trans.animateFloat(-400f, 1400f, infiniteRepeatable(tween(2200, easing=FastOutLinearInEasing)), label="")
    val brush = Brush.linearGradient(listOf(Color(0xFF00F5FF), Color(0xFF8A2BE2), Color(0xFFFF00E5), Color(0xFF00F5FF)), start=Offset(off-300f, 0f), end=Offset(off+300f, 300f))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFF10192F)).border(2.dp, brush, RoundedCornerShape(22.dp)).clickable { onClick() }.padding(20.dp)) { Column { Text(t, color=Color.White, fontWeight=FontWeight.Bold); Text(s, color=Color(0xFF9FB3FF), fontSize=12.sp) } }
}
