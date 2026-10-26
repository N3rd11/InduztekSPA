package cl.induztek.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cl.induztek.app.model.UsuarioDemo
import cl.induztek.app.model.AccesoFotos
import cl.induztek.app.ui.components.*
import cl.induztek.app.viewmodel.PerfilViewModel

@Composable
fun PerfilScreen(usuario: UsuarioDemo, vm: PerfilViewModel, accesos: AccesosMultimedia) {
    val contexto = LocalContext.current
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview(), vm::desdeCamara)
    fun abrirCamara() { try { camara.launch(null) } catch (_: Exception) { vm.noDisponible("la cámara") } }
    val permisoCamara = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitido ->
        if (permitido) abrirCamara() else vm.permisoCamaraDenegado()
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), vm::desdeGaleria)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Crossfade(targetState = vm.foto, label = "Actualizar avatar") { imagen ->
            Box(Modifier.size(128.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                if (imagen != null) Image(imagen.asImageBitmap(), "Foto de perfil", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text(usuario.nombre.split(' ').take(2).joinToString("") { it.take(1) }, style = MaterialTheme.typography.displayMedium)
            }
        }
        Text(usuario.nombre, style = MaterialTheme.typography.titleLarge)
        Text(usuario.rol.titulo(), color = MaterialTheme.colorScheme.primary)
        Text(usuario.correo)
        Text("RUT: ${usuario.rut}", style = MaterialTheme.typography.bodyMedium)
        if (vm.ocupado) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onClick = {
            if (contexto.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) abrirCamara()
            else permisoCamara.launch(Manifest.permission.CAMERA)
        },
            enabled = !vm.ocupado, modifier = Modifier.fillMaxWidth()) {
            IconoInduztek(Simbolo.CAMARA); Spacer(Modifier.width(8.dp)); Text("Tomar foto de perfil")
        }
        OutlinedButton(onClick = { try { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            catch (_: Exception) { vm.noDisponible("la galería") } }, enabled = !vm.ocupado, modifier = Modifier.fillMaxWidth()) {
            IconoInduztek(Simbolo.GALERIA); Spacer(Modifier.width(8.dp)); Text("Elegir de la galería")
        }
        AvisoInduztek(vm.mensaje)
        AvisoInduztek(vm.error, error = true)
        Text("Cámara: ${if (accesos.estado.camara) "permitida" else "sin permiso"} · Fotos: ${when(accesos.estado.fotos) {
            AccesoFotos.COMPLETO -> "acceso permitido"
            AccesoFotos.SELECCIONADAS -> "solo seleccionadas"
            AccesoFotos.NINGUNO -> "mediante selector de imágenes"
        }}", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = accesos.solicitar) { Text("Revisar permisos de cámara y fotos") }
        TextButton(onClick = {
            try { contexto.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${contexto.packageName}"))) }
            catch (_: Exception) { vm.noDisponible("los ajustes de permisos") }
        }) { Text("Abrir permisos en Ajustes") }
    }
}
