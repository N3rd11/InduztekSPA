package cl.induztek.app.data

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class LugarClima(
    val name: String,
    val latitude: Double?,
    val longitude: Double?
)

data class LugaresClima(val results: List<LugarClima>?)

data class ClimaActual(
    val time: String?,
    @SerializedName("temperature_2m") val temperatura: Double?,
    @SerializedName("relative_humidity_2m") val humedad: Double?
)

data class PronosticoClima(val current: ClimaActual?)
data class ClimaComuna(val localidad: String, val hora: String, val temperatura: Double, val humedad: Double)

interface GeocodificacionApi {
    @GET("v1/search?countryCode=CL&language=es&count=5")
    suspend fun buscar(@Query("name") nombre: String): LugaresClima
}

interface PronosticoApi {
    @GET("v1/forecast?current=temperature_2m,relative_humidity_2m&timezone=auto")
    suspend fun consultar(@Query("latitude") latitud: Double, @Query("longitude") longitud: Double): PronosticoClima
}

class ClimaRepository(private val lugares: GeocodificacionApi, private val pronostico: PronosticoApi) {
    suspend fun consultar(comuna: String): ClimaComuna? {
        val nombre = comuna.trim()
        if (nombre.length < 2) return null
        val lugar = lugares.buscar(nombre).results.orEmpty().firstOrNull() ?: return null
        val latitud = lugar.latitude ?: return null
        val longitud = lugar.longitude ?: return null
        return consultarCoordenadas(latitud, longitud, lugar.name)
    }

    suspend fun consultarCoordenadas(
        latitud: Double,
        longitud: Double,
        localidad: String = "Ubicación actual"
    ): ClimaComuna? {
        if (latitud !in -90.0..90.0 || longitud !in -180.0..180.0) return null
        val actual = pronostico.consultar(latitud, longitud).current ?: return null
        val hora = actual.time ?: return null
        val temperatura = actual.temperatura?.takeIf { it.isFinite() } ?: return null
        val humedad = actual.humedad?.takeIf { it in 0.0..100.0 } ?: return null
        return ClimaComuna(localidad, hora, temperatura, humedad)
    }

    companion object {
        val externo by lazy {
            val cliente = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()
            fun retrofit(url: String) = Retrofit.Builder().baseUrl(url).client(cliente)
                .addConverterFactory(GsonConverterFactory.create()).build()
            ClimaRepository(
                retrofit("https://geocoding-api.open-meteo.com/").create(GeocodificacionApi::class.java),
                retrofit("https://api.open-meteo.com/").create(PronosticoApi::class.java)
            )
        }
    }
}
