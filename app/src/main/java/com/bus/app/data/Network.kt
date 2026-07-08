package com.bus.app.data

import com.bus.app.config.AppConfig
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit
import org.osmdroid.util.GeoPoint
import com.bus.app.data.model.DefectReportDto
import com.bus.app.data.model.LiveMapVehicleDto
import com.bus.app.data.model.StopPointDto
import com.bus.app.data.model.DriverShiftDto
import com.bus.app.data.model.InspectionDto
import com.bus.app.data.model.TripDto

// --- МОДЕЛИ ДАННЫХ ---
data class BusStop(val name: String, val location: GeoPoint)

data class UserDto(
    val login: String,
    val role: String,
    @SerializedName("company_id") val companyId: Int?,
    @SerializedName("company_name") val companyName: String?,
    @SerializedName("vehicle_model") val vehicleModel: String? = null,
    @SerializedName("license_plate") val licensePlate: String? = null
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String? = null,
    val role: String? = null,
    val login: String? = null,
    @SerializedName("company_id") val companyId: Int? = null,
    @SerializedName("company_name") val companyName: String? = null
)

data class CurrentUserDto(
    val id: Int? = null,
    val login: String,
    val role: String,
    @SerializedName("company_id") val companyId: Int? = null,
    @SerializedName("company_name") val companyName: String? = null,
    @SerializedName("vehicle_model") val vehicleModel: String? = null,
    @SerializedName("license_plate") val licensePlate: String? = null
)

enum class AuthErrorType { INVALID_CREDENTIALS, UNAUTHORIZED, RATE_LIMITED, NETWORK, SERVER }

sealed class AuthResult {
    data class Success(val response: LoginResponse) : AuthResult()
    data class Failure(val type: AuthErrorType, val statusCode: Int? = null) : AuthResult()
}

data class RouteRequest(
    @SerializedName("start_name") val startName: String,
    @SerializedName("start_lat") val startLat: Double,
    @SerializedName("start_lng") val startLng: Double,
    @SerializedName("end_name") val endName: String,
    @SerializedName("end_lat") val endLat: Double,
    @SerializedName("end_lng") val endLng: Double,
    @SerializedName("start_time") val startTime: String
)

data class RouteResponse(val id: Int, val status: String)

data class LocationUpdate(val latitude: Double, val longitude: Double)

data class ActiveBus(
    val id: Int,
    @SerializedName("driver_login") val driverLogin: String,
    @SerializedName("vehicle_model") val vehicleModel: String?,
    @SerializedName("license_plate") val licensePlate: String?,
    val latitude: Double,
    val longitude: Double,
    @SerializedName("company_id") val companyId: Int,
    val speed: Double? = null
)

data class UserCreateRequest(
    val login: String,
    val password: String,
    val role: String,
    @SerializedName("company_id") val companyId: Int? = null,
    @SerializedName("vehicle_model") val vehicleModel: String? = null,
    @SerializedName("license_plate") val licensePlate: String? = null
)

data class DriverAcceptVehicleRequest(
    @SerializedName("vehicle_id") val vehicleId: Int
)

data class DriverInspectionCreateRequest(
    @SerializedName("vehicle_id") val vehicleId: Int,
    val status: String,
    val notes: String? = null,
    val type: String? = null
)

data class MechanicAssignRepairRequest(
    val notes: String? = null
)

data class MechanicCloseDefectRequest(
    val resolution: String? = null
)


data class MapConfigDto(
    @SerializedName("tile_url") val tileUrl: String? = null,
    @SerializedName("tile_url_template") val tileUrlTemplate: String? = null,
    @SerializedName("min_zoom") val minZoom: Int? = null,
    @SerializedName("max_zoom") val maxZoom: Int? = null,
    @SerializedName("center_lat") val centerLat: Double? = null,
    @SerializedName("center_lng") val centerLng: Double? = null,
    @SerializedName("default_zoom") val defaultZoom: Double? = null,
    @SerializedName("attribution") val attribution: String? = null
)

