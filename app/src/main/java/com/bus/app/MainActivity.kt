package com.bus.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.bus.app.ui.theme.СлужебныйАвтобусTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.URLEncoder

object AppState {
    var token by mutableStateOf<String?>(null)
    var userRole by mutableStateOf("")
    var userLogin by mutableStateOf("")
    var companyId by mutableStateOf<Int?>(null)
    var companyName by mutableStateOf<String?>(null)
    var userLocation by mutableStateOf<GeoPoint?>(null)
    var activeBuses = mutableStateListOf<ActiveBus>()
    var startPoint by mutableStateOf<GeoPoint?>(null)
    var endPoint by mutableStateOf<GeoPoint?>(null)
    var routePoints by mutableStateOf<List<GeoPoint>>(emptyList())
    var travelTimeInfo by mutableStateOf("Расчет...")
}

data class BusStop(val name: String, val location: GeoPoint)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        enableEdgeToEdge()
        setContent {
            СлужебныйАвтобусTheme {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    while(true) {
                        if (AppState.userLocation == null) AppState.userLocation = GeoPoint(43.1155, 131.8855)
                        else AppState.userLocation = GeoPoint(AppState.userLocation!!.latitude + 0.0001, AppState.userLocation!!.longitude + 0.0001)
                        delay(5000)
                    }
                }

                LaunchedEffect(AppState.token) {
                    if (AppState.token != null) {
                        while(true) {
                            scope.launch {
                                try {
                                    if (AppState.userRole == "driver" && AppState.userLocation != null) {
                                        ApiClient.api.updateLocation("Bearer ${AppState.token}", LocationUpdate(AppState.userLocation!!.latitude, AppState.userLocation!!.longitude))
                                    }
                                    val response = ApiClient.api.getActiveRoutes("Bearer ${AppState.token}")
                                    if (response.isSuccessful) {
                                        AppState.activeBuses.clear()
                                        val buses = response.body() ?: emptyList()
                                        val filtered = if (AppState.userRole == "admin") buses else buses.filter { it.companyId == AppState.companyId }
                                        AppState.activeBuses.addAll(filtered)
                                    }
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                            delay(5000)
                        }
                    }
                }

                NavHost(navController = navController, startDestination = "auth") {
                    composable("auth") { AuthScreen(navController) }
                    composable("main_map") { MainMapScreen(navController) }
                    composable("driver_setup") { DriverSetupScreen(navController) }
                    composable("passenger_setup") { PassengerSetupScreen(navController) }
                    composable("admin_panel") { AdminPanelScreen(navController) }
                }
            }
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
fun AuthScreen(navController: NavController) {
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
                        val response = ApiClient.api.login(login.trim(), password.trim())
                        if (response.isSuccessful) {
                            val body = response.body()!!
                            AppState.token = body.accessToken
                            AppState.userRole = body.role
                            AppState.userLogin = body.login
                            AppState.companyId = body.companyId
                            AppState.companyName = body.companyName
                            navController.navigate("main_map")
                        } else { Toast.makeText(context, "Неверный вход", Toast.LENGTH_SHORT).show() }
                    } catch (e: Exception) { Toast.makeText(context, "Сервер недоступен", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }
}

@Composable
fun MainMapScreen(navController: NavController) {
    var showMenu by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context -> MapView(context).apply { setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true); controller.setZoom(12.0); controller.setCenter(GeoPoint(43.1155, 131.8855)) } },
            update = { view ->
                view.overlays.clear()
                AppState.activeBuses.forEach { bus ->
                    val m = Marker(view); m.position = GeoPoint(bus.latitude, bus.longitude); m.title = "${bus.vehicleModel} (${bus.licensePlate})"; m.icon = view.context.getDrawable(android.R.drawable.ic_menu_compass); view.overlays.add(m)
                }
                AppState.userLocation?.let { val m = Marker(view); m.position = it; m.title = "Вы"; m.icon = view.context.getDrawable(android.R.drawable.star_on); view.overlays.add(m) }
                if (AppState.routePoints.isNotEmpty()) {
                    val line = Polyline(); line.setPoints(AppState.routePoints); line.outlinePaint.color = android.graphics.Color.GREEN; line.outlinePaint.strokeWidth = 12f; view.overlays.add(line)
                }
                view.invalidate()
            }
        )
        IconButton(onClick = { showMenu = !showMenu }, modifier = Modifier.padding(top = 40.dp, start = 16.dp).background(Color(0xFF0A1024).copy(0.8f), RoundedCornerShape(8.dp))) {
            Icon(Icons.Default.Menu, null, tint = Color.White)
        }
        if (showMenu) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable { showMenu = false }) {
                Column(modifier = Modifier.width(260.dp).fillMaxSize().background(Color(0xFF050816)).padding(24.dp).clickable(enabled=false){}) {
                    Text("Меню (${AppState.userRole})", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))
                    if (AppState.userRole == "admin") MenuButton("Админ-панель", Icons.Default.Settings) { navController.navigate("admin_panel"); showMenu = false }
                    if (AppState.userRole == "driver" || AppState.userRole == "admin") MenuButton("Настроить рейс", Icons.Default.Add) { navController.navigate("driver_setup"); showMenu = false }
                    if (AppState.userRole == "passenger" || AppState.userRole == "admin") MenuButton("Выбрать автобус", Icons.Default.Search) { navController.navigate("passenger_setup"); showMenu = false }
                    Spacer(modifier = Modifier.weight(1f))
                    MenuButton("Выйти", Icons.Default.ExitToApp) { AppState.token = null; navController.navigate("auth"); showMenu = false }
                }
            }
        }
    }
}

