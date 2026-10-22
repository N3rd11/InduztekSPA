
package cl.induztek.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

import cl.induztek.app.model.Rol
import cl.induztek.app.model.UsuarioDemo
import cl.induztek.app.model.UsuariosDemo

import java.io.File

@Composable
fun LoginScreen(
    onAcceder: (UsuarioDemo) -> Unit
) {
    var correo by rememberSaveable {
        mutableStateOf("")
    }

    var clave by rememberSaveable {
        mutableStateOf("")
    }

    var error by remember {
        mutableStateOf("")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            "INDUZTEK",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(Modifier.height(8.dp))

        Text("Acceso a protocolos eléctricos")

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = correo,
            onValueChange = {
                correo = it
                error = ""
            },
            label = { Text("Correo electrónico") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = clave,
            onValueChange = {
                clave = it
                error = ""
            },
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                val usuario = UsuariosDemo.validar(
                    correo,
                    clave
                )

                if (usuario != null) {
                    onAcceder(usuario)
                } else {
                    error = "Credenciales incorrectas"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Iniciar sesión")
        }

        if (error.isNotEmpty()) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(24.dp))

        Text("Cuentas de demostración")
        Text("Contraseña para todas: 1234")

        UsuariosDemo.todos.forEach { usuario ->
            TextButton(
                onClick = {
                    correo = usuario.correo
                    clave = "1234"
                    error = ""
                }
            ) {
                Text(usuario.nombre)
            }
        }
    }
}

@Composable
fun InicioScreen(
    usuario: UsuarioDemo,
    onRegistrar: () -> Unit,
    onCerrarSesion: () -> Unit
) {

    val contexto = LocalContext.current

    val archivo = remember(usuario.id) {
        File(
            contexto.filesDir,
            "foto_${usuario.id}.jpg"
        )
    }

    var foto by remember(usuario.id) {
        mutableStateOf(
            BitmapFactory.decodeFile(
                archivo.absolutePath
            )
        )
    }

    var mensajeCamara by remember {
        mutableStateOf("")
    }

    val camara = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { imagen ->

        if (imagen != null) {
            try {
                archivo.outputStream().use { salida ->
                    check(
                        imagen.compress(
                            Bitmap.CompressFormat.JPEG,
                            85,
                            salida
                        )
                    )
                }

                foto = imagen
                mensajeCamara = "Fotografía guardada"

            } catch (e: Exception) {
                mensajeCamara = "Error al guardar la foto"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            "Induztek",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(8.dp))

        if (foto != null) {

            Image(
                bitmap = foto!!.asImageBitmap(),
                contentDescription = "Foto de perfil",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
            )

        } else {

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    usuario.nombre.take(1),
                    style = MaterialTheme.typography.headlineLarge
                )
            }
        }

        Text(
            usuario.nombre,
            style = MaterialTheme.typography.titleLarge
        )

        Text("Rol: ${usuario.rol.name}")

        OutlinedButton(
            onClick = {
                try {
                    camara.launch(null)
                } catch (e: Exception) {
                    mensajeCamara = "No se pudo abrir la cámara"
                }
            }
        ) {
            Text("Tomar foto de perfil")
        }

        if (mensajeCamara.isNotEmpty()) {
            Text(mensajeCamara)
        }

        HorizontalDivider()

        Text(
            "Panel principal",
            style = MaterialTheme.typography.titleMedium
        )

        if (usuario.rol != Rol.CLIENTE) {

            Button(
                onClick = onRegistrar,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Registrar mediciones")
            }

        } else {

            Text(
                "Acceso de solo consulta. " +
                        "Aquí implementaremos los protocolos " +
                        "e historial de tu empresa."
            )
        }

        OutlinedButton(
            onClick = onCerrarSesion,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cerrar sesión")
        }
    }
}