data class WialonAccount(
    val id: Int,
    val name: String,
    @SerializedName("base_url") val baseUrl: String
)

data class WialonAccountCreateRequest(
    val name: String,
    @SerializedName("base_url") val baseUrl: String,
    val token: String
)

data class WialonUnit(
    val id: Int,
    @SerializedName("external_id") val externalId: String?,
    val name: String,
    @SerializedName("license_plate") val licensePlate: String?
)

// --- API ИНТЕРФЕЙС ---
interface BusApi {
    @GET("/health")
    suspend fun health(): Response<Map<String, Any>>

    @FormUrlEncoded
    @POST("/auth/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") pass: String
    ): Response<LoginResponse>

    @GET("/auth/me")
    suspend fun getCurrentUser(@Header("Authorization") token: String): Response<CurrentUserDto>

    @GET("/routes/active")
    suspend fun getActiveRoutes(@Header("Authorization") token: String): Response<List<ActiveBus>>

    @POST("/location/update")
    suspend fun updateLocation(@Header("Authorization") token: String, @Body loc: LocationUpdate): Response<Unit>

    @POST("/route/start")
    suspend fun startRoute(@Header("Authorization") token: String, @Body route: RouteRequest): Response<RouteResponse>

    @GET("/admin/companies")
    suspend fun getCompanies(@Header("Authorization") token: String): Response<List<Company>>

    @POST("/admin/companies")
    suspend fun createCompany(@Header("Authorization") token: String, @Body name: Map<String, String>): Response<Unit>

    @GET("/admin/users")
    suspend fun getUsers(@Header("Authorization") token: String): Response<List<UserDto>>

    @POST("/admin/users")
    suspend fun createUser(@Header("Authorization") token: String, @Body user: UserCreateRequest): Response<Unit>

    @GET("/admin/wialon/accounts")
    suspend fun getWialonAccounts(@Header("Authorization") token: String): Response<List<WialonAccount>>

    @POST("/admin/wialon/accounts")
    suspend fun createWialonAccount(
        @Header("Authorization") token: String,
        @Body request: WialonAccountCreateRequest
    ): Response<Unit>

    @POST("/admin/wialon/accounts/{id}/test")
    suspend fun testWialonAccount(
        @Header("Authorization") token: String,
        @Path("id") accountId: Int
    ): Response<Unit>

    @POST("/admin/wialon/accounts/{id}/sync-units")
    suspend fun syncWialonUnits(
        @Header("Authorization") token: String,
        @Path("id") accountId: Int
    ): Response<Unit>

    @GET("/admin/wialon/units")
    suspend fun getWialonUnits(@Header("Authorization") token: String): Response<List<WialonUnit>>


    @GET("/admin/map/config")
    suspend fun getMapConfig(@Header("Authorization") token: String): Response<MapConfigDto>

    @GET("/admin/map/tiles/{z}/{x}/{y}")
    suspend fun getMapTile(
        @Header("Authorization") token: String,
        @Path("z") z: Int,
        @Path("x") x: Int,
        @Path("y") y: Int
    ): Response<ResponseBody>

    @GET("/map/vehicles/live")
    suspend fun getLiveMapVehicles(@Header("Authorization") token: String): Response<List<LiveMapVehicleDto>>

    @GET("/admin/map/vehicles")
    suspend fun getAdminMapVehicles(@Header("Authorization") token: String): Response<List<LiveMapVehicleDto>>

    @GET("/admin/map/vehicle/{vehicle_id}")
    suspend fun getAdminMapVehicle(
        @Header("Authorization") token: String,
        @Path("vehicle_id") vehicleId: Int
    ): Response<LiveMapVehicleDto>

    @GET("/admin/map/stops")
    suspend fun getMapStops(@Header("Authorization") token: String): Response<List<StopPointDto>>

    @GET("/admin/map/icons/{icon_kind}")
    suspend fun getMapIcon(
        @Header("Authorization") token: String,
        @Path("icon_kind") iconKind: String
    ): Response<ResponseBody>

    @GET("/driver/shifts/current")
    suspend fun getCurrentDriverShift(@Header("Authorization") token: String): Response<DriverShiftDto>

    @POST("/driver/shifts/start")
    suspend fun startDriverShift(@Header("Authorization") token: String): Response<DriverShiftDto>

    @POST("/driver/shifts/accept-vehicle")
    suspend fun acceptDriverVehicle(
        @Header("Authorization") token: String,
        @Body request: DriverAcceptVehicleRequest
    ): Response<DriverShiftDto>

    @POST("/driver/inspections")
    suspend fun createDriverInspection(
        @Header("Authorization") token: String,
        @Body request: DriverInspectionCreateRequest
    ): Response<InspectionDto>

    @GET("/driver/inspections")
    suspend fun getDriverInspections(@Header("Authorization") token: String): Response<List<InspectionDto>>

    @GET("/driver/trips")
    suspend fun getDriverTrips(@Header("Authorization") token: String): Response<List<TripDto>>

    @POST("/driver/trips/{trip_id}/start")
    suspend fun startDriverTrip(
        @Header("Authorization") token: String,
        @Path("trip_id") tripId: Int
    ): Response<TripDto>

    @POST("/driver/trips/{trip_id}/complete")
    suspend fun completeDriverTrip(
        @Header("Authorization") token: String,
        @Path("trip_id") tripId: Int
    ): Response<TripDto>

    @POST("/driver/shifts/finish")
    suspend fun finishDriverShift(@Header("Authorization") token: String): Response<DriverShiftDto>

    @Multipart
    @POST("/driver/defects")
    suspend fun createDriverDefect(
        @Header("Authorization") token: String,
        @Part("vehicle_id") vehicleId: RequestBody,
        @Part("description") description: RequestBody,
        @Part("severity") severity: RequestBody?,
        @Part photo: MultipartBody.Part?
    ): Response<DefectReportDto>

    @GET("/driver/defects")
    suspend fun getDriverDefects(@Header("Authorization") token: String): Response<List<DefectReportDto>>

    @GET("/admin/mechanic/defects")
    suspend fun getMechanicDefects(@Header("Authorization") token: String): Response<List<DefectReportDto>>

    @POST("/admin/mechanic/defects/{defect_id}/accept")
    suspend fun acceptMechanicDefect(
        @Header("Authorization") token: String,
        @Path("defect_id") defectId: Int
    ): Response<DefectReportDto>

    @POST("/admin/mechanic/defects/{defect_id}/assign-repair")
    suspend fun assignMechanicRepair(
        @Header("Authorization") token: String,
        @Path("defect_id") defectId: Int,
        @Body request: MechanicAssignRepairRequest
    ): Response<DefectReportDto>

    @POST("/admin/mechanic/defects/{defect_id}/close")
    suspend fun closeMechanicDefect(
        @Header("Authorization") token: String,
        @Path("defect_id") defectId: Int,
        @Body request: MechanicCloseDefectRequest
    ): Response<DefectReportDto>

    @GET("/admin/mechanic/vehicles/{vehicle_id}/history")
    suspend fun getVehicleRepairHistory(
        @Header("Authorization") token: String,
        @Path("vehicle_id") vehicleId: Int
    ): Response<List<DefectReportDto>>
}

object ApiClient {
    private val LOGGING_ENABLED = AppConfig.HTTP_LOGGING_ENABLED

    private val logging = HttpLoggingInterceptor().apply {
        level = if (LOGGING_ENABLED) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }
    
    val okHttpClient = OkHttpClient.Builder()
        .apply {
            addInterceptor(AuthInterceptor())
            addInterceptor(UnauthorizedInterceptor())
            if (LOGGING_ENABLED) {
                addInterceptor(logging)
            }
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: BusApi by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
            .create(BusApi::class.java)
    }
}
