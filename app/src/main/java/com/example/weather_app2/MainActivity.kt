package com.example.weather_app2

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.weather_app2.database.Database
import com.example.weather_app2.database.data
import com.example.weather_app2.database.weatherDAO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    private lateinit var weatherDao: weatherDAO

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the Room database and DAO
        val db = Room.databaseBuilder(
            applicationContext,
            Database::class.java, "weather-database"
        ).fallbackToDestructiveMigration().build()
        weatherDao = db.weatherDao()

        // Create the WeatherApiClient and WeatherRepository
        val weatherApiClient = WeatherApiClient.create()
        val weatherRepository = WeatherRepository(weatherApiClient, weatherDao)

        // Set the content view using Compose
        setContent {
            WeatherAppUI(WeatherViewModel(weatherRepository,applicationContext))
        }
    }
}


@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun WeatherAppUI(viewModel: WeatherViewModel) {
    var dateState by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        TextField(
            value = dateState,
            onValueChange = { dateState = it },
            label = { Text("Enter Date (YYYY-MM-DD)") },
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(
            onClick = { viewModel.fetchWeatherDataAndStore(dateState) },
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text("Search")
        }

        Text(
            text = "Max Temperature: ${viewModel.maxTemperature ?: "-"}°C",
            style = MaterialTheme.typography.h6
        )
        Text(
            text = "Min Temperature: ${viewModel.minTemperature ?: "-"}°C",
            style = MaterialTheme.typography.h6
        )
    }
}

class WeatherViewModel(private val repository: WeatherRepository,private val applicationContext: Context) : ViewModel() {
    var maxTemperature: Double? by mutableStateOf(null)
    var minTemperature: Double? by mutableStateOf(null)

    @RequiresApi(Build.VERSION_CODES.O)
    fun fetchWeatherDataAndStore(date: String) {
        val currentDate = LocalDate.now()
        val providedDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        viewModelScope.launch(Dispatchers.IO) {
            if (isInternetAvailable(applicationContext)) {
                if (providedDate.isAfter(currentDate)) {
                    // Date is in the future, calculate average of last 10 years
                    val averageTemp = repository.getAverageTemperatureForLastTenYears()
                    maxTemperature = averageTemp.first
                    minTemperature = averageTemp.second
                } else {
                    val response = repository.getWeatherData(date)
                    if (response.isSuccessful) {
                        val weatherResponse = response.body()
                        val maxTemp = weatherResponse?.daily?.temperature_2m_max?.get(0) ?: 0.0
                        val minTemp = weatherResponse?.daily?.temperature_2m_min?.get(0) ?: 0.0
                        maxTemperature = maxTemp?.toDouble()
                        minTemperature = minTemp?.toDouble()

                        repository.insertWeatherData(date, maxTemperature!!, minTemperature!!)
                    } else {
                        maxTemperature = null
                        minTemperature = null
                        // Handle error
                        println("Failed to fetch weather data: ${response.message()}")
                    }
                }
            }else {
                    // Fetch from local database
                    val weatherData = repository.getWeatherDataFromDatabase(date)
                    if (weatherData != null) {
                        maxTemperature = weatherData.maxTemperature
                        minTemperature = weatherData.minTemperature
                    } else {
                        maxTemperature = null
                        minTemperature = null
                        // Handle error
                        println("No data available in the database for date: $date")
                    }
            }
        }
    }
}

object WeatherApiClient {
    private const val BASE_URL = "https://archive-api.open-meteo.com/v1/"
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    fun create(): WeatherApiService {
        return retrofit.create(WeatherApiService::class.java)
    }
}

interface WeatherApiService {
    @GET("era5")
    suspend fun getWeatherData(
        @Query("latitude") latitude: Double = 52.52,
        @Query("longitude") longitude: Double = 13.41,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min"
    ): Response<WeatherResponse>
}

data class WeatherResponse(
    val latitude: Double,
    val longitude: Double,
    val generationtime_ms: Double,
    val utc_offset_seconds: Int,
    val timezone: String,
    val timezone_abbreviation: String,
    val elevation: Int,
    val daily: Daily
)

data class Daily(
    val time: List<String>,
    val temperature_2m_max: List<Float>,
    val temperature_2m_min: List<Float>,
    val temperature_2m_mean: List<Float>
)
class WeatherRepository(private val weatherApiService: WeatherApiService, private val weatherDao: weatherDAO) {

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun insertWeatherData(date: String, maxTemp: Double, minTemp: Double) {
        val weatherData = data(date = date, maxTemperature = maxTemp, minTemperature = minTemp)
        weatherDao.insert(weatherData)
    }

    suspend fun getWeatherData(date: String): Response<WeatherResponse> {
        // Dummy latitude and longitude, replace with actual values
        val latitude = 52.52
        val longitude = 13.41
        return weatherApiService.getWeatherData(latitude, longitude, date, date)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun fetchHistoricalWeatherData(startDate: LocalDate, endDate: LocalDate): Response<WeatherResponse> {
        val startDateString = startDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val endDateString = endDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        return weatherApiService.getWeatherData(startDate = startDateString, endDate = endDateString)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getAverageTemperatureForLastTenYears(): Pair<Double, Double> {
        val currentYear = LocalDate.now().year
        var totalMaxTemperature = 0.0
        var totalMinTemperature = 0.0
        var totalDays = 0

        for (year in currentYear - 10 until currentYear) {
            val yearStartDate = LocalDate.of(year, 1, 1)
            val yearEndDate = LocalDate.of(year, 12, 31)
            val response = fetchHistoricalWeatherData(yearStartDate, yearEndDate)

            if (response.isSuccessful) {
                val historicalData = response.body()
                val maxTemp = historicalData?.daily?.temperature_2m_max?.maxOrNull()?.toDouble() ?: 0.0
                val minTemp = historicalData?.daily?.temperature_2m_min?.minOrNull()?.toDouble() ?: 0.0

                totalMaxTemperature += maxTemp
                totalMinTemperature += minTemp
                totalDays += 1
            } else {
                // Handle error
                println("Failed to fetch historical weather data for year $year: ${response.message()}")
            }
        }

        val averageMaxTemperature = if (totalDays > 0) {
            totalMaxTemperature / totalDays
        } else {
            0.0 // Return 0 if no data is available
        }

        val averageMinTemperature = if (totalDays > 0) {
            totalMinTemperature / totalDays
        } else {
            0.0 // Return 0 if no data is available
        }

        return Pair(averageMaxTemperature, averageMinTemperature)
    }

    suspend fun getWeatherDataFromDatabase(date: String): data? {
        return weatherDao.getWeatherDataByDate(date)
    }
}


@SuppressLint("ServiceCast")
fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetworkInfo = connectivityManager?.activeNetworkInfo
    return activeNetworkInfo != null && activeNetworkInfo.isConnected
}

fun isInternetAvailable(context: Context): Boolean {
    var result = false
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        connectivityManager?.run {
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.run {
                result = when {
                    hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                    hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                    // for other device how are able to connect with Ethernet
                    hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                    else -> false
                }
            }
        }
    } else {
        connectivityManager?.run {
            connectivityManager.activeNetworkInfo?.run {
                if (type == ConnectivityManager.TYPE_WIFI) {
                    result = true
                } else if (type == ConnectivityManager.TYPE_MOBILE) {
                    result = true
                }
            }
        }
    }
    return result
}