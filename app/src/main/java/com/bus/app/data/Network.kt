package com.bus.app.data

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit
import org.osmdroid.util.GeoPoint

// --- МОДЕЛИ ДАННЫХ ---
data class Company(val id: Int, val name: String)

data class BusStop(val name: String, val location: GeoPoint)

data class UserDto(
    val login: String,
    val role: String,
    @SerializedName("company_id") val companyId: Int?,
    @SerializedName("company_name") val companyName: String?,
    @SerializedName("vehicle_model") val vehicleModel: String? = null,
    @SerializedName("license_plate") val licensePlate: String? = null
)

data class LoginResponse(
    @SerializedName("access_token") val accessToken: String,
    val role: String,
    val login: String,
    @SerializedName("company_id") val companyId: Int?,
    @SerializedName("company_name") val companyName: String?
)

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
    @SerializedName("company_id") val companyId: Int
)

data class UserCreateRequest(
    val login: String,
    val password: String,
    val role: String,
    @SerializedName("company_id") val companyId: Int,
    @SerializedName("vehicle_model") val vehicleModel: String? = null,
    @SerializedName("license_plate") val licensePlate: String? = null
)

// --- API ИНТЕРФЕЙС ---
interface BusApi {
    @FormUrlEncoded
    @POST("/auth/login")
    suspend fun login(@Field("username") user: String, @Field("password") pass: String): Response<LoginResponse>

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
}

object ApiClient {
    private const val BASE_URL = "https://orientation-ahead-stroke-statutory.trycloudflare.com/"

    private val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
    
    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: BusApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
            .create(BusApi::class.java)
    }
}
