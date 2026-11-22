package cl.induztek.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cl.induztek.app.model.ValidacionesFormulario
import cl.induztek.app.sync.ConfigSync
import cl.induztek.app.sync.ServiciosRemotos
import cl.induztek.app.sync.SolicitudRegistro
import cl.induztek.app.ui.components.AvisoInduztek
import cl.induztek.app.ui.components.CampoInduztek
import cl.induztek.app.ui.components.Simbolo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException

@Composable
fun RegistroCuentaScreen(onVolver: () -> Unit, onEnviada: () -> Unit) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var correo by rememberSaveable { mutableStateOf("") }
    var rut by rememberSaveable { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var confirmacion by remember { mutableStateOf("") }
    var errores by remember { mutableStateOf(emptyMap<String, String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }
    val contexto = LocalContext.current
    val config = remember(contexto) { ConfigSync(contexto) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Registro de cliente", style = MaterialTheme.typography.headlineSmall)
        Text("El jefe técnico revisará tu solicitud y vinculará la cuenta a tu empresa.")
        CampoInduztek(nombre, { nombre = it; errores = errores - "nombre" }, "Nombre completo", errores["nombre"], Simbolo.PERFIL, enabled = !enviando)
        CampoInduztek(correo, { correo = it; errores = errores - "correo" }, "Correo electrónico", errores["correo"], Simbolo.CORREO, KeyboardType.Email, enabled = !enviando)
        CampoInduztek(rut, { rut = it; errores = errores - "rut" }, "RUT", errores["rut"], Simbolo.PERFIL, enabled = !enviando)
        CampoInduztek(clave, { clave = it; errores = errores - "clave" - "confirmacion" }, "Contraseña", errores["clave"],
            Simbolo.CANDADO, KeyboardType.Password, enabled = !enviando, transformacion = PasswordVisualTransformation())
        CampoInduztek(confirmacion, { confirmacion = it; errores = errores - "confirmacion" }, "Repite la contraseña", errores["confirmacion"],
            Simbolo.CANDADO, KeyboardType.Password, enabled = !enviando, transformacion = PasswordVisualTransformation())
        AvisoInduztek(error, error = true)
        Button(enabled = !enviando, modifier = Modifier.fillMaxWidth(), onClick = {
            error = null
            errores = ValidacionesFormulario.cuenta(nombre, correo, rut, clave)
            if (clave != confirmacion) errores = errores + ("confirmacion" to "Las contraseñas no coinciden")
            if (errores.isEmpty()) {
                val solicitud = SolicitudRegistro(nombre.trim(), correo.trim(), rut.trim(), clave)
                enviando = true
                scope.launch {
                    try {
                        ServiciosRemotos.registrar(config.acceso, solicitud)
                        onEnviada()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: HttpException) {
                        error = when (e.code()) {
                            409 -> "Ya existe una cuenta o solicitud con esos datos."
                            400, 422 -> "Revisa los datos del formulario."
                            else -> "El servicio de registro no está disponible."
                        }
                    } catch (_: Exception) {
                        error = "No se pudo conectar con Usuarios. Revisa la conexión e intenta nuevamente."
                    } finally {
                        enviando = false
                    }
                }
            }
        }) { Text(if (enviando) "Enviando…" else "Enviar solicitud") }
        TextButton(enabled = !enviando, onClick = onVolver) { Text("Volver al inicio de sesión") }
    }
}