@Composable
fun AdminPanelScreen(navController: NavController) {
    var companies by remember { mutableStateOf<List<Company>>(emptyList()) }
    var users by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var newCompName by remember { mutableStateOf("") }
    var newUserLogin by remember { mutableStateOf("") }
    var newUserPass by remember { mutableStateOf("") }
    var newUserRole by remember { mutableStateOf("passenger") }
    var selectedCompId by remember { mutableStateOf<Int?>(null) }
    var vModel by remember { mutableStateOf("") }
    var lPlate by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope(); val scroll = rememberScrollState(); val context = LocalContext.current

    fun refreshData() {
        scope.launch {
            val cRes = ApiClient.api.getCompanies("Bearer ${AppState.token}")
            if (cRes.isSuccessful) companies = cRes.body() ?: emptyList()
            val uRes = ApiClient.api.getUsers("Bearer ${AppState.token}")
            if (uRes.isSuccessful) users = uRes.body() ?: emptyList()
        }
    }
    LaunchedEffect(Unit) { refreshData() }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF050816)).padding(24.dp).verticalScroll(scroll)) {
        Text("Админ-панель", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp)); Text("1. Компании", color = Color(0xFF00F5FF))
        NeonTextField(newCompName, { newCompName = it }, "Название")
        Button(modifier = Modifier.fillMaxWidth(), onClick = { scope.launch { 
            if (ApiClient.api.createCompany("Bearer ${AppState.token}", mapOf("name" to newCompName)).isSuccessful) { Toast.makeText(context, "Компания создана", Toast.LENGTH_SHORT).show(); newCompName = ""; refreshData() }
            else { Toast.makeText(context, "Ошибка создания", Toast.LENGTH_SHORT).show() }
        } }) { Text("Добавить компанию") }

        Spacer(modifier = Modifier.height(24.dp)); Text("2. Пользователь", color = Color(0xFF00F5FF))
        NeonTextField(newUserLogin, { newUserLogin = it }, "Логин")
        NeonTextField(newUserPass, { newUserPass = it }, "Пароль")
        Row {
            RadioButton(newUserRole == "passenger", { newUserRole = "passenger" }); Text("Пасс.", color = Color.White)
            RadioButton(newUserRole == "driver", { newUserRole = "driver" }); Text("Вод.", color = Color.White)
        }
        if (newUserRole == "driver") { NeonTextField(vModel, { vModel = it }, "Марка"); NeonTextField(lPlate, { lPlate = it }, "Номер") }
        Text("Компания:", color = Color.Gray)
        companies.forEach { comp -> Row(Modifier.clickable { selectedCompId = comp.id }, verticalAlignment = Alignment.CenterVertically) { 
            RadioButton(selectedCompId == comp.id, { selectedCompId = comp.id }); Text(comp.name, color = Color.White)
        } }
        Button(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), onClick = { scope.launch {
            val req = UserCreateRequest(newUserLogin, newUserPass, newUserRole, selectedCompId ?: 0, vModel, lPlate)
            if (ApiClient.api.createUser("Bearer ${AppState.token}", req).isSuccessful) { Toast.makeText(context, "Пользователь создан", Toast.LENGTH_SHORT).show(); newUserLogin = ""; newUserPass = ""; refreshData() }
            else { Toast.makeText(context, "Ошибка", Toast.LENGTH_SHORT).show() }
        } }) { Text("Создать пользователя") }

        Spacer(modifier = Modifier.height(32.dp)); Text("3. Список пользователей", color = Color(0xFF00F5FF))
        users.forEach { user -> Text("${user.role}: ${user.login} (${user.companyName})", color = Color.Gray, fontSize = 14.sp) }
        Spacer(modifier = Modifier.height(24.dp)); Button(modifier = Modifier.fillMaxWidth(), onClick = { navController.popBackStack() }) { Text("Назад") }
    }
}

