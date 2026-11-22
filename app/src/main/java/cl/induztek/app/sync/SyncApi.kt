package cl.induztek.app.sync

import cl.induztek.app.data.EstadoLocal
import cl.induztek.app.data.PendienteSyncEntity
import cl.induztek.app.model.UsuarioDemo
import cl.induztek.app.model.Rol
import cl.induztek.app.model.Persona
import com.google.gson.JsonObject
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Streaming
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class Confirmacion(val id: String)
data class ActaRemota(val visitaId: String, val ot: String, val texto: String, val cierre: Long, val sha256: String) {
    fun esValida(id: String): Boolean {
        if (visitaId != id || cierre <= 0 || texto.isBlank()) return false
        val huella = MessageDigest.getInstance("SHA-256").digest(texto.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return sha256.equals(huella, ignoreCase = true)
    }
}

interface ActasApi {
    @GET("api/actas/{visitaId}") suspend fun acta(@Path("visitaId") id: String): ActaRemota
}

data class AltaCliente(val nombre: String, val correo: String, val rut: String, val clave: String, val clienteId: String, val rol: Rol = Rol.CLIENTE)
data class SolicitudRegistro(val nombre: String, val correo: String, val rut: String, val clave: String)
data class EstadoCuenta(val activo: Boolean, val rol: Rol, val clienteId: String? = null)
data class RegistroIngreso(val id: String)

interface AccesoApi {
    @GET("api/session") suspend fun perfil(): UsuarioDemo
    @GET("api/servicios/{servicio}") suspend fun comprobar(@Path("servicio") servicio: String): UsuarioDemo
    @POST("api/registro") suspend fun registrar(@Body solicitud: SolicitudRegistro): Response<Void>
    @POST("api/ingresos") suspend fun registrarIngreso(@Body ingreso: RegistroIngreso)
}

interface FotoPerfilApi {
    @Streaming @GET("api/perfil/foto") suspend fun foto(): Response<ResponseBody>
    @PUT("api/perfil/foto") suspend fun guardar(@Body jpeg: RequestBody)
}

interface CuentasApi {
    @GET("api/cuentas") suspend fun cuentas(): List<UsuarioDemo>
    @GET("api/personal") suspend fun personal(): List<Persona>
    @POST("api/cuentas") suspend fun alta(@Body alta: AltaCliente): UsuarioDemo
    @PATCH("api/cuentas/{id}/permisos")
    suspend fun estado(@Path("id") id: String, @Body estado: EstadoCuenta): UsuarioDemo
}

interface SyncApi {
    @GET("api/session") suspend fun sesion(): JsonObject
    @GET("api/snapshot") suspend fun snapshot(): EstadoLocal
    @POST("api/events") suspend fun enviar(@Body evento: PendienteSyncEntity): Confirmacion
}

class ServicioNoDisponible(val servicio: String, causa: Exception) : IOException(causa)

object ServiciosRemotos {
    private data class SesionRemota(val perfil: UsuarioDemo, val cabecera: String)
    private val credenciales = ConcurrentHashMap<String, SesionRemota>()
    fun cuentaEnMemoria(id: String?): UsuarioDemo? = id?.let { credenciales[it]?.perfil }
    fun cuentasEnMemoria(): List<UsuarioDemo> = credenciales.values.map { it.perfil }.sortedBy { it.rol }
    private val cliente = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).followRedirects(false).build()
    private val clienteAcceso = cliente.newBuilder().callTimeout(6, TimeUnit.SECONDS).build()

    fun crear(url: String, usuario: UsuarioDemo): SyncApi =
        retrofit(url, { autorizacion(usuario) }, cliente).create(SyncApi::class.java)

    fun actas(url: String, usuario: UsuarioDemo): ActasApi =
        retrofit(url, { autorizacion(usuario) }, cliente.newBuilder().callTimeout(8, TimeUnit.SECONDS).build())
            .create(ActasApi::class.java)

    fun cuentas(url: String, usuario: UsuarioDemo): CuentasApi =
        retrofit(url, { autorizacion(usuario) }, cliente).create(CuentasApi::class.java)

    fun fotos(url: String, usuario: UsuarioDemo): FotoPerfilApi =
        retrofit(url, { autorizacion(usuario) }, clienteAcceso).create(FotoPerfilApi::class.java)

    suspend fun registrar(url: String, solicitud: SolicitudRegistro) {
        val respuesta = retrofit(url, null, clienteAcceso).create(AccesoApi::class.java).registrar(solicitud)
        if (!respuesta.isSuccessful) throw HttpException(respuesta)
    }

    suspend fun autenticar(config: ConfigSync, correo: String, clave: String): UsuarioDemo {
        val cabecera = Credentials.basic(correo, clave, Charsets.UTF_8)
        return verificarSesion(config, correo, cabecera)
    }

    suspend fun reactivar(config: ConfigSync, id: String): UsuarioDemo {
        val sesion = credenciales[id] ?: throw IOException("La sesión expiró")
        return verificarSesion(config, sesion.perfil.correo, sesion.cabecera, id)
    }

    private suspend fun verificarSesion(config: ConfigSync, correo: String, cabecera: String, id: String? = null): UsuarioDemo {
        fun acceso(url: String) = retrofit(url, { cabecera }, clienteAcceso).create(AccesoApi::class.java)
        val perfil = acceso(config.acceso).perfil()
        require(perfil.activo && perfil.correo.equals(correo, ignoreCase = true) && (id == null || perfil.id == id)) { "Identidad inválida" }
        coroutineScope {
            listOf(
                Triple("catalogos", "Catálogos", config.catalogo),
                Triple("protocolos", "Protocolos", config.protocolos),
                Triple("actas", "Actas", config.actas)
            ).map { (ruta, servicio, url) ->
                    async {
                        try {
                            check(acceso(url).comprobar(ruta) == perfil) { "Los servicios identificaron cuentas diferentes" }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            throw ServicioNoDisponible(servicio, e)
                        }
                    }
                }.awaitAll()
        }
        if (id == null) acceso(config.acceso).registrarIngreso(RegistroIngreso(UUID.randomUUID().toString()))
        credenciales[perfil.id] = SesionRemota(perfil, cabecera)
        return perfil
    }

    fun cerrarSesion(id: String) { credenciales.remove(id) }
    fun cerrarTodas() { credenciales.clear() }

    private fun autorizacion(usuario: UsuarioDemo): String =
        credenciales[usuario.id]?.cabecera ?: throw IOException("Inicia sesión para consultar el servidor")

    private fun retrofit(url: String, autorizacion: (() -> String)?, base: OkHttpClient): Retrofit {
        val direccion = direccionServicio(url)
        val autenticado = if (autorizacion == null) base else base.newBuilder()
            .addInterceptor { cadena -> cadena.proceed(cadena.request().newBuilder()
                .header("Authorization", autorizacion())
                .build()) }.build()
        return Retrofit.Builder().baseUrl(direccion).client(autenticado)
            .addConverterFactory(GsonConverterFactory.create()).build()
    }
}

fun direccionServicio(valor: String): String {
    val url = HttpUrl.get(valor.trim())
    require(url.isHttps || url.host() in setOf("localhost", "127.0.0.1")) { "El servidor remoto requiere HTTPS" }
    require(url.username().isEmpty() && url.password().isEmpty() && url.query() == null && url.fragment() == null)
    return url.toString().trimEnd('/') + "/"
}
