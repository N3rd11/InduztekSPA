package cl.induztek.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.induztek.app.model.*
import cl.induztek.app.ui.components.*
import cl.induztek.app.viewmodel.AccesoViewModel

@Composable
fun LoginScreen(onAcceder: (UsuarioDemo) -> Unit, vm: AccesoViewModel = viewModel()) {
    var mostrarClave by rememberSaveable { mutableStateOf(false) }
    var mostrarCuentas by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(12.dp))
        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LogoInduztek()
                    Column(Modifier.weight(1f)) {
                        Text("INDUZTEK", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text("INGENIERÍA EN TERRENO", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text("Cada prueba, un respaldo confiable.", style = MaterialTheme.typography.titleMedium)
            }
        }
        Text("Acceso a protocolos eléctricos", style = MaterialTheme.typography.headlineSmall)
        Text("Registra en terreno y consulta el historial de tus equipos, incluso sin conexión.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        CampoInduztek(vm.correo, vm::correo, "Correo electrónico", vm.errores["correo"], Simbolo.CORREO, KeyboardType.Email)
        CampoInduztek(vm.clave, vm::clave, "Contraseña", vm.errores["clave"], Simbolo.CANDADO, KeyboardType.Password,
            transformacion = if (mostrarClave) VisualTransformation.None else PasswordVisualTransformation(),
            final = { TextButton(onClick = { mostrarClave = !mostrarClave }) { Text(if (mostrarClave) "Ocultar" else "Ver") } })
        AvisoInduztek(vm.error, error = true)
        Button(onClick = { vm.ingresar()?.let(onAcceder) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("Iniciar sesión")
        }
        OutlinedButton(onClick = { mostrarCuentas = !mostrarCuentas }, modifier = Modifier.fillMaxWidth()) {
            Text(if (mostrarCuentas) "Ocultar usuarios" else "Seleccionar usuario")
        }
        AnimatedVisibility(mostrarCuentas) {
            Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UsuariosDemo.todos.filter { it.activo }.forEach { u ->
                    OutlinedCard(onClick = { vm.elegir(u) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconoInduztek(Simbolo.PERFIL)
                            Column { Text(u.nombre, fontWeight = FontWeight.SemiBold); Text(u.rol.titulo(), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}

fun Rol.titulo() = when(this) {
    Rol.TECNICO -> "Técnico de terreno"
    Rol.INGENIERO -> "Ingeniero de pruebas"
    Rol.JEFE_TECNICO -> "Jefe técnico"
    Rol.CLIENTE -> "Cliente · solo consulta"
}

@Composable
fun InicioScreen(usuario: UsuarioDemo, onRegistrar: () -> Unit, onCerrarSesion: () -> Unit,
                 contenidoPanel: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(usuario.rol.titulo().uppercase(), style = MaterialTheme.typography.labelLarge)
                Text("Hola, ${usuario.nombre.substringBefore(' ')}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(when(usuario.rol) {
                    Rol.TECNICO -> "Prepara tu visita, registra las pruebas y entrega el respaldo en terreno."
                    Rol.INGENIERO -> "Revisa resultados y formaliza los protocolos de forma explícita."
                    Rol.JEFE_TECNICO -> "Supervisa los protocolos y la vigencia de los instrumentos."
                    Rol.CLIENTE -> "Consulta las actas y el historial de los equipos de tu empresa."
                })
            }
        }
        Text("Panel principal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        contenidoPanel()
        if (Permisos.registrar(usuario)) OutlinedButton(onClick = onRegistrar, modifier = Modifier.fillMaxWidth()) {
            IconoInduztek(Simbolo.ENERGIA); Spacer(Modifier.width(8.dp)); Text("Mediciones de aislación anteriores")
        }
        OutlinedButton(onClick = onCerrarSesion, modifier = Modifier.fillMaxWidth()) {
            IconoInduztek(Simbolo.SALIR); Spacer(Modifier.width(8.dp)); Text("Cerrar sesión")
        }
    }
}