@Composable
fun DriverSetupScreen(navController: NavController) {
    var sName by remember { mutableStateOf("") }; var eName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF050816)).padding(24.dp)) {
        Text("Создание рейса", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp)); SearchableStopField("Откуда", sName, { sName = it }, { AppState.startPoint = it.location })
        Spacer(modifier = Modifier.height(16.dp)); SearchableStopField("Куда", eName, { eName = it }, { AppState.endPoint = it.location })
        Spacer(modifier = Modifier.weight(1f))
        if (AppState.startPoint != null && AppState.endPoint != null) {
            NeonRunningButton("Запустить рейс", "Старт") { scope.launch { 
                val req = RouteRequest(sName, AppState.startPoint!!.latitude, AppState.startPoint!!.longitude, eName, AppState.endPoint!!.latitude, AppState.endPoint!!.longitude, "Теперь")
                if (ApiClient.api.startRoute("Bearer ${AppState.token}", req).isSuccessful) navController.navigate("main_map")
            } }
        }
        Button(modifier = Modifier.fillMaxWidth(), onClick = { navController.popBackStack() }) { Text("Назад") }
    }
}

@Composable
fun PassengerSetupScreen(navController: NavController) {
    Column(Modifier.fillMaxSize().background(Color(0xFF050816)).padding(24.dp)) {
        Text("Ваши автобусы", color = Color.White, fontSize = 20.sp); Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.weight(1f)) { items(AppState.activeBuses) { bus -> Card(Modifier.fillMaxWidth().padding(vertical=4.dp).clickable{navController.navigate("main_map")}, colors=CardDefaults.cardColors(containerColor=Color(0xFF10192F))) { 
            Column(Modifier.padding(16.dp)) { Text(bus.vehicleModel ?: "Автобус", color=Color.White, fontWeight=FontWeight.Bold); Text(bus.licensePlate ?: bus.driverLogin, color=Color.Gray, fontSize=12.sp) }
        } } }
        Button(modifier = Modifier.fillMaxWidth(), onClick={navController.popBackStack()}) { Text("Назад") }
    }
}

@Composable
fun SearchableStopField(label: String, query: String, onQueryChange: (String) -> Unit, onStopSelected: (BusStop) -> Unit) {
    var results by remember { mutableStateOf<List<BusStop>>(emptyList()) }
    LaunchedEffect(query) {
        if (query.length < 3) { results = emptyList(); return@LaunchedEffect }
        delay(600); withContext(Dispatchers.IO) {
            try {
                val url = "https://nominatim.openstreetmap.org/search?q=${URLEncoder.encode("$query Приморский край", "UTF-8")}&format=json&addressdetails=1&limit=5"
                val res = ApiClient.okHttpClient.newCall(Request.Builder().url(url).header("User-Agent", "BusApp").build()).execute()
                val jsonArray = JSONArray(res.body?.string() ?: "[]")
                val list = mutableListOf<BusStop>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i); val addr = obj.optJSONObject("address")
                    val city = addr?.optString("city") ?: addr?.optString("town") ?: addr?.optString("village") ?: addr?.optString("suburb") ?: ""
                    val name = obj.getString("display_name").split(",")[0]
                    val fullName = if(city.isNotEmpty()) "остановка $name, г. $city" else "остановка $name"
                    list.add(BusStop(fullName, GeoPoint(obj.getDouble("lat"), obj.getDouble("lon"))))
                }
                withContext(Dispatchers.Main) { results = list }
            } catch (e: Exception) {}
        }
    }
    Column { NeonTextField(query, onQueryChange, label)
        if (results.isNotEmpty()) { Column(Modifier.fillMaxWidth().background(Color(0xFF10192F)).border(1.dp, Color(0xFF00F5FF).copy(0.4f))) {
            results.forEach { stop -> Text(stop.name, color=Color.White, modifier=Modifier.fillMaxWidth().clickable { onStopSelected(stop); onQueryChange(stop.name); results=emptyList() }.padding(12.dp)) }
        } }
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
