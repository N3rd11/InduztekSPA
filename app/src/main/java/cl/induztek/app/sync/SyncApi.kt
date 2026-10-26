package cl.induztek.app.sync

import cl.induztek.app.data.EstadoLocal
import cl.induztek.app.data.PendienteSyncEntity
import cl.induztek.app.model.UsuarioDemo
import com.google.gson.JsonObject
import okhttp3.Credentials
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class Confirmacion(val id: String)

interface SyncApi {
    @GET("api/session") suspend fun sesion(): JsonObject
    @GET("api/snapshot") suspend fun snapshot(): EstadoLocal
    @POST("api/events") suspend fun enviar(@Body evento: PendienteSyncEntity): Confirmacion
}

object ServiciosRemotos {
    fun crear(url: String, usuario: UsuarioDemo): SyncApi {
        require(url.startsWith("https://") || url.startsWith("http://")) { "URL HTTP/HTTPS inválida" }
        val cliente = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { cadena -> cadena.proceed(cadena.request().newBuilder()
                .header("Authorization", Credentials.basic(usuario.correo, "1234"))
                .build()) }.build()
        return Retrofit.Builder().baseUrl(url.trimEnd('/') + "/").client(cliente)
            .addConverterFactory(GsonConverterFactory.create()).build().create(SyncApi::class.java)
    }
}
