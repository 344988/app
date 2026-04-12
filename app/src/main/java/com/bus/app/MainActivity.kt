package com.bus.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
                            delay(5000)
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
                        } else { Toast.makeText(context, "Неверный вход", Toast.LENGTH_SHORT).show() }
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
    var mapError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val response = ApiClient.okHttpClient.newCall(
                    Request.Builder()
                        .url("https://tile.openstreetmap.org/0/0/0.png")
                        .header("User-Agent", "BusApp")
                        .head()
                        .build()
                ).execute()
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        mapError = "Не удалось загрузить тайлы карты (код ${response.code})."
                    }
                } else {
                    withContext(Dispatchers.Main) { mapError = null }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    mapError = "Нет доступа к tile.openstreetmap.org. Проверьте интернет/DNS."
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context -> MapView(context).apply { setTileSource(OSM_HTTPS_TILE_SOURCE); setMultiTouchControls(true); controller.setZoom(12.0); controller.setCenter(GeoPoint(43.1155, 131.8855)) } },
            update = { view ->
                view.overlays.clear()
                uiState.activeBuses.forEach { bus ->
                    val m = Marker(view); m.position = GeoPoint(bus.latitude, bus.longitude); m.title = "${bus.vehicleModel} (${bus.licensePlate})"; m.icon = view.context.getDrawable(android.R.drawable.ic_menu_compass); view.overlays.add(m)
                }
                uiState.userLocation?.let { val m = Marker(view); m.position = it; m.title = "Вы"; m.icon = view.context.getDrawable(android.R.drawable.star_on); view.overlays.add(m) }
                if (uiState.routePoints.isNotEmpty()) {
                    val line = Polyline(); line.setPoints(uiState.routePoints); line.outlinePaint.color = android.graphics.Color.GREEN; line.outlinePaint.strokeWidth = 12f; view.overlays.add(line)
                }
                view.invalidate()
            }
        )
        mapError?.let { error ->
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
    var users by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var newUserLogin by remember { mutableStateOf("") }
    var newUserPass by remember { mutableStateOf("") }
    var newUserRole by remember { mutableStateOf("passenger") }
    var vModel by remember { mutableStateOf("") }
    var lPlate by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope(); val scroll = rememberScrollState(); val context = LocalContext.current

    fun refreshData() {
        scope.launch {
            users = appViewModel.getAdminData()
        }
    }
    LaunchedEffect(Unit) { refreshData() }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF050816)).padding(24.dp).verticalScroll(scroll)) {
        Text("Админ-панель", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp)); Text("CRUD компаний недоступен: backend не отдает /admin/companies", color = Color(0xFFFFEA00), fontSize = 12.sp)
        Spacer(modifier = Modifier.height(24.dp)); Text("Пользователь", color = Color(0xFF00F5FF))
        NeonTextField(newUserLogin, { newUserLogin = it }, "Логин")
        NeonTextField(newUserPass, { newUserPass = it }, "Пароль")
        Row {
            RadioButton(newUserRole == "passenger", { newUserRole = "passenger" }); Text("Пасс.", color = Color.White)
            RadioButton(newUserRole == "driver", { newUserRole = "driver" }); Text("Вод.", color = Color.White)
        }
        if (newUserRole == "driver") { NeonTextField(vModel, { vModel = it }, "Марка"); NeonTextField(lPlate, { lPlate = it }, "Номер") }
        Button(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), onClick = { scope.launch {
            val req = UserCreateRequest(newUserLogin, newUserPass, newUserRole, null, vModel, lPlate)
            if (appViewModel.createUser(req)) { Toast.makeText(context, "Пользователь создан", Toast.LENGTH_SHORT).show(); newUserLogin = ""; newUserPass = ""; refreshData() }
            else { Toast.makeText(context, "Ошибка", Toast.LENGTH_SHORT).show() }
        } }) { Text("Создать пользователя") }

        Spacer(modifier = Modifier.height(32.dp)); Text("Список пользователей", color = Color(0xFF00F5FF))
        users.forEach { user -> Text("${user.role}: ${user.login} (${user.companyName})", color = Color.Gray, fontSize = 14.sp) }
        Spacer(modifier = Modifier.height(24.dp)); Button(modifier = Modifier.fillMaxWidth(), onClick = { navController.popBackStack() }) { Text("Назад") }
    }
}

@Composable
fun DriverSetupScreen(navController: NavController, appViewModel: AppViewModel) {
    val uiState by appViewModel.uiState.collectAsState()
    var sName by remember { mutableStateOf("") }; var eName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF050816)).padding(24.dp)) {
        Text("Создание рейса", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp)); SearchableStopField("Откуда", sName, { sName = it }, { appViewModel.setStartPoint(it.location) })
        Spacer(modifier = Modifier.height(16.dp)); SearchableStopField("Куда", eName, { eName = it }, { appViewModel.setEndPoint(it.location) })
        Spacer(modifier = Modifier.weight(1f))
        if (uiState.startPoint != null && uiState.endPoint != null) {
            NeonRunningButton("Запустить рейс", "Старт") { scope.launch {
                val req = RouteRequest(sName, uiState.startPoint!!.latitude, uiState.startPoint!!.longitude, eName, uiState.endPoint!!.latitude, uiState.endPoint!!.longitude, "Теперь")
                if (appViewModel.startRoute(req)) navController.navigate("main_map")
            } }
        }
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
