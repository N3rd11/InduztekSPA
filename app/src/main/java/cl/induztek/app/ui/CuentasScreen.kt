package cl.induztek.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cl.induztek.app.data.ClienteEntity
import cl.induztek.app.sync.AltaCliente
import cl.induztek.app.viewmodel.CuentasViewModel
import cl.induztek.app.model.Rol
import cl.induztek.app.model.UsuarioDemo
import kotlinx.coroutines.launch

@Composable
fun CuentasScreen(vm: CuentasViewModel, empresas: List<ClienteEntity>, administradorId: String) {
    var nombre by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var rut by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var empresa by remember { mutableStateOf("") }
    var rol by remember { mutableStateOf(Rol.CLIENTE) }
    var cuentaEditada by remember { mutableStateOf<UsuarioDemo?>(null) }
    var aviso by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(vm) { vm.cargar() }

    LazyColumn(Modifier.testTag("lista-cuentas"), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Nueva cuenta", style = MaterialTheme.typography.titleLarge)
            if (vm.ocupado) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        item { CampoCuenta("Nombre", nombre, { nombre = it }, vm.errores["nombre"], !vm.ocupado) }
        item { CampoCuenta("Correo", correo, { correo = it }, vm.errores["correo"], !vm.ocupado) }
        item { CampoCuenta("RUT", rut, { rut = it }, vm.errores["rut"], !vm.ocupado) }
        item { CampoCuenta("Contraseña", clave, { clave = it }, vm.errores["clave"], !vm.ocupado, secreta = true) }
        item { SelectorRol(rol, !vm.ocupado) { rol = it } }
        if (rol == Rol.CLIENTE) item {
            SelectorEmpresa(empresas, empresa, !vm.ocupado) { empresa = it }
            if (empresas.isEmpty()) Text("Descarga los catálogos para elegir una empresa.")
            vm.errores["empresa"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        item {
            Button(enabled = !vm.ocupado && (rol != Rol.CLIENTE || empresas.isNotEmpty()), onClick = {
                aviso = ""
                scope.launch {
                    if (vm.crear(AltaCliente(nombre, correo, rut, clave, empresa, rol))) {
                        nombre = ""; correo = ""; rut = ""; clave = ""; empresa = ""
                        aviso = if (rol == Rol.CLIENTE) "Cuenta cliente creada." else "Cuenta creada."
                    }
                }
            }) { Text(if (rol == Rol.CLIENTE) "Crear cuenta cliente" else "Crear cuenta") }
            if (aviso.isNotBlank()) Text(aviso)
        }
        item {
            Text("Cuentas y permisos", style = MaterialTheme.typography.titleLarge)
            TextButton(enabled = !vm.ocupado, onClick = { scope.launch { vm.cargar() } }) { Text("Actualizar lista") }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        items(vm.cuentas, key = { it.id }) { cuenta ->
            val estado = when {
                cuenta.activo -> "Activa"
                cuenta.rol == Rol.CLIENTE && cuenta.clienteId == null -> "Pendiente de aprobación"
                else -> "Inactiva"
            }
            var vinculacion by remember { mutableStateOf("") }
            OutlinedCard(Modifier.fillMaxWidth().testTag("cuenta-cliente")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${cuenta.nombre} · $estado", style = MaterialTheme.typography.titleMedium)
                    Text(cuenta.correo)
                    Text(cuenta.rol.titulo())
                    if (cuenta.rol == Rol.CLIENTE && cuenta.clienteId == null) {
                        Text("RUT: ${cuenta.rut}")
                        SelectorEmpresa(empresas, vinculacion, !vm.ocupado) { vinculacion = it }
                        TextButton(enabled = !vm.ocupado && vinculacion.isNotEmpty(), onClick = {
                            scope.launch { vm.cambiarEstado(cuenta, vinculacion) }
                        }) { Text("Aprobar y activar") }
                    } else if (cuenta.id != administradorId) {
                        cuenta.clienteId?.let { id -> Text(empresas.firstOrNull { it.id == id }?.nombre ?: id) }
                        TextButton(enabled = !vm.ocupado, onClick = { scope.launch { vm.cambiarEstado(cuenta) } }) {
                            Text(if (cuenta.activo) "Desactivar" else "Activar")
                        }
                    }
                    if (cuenta.id != administradorId) {
                        TextButton(enabled = !vm.ocupado, onClick = { cuentaEditada = cuenta }) { Text("Editar rol y acceso") }
                    }
                }
            }
        }
    }
    cuentaEditada?.let { cuenta ->
        var nuevoRol by remember(cuenta.id) { mutableStateOf(cuenta.rol) }
        var nuevaEmpresa by remember(cuenta.id) { mutableStateOf(cuenta.clienteId.orEmpty()) }
        var activa by remember(cuenta.id) { mutableStateOf(cuenta.activo) }
        AlertDialog(
            onDismissRequest = { if (!vm.ocupado) cuentaEditada = null },
            title = { Text(cuenta.nombre) },
            text = {
                Column {
                    SelectorRol(nuevoRol, !vm.ocupado) { nuevoRol = it }
                    if (nuevoRol == Rol.CLIENTE) SelectorEmpresa(empresas, nuevaEmpresa, !vm.ocupado) { nuevaEmpresa = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Switch(activa, { activa = it }, enabled = !vm.ocupado)
                        Text("Acceso activo", Modifier.padding(top = 14.dp))
                    }
                    vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !vm.ocupado && (nuevoRol != Rol.CLIENTE || nuevaEmpresa.isNotBlank()), onClick = {
                    scope.launch { if (vm.modificar(cuenta, nuevoRol, nuevaEmpresa, activa)) cuentaEditada = null }
                }) { Text("Guardar cambios") }
            },
            dismissButton = {
                TextButton(enabled = !vm.ocupado, onClick = { cuentaEditada = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun SelectorRol(rol: Rol, habilitado: Boolean, elegir: (Rol) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(enabled = habilitado, onClick = { menu = true }) { Text("Rol · ${rol.titulo()}") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            Rol.entries.forEach { opcion ->
                DropdownMenuItem(text = { Text(opcion.titulo()) }, onClick = { elegir(opcion); menu = false })
            }
        }
    }
}
@Composable
private fun SelectorEmpresa(empresas: List<ClienteEntity>, seleccion: String, habilitado: Boolean, elegir: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { menu = true }, enabled = habilitado && empresas.isNotEmpty()) {
            Text(empresas.firstOrNull { it.id == seleccion }?.nombre ?: "Seleccionar empresa")
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            empresas.forEach { empresa ->
                DropdownMenuItem(text = { Text(empresa.nombre) }, onClick = { elegir(empresa.id); menu = false })
            }
        }
    }
}

@Composable
private fun CampoCuenta(titulo: String, valor: String, cambiar: (String) -> Unit, error: String?, habilitado: Boolean,
                        secreta: Boolean = false) {
    OutlinedTextField(valor, cambiar, label = { Text(titulo) }, singleLine = true, enabled = habilitado,
        isError = error != null, supportingText = error?.let { { Text(it) } },
        visualTransformation = if (secreta) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth())
}
